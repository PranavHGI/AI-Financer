package com.example.aifinancerfree.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aifinancerfree.data.ThemeMode
import com.example.aifinancerfree.ui.viewmodel.ProfileUiState
import com.example.aifinancerfree.ui.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    mode: ThemeMode,
    select: (ThemeMode) -> Unit,
    onConsent: () -> Unit,
    logout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Profile & settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    when (val state = uiState) {
                        is ProfileUiState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Loading profile...")
                        }
                        is ProfileUiState.Success -> {
                            Text(
                                text = state.user.username,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.user.email,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is ProfileUiState.Error -> {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.fetchProfile() }) {
                                Text("Retry")
                            }
                        }
                        is ProfileUiState.Unauthenticated -> {
                            Text("Not logged in")
                        }
                    }
                }
            }
        }
        item {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(ThemeMode.entries) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = mode == item,
                    onClick = { select(item) }
                )
                Text(
                    text = item.label,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        item {
            HorizontalDivider()
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConsent() }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SMS Ingestion & Privacy", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Manage default SMS app settings and consent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            HorizontalDivider()
        }
        item {
            OutlinedButton(
                onClick = logout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log out")
            }
        }
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System default"
        ThemeMode.LIGHT -> "Light mode"
        ThemeMode.DARK -> "Dark mode"
    }

