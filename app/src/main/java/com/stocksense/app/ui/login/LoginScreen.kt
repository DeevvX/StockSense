package com.stocksense.app.ui.login

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ── Paleta StockSense ────────────────────────────────────────────────
object SSColors {
    val BgDeep    = Color(0xFF060D1F)
    val BgMid     = Color(0xFF0A1628)
    val BgSurface = Color(0xFF0D1F35)
    val Card      = Color(0xFF111827)
    val CardBorder= Color(0xFF1E2D45)
    val Cyan      = Color(0xFF00D4FF)
    val CyanDim   = Color(0x2200D4FF)
    val CyanGlow  = Color(0x4400D4FF)
    val Purple    = Color(0xFF7C3AED)
    val PurpleDim = Color(0x227C3AED)
    val Green     = Color(0xFF00FF9C)
    val Text      = Color(0xFFF0F4FF)
    val TextMuted = Color(0xFF6B7FA3)
    val TextDim   = Color(0xFF374151)
}

val bgGradient = Brush.verticalGradient(
    colors = listOf(SSColors.BgDeep, SSColors.BgMid, SSColors.BgSurface)
)

// ── Input personalizado ──────────────────────────────────────────────
@Composable
fun SSTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (focused) SSColors.Cyan else SSColors.CardBorder,
        animationSpec = tween(200), label = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (focused) SSColors.CyanDim else Color(0xFF0D1525),
        animationSpec = tween(200), label = "bg"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = SSColors.TextMuted,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (focused) SSColors.Cyan else SSColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = SSColors.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(SSColors.Cyan),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                text = if (isPassword) "••••••••" else label,
                                color = SSColors.TextDim,
                                fontSize = 13.sp
                            )
                        }
                        inner()
                    }
                )
            }
        }
    }
}

// ── Logo de Google con colores oficiales ────────────────────────────
@Composable
fun GoogleLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f

        // Fondo blanco circular
        drawCircle(color = Color.White, radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy))

        // Colores Google
        val blue   = Color(0xFF4285F4)
        val red    = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green  = Color(0xFF34A853)

        // Arco azul (derecha, cuadrante 4 → 1, aprox -30° a 90°)
        drawArc(color = blue,   startAngle = -30f,  sweepAngle = 120f, useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.38f),
            topLeft = androidx.compose.ui.geometry.Offset(cx - r * 0.62f, cy - r * 0.62f),
            size = androidx.compose.ui.geometry.Size(r * 1.24f, r * 1.24f))
        // Arco rojo (arriba)
        drawArc(color = red,    startAngle = -150f, sweepAngle = 120f, useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.38f),
            topLeft = androidx.compose.ui.geometry.Offset(cx - r * 0.62f, cy - r * 0.62f),
            size = androidx.compose.ui.geometry.Size(r * 1.24f, r * 1.24f))
        // Arco amarillo (abajo izquierda)
        drawArc(color = yellow, startAngle = 90f,   sweepAngle = 90f,  useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.38f),
            topLeft = androidx.compose.ui.geometry.Offset(cx - r * 0.62f, cy - r * 0.62f),
            size = androidx.compose.ui.geometry.Size(r * 1.24f, r * 1.24f))
        // Arco verde (abajo derecha)
        drawArc(color = green,  startAngle = 180f,  sweepAngle = -90f, useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.38f),
            topLeft = androidx.compose.ui.geometry.Offset(cx - r * 0.62f, cy - r * 0.62f),
            size = androidx.compose.ui.geometry.Size(r * 1.24f, r * 1.24f))

        // Barra horizontal azul (el palo de la G)
        drawRect(
            color = blue,
            topLeft = androidx.compose.ui.geometry.Offset(cx, cy - r * 0.19f),
            size = androidx.compose.ui.geometry.Size(r * 0.62f, r * 0.38f)
        )
    }
}

// ── Login Screen ─────────────────────────────────────────────────────
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val loginState by viewModel.loginState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val isLoading = loginState is LoginState.Loading

    LaunchedEffect(loginState) {
        when (val s = loginState) {
            is LoginState.Success -> { viewModel.resetState(); onLoginSuccess() }
            is LoginState.Error   -> { snackbarHostState.showSnackbar(s.message); viewModel.resetState() }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(padding)
        ) {
            // Glow orb fondo
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = (-60).dp)
                    .blur(80.dp)
                    .background(SSColors.CyanDim, CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Logo ────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Brush.linearGradient(listOf(SSColors.CyanDim, SSColors.PurpleDim)))
                                .border(1.5.dp, SSColors.CyanGlow, RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ShoppingCart,
                                contentDescription = "StockSense",
                                tint = SSColors.Cyan,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .offset(x = 3.dp, y = (-3).dp)
                                .background(SSColors.Green, CircleShape)
                                .border(2.dp, SSColors.BgDeep, CircleShape)
                        )
                    }

                    Text(
                        text = "StockSense",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = SSColors.Cyan,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "Control Inteligente de Inventario",
                        fontSize = 10.sp,
                        color = SSColors.TextMuted,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(32.dp))

                // ── Card formulario ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SSColors.Card)
                        .border(1.dp, SSColors.CardBorder, RoundedCornerShape(20.dp))
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Iniciar Sesión",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SSColors.Text,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        SSTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Correo electrónico",
                            icon = Icons.Filled.Email,
                            keyboardType = KeyboardType.Email
                        )

                        SSTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Contraseña",
                            icon = Icons.Filled.Lock,
                            keyboardType = KeyboardType.Password,
                            isPassword = true
                        )

                        Text(
                            text = "¿Olvidaste tu contraseña?",
                            fontSize = 10.sp,
                            color = SSColors.Cyan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {},
                            textAlign = TextAlign.End
                        )

                        // Botón principal con gradiente
                        val btnBrush = if (isLoading)
                            Brush.horizontalGradient(listOf(SSColors.Green, SSColors.Green))
                        else
                            Brush.horizontalGradient(listOf(SSColors.Cyan, SSColors.Purple))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(btnBrush)
                                .clickable(
                                    enabled = !isLoading,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { viewModel.login(email.trim(), password) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = SSColors.BgDeep,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = SSColors.BgDeep,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Entrar al Sistema",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SSColors.BgDeep
                                    )
                                }
                            }
                        }

                        // Divisor "o continúa con"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = SSColors.CardBorder)
                            Text("o continúa con", fontSize = 10.sp, color = SSColors.TextMuted)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = SSColors.CardBorder)
                        }

                        // Botón Google Sign-In
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1A1F2E))
                                .border(1.dp, SSColors.CardBorder, RoundedCornerShape(14.dp))
                                .clickable(
                                    enabled = !isLoading,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { viewModel.loginConGoogle(context) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GoogleLogo(modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Continuar con Google",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SSColors.Text
                                )
                            }
                        }

                        Text(
                            text = "¿No tienes cuenta? Regístrate",
                            fontSize = 12.sp,
                            color = SSColors.Cyan,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onNavigateToRegister() },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}