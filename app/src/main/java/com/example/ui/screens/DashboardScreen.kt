package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.DecimalFormat

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onSymbolSelected: (String) -> Unit,
    onQuickAlertRequest: (String) -> Unit
) {
    val activeSub by viewModel.activeSymbols.collectAsState()
    val priceState by viewModel.priceState.collectAsState()
    val connStatus by viewModel.connectionStatus.collectAsState()
    val latency by viewModel.latencyMs.collectAsState()
    val cardStyle by viewModel.dashboardCardStyle.collectAsState()
    val priceTextSize by viewModel.priceTextSize.collectAsState()
    val symbolIdTextSize by viewModel.symbolIdTextSize.collectAsState()
    val symbolNameTextSize by viewModel.symbolNameTextSize.collectAsState()

    val resolvedPriceSize = remember(priceTextSize, cardStyle) {
        if (priceTextSize > 0f) {
            priceTextSize.sp
        } else {
            when (cardStyle) {
                "Classic Row" -> 20.sp
                "Compact" -> 22.sp
                else -> 32.sp
            }
        }
    }

    val resolvedSymbolIdSize = remember(symbolIdTextSize, cardStyle) {
        if (symbolIdTextSize > 0f) {
            symbolIdTextSize.sp
        } else {
            when (cardStyle) {
                "Classic Row" -> 16.sp
                "Compact" -> 14.sp
                else -> 18.sp
            }
        }
    }

    val resolvedSymbolNameSize = remember(symbolNameTextSize, cardStyle) {
        if (symbolNameTextSize > 0f) {
            symbolNameTextSize.sp
        } else {
            when (cardStyle) {
                "Classic Row" -> 12.sp
                "Compact" -> 10.sp
                else -> 12.sp
            }
        }
    }

    var showManageAssetsDialog by remember { mutableStateOf(false) }
    val maxLimit = 6

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Branded Page Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FinTrace Ticker",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Real-time assets monitoring",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            
            // Highly professional asset manager button
            Button(
                onClick = { showManageAssetsDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Manage Assets",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Manage",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Connection & Diagnostic Status Bar
        ConnectionStatusBar(
            status = connStatus,
            latency = latency,
            activeCount = activeSub.size,
            maxLimit = maxLimit
        )

        if (activeSub.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AddChart,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Active Symbols",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Go to Settings or tap the [+] button in the status bar to active your portfolio assets for real-time monitoring.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showManageAssetsDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Assets Now")
                    }
                }
            }
        } else {
            if (cardStyle == "Classic Row") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val activeTicks = activeSub.mapNotNull { priceState[it] }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(activeTicks, key = { it.symbol }) { tick ->
                            PriceMetricClassicRow(
                                tick = tick,
                                connectionStatus = connStatus,
                                priceSize = resolvedPriceSize,
                                symbolIdSize = resolvedSymbolIdSize,
                                symbolNameSize = resolvedSymbolNameSize,
                                onTap = { onSymbolSelected(tick.symbol) },
                                onLongPress = { onQuickAlertRequest(tick.symbol) }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                val gridCells = if (cardStyle == "Compact") GridCells.Fixed(2) else GridCells.Fixed(1)
                LazyVerticalGrid(
                    columns = gridCells,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val activeTicks = activeSub.mapNotNull { priceState[it] }
                    items(activeTicks, key = { it.symbol }) { tick ->
                        PriceMetricCard(
                            tick = tick,
                            connectionStatus = connStatus,
                            cardStyle = cardStyle,
                            priceSize = resolvedPriceSize,
                            symbolIdSize = resolvedSymbolIdSize,
                            symbolNameSize = resolvedSymbolNameSize,
                            onTap = { onSymbolSelected(tick.symbol) },
                            onLongPress = { onQuickAlertRequest(tick.symbol) }
                        )
                    }
                }
            }
        }
    }

    // Asset Management Dialog
    if (showManageAssetsDialog) {
        ManageAssetsDialog(
            currentActiveSymbols = activeSub,
            allSymbols = SymbolInfo.ALL,
            maxLimit = maxLimit,
            onToggle = { symbol, active ->
                viewModel.toggleSymbolActive(symbol, active)
            },
            onDismiss = { showManageAssetsDialog = false }
        )
    }
}

