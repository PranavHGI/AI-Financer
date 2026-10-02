package com.example.aifinancerfree.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.aifinancerfree.data.repository.LocalBudget
import com.example.aifinancerfree.data.repository.LocalTransaction
import com.example.aifinancerfree.ui.viewmodel.FinanceViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    viewModel: FinanceViewModel
) {
    val context = LocalContext.current
    val budgets by viewModel.budgets.collectAsState()
    val txs by viewModel.transactions.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var selectedCategoryForEdit by remember { mutableStateOf("") }
    var newLimitInput by remember { mutableStateOf("") }

    // Aggregate monthly spending per category
    val categorySpentMap = remember(txs) {
        val spentMap = mutableMapOf<String, Double>()
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val cal = Calendar.getInstance()

        for (tx in txs) {
            if (tx.type == "expense") {
                cal.timeInMillis = tx.date
                val txMonth = cal.get(Calendar.MONTH)
                val txYear = cal.get(Calendar.YEAR)
                if (txMonth == currentMonth && txYear == currentYear) {
                    spentMap[tx.category] = spentMap.getOrDefault(tx.category, 0.0) + tx.amount
                }
            }
        }
        spentMap
    }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Budgets", fontWeight = FontWeight.Bold) }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Manage your limits and track monthly progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(budgets) { budget ->
                    val spent = categorySpentMap.getOrDefault(budget.category, 0.0)
                    BudgetItem(
                        budget = budget,
                        spent = spent,
                        onEdit = {
                            selectedCategoryForEdit = budget.category
                            newLimitInput = budget.limitAmount.toInt().toString()
                            showEditDialog = true
                        }
                    )
                }
            }
        }

        // Edit Limit Dialog
        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit Limit for $selectedCategoryForEdit") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter the new monthly spending limit (₹):")
                        OutlinedTextField(
                            value = newLimitInput,
                            onValueChange = { newLimitInput = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val limit = newLimitInput.toDoubleOrNull()
                            if (limit == null || limit < 0.0) {
                                Toast.makeText(context, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.updateBudgetLimit(selectedCategoryForEdit, limit)
                            showEditDialog = false
                            Toast.makeText(context, "Budget limit updated successfully.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun BudgetItem(
    budget: LocalBudget,
    spent: Double,
    onEdit: () -> Unit
) {
    val progress = if (budget.limitAmount > 0) (spent / budget.limitAmount).toFloat() else 0f
    val isOverBudget = spent > budget.limitAmount
    val progressColor = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(budget.category, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Limit", modifier = Modifier.size(20.dp))
                }
            }

            // Spent vs Limit numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spent: ₹${String.format(Locale.US, "%,.2f", spent)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Limit: ₹${String.format(Locale.US, "%,.2f", budget.limitAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Linear Progress Bar
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Overdraft Warning
            if (isOverBudget) {
                Text(
                    text = "Warning: Budget limit exceeded by ₹${String.format(Locale.US, "%,.2f", spent - budget.limitAmount)}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
