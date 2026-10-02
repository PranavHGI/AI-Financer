package com.example.aifinancerfree.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.aifinancerfree.data.model.GoalResponse
import com.example.aifinancerfree.ui.viewmodel.GoalsViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(viewModel: GoalsViewModel) {
    val goals by viewModel.goals.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAddGoalDialog by remember { mutableStateOf(false) }
    var showAddSavingsDialog by remember { mutableStateOf<GoalResponse?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchGoals()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isLoading && goals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (goals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "No savings goals set yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Set financial targets (e.g. buying a laptop or house) and track your saving progress.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(onClick = { showAddGoalDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Goal", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(goals) { goal ->
                    GoalCard(
                        goal = goal,
                        onAddSavings = { showAddSavingsDialog = goal },
                        onDelete = { viewModel.deleteGoal(goal.id) }
                    )
                }
            }
        }

        // Floating Action Button to Add Goal (if not empty)
        if (goals.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showAddGoalDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Savings Goal")
            }
        }
    }

    // Add Goal Dialog
    if (showAddGoalDialog) {
        var title by remember { mutableStateOf("") }
        var targetAmount by remember { mutableStateOf("") }
        var savedAmount by remember { mutableStateOf("") }
        var monthsRemaining by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = { Text("New Savings Goal", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Goal Title (e.g. Laptop, Trip)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetAmount,
                        onValueChange = { targetAmount = it },
                        label = { Text("Target Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = savedAmount,
                        onValueChange = { savedAmount = it },
                        label = { Text("Already Saved (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = monthsRemaining,
                        onValueChange = { monthsRemaining = it },
                        label = { Text("Target Duration (Months)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = targetAmount.toDoubleOrNull() ?: 0.0
                        val initial = savedAmount.toDoubleOrNull() ?: 0.0
                        val months = monthsRemaining.toIntOrNull() ?: 1
                        if (title.isNotBlank() && target > 0.0 && months > 0) {
                            viewModel.createGoal(title, target, initial, months)
                            showAddGoalDialog = false
                        }
                    }
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGoalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Savings Money Dialog
    showAddSavingsDialog?.let { goal ->
        var addAmount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSavingsDialog = null },
            title = { Text("Add savings to \"${goal.title}\"", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = addAmount,
                    onValueChange = { addAmount = it },
                    label = { Text("Amount to Save (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = addAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            viewModel.addSavings(goal.id, amt)
                            showAddSavingsDialog = null
                        }
                    }
                ) {
                    Text("Add Money", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSavingsDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GoalCard(
    goal: GoalResponse,
    onAddSavings: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (goal.targetAmount > 0.0) (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val percent = (progress * 100).toInt()
    val completed = progress >= 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (completed) "Target Achieved! 🎉" else "Target: ${goal.monthsRemaining} months remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Goal",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            // Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Saved: ₹${String.format(Locale.US, "%,.2f", goal.savedAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Target: ₹${String.format(Locale.US, "%,.2f", goal.targetAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Estimate details
            if (!completed) {
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Required Monthly Saving",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${String.format(Locale.US, "%,.2f", goal.monthlySavingEstimate)} / month",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = onAddSavings,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Savings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
