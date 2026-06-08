package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLog
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: MainViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Diagnostic Terminal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TerminalContent(viewModel = viewModel)
        }
    }
}

@Composable
fun LogsScreenDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E2124), // matching photo slate terminal look
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Diagonal header: green terminal icon + title
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal, 
                        contentDescription = null, 
                        tint = Color(0xFF00FFC2), // neon cyan/green
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unified Diagnostic Terminal",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFECEFF1)
                        )
                    )
                }
                
                Divider(color = Color(0x22ECEFF1), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                
                // Embedded Terminal contents
                Box(modifier = Modifier.weight(1f)) {
                    TerminalContent(viewModel = viewModel)
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                // Dialog action footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("terminal_dismiss_button")
                    ) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FFC2) // matching photo green color
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalContent(
    viewModel: MainViewModel
) {
    val logs by viewModel.allLogs.collectAsState()
    val storage by viewModel.storageInfo.collectAsState()
    
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var showExportResultDialog by remember { mutableStateOf(false) }
    var exportResultText by remember { mutableStateOf("") }
    var exportType by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val jsonExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(exportResultText.toByteArray(Charsets.UTF_8))
                    }
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "JSON exported and saved successfully!", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to save JSON: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val csvExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(exportResultText.toByteArray(Charsets.UTF_8))
                    }
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "CSV exported and saved successfully!", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to save CSV: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Query storage metrics when the terminal comes into view
    LaunchedEffect(Unit) {
        viewModel.updateStorageInfo()
    }

    val filteredLogs = remember(logs, selectedFilter, searchQuery) {
        logs.filter { log ->
            val matchesFilter = when (selectedFilter) {
                "Ticks" -> log.type == "TICK"
                "Alerts" -> log.type == "ALERT_TRIGGER"
                "Failures" -> log.type == "ERROR" || log.type == "CRASH"
                "Heal / Protect" -> log.type == "HEALING" || log.type == "PROTECTION" || log.type == "RECOVERY" || log.type == "SYSTEM"
                else -> true
            }
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                log.message.contains(searchQuery, ignoreCase = true) ||
                        (log.symbol?.contains(searchQuery, ignoreCase = true) == true)
            }
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Line header showing bold Live Log Terminal + red Delete button
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Live Log Terminal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "System Register Diagnostics • Limit 100 in view",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF90A4AE)
                )
            }
            IconButton(
                onClick = { viewModel.clearAllLogs() },
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear All Logs",
                    tint = Color(0xFFEF5350)
                )
            }
        }

        // Outlined search text field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter keywords... (e.g. BTC, rule, crash)", fontSize = 12.sp, color = Color(0x66ECEFF1)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFECEFF1).copy(alpha = 0.6f), modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search", tint = Color(0xFFECEFF1).copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            },
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("log_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00FFC2),
                unfocusedBorderColor = Color(0x33ECEFF1),
                unfocusedContainerColor = Color(0xFF15181C),
                focusedContainerColor = Color(0xFF15181C),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Rounded filter pills row (All matches green inside photo screenshot)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Ticks", "Alerts", "Failures", "Heal / Protect").forEach { category ->
                val isSelected = selectedFilter == category
                val containerColor = if (isSelected) Color(0xFF00FFC2) else Color(0xFF15181C)
                val labelColor = if (isSelected) Color.Black else Color(0xFF90A4AE)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(containerColor)
                        .clickable { selectedFilter = category }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("log_filter_${category.lowercase().replace(" ", "_").replace("/", "")}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )
                }
            }
        }

        // Disk Storage Metrics display with remaining space tracking & auto-purging notification
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            color = Color(0xFF15181C),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1B00FFC2))
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "ROM Disk Logs Storage: ${String.format("%.3f", storage.usedMB)} MB / 10.00 MB",
                            fontSize = 11.sp,
                            fontFamily = PriceTextFontFamily,
                            color = Color(0xFFECEFF1),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format("%.1f", (1f - storage.usedPercent) * 100f)}% Free",
                            fontSize = 10.sp,
                            fontFamily = PriceTextFontFamily,
                            color = if (storage.usedPercent > 0.8f) Color(0xFFFF1744) else Color(0xFF00FFC2),
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { storage.usedPercent },
                        color = if (storage.usedPercent > 0.8f) Color(0xFFFF1744) else Color(0xFF00FFC2),
                        trackColor = Color(0xFF263238),
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%.3f", storage.remainingMB)} MB Remaining • Oldest 70% auto-cleared when full.",
                        fontSize = 9.sp,
                        fontFamily = PriceTextFontFamily,
                        color = Color(0x99ECEFF1)
                    )
                }
            }
        }

        // Row of export action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isExporting = true
                        val allLogsForExport = viewModel.getAllLogsForExport()
                        val json = try {
                            val arr = org.json.JSONArray()
                            allLogsForExport.forEach { log ->
                                val obj = org.json.JSONObject()
                                obj.put("id", log.id)
                                obj.put("timestamp", log.timestamp)
                                obj.put("type", log.type)
                                obj.put("symbol", log.symbol ?: "")
                                obj.put("message", log.message)
                                arr.put(obj)
                            }
                            arr.toString(2)
                        } catch (e: Exception) {
                            "[]"
                        }
                        exportResultText = json
                        exportType = "JSON"
                        clipboardManager.setText(AnnotatedString(json))
                        shareExportedFile(context, "fintrace_diagnostic_logs.json", json, true)
                        try {
                            jsonExporter.launch("fintrace_diagnostic_logs.json")
                        } catch (ex: Exception) {
                            android.util.Log.e("LogsScreen", "SAF export launch error: ${ex.message}")
                        }
                        showExportResultDialog = true
                        isExporting = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF37474F),
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !isExporting
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    scope.launch {
                        isExporting = true
                        val allLogsForExport = viewModel.getAllLogsForExport()
                        val csv = buildString {
                            append("ID,Timestamp,Type,Symbol,Message\n")
                            allLogsForExport.forEach { log ->
                                val cleanMsg = log.message.replace("\"", "\"\"")
                                val cleanSymbol = (log.symbol ?: "").replace("\"", "\"\"")
                                append("${log.id},${log.timestamp},${log.type},\"$cleanSymbol\",\"$cleanMsg\"\n")
                            }
                        }
                        exportResultText = csv
                        exportType = "CSV"
                        clipboardManager.setText(AnnotatedString(csv))
                        shareExportedFile(context, "fintrace_diagnostic_logs.csv", csv, false)
                        try {
                            csvExporter.launch("fintrace_diagnostic_logs.csv")
                        } catch (ex: Exception) {
                            android.util.Log.e("LogsScreen", "SAF export launch error: ${ex.message}")
                        }
                        showExportResultDialog = true
                        isExporting = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF37474F),
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !isExporting
            ) {
                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Stress test crash simulator button matching screenshot
        Button(
            onClick = {
                throw RuntimeException("FinTrace User-Triggered Diagnostic Stress Test Crash")
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5D1015),
                contentColor = Color(0xFFFFCDD2)
            ),
            modifier = Modifier.fillMaxWidth().height(36.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFFCDD2))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Stress Test Crash Trigger", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFCDD2))
        }

        // Automatic safety capping rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Displaying ${filteredLogs.size} records",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF90A4AE)
            )
            Text(
                text = "Automatic Safety Capping: Active (Max 100)",
                fontSize = 11.sp,
                color = Color(0xFF00FFC2),
                fontWeight = FontWeight.Bold
            )
        }

        // Monospaced terminal list panel
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0B0D11))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = Color(0x33ECEFF1),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Terminal silent. No records match filter.",
                        color = Color(0x99ECEFF1),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = PriceTextFontFamily,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0B0D11))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = filteredLogs,
                    key = { "${it.id}_${it.type}_${it.timestamp}" } // absolutely bulletproof composable key format
                ) { logItem ->
                    LogRecordRow(log = logItem, timeStr = sdf.format(Date(logItem.timestamp)))
                }
            }
        }
    }

    // EXPORT SUCCESS AND PREVIEW MODAL
    if (showExportResultDialog) {
        AlertDialog(
            onDismissRequest = { showExportResultDialog = false },
            confirmButton = {
                TextButton(onClick = { showExportResultDialog = false }) { 
                    Text("Dismiss", fontWeight = FontWeight.Bold) 
                }
            },
            title = { Text("$exportType Diagnostic Export") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The full audit trace was compiled successfully into a file, shared with the system, and copied to your clipboard! See preview below:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = exportResultText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = PriceTextFontFamily, fontSize = 9.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0B0D11),
                            unfocusedContainerColor = Color(0xFF0B0D11),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(exportResultText))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy to Clipboard")
                    }
                }
            }
        )
    }
}

