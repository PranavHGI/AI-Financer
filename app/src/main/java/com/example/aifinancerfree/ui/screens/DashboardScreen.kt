package com.example.aifinancerfree.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.aifinancerfree.data.repository.LocalTransaction
import com.example.aifinancerfree.data.model.InsightResponse
import com.example.aifinancerfree.data.model.RecurringBill
import com.example.aifinancerfree.data.model.AnomalyAlert
import com.example.aifinancerfree.data.model.DuplicateAlert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Repeat
import com.example.aifinancerfree.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    username: String,
    advisor: () -> Unit
) {
    val context = LocalContext.current

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    val transactions by viewModel.transactions.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val totalSpent by viewModel.totalSpent.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val totalBalance = totalIncome - totalSpent
    val recentTxs = remember(transactions) { transactions.take(3) }

    // Total monthly budget limit
    val totalLimit = remember(budgets) {
        val sum = budgets.sumOf { it.limitAmount }
        if (sum == 0.0) 15000.0 else sum
    }
    val progress = (totalSpent / totalLimit).toFloat()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanSmsInbox(context) { count ->
                Toast.makeText(context, "Scan complete! Imported $count transactions.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "SMS read permission is required to import history.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadData()
        viewModel.syncRemote()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$greeting ${username.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Here’s your financial snapshot.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = { viewModel.syncRemote() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Now")
                    }
                }
            }
        }

        item {
            BalanceCard(totalBalance)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard("Income", totalIncome, Modifier.weight(1f))
                MetricCard("Spent", totalSpent, Modifier.weight(1f))
            }
        }

        // SMS Import Sync Card
        item {
            Card(
                onClick = {
                    val isDefault = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
                    if (!isDefault) {
                        Toast.makeText(context, "Please set AI Financer as default SMS app in Settings first.", Toast.LENGTH_LONG).show()
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_SMS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.scanSmsInbox(context) { count ->
                                Toast.makeText(context, "Scan complete! Imported $count transactions.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                        }
                    }
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Scan Historical SMS", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Backfill transaction history from inbox messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // AI Financial Insights Card
        item {
            val insights by viewModel.insights.collectAsState()
            insights?.let { ins ->
                var showSheet by remember { mutableStateOf(false) }

                Card(
                    onClick = { showSheet = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Financial Insights Available", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Top spend: ${ins.topCategory} • Forecast: ₹${String.format(Locale.US, "%.0f", ins.nextMonthPrediction)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("View", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (showSheet) {
                    InsightsBottomSheet(insights = ins, onDismiss = { showSheet = false })
                }
            }
        }

        item {
            SectionTitle("This month")
        }

        item {
            SpendingCard(spent = totalSpent, limit = totalLimit, progress = progress)
        }

        item {
            val categoryData = remember(transactions) {
                transactions.filter { it.type == "expense" }
                    .groupBy { it.category }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            }
            CategoryPieChart(categoryData)
        }

        item {
            MonthlyTrendBarChart(transactions)
        }

        item {
            SectionTitle("Recent activity")
        }

        if (recentTxs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent transactions. Tap + below to add one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentTxs) { transaction ->
                TransactionRow(transaction)
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                onClick = advisor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Ask AI Advisor", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Get educational budgeting guidance",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceCard(balance: Double) {
    val isNegative = balance < 0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(
                text = "Available balance",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = (if (isNegative) "-" else "") + "₹" + String.format(Locale.US, "%,.2f", Math.abs(balance)),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Safe-to-spend levels computed locally",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .9f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun MetricCard(label: String, value: Double, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "₹" + String.format(Locale.US, "%,.2f", value), 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This month",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SpendingCard(spent: Double, limit: Double, progress: Float) {
    val isOverBudget = spent > limit
    val progressColor = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "Monthly Spending Progress",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Spent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "₹${String.format(Locale.US, "%,.2f", spent)} of ₹${String.format(Locale.US, "%,.2f", limit)}",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(14.dp))
            Progress(progress.coerceIn(0f, 1f), progressColor)
            
            if (isOverBudget) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You have exceeded your total category limits!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun Progress(value: Float, color: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(value),
            color = color
        ) {}
    }
}

@Composable
fun TransactionRow(tx: LocalTransaction) {
    val dateStr = remember(tx.date) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(tx.date))
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.merchant, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${tx.category} • $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            val isExpense = tx.type == "expense"
            val prefix = if (isExpense) "- ₹" else "+ ₹"
            val color = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

            Text(
                text = "$prefix${String.format(Locale.US, "%,.2f", tx.amount)}",
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsBottomSheet(
    insights: InsightResponse,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "AI Financial Insights",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            // ML Expense Forecast Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Next Month Forecast Spend", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Predicted spent total: ₹${String.format(Locale.US, "%,.2f", insights.nextMonthPrediction)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Based on Linear Regression trend line from your historical spend months.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Anomaly / Outliers Warnings
            if (insights.anomalies.isNotEmpty()) {
                Text("Detected Anomaly Spending Outliers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                insights.anomalies.take(2).forEach { alert ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Column {
                                Text("Unusual high spend at ${alert.merchant}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Amount: ₹${alert.amount} • Category: ${alert.category}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Duplicate Alerts
            if (insights.duplicates.isNotEmpty()) {
                Text("Potential Duplicate Transactions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                insights.duplicates.take(2).forEach { dup ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null)
                            Column {
                                Text("${dup.count} duplicate logs for ${dup.merchant}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Amount: ₹${dup.amount} logged multiple times within 10 minutes.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Recurring Bills
            if (insights.recurringBills.isNotEmpty()) {
                Text("Recurring Subscriptions & Bills", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                insights.recurringBills.take(2).forEach { bill ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Repeat, null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("${bill.merchant} Subscription", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("₹${bill.amount} charged approx every ${bill.frequencyDays} days.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Legally Required Advisor Disclaimer Warning Box
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = insights.disclaimer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryPieChart(categoryData: Map<String, Double>) {
    if (categoryData.isEmpty()) return

    val total = categoryData.values.sum()
    val colors = listOf(
        Color(0xFF3FD7BC), // Teal
        Color(0xFFFFB74D), // Orange
        Color(0xFF9575CD), // Purple
        Color(0xFFF06292), // Pink
        Color(0xFF4FC3F7), // Blue
        Color(0xFFFFF176), // Yellow
        Color(0xFF81C784), // Light Green
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Category Breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(110.dp)
                ) {
                    var startAngle = 0f
                    categoryData.entries.forEachIndexed { index, entry ->
                        val sweepAngle = ((entry.value / total) * 360f).toFloat()
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )
                        startAngle += sweepAngle
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categoryData.entries.take(4).forEachIndexed { index, entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(colors[index % colors.size])
                            )
                            Text(
                                text = "${entry.key}: ₹${String.format(Locale.US, "%.0f", entry.value)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyTrendBarChart(transactions: List<LocalTransaction>) {
    val monthlyData = remember(transactions) {
        val format = SimpleDateFormat("MMM", Locale.getDefault())
        transactions.filter { it.type == "expense" }
            .groupBy { 
                val cal = Calendar.getInstance().apply { timeInMillis = it.date }
                format.format(cal.time)
            }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .entries.sortedBy { entry ->
                try {
                    val cal = Calendar.getInstance()
                    cal.time = SimpleDateFormat("MMM", Locale.getDefault()).parse(entry.key)!!
                    cal.get(Calendar.MONTH)
                } catch(e: Exception) {
                    0
                }
            }
            .takeLast(3)
    }

    if (monthlyData.isEmpty()) return

    val maxVal = (monthlyData.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Monthly Expense Trend", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                monthlyData.forEach { entry ->
                    val ratio = (entry.value / maxVal).toFloat().coerceIn(0.1f, 1f)
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "₹${String.format(Locale.US, "%.0f", entry.value)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .fillMaxHeight(ratio * 0.7f)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = entry.key,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
