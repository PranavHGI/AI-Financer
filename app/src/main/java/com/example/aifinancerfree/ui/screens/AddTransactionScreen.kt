package com.example.aifinancerfree.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Mic
import android.speech.RecognizerIntent
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.aifinancerfree.data.repository.TransactionRepository
import com.example.aifinancerfree.ui.viewmodel.FinanceViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: FinanceViewModel,
    back: () -> Unit
) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var merchant by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TransactionRepository.DEFAULT_CATEGORIES[0]) }
    var selectedAccount by remember { mutableStateOf("Cash") }
    var notes by remember { mutableStateOf("") }
    var dateMs by remember { mutableStateOf(System.currentTimeMillis()) }

    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showScanSourceDialog by remember { mutableStateOf(false) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val incomeCategories = remember { listOf("Salary", "Business", "Freelance", "Investment", "Others") }

    LaunchedEffect(isExpense) {
        selectedCategory = if (isExpense) TransactionRepository.DEFAULT_CATEGORIES[0] else incomeCategories[0]
    }

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenTexts = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val bestMatch = spokenTexts?.firstOrNull() ?: ""
            if (bestMatch.isNotBlank()) {
                val parsed = parseVoiceInput(bestMatch, TransactionRepository.DEFAULT_CATEGORIES, incomeCategories)
                amount = if (parsed.amount > 0.0) parsed.amount.toString() else ""
                isExpense = parsed.isExpense
                selectedCategory = parsed.category
                merchant = parsed.merchant
                notes = "Voice Transaction: \"$bestMatch\""
                Toast.makeText(context, "Voice input parsed successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Audio permission request launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your transaction...")
            }
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Speech recognition is not supported on this device.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val accountsList = listOf("Cash", "Bank Account", "Credit Card")
    val formattedDate = remember(dateMs) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dateMs))
    }

    // Helper function to create temporary shared Uri
    fun createTempImageUri(ctx: Context): Uri {
        val tempFile = File(ctx.cacheDir, "temp_receipt.jpg").apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            tempFile
        )
    }

    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isOcrLoading = true
            viewModel.uploadReceipt(context, it) { response, error ->
                isOcrLoading = false
                if (response != null) {
                    amount = response.amount.toString()
                    merchant = response.merchant
                    // Map category if it exists in our default list
                    if (TransactionRepository.DEFAULT_CATEGORIES.contains(response.category)) {
                        selectedCategory = response.category
                    }
                    notes = "Parsed via Receipt OCR scanner"
                    Toast.makeText(context, "Scan complete! Amount: ₹${response.amount}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Scan failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Camera picture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let { uri ->
                isOcrLoading = true
                viewModel.uploadReceipt(context, uri) { response, error ->
                    isOcrLoading = false
                    if (response != null) {
                        amount = response.amount.toString()
                        merchant = response.merchant
                        if (TransactionRepository.DEFAULT_CATEGORIES.contains(response.category)) {
                            selectedCategory = response.category
                        }
                        notes = "Parsed via Receipt OCR scanner"
                        Toast.makeText(context, "Scan complete! Amount: ₹${response.amount}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Scan failed: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Camera permission request launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createTempImageUri(context)
            tempImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission is required to scan receipt", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add transaction", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your transaction...")
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Speech recognition is not supported on this device.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Input")
                    }
                    IconButton(onClick = { showScanSourceDialog = true }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Scan Receipt")
                    }
                }
            )
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Type Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    listOf(true to "Expense", false to "Income").forEach { (typeVal, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isExpense == typeVal) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { isExpense = typeVal }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isExpense == typeVal) MaterialTheme.colorScheme.onPrimary 
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Amount Field
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Merchant / Payee
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(if (isExpense) "Merchant / Store" else "Source / Payee") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Category Selection Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        label = { Text("Category") },
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { categoryExpanded = true })
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryExpanded = true }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        val currentCategories = if (isExpense) TransactionRepository.DEFAULT_CATEGORIES else incomeCategories
                        currentCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Account Selection Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedAccount,
                        onValueChange = {},
                        label = { Text("Payment Method") },
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { accountExpanded = true })
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { accountExpanded = true }
                    )
                    DropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false }
                    ) {
                        accountsList.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account) },
                                onClick = {
                                    selectedAccount = account
                                    accountExpanded = false
                                }
                            )
                        }
                    }
                }

                // Notes Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Date Selection
                OutlinedTextField(
                    value = formattedDate,
                    onValueChange = {},
                    label = { Text("Transaction Date") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.weight(1f))

                // Save Button
                Button(
                    onClick = {
                        val amtDouble = amount.toDoubleOrNull()
                        if (amtDouble == null || amtDouble <= 0.0) {
                            Toast.makeText(context, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (merchant.trim().isEmpty()) {
                            Toast.makeText(context, "Please enter a merchant name.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSaving = true
                        viewModel.addTransaction(
                            type = if (isExpense) "expense" else "income",
                            amount = amtDouble,
                            category = selectedCategory,
                            merchant = merchant.trim(),
                            account = selectedAccount,
                            notes = notes.trim().ifEmpty { null },
                            date = dateMs
                        )
                        Toast.makeText(context, "Transaction saved successfully.", Toast.LENGTH_SHORT).show()
                        back()
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Save Transaction", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // OCR Scanning Source Dialog
            if (showScanSourceDialog) {
                AlertDialog(
                    onDismissRequest = { showScanSourceDialog = false },
                    title = { Text("Scan Receipt") },
                    text = { Text("Choose a source to import your transaction receipt details:") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showScanSourceDialog = false
                                val hasCamPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasCamPermission) {
                                    val uri = createTempImageUri(context)
                                    tempImageUri = uri
                                    cameraLauncher.launch(uri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        ) {
                            Text("Use Camera")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showScanSourceDialog = false
                                galleryLauncher.launch("image/*")
                            }
                        ) {
                            Text("Choose Gallery")
                        }
                    }
                )
            }

            // Loading overlay during OCR execution
            if (isOcrLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Reading receipt details...", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class VoiceParsedTransaction(
    val isExpense: Boolean,
    val amount: Double,
    val category: String,
    val merchant: String
)

fun parseVoiceInput(
    text: String,
    expenseCategories: List<String>,
    incomeCategories: List<String>
): VoiceParsedTransaction {
    val cleanText = text.lowercase()

    // 1. Determine type (income vs expense)
    var isExpense = true
    if (cleanText.contains("received") || cleanText.contains("income") || cleanText.contains("earned") || cleanText.contains("salary") || cleanText.contains("freelance")) {
        isExpense = false
    }

    // 2. Extract amount (looks for numbers)
    val amountRegex = Regex("(\\d+(?:[\\d,\\.]*\\d)?)")
    val matches = amountRegex.findAll(cleanText)
    var amount = 0.0
    for (match in matches) {
        val numStr = match.value.replace(",", "")
        val parsed = numStr.toDoubleOrNull()
        if (parsed != null && parsed > 0.0) {
            amount = parsed
            break
        }
    }

    // 3. Extract category
    val categories = if (isExpense) expenseCategories else incomeCategories
    var category = categories[0]
    for (cat in categories) {
        if (cleanText.contains(cat.lowercase())) {
            category = cat
            break
        }
    }

    // 4. Extract merchant
    var merchant = "Voice Input"
    val merchantRegex = Regex("(?:at|from|to|on|for)\\s+([a-zA-Z0-9\\s]+)")
    val merchantMatch = merchantRegex.find(cleanText)
    if (merchantMatch != null) {
        val candidate = merchantMatch.groupValues[1].trim()
        if (candidate.isNotBlank() && !categories.any { it.lowercase() == candidate }) {
            merchant = candidate.split(" ").take(3).joinToString(" ").replaceFirstChar { it.uppercase() }
        }
    }

    return VoiceParsedTransaction(isExpense, amount, category, merchant)
}