@Composable
fun LogRecordRow(log: AppLog, timeStr: String) {
    val isSimulated = log.type == "TICK" && (log.message.contains("simulated", ignoreCase = true) || !log.message.contains("live", ignoreCase = true))

    val termColor = when {
        isSimulated -> Color(0xFFFF1844)     // Flame Crimson / Red for simulated ticks
        log.type == "TICK" -> Color(0xFF00FFC2)          // Neon Seafoam (Live market feeds)
        log.type == "ALERT_TRIGGER" -> Color(0xFFFF3D00) // Vibrant Red (Alarm triggers)
        log.type == "SYSTEM" -> Color(0xFFFFD600)        // Terminal Amber/Yellow (System syncs)
        log.type == "CRASH" -> Color(0xFFFF1744)         // Flame Crimson (Uncaught exceptions)
        log.type == "ERROR" -> Color(0xFFFF9100)         // Orange/Red (Recovered errors)
        log.type == "HEALING" -> Color(0xFF69F0AE)       // Pale Mint (Component self-healing events)
        log.type == "PROTECTION" -> Color(0xFF00E5FF)    // Electric Blue (Resource protections)
        log.type == "RECOVERY" -> Color(0xFFE040FB)      // Bright Magenta (System state retrievals)
        else -> Color(0xFFECEFF1)
    }

    val typePrefix = when {
        isSimulated -> "✖ TICK"
        log.type == "TICK" -> "✔ TICK"
        log.type == "ALERT_TRIGGER" -> "🚨 ALERT"
        log.type == "SYSTEM" -> "⚙ SYSTEM"
        log.type == "CRASH" -> "💀 CRASH"
        log.type == "ERROR" -> "❌ ERROR"
        log.type == "HEALING" -> "🩺 HEAL"
        log.type == "PROTECTION" -> "🛡️ PROT"
        log.type == "RECOVERY" -> "🔄 RECOV"
        else -> "ℹ INFO"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[$timeStr]",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PriceTextFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = Color(0x80ECEFF1),
            modifier = Modifier.padding(top = 1.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = String.format("%-8s", typePrefix),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PriceTextFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = termColor,
            modifier = Modifier.padding(top = 1.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PriceTextFontFamily,
                fontSize = 11.sp,
                lineHeight = 14.sp
            ),
            color = Color(0xFFECEFF1),
            modifier = Modifier.weight(1f)
        )
    }
}

fun shareExportedFile(context: android.content.Context, filename: String, content: String, isJson: Boolean) {
    try {
        val cacheFile = java.io.File(context.cacheDir, filename)
        cacheFile.writeText(content)

        val authority = "com.example.fintrace.fileprovider"
        val fileUri = androidx.core.content.FileProvider.getUriForFile(context, authority, cacheFile)

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = if (isJson) "application/json" else "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "FinTrace $filename")
            putExtra(android.content.Intent.EXTRA_TEXT, "FinTrace system diagnostic logs exported format.")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(android.content.Intent.createChooser(shareIntent, "Save or Send FinTrace Diagnostic logs..."))
    } catch (e: Exception) {
        android.util.Log.e("LogsScreen", "Failed to share/export file: ${e.message}", e)
        android.widget.Toast.makeText(context, "Export error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}
