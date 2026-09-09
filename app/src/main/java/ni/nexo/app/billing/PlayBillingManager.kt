package ni.nexo.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ni.nexo.app.data.NexoRepository

data class StoreProduct(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val recurring: Boolean
)

data class BillingUiState(
    val connecting: Boolean = true,
    val ready: Boolean = false,
    val products: List<StoreProduct> = emptyList(),
    val processing: Boolean = false,
    val message: String? = null
)

class PlayBillingManager(
    context: Context,
    private val repository: NexoRepository,
    private val onEntitlementsChanged: () -> Unit
) : PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val detailsById = linkedMapOf<String, ProductDetails>()
    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (client.isReady) {
            loadProducts()
            return
        }
        _state.value = _state.value.copy(connecting = true, message = null)
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(connecting = false, ready = true)
                    loadProducts()
                    restorePurchases(silent = true)
                } else {
                    _state.value = _state.value.copy(
                        connecting = false,
                        ready = false,
                        message = "Google Play no está disponible en este dispositivo."
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(ready = false, message = "Reconectando con Google Play…")
            }
        })
    }

    fun launchPurchase(activity: Activity, productId: String) {
        val details = detailsById[productId]
        if (!client.isReady || details == null) {
            _state.value = _state.value.copy(message = "Este producto todavía no está disponible en Google Play.")
            return
        }
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        details.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let(paramsBuilder::setOfferToken)
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(paramsBuilder.build())).build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(message = "No pudimos abrir el pago de Google Play.")
        } else {
            _state.value = _state.value.copy(processing = true, message = null)
        }
    }

    fun restorePurchases(silent: Boolean = false) {
        if (!client.isReady) {
            if (!silent) _state.value = _state.value.copy(message = "Google Play todavía se está conectando.")
            return
        }
        _state.value = _state.value.copy(processing = true, message = if (silent) null else "Buscando compras…")
        var pendingQueries = 2
        val collected = mutableListOf<Purchase>()
        fun complete(result: BillingResult, purchases: List<Purchase>) {
            if (result.responseCode == BillingClient.BillingResponseCode.OK) collected += purchases
            pendingQueries--
            if (pendingQueries == 0) {
                if (collected.isEmpty()) {
                    _state.value = _state.value.copy(processing = false, message = if (silent) null else "No encontramos compras activas.")
                } else {
                    processPurchases(collected.distinctBy { it.purchaseToken }, restored = true)
                }
            }
        }
        listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).forEach { type ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, purchases ->
                complete(result, purchases)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty(), restored = false)
            BillingClient.BillingResponseCode.USER_CANCELED ->
                _state.value = _state.value.copy(processing = false, message = "Pago cancelado. No se realizó ningún cargo.")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restorePurchases()
            else -> _state.value = _state.value.copy(processing = false, message = "Google Play no pudo completar el pago.")
        }
    }

    fun close() {
        if (client.isReady) client.endConnection()
        scope.cancel()
    }

    private fun loadProducts() {
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PLUS_MONTHLY).setProductType(BillingClient.ProductType.SUBS).build(),
            QueryProductDetailsParams.Product.newBuilder().setProductId(PLUS_YEARLY).setProductType(BillingClient.ProductType.SUBS).build(),
            QueryProductDetailsParams.Product.newBuilder().setProductId(BOOST_24H).setProductType(BillingClient.ProductType.INAPP).build()
        )
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build()
        ) { result, response ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.value = _state.value.copy(message = "No pudimos consultar los precios de Google Play.")
                return@queryProductDetailsAsync
            }
            detailsById.clear()
            response.productDetailsList.forEach { detailsById[it.productId] = it }
            _state.value = _state.value.copy(
                products = response.productDetailsList.mapNotNull(::toStoreProduct),
                message = if (response.productDetailsList.isEmpty()) "Los productos todavía deben publicarse en Play Console." else null
            )
        }
    }

    private fun processPurchases(purchases: List<Purchase>, restored: Boolean) {
        val purchased = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased.isEmpty()) {
            _state.value = _state.value.copy(processing = false, message = "El pago está pendiente de confirmación.")
            return
        }
        _state.value = _state.value.copy(processing = true, message = "Verificando la compra de forma segura…")
        scope.launch {
            var verifiedAny = false
            purchased.forEach { purchase ->
                val verified = runCatching {
                    repository.verifyGooglePlayPurchase(purchase.purchaseToken, purchase.products)
                }.getOrDefault(false)
                if (verified) {
                    verifiedAny = true
                    if (BOOST_24H in purchase.products) {
                        client.consumeAsync(
                            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                        ) { _, _ -> }
                    } else if (!purchase.isAcknowledged) {
                        client.acknowledgePurchase(
                            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                        ) { }
                    }
                }
            }
            if (verifiedAny) onEntitlementsChanged()
            _state.value = _state.value.copy(
                processing = false,
                message = if (verifiedAny) {
                    if (restored) "Compra restaurada correctamente." else "Compra verificada. Tus beneficios ya están activos."
                } else {
                    "La compra llegó, pero el servidor aún no pudo verificarla. No se activaron beneficios; Google Play la reintentará o la reembolsará si no puede confirmarse."
                }
            )
        }
    }

    private fun toStoreProduct(details: ProductDetails): StoreProduct? {
        val subscriptionPrice = details.subscriptionOfferDetails
            ?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        val oneTimePrice = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
        val price = subscriptionPrice ?: oneTimePrice ?: return null
        return StoreProduct(
            productId = details.productId,
            title = details.title.substringBefore(" (").ifBlank { productTitle(details.productId) },
            description = details.description,
            formattedPrice = price,
            recurring = details.productType == BillingClient.ProductType.SUBS
        )
    }

    companion object {
        const val PLUS_MONTHLY = "nexo_plus_monthly"
        const val PLUS_YEARLY = "nexo_plus_yearly"
        const val BOOST_24H = "nexo_boost_24h"

        fun productTitle(productId: String): String = when (productId) {
            PLUS_MONTHLY -> "NEXO Plus mensual"
            PLUS_YEARLY -> "NEXO Plus anual"
            BOOST_24H -> "Impulso de perfil por 24 horas"
            else -> "NEXO"
        }
    }
}
