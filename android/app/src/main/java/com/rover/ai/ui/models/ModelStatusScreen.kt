package com.rover.ai.ui.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rover.ai.ai.model.ModelFileStatus
import com.rover.ai.ai.model.ModelInfo
import com.rover.ai.ai.model.ModelRegistry
import kotlinx.coroutines.launch

/**
 * Model Status Screen
 * 
 * Shows status of AI models stored in external storage:
 * - Which models are detected / missing
 * - File size of each detected model
 * - ADB push instructions for missing models
 * - Refresh button to rescan for models
 * 
 * @param modelRegistry Model registry for accessing model status
 * @param modifier Optional modifier for layout customization
 */
@Composable
fun ModelStatusScreen(
    modelRegistry: ModelRegistry,
    modifier: Modifier = Modifier
) {
    val modelStates by modelRegistry.modelStates.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    // Scan on initial composition
    LaunchedEffect(Unit) {
        modelRegistry.scanModels()
    }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        item {
            Text(
                text = "AI Model Status",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp)
            )
        }
        
        // Storage location info
        item {
            StorageLocationCard(modelRegistry)
        }
        
        // Refresh button
        item {
            Button(
                onClick = {
                    scope.launch {
                        modelRegistry.scanModels()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh Model Status")
            }
        }
        
        // Model status cards
        items(modelStates.values.toList()) { modelInfo ->
            ModelStatusCard(
                modelInfo = modelInfo,
                modelRegistry = modelRegistry,
                onCopyCommand = { command ->
                    clipboardManager.setText(AnnotatedString(command))
                }
            )
        }
        
        // Help section
        item {
            HelpSection()
        }
    }
}

/**
 * Storage location information card
 */
@Composable
private fun StorageLocationCard(modelRegistry: ModelRegistry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Models Directory",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = modelRegistry.getModelsDirectory().absolutePath,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "ℹ️ App-scoped storage (no permissions needed)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Individual model status card
 */
@Composable
private fun ModelStatusCard(
    modelInfo: ModelInfo,
    modelRegistry: ModelRegistry,
    onCopyCommand: (String) -> Unit
) {
    val (statusColor, statusText, statusIcon) = when (modelInfo.status) {
        ModelFileStatus.MISSING -> Triple(Color(0xFFF44336), "Missing", Icons.Default.Error)
        ModelFileStatus.FOUND -> Triple(Color(0xFF4CAF50), "Found", Icons.Default.CheckCircle)
        ModelFileStatus.LOADED -> Triple(Color(0xFF2196F3), "Loaded", Icons.Default.Cloud)
        ModelFileStatus.ERROR -> Triple(Color(0xFFFF9800), "Error", Icons.Default.Warning)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Model name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelInfo.metadata.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = modelInfo.metadata.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // File size info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Expected Size:",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatSize(modelInfo.metadata.expectedSizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (modelInfo.actualSizeBytes != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Actual Size:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatSize(modelInfo.actualSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (modelInfo.actualSizeBytes < modelInfo.metadata.expectedSizeBytes * 0.9) {
                            Color(0xFFFF9800)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            
            // Error message
            if (modelInfo.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Error: ${modelInfo.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF44336)
                )
            }
            
            // ADB push instructions for missing models
            if (modelInfo.status == ModelFileStatus.MISSING) {
                Spacer(modifier = Modifier.height(12.dp))
                
                HorizontalDivider()
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "⚠️ Push via ADB:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "adb push ${modelInfo.metadata.fileName} ${modelRegistry.getModelsDirectory().absolutePath}/",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = {
                                onCopyCommand("adb push ${modelInfo.metadata.fileName} ${modelRegistry.getModelsDirectory().absolutePath}/")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy command",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Help section with general instructions
 */
@Composable
private fun HelpSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Help,
                    contentDescription = "Help",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "How to Push Models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "1. Connect your device via USB",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Text(
                text = "2. Enable USB debugging in Developer Options",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Text(
                text = "3. Run the ADB push command from your computer",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Text(
                text = "4. Tap Refresh to rescan for models",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "💡 Tip: Use scripts/push_models.sh to push all models at once",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Format file size in human-readable format
 */
private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.2f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
