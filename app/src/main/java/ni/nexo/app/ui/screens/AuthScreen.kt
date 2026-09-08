package ni.nexo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.data.SupabaseClientProvider
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoGradientButton
import ni.nexo.app.ui.components.NexoGlassCard
import ni.nexo.app.ui.components.NexoLogoMark
import ni.nexo.app.ui.components.NexoWordmark
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoSuccess

@Composable
fun AuthScreen(
    startInRegisterMode: Boolean,
    backendConfigured: Boolean,
    backendIssue: String? = SupabaseClientProvider.configurationIssue,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onSubmit: (register: Boolean, email: String, password: String) -> Unit,
    onGoogle: () -> Unit,
    onFacebook: () -> Unit
) {
    var registerMode by remember(startInRegisterMode) { mutableStateOf(startInRegisterMode) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    NexoBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !busy) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (backendConfigured) "Protegido por Supabase" else "Modo demo",
                    color = if (backendConfigured) NexoSuccess else NexoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!backendConfigured && !backendIssue.isNullOrBlank()) {
                Text(
                    text = backendIssue,
                    color = NexoPink,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            NexoLogoMark(Modifier.size(82.dp))
            Spacer(Modifier.height(10.dp))
            NexoWordmark(compact = true)
            Spacer(Modifier.height(18.dp))

            Text(
                text = if (registerMode) "Creá tu conexión" else "Bienvenido de nuevo",
                color = Color.White,
                fontSize = 29.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = if (registerMode)
                    "Un perfil auténtico, privado y hecho para conexiones reales."
                else
                    "Entrá y seguí donde dejaste tus conversaciones.",
                color = NexoMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))
            NexoGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Correo electrónico") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Email, contentDescription = null)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = nexoFieldColors()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = nexoFieldColors()
                    )

                    if (!message.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = message,
                            color = NexoCyan,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    NexoGradientButton(
                        text = if (registerMode) "Crear mi cuenta" else "Continuar",
                        onClick = { onSubmit(registerMode, email, password) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = email.contains("@") && password.length >= 6,
                        busy = busy
                    )

                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(
                            Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(NexoMuted.copy(alpha = 0.25f))
                        )
                        Text(
                            "  o continúa con  ",
                            color = NexoMuted,
                            fontSize = 12.sp
                        )
                        Spacer(
                            Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(NexoMuted.copy(alpha = 0.25f))
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    SocialButton(
                        label = "Continuar con Google",
                        iconText = "G",
                        iconBackground = Color.White,
                        iconColor = Color(0xFF4285F4),
                        enabled = !busy,
                        onClick = onGoogle
                    )
                    Spacer(Modifier.height(10.dp))
                    SocialButton(
                        label = "Continuar con Facebook",
                        iconText = "f",
                        iconBackground = Color(0xFF1877F2),
                        iconColor = Color.White,
                        enabled = !busy,
                        onClick = onFacebook
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            TextButton(
                enabled = !busy,
                onClick = {
                    registerMode = !registerMode
                    password = ""
                }
            ) {
                Text(
                    if (registerMode) "¿Ya tenés cuenta? Iniciá sesión" else "¿No tenés cuenta? Registrate",
                    color = NexoPink,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Al continuar confirmás que sos mayor de 18 años.",
                color = NexoMuted.copy(alpha = 0.75f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SocialButton(
    label: String,
    iconText: String,
    iconBackground: Color,
    iconColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(iconBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(iconText, color = iconColor, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            Spacer(Modifier.size(12.dp))
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun nexoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = NexoCyan,
    unfocusedBorderColor = NexoMuted.copy(alpha = 0.42f),
    focusedLeadingIconColor = NexoCyan,
    unfocusedLeadingIconColor = NexoMuted,
    focusedTrailingIconColor = NexoCyan,
    unfocusedTrailingIconColor = NexoMuted,
    focusedPlaceholderColor = NexoMuted,
    unfocusedPlaceholderColor = NexoMuted,
    cursorColor = NexoPink,
    focusedContainerColor = Color.White.copy(alpha = 0.045f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
)
