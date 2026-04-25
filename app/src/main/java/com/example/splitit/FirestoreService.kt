package com.example.splitit

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.FieldValue

object FirestoreService {

    fun addExpense(groupId: String, title: String, amount: Double, payerId: String? = null, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val db = Firebase.firestore
        val currentUser = Firebase.auth.currentUser ?: return

        val expenseId = db.collection("expenses").document().id
        val expense = Expense(
            id = expenseId,
            title = title,
            amount = amount,
            payerId = payerId ?: currentUser.uid,
            groupId = groupId
        )

        db.collection("expenses").document(expenseId).set(expense)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Error") }
    }

    fun getExpensesByGroup(groupId: String, onSuccess: (List<Expense>) -> Unit) {
        Firebase.firestore.collection("expenses")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, _ ->
                val expenses = snapshot?.toObjects(Expense::class.java) ?: emptyList()
                onSuccess(expenses)
            }
    }

    fun createGroup(name: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val db = Firebase.firestore
        val currentUser = Firebase.auth.currentUser ?: return

        val groupId = db.collection("groups").document().id
        val group = Group(
            id = groupId,
            name = name,
            ownerId = currentUser.uid,
            members = listOf(currentUser.uid)
        )

        db.collection("groups").document(groupId).set(group)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Error") }
    }

    fun getMyGroups(onSuccess: (List<Group>) -> Unit) {
        val userId = Firebase.auth.currentUser?.uid ?: return

        Firebase.firestore.collection("groups")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, _ ->
                val groups = snapshot?.toObjects(Group::class.java) ?: emptyList()
                onSuccess(groups)
            }
    }

    fun saveUser(uid: String, name: String, email: String, onComplete: (() -> Unit)? = null) {
        val user = User(uid, name, email)
        Firebase.firestore.collection("users").document(uid).set(user)
            .addOnCompleteListener { onComplete?.invoke() }
    }

    fun addMemberByEmail(groupId: String, email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val db = Firebase.firestore

        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.documents.firstOrNull()?.toObject(User::class.java)
                if (user != null) {
                    db.collection("groups").document(groupId)
                        .update("members", FieldValue.arrayUnion(user.uid))
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onError("No se pudo añadir al grupo") }
                } else {
                    onError("No existe ningún usuario con ese correo")
                }
            }
            .addOnFailureListener { onError("Error en la búsqueda") }
    }

    fun getUsersInfo(userIds: List<String>, onSuccess: (List<User>) -> Unit) {
        if (userIds.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        Firebase.firestore.collection("users")
            .whereIn("uid", userIds)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.toObjects(User::class.java))
            }
    }

    fun getGroupById(groupId: String, onSuccess: (Group?) -> Unit) {
        Firebase.firestore.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, _ ->
                onSuccess(snapshot?.toObject(Group::class.java))
            }
    }

    fun logout() {
        Firebase.auth.signOut()
    }

    fun updateName(newName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = Firebase.auth.currentUser ?: return
        val db = Firebase.firestore

        // Primero comprobamos si el nuevo nombre ya lo tiene otra persona
        db.collection("users")
            .whereEqualTo("name", newName)
            .get()
            .addOnSuccessListener { snapshot ->
                val isNameTaken = snapshot.documents.any { it.id != user.uid }

                if (isNameTaken) {
                    onError("El nombre de usuario '$newName' ya está en uso por otra persona")
                } else {
                    // Si está libre, actualizamos el perfil
                    val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                        displayName = newName
                    }
                    user.updateProfile(profileUpdates).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            db.collection("users").document(user.uid).update("name", newName)
                                .addOnSuccessListener { onSuccess() }
                                .addOnFailureListener { onError(it.message ?: "Error al actualizar en base de datos") }
                        } else {
                            onError(task.exception?.message ?: "Error al actualizar el perfil")
                        }
                    }
                }
            }
            .addOnFailureListener { onError("Error al verificar disponibilidad del nombre") }
    }

    fun updateEmail(newEmail: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val db = Firebase.firestore
        val currentUser = Firebase.auth.currentUser ?: return

        db.collection("users")
            .whereEqualTo("email", newEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                val isEmailTaken = snapshot.documents.any { it.id != currentUser.uid }

                if (isEmailTaken) {
                    onError("Este email ya está vinculado a otra cuenta de usuario")
                } else {
                    currentUser.verifyBeforeUpdateEmail(newEmail).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            db.collection("users").document(currentUser.uid).update("email", newEmail)
                            onSuccess()
                        } else {
                            val exception = task.exception
                            val msg = exception?.message?.lowercase() ?: ""

                            val translatedError = when {
                                msg.contains("invalid_new_email") || msg.contains("badly formatted") ->
                                    "El formato del nuevo correo no es válido"

                                msg.contains("recent login") || msg.contains("sensitive operation") ->
                                    "Por seguridad, cierra sesión y vuelve a entrar para cambiar el email"

                                else -> "Error: ${exception?.localizedMessage ?: "desconocido"}"
                            }
                            onError(translatedError)
                        }
                    }
                }
            }
            .addOnFailureListener { onError("Error al conectar con la base de datos") }
    }

    fun updatePassword(currentPass: String, newPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = Firebase.auth.currentUser ?: return
        val email = user.email ?: return

        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPass)

        user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
            if (reAuthTask.isSuccessful) {
                user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                    if (updateTask.isSuccessful) onSuccess()
                    else onError(updateTask.exception?.message ?: "Error al actualizar contraseña")
                }
            } else {
                onError("La contraseña actual no es correcta")
            }
        }
    }

    fun updateExpense(expenseId: String, title: String, amount: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val updates = mapOf("title" to title, "amount" to amount)
        Firebase.firestore.collection("expenses").document(expenseId).update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Error al editar") }
    }

    fun isUsernameAvailable(username: String, onResult: (Boolean) -> Unit, onError: (String) -> Unit) {
        Firebase.firestore.collection("users")
            .whereEqualTo("name", username)
            .get()
            .addOnSuccessListener { snapshot -> onResult(snapshot.isEmpty) }
            .addOnFailureListener { onError("Error de conexión a la base de datos") }
    }

    fun getEmailByUsername(username: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        Firebase.firestore.collection("users")
            .whereEqualTo("name", username)
            .get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.documents.firstOrNull()?.toObject(User::class.java)
                if (user != null) onSuccess(user.email)
                else onError("Nombre de usuario no encontrado")
            }
            .addOnFailureListener { onError("Error al buscar usuario") }
    }

    fun updateGroupName(groupId: String, newName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        Firebase.firestore.collection("groups").document(groupId).update("name", newName)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Error al editar grupo") }
    }

    fun deleteGroup(groupId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val db = Firebase.firestore

        db.collection("expenses")
            .whereEqualTo("groupId", groupId)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()

                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }

                batch.delete(db.collection("groups").document(groupId))

                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it.message ?: "Error al borrar el grupo y sus gastos") }
            }
            .addOnFailureListener { onError("No se pudieron encontrar los gastos del grupo") }
    }
}