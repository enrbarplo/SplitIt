package com.example.splitit

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.auth.FirebaseAuthException

@Composable
fun RegisterScreen(onRegisterSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val auth = Firebase.auth

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Crear Cuenta", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Nombre de usuario") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = null)
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (username.isBlank() || email.isBlank() || password.isBlank()) {
                    errorMsg = "Rellena todos los campos"
                    return@Button
                }

                isLoading = true
                errorMsg = ""

                FirestoreService.isUsernameAvailable(username,
                    onResult = { available ->
                        if (available) {
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val user = task.result.user
                                        if (user != null) {
                                            val profileUpdates = userProfileChangeRequest {
                                                displayName = username
                                            }

                                            user.updateProfile(profileUpdates).addOnCompleteListener {
                                                FirestoreService.saveUser(user.uid, username, email) {
                                                    onRegisterSuccess()
                                                }
                                            }
                                        } else {
                                            isLoading = false
                                            errorMsg = "Error al obtener datos del usuario"
                                        }
                                    } else {
                                        isLoading = false
                                        val exception = task.exception
                                        errorMsg = when {
                                            exception is FirebaseAuthException && exception.errorCode == "ERROR_EMAIL_ALREADY_IN_USE" ->
                                                "Este correo electrónico ya está en uso"
                                            exception?.message?.contains("email address is already in use") == true ->
                                                "Este correo electrónico ya está registrado"
                                            exception?.message?.contains("badly formatted") == true ->
                                                "El formato del correo no es válido"
                                            exception?.message?.contains("Password should be at least 6 characters") == true ->
                                                "La contraseña debe tener al menos 6 caracteres"
                                            else -> "Error al registrar: ${exception?.localizedMessage}"
                                        }
                                    }
                                }
                        } else {
                            isLoading = false
                            errorMsg = "El nombre de usuario '$username' ya existe"
                        }
                    },
                    onError = {
                        isLoading = false
                        errorMsg = it
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Registrarse")
            }
        }

        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMsg, color = MaterialTheme.colorScheme.error)
        }
    }
}