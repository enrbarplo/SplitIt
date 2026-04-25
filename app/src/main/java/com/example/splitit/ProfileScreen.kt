package com.example.splitit

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit, onBack: () -> Unit) {
    val user = Firebase.auth.currentUser
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showNameDialog by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var showPassDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AccountCircle, null, Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(24.dp))

            ProfileItem(label = "Nombre", value = user?.displayName ?: "Sin nombre") {
                showNameDialog = true
            }

            ProfileItem(label = "Email", value = user?.email ?: "Sin email") {
                showEmailDialog = true
            }

            ProfileItem(label = "Contraseña", value = "********") {
                showPassDialog = true
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    FirestoreService.logout()
                    onLogout()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar Sesión")
            }
        }

        if (showNameDialog) {
            EditDialog(
                title = "Editar nombre",
                initialValue = user?.displayName ?: "",
                onDismiss = { showNameDialog = false },
                onConfirm = { newValue ->
                    FirestoreService.updateName(newValue,
                        onSuccess = {
                            showNameDialog = false
                            scope.launch { snackbarHostState.showSnackbar("Nombre actualizado") }
                        },
                        onError = { scope.launch { snackbarHostState.showSnackbar(it) } }
                    )
                }
            )
        }

        if (showEmailDialog) {
            EditDialog(
                title = "Editar email",
                initialValue = user?.email ?: "",
                onDismiss = { showEmailDialog = false },
                onConfirm = { newValue ->
                    FirestoreService.updateEmail(newValue,
                        onSuccess = {
                            showEmailDialog = false
                            scope.launch { snackbarHostState.showSnackbar("Email actualizado. Revisa la bandeja de entrada del nuevo email para confirmar.") }
                        },
                        onError = { scope.launch { snackbarHostState.showSnackbar(it) } }
                    )
                }
            )
        }

        if (showPassDialog) {
            ChangePasswordDialog(
                onDismiss = { showPassDialog = false },
                onConfirm = { current, new ->
                    FirestoreService.updatePassword(current, new,
                        onSuccess = {
                            showPassDialog = false
                            scope.launch { snackbarHostState.showSnackbar("Contraseña cambiada con éxito") }
                        },
                        onError = { scope.launch { snackbarHostState.showSnackbar(it) } }
                    )
                }
            )
        }
    }
}

@Composable
fun ProfileItem(label: String, value: String, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun EditDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        errorMsg = if (it.isBlank()) "Este campo es obligatorio" else ""
                    },
                    singleLine = true,
                    isError = errorMsg.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank() && text != initialValue
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contraseña") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPass,
                    onValueChange = { currentPass = it },
                    label = { Text("Contraseña actual") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPass,
                    onValueChange = {
                        newPass = it
                        errorMsg = when {
                            it.isEmpty() -> "La nueva contraseña no puede estar vacía"
                            it.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
                            else -> ""
                        }
                    },
                    label = { Text("Nueva contraseña") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMsg.isNotEmpty()
                )

                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentPass.isNotBlank() && newPass.length >= 6) {
                        onConfirm(currentPass, newPass)
                    }
                },
                enabled = currentPass.isNotBlank() && newPass.length >= 6
            ) {
                Text("Actualizar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}