@Composable
fun ConnectionStatusBar(
    status: String,
    latency: Long,
    activeCount: Int,
    maxLimit: Int
) {
    val (dotColor, statusText) = when (status) {
        "LIVE" -> Pair(ConnectionLive, "LIVE")
        "CONNECTING" -> Pair(ConnectionReconnecting, "CONNECTING")
        else -> Pair(ConnectionOffline, "OFFLINE")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(dotColor)
            )
            
            Text(
                text = "$statusText Mode",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = dotColor,
                    fontSize = 11.sp
                )
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "$activeCount / $maxLimit Monitored",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
            
            val pingColor = if (latency < 300) ConnectionLive else ConnectionReconnecting
            Text(
                text = "Ping: ${latency}ms",
                style = MaterialTheme.typography.labelMedium,
                color = pingColor,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ManageAssetsDialog(
    currentActiveSymbols: List<String>,
    allSymbols: List<SymbolInfo>,
    maxLimit: Int,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontWeight = FontWeight.Black)
            }
        },
        title = {
            Column {
                Text(
                    text = "Asset Portfolio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Active Tracking: ${currentActiveSymbols.size} / $maxLimit Max",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentActiveSymbols.size >= maxLimit) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Max limit of $maxLimit reached. Please disable a monitored asset to add a new one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allSymbols) { info ->
                        val isChecked = currentActiveSymbols.contains(info.symbol)
                        val isLimitReached = currentActiveSymbols.size >= maxLimit && !isChecked
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable(enabled = !isLimitReached) {
                                    onToggle(info.symbol, !isChecked)
                                }
                                .padding(vertical = 6.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = info.symbol,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = info.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { active ->
                                    if (!isLimitReached) {
                                        onToggle(info.symbol, active)
                                    }
                                },
                                enabled = !isLimitReached,
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriceMetricCard(
    tick: PriceTick,
    connectionStatus: String,
    cardStyle: String,
    priceSize: androidx.compose.ui.unit.TextUnit,
    symbolIdSize: androidx.compose.ui.unit.TextUnit,
    symbolNameSize: androidx.compose.ui.unit.TextUnit,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val info = SymbolInfo.find(tick.symbol)

    // Flashing animations on price updates
    var prevPrice by remember { mutableStateOf(tick.price) }
    var flashColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(tick.price) {
        if (tick.price > prevPrice) {
            flashColor = PriceUpDark.copy(alpha = 0.25f)
            delay(120)
            flashColor = Color.Transparent
        } else if (tick.price < prevPrice) {
            flashColor = PriceDownDark.copy(alpha = 0.25f)
            delay(120)
            flashColor = Color.Transparent
        }
        prevPrice = tick.price
    }

    val arrowSymbol = if (tick.change >= 0) "▲" else "▼"
    val changeColor = if (tick.change >= 0) PriceUpDark else PriceDownDark

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(flashColor)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val displayDecs = info.getDisplayDecimals()
            
            // 1. Live Price (Top)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tick.price.formatPriceDynamic(displayDecs),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = PriceTextFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = priceSize,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alignByBaseline()
                )
                
                // Subtle Live Activity Tag
                val isLive = connectionStatus == "LIVE"
                val indicatorColor = if (isLive) ConnectionLive else ConnectionOffline
                val indicatorText = if (isLive) "LIVE" else "LOST"

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(indicatorColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .alignByBaseline()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(indicatorColor)
                        )
                        Text(
                            text = indicatorText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = indicatorColor,
                            fontSize = 8.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2 & 3 & 4. Asset Details Row (Symbol, Description, Percentage Movement badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tick.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = symbolIdSize),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = info.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = symbolNameSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Percentage Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(changeColor.copy(alpha = 0.11f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (tick.change >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = changeColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = String.format("%.2f%%", kotlin.math.abs(tick.changePercent)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, fontSize = (priceSize.value * 0.55f).sp),
                            color = changeColor
                        )
                    }
                }
            }

            // 5. Supporting Info (Spread Bid/Ask and historical canvas)
            if (cardStyle != "Compact") {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bid/Ask Values
                    Column {
                        Text(
                            text = "BID / ASK FEED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "B: ${tick.bid.formatPriceDynamic(displayDecs)}  |  A: ${tick.ask.formatPriceDynamic(displayDecs)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PriceTextFontFamily, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Spread Points
                    val spread = if (tick.ask > tick.bid) tick.ask - tick.bid else 0.0001
                    val multiplier = java.lang.Math.pow(10.0, displayDecs.toDouble())
                    val spreadInt = kotlin.math.round(spread * multiplier).toInt()
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "SPREAD",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "$spreadInt PTS",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PriceTextFontFamily, fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Canvas Sparkline
                if (tick.history.size > 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                            .padding(vertical = 3.dp, horizontal = 10.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height

                            val minVal = tick.history.minOrNull() ?: 1.0
                            val maxVal = tick.history.maxOrNull() ?: 1.0
                            val diff = if (maxVal - minVal > 0.0) maxVal - minVal else 1.0

                            val stepX = width / (tick.history.size - 1)
                            val path = Path()

                            tick.history.forEachIndexed { i, p ->
                                val x = i * stepX
                                val normalizeY = (p - minVal) / diff
                                val y = height - (normalizeY * height).toFloat()

                                if (i == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                            }

                            drawPath(
                                path = path,
                                color = changeColor,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssetOverlapIcon(symbol: String, modifier: Modifier = Modifier) {
    val parts = symbol.split("/")
    val base = parts.getOrNull(0) ?: symbol.take(3)
    val target = parts.getOrNull(1) ?: symbol.drop(3).take(3)

    val (baseEmoji, baseColor) = when (base.uppercase()) {
        "XAU" -> Pair("🪙", Color(0xFFFFD700))
        "XAG" -> Pair("💿", Color(0xFFC0C0C0))
        "EUR" -> Pair("🇪🇺", Color(0xFF003399))
        "GBP" -> Pair("🇬🇧", Color(0xFFC8102E))
        "USD" -> Pair("🇺🇸", Color(0xFF002868))
        "JPY" -> Pair("🇯🇵", Color(0xFFBC002D))
        "CHF" -> Pair("🇨🇭", Color(0xFFD52B1E))
        "AUD" -> Pair("🇦🇺", Color(0xFF00008B))
        "CAD" -> Pair("🇨🇦", Color(0xFFFF0000))
        "NZD" -> Pair("🇳🇿", Color(0xFF00247D))
        else -> Pair("🌐", Color(0xFF4A4A4A))
    }

    val (targetEmoji, targetColor) = when (target.uppercase()) {
        "USD" -> Pair("🇺🇸", Color(0xFF002868))
        "JPY" -> Pair("🇯🇵", Color(0xFFBC002D))
        "GBP" -> Pair("🇬🇧", Color(0xFFC8102E))
        "AUD" -> Pair("🇦🇺", Color(0xFF00008B))
        "CHF" -> Pair("🇨🇭", Color(0xFFD52B1E))
        else -> Pair("🌐", Color(0xFF4A4A4A))
    }

    Box(modifier = modifier.size(42.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(baseColor.copy(alpha = 0.2f))
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            Text(baseEmoji, fontSize = 20.sp)
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(targetColor.copy(alpha = 0.25f))
                .align(Alignment.BottomEnd),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(targetEmoji, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun PriceTextWithSuperscript(
    priceStr: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (priceStr.isEmpty()) return
    
    val lastChar = priceStr.last()
    val rest = priceStr.dropLast(1)
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = rest,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = PriceTextFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                letterSpacing = (-0.5).sp
            ),
            color = color,
            modifier = Modifier.alignByBaseline()
        )
        if (lastChar.isDigit()) {
            Text(
                text = lastChar.toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = PriceTextFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (fontSize.value * 0.65f).sp
                ),
                color = color,
                modifier = Modifier
                    .padding(start = 1.dp)
                    .alignByBaseline()
            )
        } else {
            Text(
                text = lastChar.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PriceTextFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize
                ),
                color = color,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriceMetricClassicRow(
    tick: PriceTick,
    connectionStatus: String,
    priceSize: androidx.compose.ui.unit.TextUnit,
    symbolIdSize: androidx.compose.ui.unit.TextUnit,
    symbolNameSize: androidx.compose.ui.unit.TextUnit,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val info = SymbolInfo.find(tick.symbol)
    val displayDecs = info.getDisplayDecimals()
    
    val changeColor = if (tick.change >= 0) PriceUpDark else PriceDownDark
    val changePrefix = if (tick.change >= 0) "+" else ""

    var prevPrice by remember { mutableStateOf(tick.price) }
    var flashColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(tick.price) {
        if (tick.price > prevPrice) {
            flashColor = PriceUpDark.copy(alpha = 0.15f)
            delay(120)
            flashColor = Color.Transparent
        } else if (tick.price < prevPrice) {
            flashColor = PriceDownDark.copy(alpha = 0.15f)
            delay(120)
            flashColor = Color.Transparent
        }
        prevPrice = tick.price
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(flashColor)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssetOverlapIcon(symbol = tick.symbol, modifier = Modifier.padding(end = 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = tick.symbol.replace("/", ""),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = symbolIdSize
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                val isLive = connectionStatus == "LIVE"
                val dotColor = if (isLive) ConnectionLive else ConnectionOffline
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(dotColor)
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = info.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = symbolNameSize
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            val formattedPrice = tick.price.formatPriceDynamic(displayDecs)
            PriceTextWithSuperscript(
                priceStr = formattedPrice,
                fontSize = priceSize,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            
            val formattedChange = tick.change.formatPriceDynamic(displayDecs)
            val formattedPercent = String.format("%.2f%%", kotlin.math.abs(tick.changePercent))
            
            Text(
                text = "$changePrefix$formattedChange  $changePrefix$formattedPercent",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (priceSize.value * 0.65f).sp
                ),
                color = changeColor
            )
        }
    }
}
