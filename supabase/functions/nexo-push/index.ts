// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { withSupabase } from "jsr:@supabase/server@^1";

type PushRequest =
  | { event_type: "message"; message_id: string }
  | { event_type: "call"; call_id: string };

type ServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
};

type PushContext = {
  targetUserId: string;
  senderId: string;
  senderName: string;
  data: Record<string, string>;
  ttl: string;
  allowed: boolean;
};

const FIREBASE_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

function jsonError(message: string, status = 400) {
  return Response.json({ ok: false, error: message }, { status });
}

function base64UrlBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function base64UrlJson(value: unknown): string {
  return base64UrlBytes(new TextEncoder().encode(JSON.stringify(value)));
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const normalized = pem.replace(/\\n/g, "\n");
  const base64 = normalized
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const der = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    der.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

async function firebaseAccessToken(serviceAccount: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: serviceAccount.client_email,
    scope: FIREBASE_SCOPE,
    aud: serviceAccount.token_uri ?? DEFAULT_TOKEN_URI,
    iat: now,
    exp: now + 3600,
  };

  const unsigned = `${base64UrlJson(header)}.${base64UrlJson(claims)}`;
  const key = await importPrivateKey(serviceAccount.private_key);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const assertion = `${unsigned}.${base64UrlBytes(new Uint8Array(signature))}`;

  const tokenResponse = await fetch(serviceAccount.token_uri ?? DEFAULT_TOKEN_URI, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });

  const tokenBody = await tokenResponse.json();
  if (!tokenResponse.ok || !tokenBody.access_token) {
    console.error("Firebase OAuth error", tokenResponse.status, tokenBody);
    throw new Error("No se pudo autenticar con Firebase.");
  }
  return String(tokenBody.access_token);
}

async function loadSenderName(db: any, senderId: string): Promise<string> {
  const { data } = await db
    .from("profiles")
    .select("name")
    .eq("id", senderId)
    .maybeSingle();
  return data?.name?.trim() || "NEXO";
}

async function resolveMessagePush(db: any, callerId: string, messageId: string): Promise<PushContext> {
  const { data: message, error: messageError } = await db
    .from("messages")
    .select("id,match_id,sender_id")
    .eq("id", messageId)
    .maybeSingle();
  if (messageError || !message) throw new Error("MESSAGE_NOT_FOUND");
  if (message.sender_id !== callerId) throw new Error("FORBIDDEN");

  const { data: match, error: matchError } = await db
    .from("matches")
    .select("user_a,user_b")
    .eq("id", message.match_id)
    .maybeSingle();
  if (matchError || !match) throw new Error("MATCH_NOT_FOUND");

  const targetUserId = match.user_a === callerId ? match.user_b : match.user_a;
  if (!targetUserId || targetUserId === callerId) throw new Error("INVALID_TARGET");

  const { data: prefs } = await db
    .from("user_preferences")
    .select("notifications_enabled")
    .eq("user_id", targetUserId)
    .maybeSingle();

  const senderName = await loadSenderName(db, callerId);
  return {
    targetUserId,
    senderId: callerId,
    senderName,
    ttl: "3600s",
    allowed: prefs?.notifications_enabled !== false,
    data: {
      event_type: "message",
      message_id: message.id,
      sender_id: callerId,
      sender_name: senderName,
    },
  };
}

async function resolveCallPush(db: any, callerId: string, callId: string): Promise<PushContext> {
  const { data: call, error: callError } = await db
    .from("calls")
    .select("id,caller_id,callee_id,call_type,state")
    .eq("id", callId)
    .maybeSingle();
  if (callError || !call) throw new Error("CALL_NOT_FOUND");
  if (call.caller_id !== callerId) throw new Error("FORBIDDEN");
  if (!['ringing', 'connecting'].includes(call.state)) throw new Error("CALL_NOT_RINGING");

  const { data: prefs } = await db
    .from("user_preferences")
    .select("notifications_enabled,allow_calls")
    .eq("user_id", call.callee_id)
    .maybeSingle();

  const senderName = await loadSenderName(db, callerId);
  return {
    targetUserId: call.callee_id,
    senderId: callerId,
    senderName,
    ttl: "45s",
    allowed: prefs?.notifications_enabled !== false && prefs?.allow_calls !== false,
    data: {
      event_type: "call",
      call_id: call.id,
      call_type: call.call_type === "video" ? "video" : "audio",
      sender_id: callerId,
      sender_name: senderName,
    },
  };
}

