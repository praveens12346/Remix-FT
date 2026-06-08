package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class Alert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val condition: String, // "CROSSING", "CROSSING_UP", "CROSSING_DOWN"
    val targetPrice: Double,
    val title: String,
    val message: String,
    val isActive: Boolean = true,
    val isOneTime: Boolean = true,
    val priority: String = "HIGH", // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val cooldownUntil: Long? = null,
    val cooldownDurationMs: Long = 300000L, // 5 min default
    val colorTagIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val expiry: Long? = null
)

@Entity(tableName = "trigger_history")
data class TriggerHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val alertId: Int,
    val symbol: String,
    val priceAtTrigger: Double,
    val triggeredAt: Long = System.currentTimeMillis(),
    val method: String // "NOTIFICATION", "ALARM", "VIRTUAL_CALL"
)

@Entity(tableName = "symbol_states")
data class SymbolState(
    @PrimaryKey val symbol: String,
    val isActive: Boolean,
    val orderIndex: Int
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "app_logs")
data class AppLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "TICK", "ALERT_TRIGGER", "INFO", "ERROR", "SYSTEM"
    val symbol: String?,
    val message: String
)

data class PriceTick(
    val symbol: String,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val change: Double = 0.0,
    val changePercent: Double = 0.0,
    val bid: Double = 0.0,
    val ask: Double = 0.0,
    val history: List<Double> = emptyList(), // last 20 ticks for sparkline
    val openPrice: Double? = null
)

data class SymbolInfo(
    val symbol: String,
    val name: String,
    val category: String, // "Metals", "Majors", "Crosses"
    val decimals: Int,
    val defaultPrice: Double
) {
    companion object {
        val ALL = listOf(
            SymbolInfo("XAU/USD", "Gold Spot", "Metals", 2, 2318.45),
            SymbolInfo("XAG/USD", "Silver Spot", "Metals", 3, 27.82),
            SymbolInfo("EUR/USD", "Euro / US Dollar", "Majors", 5, 1.0823),
            SymbolInfo("GBP/USD", "British Pound / US Dollar", "Majors", 5, 1.2741),
            SymbolInfo("USD/JPY", "US Dollar / Japanese Yen", "Majors", 3, 156.34),
            SymbolInfo("USD/CHF", "US Dollar / Swiss Franc", "Majors", 5, 0.8973),
            SymbolInfo("AUD/USD", "Australian Dollar / US Dollar", "Majors", 5, 0.6521),
            SymbolInfo("USD/CAD", "US Dollar / Canadian Dollar", "Majors", 5, 1.3654),
            SymbolInfo("NZD/USD", "New Zealand Dollar / US Dollar", "Majors", 5, 0.6124),
            SymbolInfo("EUR/GBP", "Euro / British Pound", "Crosses", 5, 0.8495),
            SymbolInfo("EUR/JPY", "Euro / Japanese Yen", "Crosses", 3, 169.21),
            SymbolInfo("GBP/JPY", "British Pound / Japanese Yen", "Crosses", 3, 199.18),
            SymbolInfo("EUR/AUD", "Euro / Australian Dollar", "Crosses", 5, 1.6592),
            SymbolInfo("GBP/AUD", "British Pound / Australian Dollar", "Crosses", 5, 1.9542)
        )

        fun find(symbol: String): SymbolInfo {
            return ALL.find { it.symbol.equals(symbol, ignoreCase = true) }
                ?: SymbolInfo(symbol, symbol, "Others", 4, 1.0)
        }
    }
}

object PricePrecisionConfig {
    @Volatile
    var maxPrecision: Int? = null

    private val overrides = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun setOverride(symbol: String, value: Int?) {
        if (value == null) {
            overrides.remove(symbol.uppercase())
        } else {
            overrides[symbol.uppercase()] = value
        }
    }

    fun getOverride(symbol: String): Int? {
        return overrides[symbol.uppercase()]
    }

    fun clearAll() {
        overrides.clear()
    }
}

fun SymbolInfo.getDisplayDecimals(): Int {
    return PricePrecisionConfig.getOverride(symbol) ?: PricePrecisionConfig.maxPrecision ?: decimals
}

fun Double.formatPriceDynamic(decimals: Int): String {
    val finalDecimals = decimals.coerceIn(1, 9)
    val pattern = buildString {
        append("#,##0.")
        repeat(finalDecimals) { append('0') }
    }
    return java.text.DecimalFormat(pattern).format(this)
}
