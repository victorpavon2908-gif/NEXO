package ni.nexo.app.ui.calls

import android.content.Context
import android.media.AudioManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ni.nexo.app.BuildConfig
import ni.nexo.app.data.CallRecord
import ni.nexo.app.data.CallSignal
import ni.nexo.app.data.CallSignalType
import ni.nexo.app.data.NexoRepository
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Motor WebRTC P2P de NEXO.
 *
 * Supabase solo transporta offer/answer/ICE. El audio y video viajan por WebRTC
 * cifrados con DTLS-SRTP. STUN funciona sin configuración adicional; para redes
 * restrictivas se admite TURN mediante NEXO_TURN_URLS/USERNAME/CREDENTIAL.
 */
class WebRtcCallEngine(
    context: Context,
    private val repository: NexoRepository,
    private val call: CallRecord,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onRemoteHangup()
        fun onConnectionFailed(message: String)
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val eglBase = EglBase.create()
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val previousAudioMode = audioManager.mode
    private val previousSpeaker = audioManager.isSpeakerphoneOn

    private val factory: PeerConnectionFactory
    private val peerConnection: PeerConnection
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteDescriptionReady = false
    private var started = false
    private var closed = false

    // Debe existir antes de crear PeerConnection. Kotlin ejecuta inicializadores
    // de propiedades en orden, por eso el observer vive antes del bloque init.
    private val peerObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            when (newState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> listener.onConnected()
                PeerConnection.IceConnectionState.FAILED ->
                    listener.onConnectionFailed("La conexión P2P falló. Revisá tu red o configuración TURN.")
                else -> Unit
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

        override fun onIceCandidate(candidate: IceCandidate) {
            val payload = buildJsonObject {
                put("sdpMid", candidate.sdpMid ?: "")
                put("sdpMLineIndex", candidate.sdpMLineIndex)
                put("candidate", candidate.sdp)
            }.toString()
            scope.launch {
                runCatching { repository.sendCallSignal(call.id, CallSignalType.Ice, payload) }
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

        override fun onAddStream(stream: MediaStream) {
            stream.videoTracks.firstOrNull()?.let(::bindRemoteVideoTrack)
        }

        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(dataChannel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            val track = receiver.track()
            if (track?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                (track as? VideoTrack)?.let(::bindRemoteVideoTrack)
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                (track as? VideoTrack)?.let(::bindRemoteVideoTrack)
            }
        }
    }

    init {
        initializeWebRtc(appContext)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = call.type.name.equals("Video", ignoreCase = true)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource).apply { setEnabled(true) }

        peerConnection = factory.createPeerConnection(createRtcConfiguration(), peerObserver)
            ?: error("No se pudo crear la conexión WebRTC.")
        peerConnection.addTrack(audioTrack, listOf(STREAM_ID))

        if (call.type.name.equals("Video", ignoreCase = true)) {
            setupVideo()
        }
    }

    fun start() {
        if (started || closed) return
        started = true
        if (call.outgoing) createOffer()
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        if (closed) return
        localRenderer?.let { old -> if (old !== renderer) runCatching { old.release() } }
        localRenderer = renderer
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(true)
        videoTrack?.addSink(renderer)
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        if (closed) return
        remoteRenderer?.let { old -> if (old !== renderer) runCatching { old.release() } }
        remoteRenderer = renderer
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(false)
        remoteVideoTrack?.addSink(renderer)
    }

    fun setMuted(muted: Boolean) {
        audioTrack.setEnabled(!muted)
    }

    fun setVideoEnabled(enabled: Boolean) {
        videoTrack?.setEnabled(enabled)
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun handleSignal(signal: CallSignal) {
        if (closed) return
        when (signal.type) {
            CallSignalType.Offer -> handleOffer(signal.payload)
            CallSignalType.Answer -> handleAnswer(signal.payload)
            CallSignalType.Ice -> handleIce(signal.payload)
            CallSignalType.Hangup -> listener.onRemoteHangup()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        runCatching { videoCapturer?.stopCapture() }
        runCatching { localRenderer?.let { videoTrack?.removeSink(it) } }
        runCatching { remoteRenderer?.let { remoteVideoTrack?.removeSink(it) } }
        runCatching { localRenderer?.release() }
        runCatching { remoteRenderer?.release() }
        runCatching { surfaceTextureHelper?.dispose() }
        runCatching { videoCapturer?.dispose() }
        runCatching { videoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioTrack.dispose() }
        runCatching { audioSource.dispose() }
        runCatching { peerConnection.close() }
        runCatching { peerConnection.dispose() }
        runCatching { factory.dispose() }
        runCatching { eglBase.release() }
        audioManager.mode = previousAudioMode
        audioManager.isSpeakerphoneOn = previousSpeaker
    }

    private fun setupVideo() {
        val enumerator: CameraEnumerator = Camera2Enumerator(appContext)
        val capturer = createCameraCapturer(enumerator)
            ?: error("No encontramos una cámara disponible para la videollamada.")
        videoCapturer = capturer
        surfaceTextureHelper = SurfaceTextureHelper.create("NEXO-Camera", eglBase.eglBaseContext)
        videoSource = factory.createVideoSource(false)
        capturer.initialize(surfaceTextureHelper, appContext, videoSource!!.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        videoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource).apply { setEnabled(true) }
        peerConnection.addTrack(videoTrack, listOf(STREAM_ID))
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): CameraVideoCapturer? {
        val front = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val back = enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        return listOfNotNull(front, back).firstNotNullOfOrNull { name ->
            runCatching { enumerator.createCapturer(name, null) }.getOrNull()
        }
    }

    private fun createRtcConfiguration(): PeerConnection.RTCConfiguration {
        val servers = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val turnUrls = BuildConfig.NEXO_TURN_URLS
            .split(',', ';')
            .map(String::trim)
            .filter(String::isNotBlank)
        turnUrls.forEach { url ->
            val builder = PeerConnection.IceServer.builder(url)
            if (BuildConfig.NEXO_TURN_USERNAME.isNotBlank()) builder.setUsername(BuildConfig.NEXO_TURN_USERNAME)
            if (BuildConfig.NEXO_TURN_CREDENTIAL.isNotBlank()) builder.setPassword(BuildConfig.NEXO_TURN_CREDENTIAL)
            servers += builder.createIceServer()
        }

        return PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
    }

    private fun createOffer() {
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                setLocalAndSignal(description, CallSignalType.Offer)
            }

            override fun onCreateFailure(error: String) {
                listener.onConnectionFailed("No pudimos crear la oferta WebRTC: $error")
            }
        }, offerConstraints())
    }

    private fun createAnswer() {
        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                setLocalAndSignal(description, CallSignalType.Answer)
            }

            override fun onCreateFailure(error: String) {
                listener.onConnectionFailed("No pudimos responder la llamada: $error")
            }
        }, offerConstraints())
    }

    private fun setLocalAndSignal(description: SessionDescription, type: CallSignalType) {
        peerConnection.setLocalDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                val payload = buildJsonObject {
                    put("sdp", description.description)
                }.toString()
                scope.launch {
                    runCatching { repository.sendCallSignal(call.id, type, payload) }
                        .onFailure { listener.onConnectionFailed("No pudimos enviar la señal de llamada.") }
                }
            }

            override fun onSetFailure(error: String) {
                listener.onConnectionFailed("No pudimos preparar el audio/video local: $error")
            }
        }, description)
    }

    private fun handleOffer(payload: String) {
        if (call.outgoing) return
        val sdp = runCatching { json.parseToJsonElement(payload).jsonObject["sdp"]?.jsonPrimitive?.content }
            .getOrNull() ?: return
        val description = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionReady = true
                flushPendingIce()
                createAnswer()
            }

            override fun onSetFailure(error: String) {
                listener.onConnectionFailed("No pudimos abrir la oferta remota: $error")
            }
        }, description)
    }

    private fun handleAnswer(payload: String) {
        if (!call.outgoing) return
        val sdp = runCatching { json.parseToJsonElement(payload).jsonObject["sdp"]?.jsonPrimitive?.content }
            .getOrNull() ?: return
        val description = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionReady = true
                flushPendingIce()
            }

            override fun onSetFailure(error: String) {
                listener.onConnectionFailed("No pudimos abrir la respuesta remota: $error")
            }
        }, description)
    }

    private fun handleIce(payload: String) {
        val candidate = runCatching {
            val obj = json.parseToJsonElement(payload).jsonObject
            IceCandidate(
                obj["sdpMid"]?.jsonPrimitive?.content,
                obj["sdpMLineIndex"]?.jsonPrimitive?.int ?: 0,
                obj["candidate"]?.jsonPrimitive?.content ?: return
            )
        }.getOrNull() ?: return
        if (remoteDescriptionReady) peerConnection.addIceCandidate(candidate)
        else pendingIce += candidate
    }

    private fun flushPendingIce() {
        pendingIce.forEach(peerConnection::addIceCandidate)
        pendingIce.clear()
    }

    private fun offerConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo",
                if (call.type.name.equals("Video", ignoreCase = true)) "true" else "false"
            )
        )
    }

    private fun bindRemoteVideoTrack(track: VideoTrack) {
        remoteVideoTrack?.let { previous ->
            remoteRenderer?.let { runCatching { previous.removeSink(it) } }
        }
        remoteVideoTrack = track
        remoteRenderer?.let(track::addSink)
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }

    companion object {
        private const val STREAM_ID = "nexo-stream"
        private const val AUDIO_TRACK_ID = "nexo-audio"
        private const val VIDEO_TRACK_ID = "nexo-video"
        private val initialized = AtomicBoolean(false)

        private fun initializeWebRtc(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
            }
        }
    }
}
