package ni.nexo.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ni.nexo.app.billing.PlayBillingManager
import ni.nexo.app.billing.StoreProduct
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.PremiumEntitlements
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoGradientButton
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun NexoPlusScreen(
    repository: NexoRepository,
    onBack: () -> Unit,
    onEntitlementsChanged: (PremiumEntitlements) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var entitlements by remember { mutableStateOf(PremiumEntitlements()) }
    var secureBillingReady by remember { mutableStateOf(false) }
    var checkingBackend by remember { mutableStateOf(true) }
    val billing = remember(repository) {
        PlayBillingManager(context, repository) {
            scope.launch {
                entitlements = repository.loadPremiumEntitlements()
                onEntitlementsChanged(entitlements)
            }
        }
    }
    val billingState by billing.state.collectAsState()

    LaunchedEffect(repository) {
        entitlements = runCatching { repository.loadPremiumEntitlements() }.getOrDefault(PremiumEntitlements())
        onEntitlementsChanged(entitlements)
        secureBillingReady = repository.isSecureBillingReady()
        checkingBackend = false
    }
    DisposableEffect(billing) {
        billing.start()
        onDispose { billing.close() }
    }

    NexoBackdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Volver", tint = Color.White) }
                Column {
                    Text("NEXO Plus", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(if (entitlements.plusActive) "Tu plan está activo" else "Más control para encontrar tu conexión", color = NexoCyan, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(color = NexoPurple.copy(alpha = 0.28f), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Diamond, null, tint = NexoCyan)
                    Spacer(Modifier.height(8.dp))
                    Text("Lo esencial seguirá gratis", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text("Mensajes, contactos, bloqueos, reportes y Cita Segura no requieren suscripción.", color = NexoMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            FeatureList()
            Spacer(Modifier.height(14.dp))

            val products = billingState.products.sortedBy { productOrder(it.productId) }
            if (products.isEmpty()) {
                UnavailableProductCard("NEXO Plus mensual", "Filtros avanzados y personalización premium")
                UnavailableProductCard("NEXO Plus anual", "Los mismos beneficios con facturación anual")
                UnavailableProductCard("Impulso por 24 horas", "Prioridad temporal sin ocultar perfiles gratuitos")
            } else {
                products.forEach { product ->
                    ProductCard(
                        product = product,
                        active = if (product.productId == PlayBillingManager.BOOST_24H) {
                            entitlements.boostActive
                        } else {
                            entitlements.plusActive
                        },
                        enabled = activity != null && secureBillingReady && !billingState.processing,
                        onBuy = { activity?.let { billing.launchPurchase(it, product.productId) } }
                    )
                    Spacer(Modifier.height(9.dp))
                }
            }

            billingState.message?.let {
                Surface(color = NexoNightSoft, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = NexoCyan, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(13.dp))
                }
                Spacer(Modifier.height(10.dp))
            }

            if (!checkingBackend && !secureBillingReady) {
                Surface(color = Color(0xFF3D2631), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Los pagos permanecen bloqueados hasta desplegar la verificación segura de Google Play. Así evitamos cargos sin beneficios.",
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(15.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick = { billing.restorePurchases() },
                enabled = billingState.ready && secureBillingReady && !billingState.processing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(17.dp)
            ) {
                Icon(Icons.Rounded.Payment, null)
                Text(" Restaurar compras")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Forma de pago: Google Play mostrará los métodos disponibles en la cuenta y el precio en su moneda. NEXO no recibe ni almacena números de tarjeta.",
                color = NexoMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeatureList() {
    listOf(
        "Filtros por ciudad, intención y personas en línea",
        "Fondos y estilos premium para conversaciones",
        "Prioridad Plus equilibrada en descubrimiento",
        "Restauración de compras en tus dispositivos"
    ).forEach { feature ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = NexoCyan)
            Spacer(Modifier.padding(5.dp))
            Text(feature, color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProductCard(product: StoreProduct, active: Boolean, enabled: Boolean, onBuy: () -> Unit) {
    Surface(color = NexoNightSoft.copy(alpha = 0.90f), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (product.productId == PlayBillingManager.BOOST_24H) Icons.Rounded.Bolt else Icons.Rounded.Diamond, null, tint = NexoCyan)
                Spacer(Modifier.padding(5.dp))
                Column(Modifier.weight(1f)) {
                    Text(product.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(product.description, color = NexoMuted, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Text(product.formattedPrice, color = NexoCyan, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            NexoGradientButton(
                text = if (active) {
                    if (product.recurring) "Plan activo" else "Impulso activo"
                } else if (product.recurring) {
                    "Suscribirme con Google Play"
                } else {
                    "Comprar impulso"
                },
                onClick = onBuy,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && !active
            )
        }
    }
}

@Composable
private fun UnavailableProductCard(title: String, subtitle: String) {
    Surface(color = NexoNightSoft.copy(alpha = 0.75f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = NexoMuted, fontSize = 11.sp)
            Text("Precio pendiente de Play Console", color = NexoCyan, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

private fun productOrder(productId: String): Int = when (productId) {
    PlayBillingManager.PLUS_MONTHLY -> 0
    PlayBillingManager.PLUS_YEARLY -> 1
    PlayBillingManager.BOOST_24H -> 2
    else -> 3
}
