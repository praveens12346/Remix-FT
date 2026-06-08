package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Alert
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertListScreen(
    viewModel: MainViewModel
) {
    val alerts by viewModel.alertList.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // All, Active, Triggered

    Scaffold(
        floatingActionButton = {
            if (alerts.isEmpty()) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Alert")
                }
            }
        },
        bottomBar = {
            if (alerts.isNotEmpty()) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.activateAllAlerts() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Activate All",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deactivateAllAlerts() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Deactivate All",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteAllAlerts() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = AlertCritical)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete All Alerts",
                                    tint = AlertCritical,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showCreateDialog = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(12.dp),
                            elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create Alert")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Alerts by Symbol...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                singleLine = true
            )

            // Dynamic Pill Chips filtering row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Active", "Triggered").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    val labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(containerColor)
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = labelColor
                        )
                    }
                }
            }

            // Filter process variables
            val filteredAlerts = alerts.filter { alert ->
                val matchesSearch = alert.symbol.contains(searchQuery, ignoreCase = true) ||
                        alert.title.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (selectedFilter) {
                    "Active" -> alert.isActive
                    "Triggered" -> !alert.isActive
                    else -> true
                }
                matchesSearch && matchesFilter
            }

            if (filteredAlerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Alert Rules Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Define triggers to monitor custom target points in real-time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAlerts, key = { it.id }) { alert ->
                        AlertRuleItem(
                            alert = alert,
                            onToggleActive = { viewModel.toggleAlertActive(alert.id, it) },
                            onDelete = { viewModel.deleteAlert(alert.id) }
                        )
                    }
                }
            }
        }
    }

    // Modal creation dialog sheet
    if (showCreateDialog) {
        CreateAlertDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { symbol, cond, price, isOneTime, priority, msg ->
                viewModel.createAlert(
                    symbol = symbol,
                    condition = cond,
                    targetPrice = price,
                    title = "$symbol crossed target",
                    message = msg.ifBlank { "Crossing detected. Price exceeded $price threshold." },
                    isOneTime = isOneTime,
                    priority = priority,
                    colorTagIndex = 0
                )
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun AlertRuleItem(
    alert: Alert,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val info = SymbolInfo.find(alert.symbol)
    val formattedPrice = alert.targetPrice.formatPriceDynamic(info.getDisplayDecimals())

    val condLabel = when (alert.condition) {
        "CROSSING_UP" -> "Crossing Up"
        "CROSSING_DOWN" -> "Crossing Down"
        else -> "Crossing"
    }

    val themeColor = when (alert.priority) {
        "LOW" -> AlertExpired
        "MEDIUM" -> AlertTriggered
        "HIGH" -> AlertActive
        "CRITICAL" -> AlertCritical
        else -> AlertActive
    }

    val priorityIcon = when (alert.priority) {
        "LOW" -> Icons.Default.Info
        "MEDIUM" -> Icons.Default.CompareArrows
        "HIGH" -> Icons.Default.TrendingUp
        "CRITICAL" -> Icons.Default.Warning
        else -> Icons.Default.Notifications
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (alert.isActive && alert.priority == "CRITICAL") {
            BorderStroke(1.dp, AlertCritical.copy(alpha = 0.4f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left priority vertical bar tag representing priority visually with color coding
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(if (alert.isActive) themeColor else themeColor.copy(alpha = 0.35f))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info Column block
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = priorityIcon,
                            contentDescription = null,
                            tint = if (alert.isActive) themeColor else themeColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = alert.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (alert.isActive) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(themeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = alert.priority,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = themeColor,
                                fontSize = 8.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$condLabel at $$formattedPrice",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = PriceTextFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (alert.isActive) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (alert.isOneTime) "One-Time Execution Only" else "Repeating Active Trigger",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    // Optional User Memo Notice Card
                    if (alert.message.isNotBlank() && !alert.message.startsWith("Crossing detected.")) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = themeColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = alert.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }

                // Control Toggles block
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = alert.isActive,
                        onCheckedChange = { onToggleActive(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Alert",
                            tint = AlertCritical,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAlertDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Double, Boolean, String, String) -> Unit
) {
    var selectedSymbol by remember { mutableStateOf(SymbolInfo.ALL.first().symbol) }
    var condition by remember { mutableStateOf("CROSSING") } // "CROSSING", "CROSSING_UP", "CROSSING_DOWN"
    var targetPriceInput by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf("") }
    var isOneTime by remember { mutableStateOf(true) }
    var priority by remember { mutableStateOf("HIGH") } // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    
    var isExpandedSymbol by remember { mutableStateOf(false) }

    // State validation helper check
    val isValidPrice = remember(targetPriceInput) {
        val parsed = targetPriceInput.toDoubleOrNull()
        parsed != null && parsed > 0.0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val priceVal = targetPriceInput.toDoubleOrNull()
                    if (priceVal != null && priceVal > 0) {
                        onCreate(selectedSymbol, condition, priceVal, isOneTime, priority, messageInput)
                    }
                },
                enabled = isValidPrice,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            ) {
                Text("Create Alert", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                "New Analytics Trigger",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dropdown asset picker
                ExposedDropdownMenuBox(
                    expanded = isExpandedSymbol,
                    onExpandedChange = { isExpandedSymbol = !isExpandedSymbol }
                ) {
                    OutlinedTextField(
                        value = selectedSymbol,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Selected Asset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpandedSymbol) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = isExpandedSymbol,
                        onDismissRequest = { isExpandedSymbol = false }
                    ) {
                        SymbolInfo.ALL.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s.symbol} — ${s.name}") },
                                onClick = {
                                    selectedSymbol = s.symbol
                                    isExpandedSymbol = false
                                }
                            )
                        }
                    }
                }

                // Inline Condition Segmented Row (Avoids click-heavy dropdowns)
                Column {
                    Text(
                        "Trigger Condition",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("CROSSING", "CROSSING_UP", "CROSSING_DOWN").forEach { cond ->
                            val isSel = condition == cond
                            val activeSelectionColor = MaterialTheme.colorScheme.primary
                            val inactiveSelectionColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) activeSelectionColor else inactiveSelectionColor)
                                    .clickable { condition = cond }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cond.replace("CROSSING_", "").replace("CROSSING", "BOTH"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Numeric input field with instant validation feedback highlight
                OutlinedTextField(
                    value = targetPriceInput,
                    onValueChange = { targetPriceInput = it },
                    label = { Text("Target Threshold Price") },
                    placeholder = { Text("e.g. 2318.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    isError = targetPriceInput.isNotEmpty() && !isValidPrice,
                    supportingText = {
                        if (targetPriceInput.isNotEmpty() && !isValidPrice) {
                            Text(
                                "Invalid numeric format. Ensure a clean format (e.g. 1.08250)",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )

                // Optional Memo Note
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    label = { Text("Alert Message (Optional)") },
                    placeholder = { Text("e.g. Resistance level buy limit reached") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = false,
                    maxLines = 2
                )

                // Inline Priority selection Pill rows (Eliminates secondary nested dropdown box!)
                Column {
                    Text(
                        "Priority Rank",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { prio ->
                            val isSel = priority == prio
                            val toneColor = when (prio) {
                                "LOW" -> AlertExpired
                                "MEDIUM" -> AlertTriggered
                                "HIGH" -> AlertActive
                                "CRITICAL" -> AlertCritical
                                else -> AlertActive
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSel) toneColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                    .clickable { priority = prio }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = prio,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // One-time execution option Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "One-Time Execution",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Auto-disables the target immediately after firing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isOneTime,
                        onCheckedChange = { isOneTime = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
