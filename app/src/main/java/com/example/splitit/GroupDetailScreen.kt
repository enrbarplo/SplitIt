package com.example.splitit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onNavigateToAddExpense: () -> Unit
) {
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var group by remember { mutableStateOf<Group?>(null) }
    var members by remember { mutableStateOf<List<User>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteError by remember { mutableStateOf("") }

    var selectedMember by remember { mutableStateOf<User?>(null) }

    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }

    val currentUser = Firebase.auth.currentUser

    // Cargar gastos y datos del grupo en tiempo real
    LaunchedEffect(groupId) {
        FirestoreService.getExpensesByGroup(groupId) { expenses = it }
        FirestoreService.getGroupById(groupId) { g ->
            group = g
            g?.members?.let { ids ->
                FirestoreService.getUsersInfo(ids) { users ->
                    members = users
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Detalle del Grupo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { showInviteDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Invitar Amigo")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Añadir gasto",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FloatingActionButton(onClick = onNavigateToAddExpense) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir Gasto")
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Gastos", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Balance", modifier = Modifier.padding(16.dp))
                }
            }

            if (selectedTab == 0) {
                if (expenses.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aún no hay gastos en este grupo")
                    }
                } else {
                    LazyColumn(Modifier.padding(16.dp)) {
                        items(expenses.filter { it.amount > 0 && !it.id.startsWith("ADJ_") }) { expense ->
                            val isSettlement = expense.title.startsWith("Pagado por")

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = if (isSettlement)
                                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                                else
                                    CardDefaults.cardColors()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = expense.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isSettlement) MaterialTheme.colorScheme.secondary else Color.Unspecified
                                        )
                                        if (!isSettlement) {
                                            val payerName = members.find { it.uid == expense.payerId }?.name ?: "Alguien"
                                            Text("Pagado por $payerName", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Text(
                                        text = "${String.format(Locale.getDefault(),"%.2f", expense.amount)} €",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (isSettlement) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                    )

                                    if (expense.payerId == currentUser?.uid && !isSettlement) {
                                        IconButton(onClick = { expenseToEdit = expense }) {
                                            Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val total = expenses.sumOf { it.amount }
                val perPerson = if (members.isNotEmpty()) total / members.size else 0.0

                LazyColumn(Modifier.padding(16.dp)) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Gasto Total del Grupo", style = MaterialTheme.typography.labelSmall)
                                Text("${String.format(Locale.getDefault(), "%.2f", total)} €", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }

                    items(members) { member ->
                        val paidByHim = expenses.filter { it.payerId == member.uid }.sumOf { it.amount }
                        val balance = paidByHim - perPerson

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .clickable(enabled = member.uid != currentUser?.uid) {
                                    selectedMember = member
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (member.uid == currentUser?.uid) "${member.name} (Tú)" else member.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (balance >= 0) "Le deben" else "Debe",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                text = "${String.format(Locale.getDefault(), "%.2f", if (balance < 0) -balance else balance)} €",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        if (selectedMember != null) {
            val member = selectedMember!!
            val total = expenses.sumOf { it.amount }
            val perPerson = if (members.isNotEmpty()) total / members.size else 0.0
            val paidByHim = expenses.filter { it.payerId == member.uid }.sumOf { it.amount }
            val balance = paidByHim - perPerson

            AlertDialog(
                onDismissRequest = { selectedMember = null },
                title = { Text("Saldar deuda") },
                text = {
                    Text(if (balance < 0) "${member.name} debe ${String.format(Locale.getDefault(), "%.2f", -balance)} €" else "${member.name} está al día")
                },
                confirmButton = {
                    val uid = currentUser?.uid
                    if (member.uid != uid && balance < 0 && uid != null) {
                        Button(onClick = {
                            val amountToSettle = -balance
                            selectedMember = null // Cerramos el diálogo inmediatamente para evitar parpadeos

                            val db = Firebase.firestore
                            val settlementId = db.collection("expenses").document().id

                            // Registro visible (El pago que realiza el deudor)
                            val settlement = Expense(
                                id = settlementId,
                                title = "Pagado por ${member.name}",
                                amount = amountToSettle,
                                payerId = member.uid,
                                groupId = groupId
                            )

                            // Registro invisible (El ajuste técnico vinculado para el balance)
                            val adjustment = Expense(
                                id = "ADJ_$settlementId",
                                title = "Ajuste técnico",
                                amount = -amountToSettle,
                                payerId = uid,
                                groupId = groupId
                            )

                            val batch = db.batch()
                            batch.set(db.collection("expenses").document(settlementId), settlement)
                            batch.set(db.collection("expenses").document("ADJ_$settlementId"), adjustment)

                            batch.commit()
                        }) { Text("Confirmar Pago") }
                    }
                },
                dismissButton = { TextButton(onClick = { selectedMember = null }) { Text("Cancelar") } }
            )
        }

        if (expenseToEdit != null) {
            EditExpenseDialog(
                expense = expenseToEdit!!,
                onDismiss = { expenseToEdit = null },
                onConfirm = { title, amount ->
                    FirestoreService.updateExpense(expenseToEdit!!.id, title, amount,
                        onSuccess = { expenseToEdit = null },
                        onError = { }
                    )
                }
            )
        }

        if (showInviteDialog) {
            InviteMemberDialog(
                onDismiss = {
                    showInviteDialog = false
                    inviteError = ""
                },
                errorMessage = inviteError,
                onConfirm = { email ->
                    FirestoreService.addMemberByEmail(groupId, email,
                        onSuccess = {
                            showInviteDialog = false
                            inviteError = ""
                        },
                        onError = { error ->
                            inviteError = error
                        }
                    )
                }
            )
        }
    }
}

@Composable
fun EditExpenseDialog(expense: Expense, onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var title by remember { mutableStateOf(expense.title) }
    var amount by remember { mutableStateOf(expense.amount.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Gasto") },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextField(value = amount, onValueChange = { amount = it }, label = { Text("Cantidad (€)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (title.isNotBlank() && amt > 0) onConfirm(title, amt)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}