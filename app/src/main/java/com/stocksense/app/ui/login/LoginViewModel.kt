package com.stocksense.app.ui.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Web Client ID obtenido de google-services.json (oauth_client con client_type: 3)
    private val WEB_CLIENT_ID =
        "1009563458201-losklmlsjvk476al75qge03mq6t8rthc.apps.googleusercontent.com"

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank()) {
            _loginState.value = LoginState.Error("Ingresa tu email")
            return
        }

        if (password.isBlank()) {
            _loginState.value = LoginState.Error("Ingresa tu contraseña")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _loginState.value = LoginState.Success
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(
                    e.localizedMessage ?: "No se pudo iniciar sesión"
                )
            }
        }
    }

    /**
     * Inicia sesión con Google usando Credential Manager (API moderno de Android).
     * Requiere el Context de la Activity para lanzar el selector de cuentas de Google.
     * El flujo es:
     *   1. Credential Manager muestra el selector de cuentas Google.
     *   2. El usuario elige su cuenta → Google devuelve un ID Token.
     *   3. Usamos ese token para autenticar con Firebase Auth.
     */
    fun loginConGoogle(context: Context) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            try {
                val credentialManager = CredentialManager.create(context)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false) // muestra TODAS las cuentas Google
                    .setServerClientId(WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false) // el usuario elige, no selección automática
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )

                val googleIdTokenCredential = GoogleIdTokenCredential
                    .createFrom(result.credential.data)

                val firebaseCredential = GoogleAuthProvider.getCredential(
                    googleIdTokenCredential.idToken,
                    null
                )

                auth.signInWithCredential(firebaseCredential).await()
                _loginState.value = LoginState.Success

            } catch (e: GetCredentialException) {
                // El usuario canceló el selector o no hay cuentas disponibles
                _loginState.value = LoginState.Error(
                    "No se pudo iniciar sesión con Google: ${e.localizedMessage}"
                )
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(
                    e.localizedMessage ?: "Error al iniciar sesión con Google"
                )
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}