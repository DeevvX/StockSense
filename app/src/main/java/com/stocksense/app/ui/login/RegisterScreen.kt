package com.stocksense.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

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
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = (-50).dp)
                    .blur(80.dp)
                    .background(SSColors.PurpleDim, CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Logo compacto
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SSColors.CyanDim)
                            .border(1.dp, SSColors.CyanGlow, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = null,
                            tint = SSColors.Cyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "StockSense",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = SSColors.Cyan,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Crear cuenta nueva",
                            fontSize = 10.sp,
                            color = SSColors.TextMuted,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Card formulario
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SSColors.Card)
                        .border(1.dp, SSColors.CardBorder, RoundedCornerShape(20.dp))
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        SSTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = "Nombre completo",
                            icon = Icons.Filled.Person
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
                        SSTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = "Confirmar contraseña",
                            icon = Icons.Filled.Lock,
                            keyboardType = KeyboardType.Password,
                            isPassword = true
                        )

                        val btnBrush = if (isLoading)
                            Brush.horizontalGradient(listOf(SSColors.Green, SSColors.Green))
                        else
                            Brush.horizontalGradient(listOf(SSColors.Purple, SSColors.Cyan))

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
                                ) {
                                    scope.launch {
                                        val cleanName = name.trim()
                                        val cleanEmail = email.trim()
                                        when {
                                            cleanName.isBlank() -> snackbarHostState.showSnackbar("Ingresa tu nombre")
                                            cleanEmail.isBlank() -> snackbarHostState.showSnackbar("Ingresa tu email")
                                            password.isBlank() -> snackbarHostState.showSnackbar("Ingresa una contraseña")
                                            password.length < 6 -> snackbarHostState.showSnackbar("Mínimo 6 caracteres")
                                            password != confirmPassword -> snackbarHostState.showSnackbar("Las contraseñas no coinciden")
                                            else -> {
                                                isLoading = true
                                                try {
                                                    val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                                                    result.user?.updateProfile(
                                                        UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
                                                    )?.await()
                                                    isLoading = false
                                                    onRegisterSuccess()
                                                } catch (e: Exception) {
                                                    isLoading = false
                                                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Error al crear cuenta")
                                                }
                                            }
                                        }
                                    }
                                },
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
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = SSColors.BgDeep,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Crear cuenta",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SSColors.BgDeep
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = SSColors.CardBorder)
                            Text("o", fontSize = 10.sp, color = SSColors.TextMuted)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = SSColors.CardBorder)
                        }

                        Text(
                            text = "¿Ya tienes cuenta? Inicia sesión",
                            fontSize = 12.sp,
                            color = SSColors.Cyan,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onNavigateToLogin() },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}