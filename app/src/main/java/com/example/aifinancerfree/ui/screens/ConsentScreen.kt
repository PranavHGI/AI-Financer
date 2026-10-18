package com.example.aifinancerfree.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.aifinancerfree.AIFinancerApplication
import com.example.aifinancerfree.data.local.ConsentPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentScreen(
    prefs: ConsentPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val app = context.applicationContext as? AIFinancerApplication ?: AIFinancerApplication.instance
    val accessManager = app.smsAccessManager
    val dbHelper = app.databaseHelper

    val isAccessEnabled by accessManager.isAccessEnabled.collectAsState()
    var isConsentGranted by remember { mutableStateOf(accessManager.isConsentGranted()) }
    var hasPermissions by remember { mutableStateOf(accessManager.hasAllRequiredPermissions()) }

    var showRevokeDialog by remember { mutableStateOf(false) }
    var showResetConsentDialog by remember { mutableStateOf(false) }
    var showResetHistoryDialog by remember { mutableStateOf(false) }

    fun refreshStates() {
        accessManager.refresh()
        isConsentGranted = accessManager.isConsentGranted()
        hasPermissions = accessManager.hasAllRequiredPermissions()
    }

    // Refresh when returning from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val readGranted = perms[Manifest.permission.READ_SMS] == true
        val receiveGranted = perms[Manifest.permission.RECEIVE_SMS] == true

        if (readGranted && receiveGranted) {
            accessManager.setConsentEnabled(true)
            refreshStates()
            Toast.makeText(context, "SMS ingestion enabled successfully!", Toast.LENGTH_SHORT).show()
        } else {
            accessManager.setConsentEnabled(false)
            refreshStates()
            Toast.makeText(
                context,
                "Both READ_SMS and RECEIVE_SMS permissions are required for ingestion.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Ingestion & Privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccessEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isAccessEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAccessEnabled) "SMS Tracking Active" else "SMS Tracking Inactive",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isAccessEnabled) {
                                "Ingestion is authorized to detect transactions locally."
                            } else {
                                "Turn on the toggle below to enable local transaction parsing."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Privacy/Assurance Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Privacy Assurances", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "• Raw SMS texts are parsed locally on your device and are NEVER uploaded to any servers or stored in any database.\n\n" +
                               "• Only structured transaction details (amount, category, merchant, timestamp) are extracted and saved.\n\n" +
                               "• You can pause or revoke ingestion at any time, which immediately stops reading incoming messages.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Consent Ingestion Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Ingestion", fontWeight = FontWeight.Bold)
                    Text(
                        text = "Extract transactions from financial SMS messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isAccessEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (!accessManager.hasAllRequiredPermissions()) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.RECEIVE_SMS
                                    )
                                )
                            } else {
                                accessManager.setConsentEnabled(true)
                                refreshStates()
                            }
                        } else {
                            showRevokeDialog = true
                        }
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            // Reset Import History Button
            Button(
                onClick = { showResetHistoryDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Import History")
            }

            // Clear Consent Settings Button
            OutlinedButton(
                onClick = { showResetConsentDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete Consent Settings & Reset")
            }
        }
    }

    // Revoke Explanation Dialog
    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text("Disable SMS Tracking") },
            text = {
                Text("SMS transaction parsing will be paused immediately. To fully revoke Android system permissions, you can open App Settings.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        accessManager.setConsentEnabled(false)
                        refreshStates()
                        showRevokeDialog = false
                    }
                ) {
                    Text("Turn Off")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                        showRevokeDialog = false
                    }
                ) {
                    Text("App Settings")
                }
            }
        )
    }

    // Confirmation: Reset Consent Settings
    if (showResetConsentDialog) {
        AlertDialog(
            onDismissRequest = { showResetConsentDialog = false },
            title = { Text("Reset Consent Settings?") },
            text = {
                Text("This will clear your tracking consent preference. SMS processing will stop until you enable it again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        accessManager.clearConsent()
                        refreshStates()
                        showResetConsentDialog = false
                        Toast.makeText(context, "Consent settings cleared.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConsentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation: Reset Import History
    if (showResetHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showResetHistoryDialog = false },
            title = { Text("Reset Import History?") },
            text = {
                Text("This will clear all recorded SMS message fingerprints, allowing previously parsed inbox messages to be re-scanned.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dbHelper.resetParsedSmsHistory()
                        showResetHistoryDialog = false
                        Toast.makeText(context, "Import history reset. You can now re-scan your inbox.", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text("Reset History")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
