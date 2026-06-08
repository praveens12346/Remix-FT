package com.example.service

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SymbolInfo
import com.example.data.repository.PriceMonitorManager
import com.example.ui.theme.FinTraceTheme

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set result to CANCELED so if the user backs out, Android deletes the widget
        setResult(RESULT_CANCELED)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Determine if configuring single or five asset widget with a fallback to 5-asset tracker
        val widgetManager = AppWidgetManager.getInstance(this)
        val info = widgetManager.getAppWidgetInfo(appWidgetId)
        val providerName = info?.provider?.className ?: ""
        val isFiveWidget = info == null || providerName.contains("Five")

        setContent {
            var selectedSymbols by remember { mutableStateOf(setOf<String>()) }
            val limit = if (isFiveWidget) 5 else 1

            FinTraceTheme(mode = "AMOLED") {
                Scaffold { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(innerPadding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Configure Home Widget",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (isFiveWidget) "Select up to 5 assets to track in one widget:" else "Select 1 target asset to track:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(SymbolInfo.ALL) { info ->
                                    val isSelected = selectedSymbols.contains(info.symbol)
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                        ),
                                        onClick = {
                                            if (isSelected) {
                                                selectedSymbols = selectedSymbols - info.symbol
                                            } else {
                                                if (selectedSymbols.size < limit) {
                                                    selectedSymbols = selectedSymbols + info.symbol
                                                } else if (limit == 1) {
                                                    selectedSymbols = setOf(info.symbol)
                                                }
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    info.symbol,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    info.name,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (selectedSymbols.isNotEmpty()) {
                                    saveWidgetConfig(selectedSymbols.toList())
                                    
                                    // Trigger immediate update of both widget systems to avoid early-binding sync lags
                                    val monitor = PriceMonitorManager.getInstance(applicationContext)
                                    val currentPrices = monitor.priceState.value
                                    PriceWidgetFiveProvider.updateWidgets(applicationContext, currentPrices)
                                    PriceWidgetSingleProvider.updateWidgets(applicationContext, currentPrices)

                                    val resultValue = Intent().apply {
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    }
                                    setResult(Activity.RESULT_OK, resultValue)
                                    finish()
                                }
                            },
                            enabled = selectedSymbols.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(
                                "Save Widget Configuration",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveWidgetConfig(symbols: List<String>) {
        val prefs = getSharedPreferences("fintrace_widgets", Context.MODE_PRIVATE)
        val value = symbols.joinToString(",")
        val singleVal = symbols.firstOrNull() ?: "XAU/USD"
        prefs.edit().apply {
            // Over-write both single and five-tracker slots to avoid early-bind launcher failures
            putString("widget_five_sym_$appWidgetId", value)
            putString("widget_single_sym_$appWidgetId", singleVal)
            apply()
        }
    }
}
