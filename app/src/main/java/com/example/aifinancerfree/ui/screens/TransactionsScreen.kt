package com.example.aifinancerfree.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aifinancerfree.data.repository.LocalTransaction
import com.example.aifinancerfree.data.repository.TransactionRepository
import com.example.aifinancerfree.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: FinanceViewModel,
    addTx: () -> Unit
) {
    val txs by viewModel.transactions.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("all") } // "all", "expense", "income"
    var selectedCategory by remember { mutableStateOf("All") }

    val filteredTxs = remember(txs, searchQuery, selectedType, selectedCategory) {
        txs.filter { tx ->
            val matchesSearch = tx.merchant.contains(searchQuery, ignoreCase = true) ||
                    (tx.notes?.contains(searchQuery, ignoreCase = true) == true)
            val matchesType = when (selectedType) {
                "expense" -> tx.type == "expense"
                "income" -> tx.type == "income"
                else -> true
            }
            val matchesCategory = selectedCategory == "All" || tx.category == selectedCategory
            matchesSearch && matchesType && matchesCategory
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = addTx) {
                        Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // Tabs/Filter Type Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp)
            ) {
                listOf("all" to "All", "expense" to "Expense", "income" to "Income").forEach { (typeVal, label) ->
                    val isSelected = selectedType == typeVal
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedType = typeVal }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Category Horizontal Filter List
            ScrollableTabRow(
                selectedTabIndex = (listOf("All") + TransactionRepository.DEFAULT_CATEGORIES).indexOf(selectedCategory).coerceAtLeast(0),
                edgePadding = 0.dp,
                divider = {},
                indicator = {}
            ) {
                (listOf("All") + TransactionRepository.DEFAULT_CATEGORIES).forEach { category ->
                    val isSelected = selectedCategory == category
                    Tab(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Transactions list
            if (filteredTxs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "No transactions found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTxs) { tx ->
                        TransactionItem(
                            tx = tx,
                            onDelete = { viewModel.deleteTransaction(tx.id) },
                            onCorrectCategory = { newCategory ->
                                viewModel.correctTransactionCategory(tx.id, tx.remoteId, tx.category, newCategory)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(
    tx: LocalTransaction,
    onDelete: () -> Unit,
    onCorrectCategory: (String) -> Unit
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(tx.date) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(tx.date))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tx.merchant,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Sync status cloud icon
                    if (!tx.synced) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Sync Pending",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .clickable { categoryMenuExpanded = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tx.category,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            val allCategories = if (tx.type == "income") {
                                listOf("Salary", "Business", "Freelance", "Investment", "Others")
                            } else {
                                TransactionRepository.DEFAULT_CATEGORIES
                            }
                            allCategories.forEach { categoryName ->
                                DropdownMenuItem(
                                    text = { Text(categoryName) },
                                    onClick = {
                                        categoryMenuExpanded = false
                                        if (categoryName != tx.category) {
                                            onCorrectCategory(categoryName)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = "• ${tx.account}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isExpense = tx.type == "expense"
                val prefix = if (isExpense) "- ₹" else "+ ₹"
                val color = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                
                Text(
                    text = "$prefix${String.format(Locale.US, "%,.2f", tx.amount)}",
                    fontWeight = FontWeight.Bold,
                    color = color,
                    style = MaterialTheme.typography.titleMedium
                )

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
