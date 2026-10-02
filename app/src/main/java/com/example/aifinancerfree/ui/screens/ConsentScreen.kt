package com.example.aifinancerfree.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aifinancerfree.data.local.ConsentPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentScreen(
    prefs: ConsentPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isTrackingEnabled by remember { mutableStateOf(prefs.isSmsTrackingEnabled()) }
    var isDefaultSmsApp by remember { mutableStateOf(checkDefaultSmsStatus(context)) }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        isDefaultSmsApp = checkDefaultSmsStatus(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Ingestion & Privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Role status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDefaultSmsApp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
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
                        tint = if (isDefaultSmsApp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDefaultSmsApp) "Default SMS Handler Active" else "Default SMS Handler Inactive",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isDefaultSmsApp) "AI Financer is configured to intercept transactions." else "Default role is required to parse messages.",
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
                        text = "• Raw SMS message texts are parsed locally on your phone and are NEVER uploaded to any servers or stored in any database.\n\n" +
                               "• Only structured transaction details (amount, category, merchant, timestamp) are extracted and saved.\n\n" +
                               "• You can turn off extraction at any time, which will immediately stop processing incoming messages.",
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
                    checked = isTrackingEnabled && isDefaultSmsApp,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (!isDefaultSmsApp) {
                                requestDefaultSmsRole(context) { intent ->
                                    roleLauncher.launch(intent)
                                }
                            } else {
                                prefs.setSmsTrackingEnabled(true)
                                isTrackingEnabled = true
                            }
                        } else {
                            prefs.setSmsTrackingEnabled(false)
                            isTrackingEnabled = false
                        }
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            // Action Buttons
            if (!isDefaultSmsApp) {
                Button(
                    onClick = {
                        requestDefaultSmsRole(context) { intent ->
                            roleLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set as Default SMS App")
                }
            }

            OutlinedButton(
                onClick = {
                    prefs.clearConsent()
                    isTrackingEnabled = false
                    Toast.makeText(context, "All tracking consent configurations and local markers deleted.", Toast.LENGTH_LONG).show()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete Consent Settings & Reset")
            }
        }
    }
}

private fun checkDefaultSmsStatus(context: Context): Boolean {
    return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
}

private fun requestDefaultSmsRole(context: Context, launch: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            launch(intent)
        }
    } else {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
        launch(intent)
    }
}