async function sendFcm(
  serviceAccount: ServiceAccount,
  accessToken: string,
  token: string,
  context: PushContext,
): Promise<Response> {
  return fetch(
    `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(serviceAccount.project_id)}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          data: context.data,
          android: {
            priority: "high",
            ttl: context.ttl,
          },
        },
      }),
    },
  );
}

console.info("nexo-push started");

export default {
  fetch: withSupabase({ auth: "user" }, async (req, ctx) => {
    if (req.method !== "POST") return jsonError("Método no permitido.", 405);

    try {
      const claims = ctx.userClaims as { id?: string; sub?: string } | null;
      const callerId = claims?.id ?? claims?.sub ?? "";
      if (!callerId) return jsonError("Sesión inválida.", 401);

      const body = (await req.json()) as Partial<PushRequest>;
      let push: PushContext;

      if (body.event_type === "message" && "message_id" in body && body.message_id) {
        push = await resolveMessagePush(ctx.supabaseAdmin, callerId, body.message_id);
      } else if (body.event_type === "call" && "call_id" in body && body.call_id) {
        push = await resolveCallPush(ctx.supabaseAdmin, callerId, body.call_id);
      } else {
        return jsonError("Payload inválido.");
      }

      if (!push.allowed) {
        return Response.json({ ok: true, delivered: 0, skipped: "notifications_disabled" });
      }

      const { data: devices, error: deviceError } = await ctx.supabaseAdmin
        .from("devices")
        .select("id,push_token")
        .eq("user_id", push.targetUserId)
        .eq("enabled", true);
      if (deviceError) throw deviceError;
      if (!devices?.length) {
        return Response.json({ ok: true, delivered: 0, skipped: "no_devices" });
      }

      const rawCredentials = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
      if (!rawCredentials) throw new Error("FIREBASE_SECRET_MISSING");
      const serviceAccount = JSON.parse(rawCredentials) as ServiceAccount;
      if (!serviceAccount.project_id || !serviceAccount.client_email || !serviceAccount.private_key) {
        throw new Error("FIREBASE_SECRET_INVALID");
      }

      const accessToken = await firebaseAccessToken(serviceAccount);
      let delivered = 0;
      let failed = 0;

      for (const device of devices) {
        const response = await sendFcm(serviceAccount, accessToken, device.push_token, push);
        if (response.ok) {
          delivered += 1;
          continue;
        }

        failed += 1;
        const errorText = await response.text();
        console.warn("FCM delivery failed", response.status, errorText);

        if (response.status === 404 || errorText.includes("UNREGISTERED")) {
          await ctx.supabaseAdmin
            .from("devices")
            .update({ enabled: false })
            .eq("id", device.id);
        }
      }

      return Response.json({ ok: true, delivered, failed });
    } catch (error) {
      const code = error instanceof Error ? error.message : "UNKNOWN";
      if (code === "FORBIDDEN") return jsonError("No podés enviar este push.", 403);
      if (["MESSAGE_NOT_FOUND", "MATCH_NOT_FOUND", "CALL_NOT_FOUND"].includes(code)) {
        return jsonError("El evento ya no existe.", 404);
      }
      if (code === "CALL_NOT_RINGING") return jsonError("La llamada ya no está sonando.", 409);
      console.error("nexo-push error", error);
      return jsonError("No se pudo enviar la notificación.", 500);
    }
  }),
};
