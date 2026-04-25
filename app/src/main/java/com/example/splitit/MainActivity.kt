package com.example.splitit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.splitit.ui.theme.SplitItTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitItTheme {
                SplitItApp()
            }
        }
    }
}

@Composable
fun SplitItApp() {
    val navController = rememberNavController()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "login",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onNavigateToRegister = { navController.navigate("register") }
                )
            }

            composable("register") {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate("home") {
                            popUpTo("register") { inclusive = true }
                        }
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    onNavigateToCreateGroup = { navController.navigate("create_group") },
                    onNavigateToGroupDetail = { groupId ->
                        navController.navigate("group_detail/$groupId")
                    },
                    onNavigateToProfile = { navController.navigate("profile") }
                )
            }

            composable("profile") {
                ProfileScreen(
                    onLogout = {
                        navController.navigate("login") {
                            popUpTo(0)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("create_group") {
                CreateGroupScreen(
                    onGroupCreated = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("group_detail/{groupId}") { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                GroupDetailScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onNavigateToAddExpense = {
                        navController.navigate("add_expense/$groupId")
                    }
                )
            }

            composable("add_expense/{groupId}") { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                AddExpenseScreen(
                    groupId = groupId,
                    onExpenseAdded = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToGroupDetail: (String) -> Unit,
    onNavigateToProfile: () -> Unit
) {
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    val user = Firebase.auth.currentUser
    var groupToEdit by remember { mutableStateOf<Group?>(null) }

    LaunchedEffect(Unit) {
        FirestoreService.getMyGroups { groups = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SplitIt", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Hola, ${user?.displayName ?: user?.email?.split("@")?.get(0) ?: "Usuario"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Perfil",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Crear grupo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                FloatingActionButton(onClick = onNavigateToCreateGroup) {
                    Icon(Icons.Default.Add, contentDescription = "Crear grupo")
                }
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No tienes grupos aún. ¡Crea uno!")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                item {
                    Text(
                        text = "Tus grupos",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(groups) { group ->
                    GroupItem(
                        group = group,
                        onClick = { onNavigateToGroupDetail(group.id) },
                        onEdit = { groupToEdit = group },
                        onDelete = {
                            FirestoreService.deleteGroup(group.id, {}, {})
                        }
                    )
                }
            }
            if (groupToEdit != null) {
                var newName by remember { mutableStateOf(groupToEdit!!.name) }
                AlertDialog(
                    onDismissRequest = { groupToEdit = null },
                    title = { Text("Editar nombre del grupo") },
                    text = {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Nuevo nombre") }
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            FirestoreService.updateGroupName(groupToEdit!!.id, newName,
                                onSuccess = { groupToEdit = null },
                                onError = { }
                            )
                        }) { Text("Guardar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { groupToEdit = null }) { Text("Cancelar") }
                    }
                )
            }
        }
    }
}