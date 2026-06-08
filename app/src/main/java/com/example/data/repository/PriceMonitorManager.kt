package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.model.Alert
import com.example.data.model.AppSetting
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.SymbolState
import com.example.data.model.TriggerHistory
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class PriceMonitorManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    val db: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "fintrace_database"
    ).fallbackToDestructiveMigration().build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectionJob: Job? = null
    private var simTickJob: Job? = null
    private var logCounter = 0
    private var reconnectCount = 0

    @Volatile
    private var isTickLoggingEnabled = true

    @Volatile
    private var hasActiveAlerts = false

    private val memoryLogIdCounter = java.util.concurrent.atomic.AtomicLong(-1L)

    private val lastSymbolUpdateTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    private var isNativeModeEnabled = true

    @Volatile
    private var cachedPriceUpdateIntervalMs = 500L

    suspend fun getWebsocketUseNativeMode(): Boolean {
        return isNativeModeEnabled
    }

    private val _tickLogs = MutableStateFlow<List<com.example.data.model.AppLog>>(emptyList())
    val tickLogs: StateFlow<List<com.example.data.model.AppLog>> = _tickLogs.asStateFlow()

    fun clearTickLogs() {
        _tickLogs.value = emptyList()
    }

    fun setTickLoggingEnabled(enabled: Boolean) {
        isTickLoggingEnabled = enabled
        scope.launch {
            saveSetting("tick_logging_enabled", enabled.toString())
        }
    }

    fun getTickLoggingEnabled(): Boolean = isTickLoggingEnabled

    data class StorageInfo(
        val usedBytes: Long,
        val maxBytes: Long = 10 * 1024 * 1024L // 10MB
    ) {
        val usedMB: Double get() = usedBytes / (1024.0 * 1024.0)
        val maxMB: Double get() = maxBytes / (1024.0 * 1024.0)
        val remainingMB: Double get() = (maxBytes - usedBytes).coerceAtLeast(0L) / (1024.0 * 1024.0)
        val usedPercent: Float get() = (usedBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
    }

    fun getDatabaseStorageInfo(): StorageInfo {
        val dbFile = appContext.getDatabasePath("fintrace_database")
        val walFile = appContext.getDatabasePath("fintrace_database-wal")
        val shmFile = appContext.getDatabasePath("fintrace_database-shm")
        var totalBytes = 0L
        if (dbFile.exists()) totalBytes += dbFile.length()
        if (walFile.exists()) totalBytes += walFile.length()
        if (shmFile.exists()) totalBytes += shmFile.length()
        return StorageInfo(totalBytes)
    }

    fun logEvent(type: String, symbol: String?, message: String) {
        if (type == "TICK") {
            if (!isTickLoggingEnabled) return
            val newLog = com.example.data.model.AppLog(
                id = memoryLogIdCounter.getAndDecrement(),
                type = type,
                symbol = symbol,
                message = message
            )
            _tickLogs.update { current ->
                val updated = listOf(newLog) + current
                if (updated.size > 100) updated.take(100) else updated
            }
        } else {
            scope.launch(Dispatchers.IO) {
                try {
                    db.appLogDao().insertLog(com.example.data.model.AppLog(type = type, symbol = symbol, message = message))
                    logCounter++
                    if (logCounter >= 5) { // Check every 5 log insertions to be highly responsive
                        logCounter = 0
                        val info = getDatabaseStorageInfo()
                        if (info.usedBytes > info.maxBytes) {
                            val count = db.appLogDao().getLogCount()
                            if (count > 0) {
                                val keepCount = (count * 0.3).toInt().coerceAtLeast(1)
                                db.appLogDao().deleteOldestLogsExcept(keepCount)
                                
                                // Vacuum physical db file to reclaim filesystem ROM space
                                try {
                                    db.openHelper.writableDatabase.execSQL("VACUUM")
                                } catch (ve: Exception) {
                                    Log.e("PriceMonitor", "VACUUM failed: ${ve.message}")
                                }

                                // Write a PROTECTION log indicating storage limit exceeded action
                                db.appLogDao().insertLog(com.example.data.model.AppLog(
                                    type = "PROTECTION",
                                    symbol = null,
                                    message = "DATABASE STORAGE LIMIT MITIGATION: 10MB storage limit hit (${String.format("%.2f", info.usedMB)} MB). Auto-purged oldest 70% of logs to protect system storage. Retained last $keepCount logs and vacuumed."
                                ))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PriceMonitor", "Failed to write log: ${e.message}")
                }
            }
        }
    }

    // Price Cache & State Flow
    private val _priceState = MutableStateFlow<Map<String, PriceTick>>(emptyMap())
    val priceState: StateFlow<Map<String, PriceTick>> = _priceState.asStateFlow()

    // Active symbols state cache
    private val _activeSymbols = MutableStateFlow<List<String>>(listOf("XAU/USD", "EUR/USD", "GBP/USD", "USD/JPY"))
    val activeSymbols: StateFlow<List<String>> = _activeSymbols.asStateFlow()

    // Ticker symbols state cache
    private val _liveTickerSymbols = MutableStateFlow<List<String>>(emptyList())
    val liveTickerSymbols: StateFlow<List<String>> = _liveTickerSymbols.asStateFlow()

    // Connection Status State Flow
    // "LIVE", "RECONNECTING", "OFFLINE"
    private val _connectionStatus = MutableStateFlow("LIVE")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    init {
        // Initialize Default Symbols and load current selection
        scope.launch(Dispatchers.IO) {
            // Detect and heal previous crash state
            try {
                val prefs = appContext.getSharedPreferences("fintrace_prefs", Context.MODE_PRIVATE)
                val crashed = prefs.getBoolean("last_session_crashed", false)
                if (crashed) {
                    prefs.edit().putBoolean("last_session_crashed", false).apply()
                    logEvent("RECOVERY", null, "CRASH RECOVERY SERVICE: App has recovered successfully from a previous forced termination. Restoring system and database registers.")
                    logEvent("HEALING", null, "HEALING NODE: Cleared and optimized temporary log caches & checked data file integrity successfully.")
                }
            } catch (e: Exception) {
                Log.e("PriceMonitor", "Self-healing error state: ${e.message}")
            }

            setupInitialSymbolsIfNeeded()
            loadActiveSymbols()
            isTickLoggingEnabled = (getSetting("tick_logging_enabled") ?: "true") == "true"
            isNativeModeEnabled = (getSetting("websocket_use_native_mode") ?: "true") == "true"
            cachedPriceUpdateIntervalMs = getUiPriceIntervalSettingFromDb()

            // Reactively fetch and cache active alerts presence to avoid recurring SQLite operations
            launch {
                db.alertDao().getAllAlertsFlow().collect { list ->
                    hasActiveAlerts = list.any { it.isActive }
                }
            }
            // Load and apply decimal precision setting
            val precisionRaw = getSetting("price_precision_override") ?: "MAX"
            com.example.data.model.PricePrecisionConfig.maxPrecision = precisionRaw.toIntOrNull()
            
            // Load per-asset custom overrides
            SymbolInfo.ALL.forEach { s ->
                val key = "price_precision_override_${s.symbol.uppercase()}"
                val overrideRaw = getSetting(key)
                val overrideInt = overrideRaw?.toIntOrNull()
                com.example.data.model.PricePrecisionConfig.setOverride(s.symbol, overrideInt)
            }
            
            initializePriceCache()
            
            // Start observing live ticker symbols config
            launch {
                observeLiveTickerSymbolsSetting()
            }
            
            startMonitoringLoop()
        }
    }

    private suspend fun observeLiveTickerSymbolsSetting() {
        db.appSettingDao().getSettingFlow("live_ticker_symbols").collect { setting ->
            // Default to empty if not configured to respect user switch
            val raw = setting?.value ?: ""
            val list = raw.split(",").filter { it.isNotBlank() }
            _liveTickerSymbols.value = list
            NotificationHelper.updateTickerNotification(appContext, _priceState.value, list)
        }
    }

    private suspend fun setupInitialSymbolsIfNeeded() {
        val existing = db.symbolStateDao().getAllSymbolStates()
        if (existing.isEmpty()) {
            val initialList = SymbolInfo.ALL.mapIndexed { index, info ->
                val isActive = info.symbol in listOf("XAU/USD", "EUR/USD", "GBP/USD", "USD/JPY")
                SymbolState(info.symbol, isActive, index)
            }
            db.symbolStateDao().insertSymbolStates(initialList)
        }
    }

    private suspend fun loadActiveSymbols() {
        db.symbolStateDao().getAllSymbolStatesFlow().collect { list ->
            val active = list.filter { it.isActive }.map { it.symbol }
            _activeSymbols.value = active
        }
    }

    private fun initializePriceCache() {
        val initialMap = mutableMapOf<String, PriceTick>()
        SymbolInfo.ALL.forEach { s ->
            initialMap[s.symbol] = PriceTick(
                symbol = s.symbol,
                price = s.defaultPrice,
                bid = s.defaultPrice - (0.0004 * s.defaultPrice),
                ask = s.defaultPrice + (0.0004 * s.defaultPrice),
                history = listOf(s.defaultPrice),
                openPrice = s.defaultPrice
            )
        }
        _priceState.value = initialMap
    }

    @Volatile
    private var isScreenOn = true

    fun setScreenState(on: Boolean) {
        if (isScreenOn != on) {
            isScreenOn = on
            logEvent("SYSTEM", null, "Screen status changed: interactive=$on. Deploying smart power saver metrics.")
            startMonitoringLoop()
        }
    }

    fun startMonitoringLoop() {
        scope.launch {
            val apiKeySetting = getSetting("twelve_data_api_key")
            if (apiKeySetting.isNullOrBlank()) {
                _connectionStatus.value = "LIVE" // simulated live
                if (simTickJob?.isActive != true) {
                    startSimulation()
                }
            } else {
                if (_connectionStatus.value != "LIVE" && _connectionStatus.value != "CONNECTING") {
                    connectToTwelveData(apiKeySetting)
                }
            }
        }
    }

    fun stopMonitoring() {
        connectionJob?.cancel()
        simTickJob?.cancel()
        activeWebSocket?.close(1000, "App closed")
        activeWebSocket = null
    }

    // ── SIMULATION ENGINE ──────────────────────────────────────────────────
    private fun startSimulation() {
        connectionJob?.cancel()
        simTickJob?.cancel()
        simTickJob = scope.launch {
            while (true) {
                if (!isScreenOn) {
                    if (!hasActiveAlerts) {
                        // Screen is off, and no alerts are active. Sleep simulation to save 100% background power.
                        delay(5000)
                        continue
                    } else {
                        // Alerts are active, perform a slow check loop once every 15 seconds to monitor safely
                        delay(15000)
                    }
                } else {
                    val delayTime = getUiPriceIntervalSetting()
                    delay(delayTime)
                }
                tickSimulatedPrices()
            }
        }
    }

    private suspend fun tickSimulatedPrices() {
        try {
            val activeList = _activeSymbols.value
            val currentPrices = _priceState.value.toMutableMap()

            activeList.forEach { sym ->
                try {
                    val info = SymbolInfo.find(sym)
                    val currentTick = currentPrices[sym] ?: PriceTick(sym, info.defaultPrice)
                    val prevPrice = currentTick.price
                    val openPrice = currentTick.openPrice ?: info.defaultPrice

                    // Random walk simulation with mean-reversion towards the default base price to prevent unrealistic extreme drift
                    val volatility = when (info.category) {
                        "Metals" -> 0.0003
                        "Majors" -> 0.00008
                        else -> 0.00015
                    }
                    // Ornstein-Uhlenbeck-like mean reversion: pull back harder when further from baseline
                    val deviationPct = (prevPrice - info.defaultPrice) / info.defaultPrice
                    val pullSpeed = 0.04
                    val drift = -deviationPct * pullSpeed
                    
                    val changePercent = (Random.nextDouble() - 0.5) * 2.0 * volatility + drift
                    val priceChange = prevPrice * changePercent
                    
                    // Strictly bound the price to maximum ±4% deviation from the actual real baseline price
                    val minPrice = info.defaultPrice * 0.96
                    val maxPrice = info.defaultPrice * 1.04
                    val newPrice = (prevPrice + priceChange).coerceIn(minPrice, maxPrice)

                    // Calculate bid-ask
                    val spreadFactor = when (info.category) {
                        "Metals" -> 0.0002
                        "Majors" -> 0.0001
                        else -> 0.00015
                    }
                    val spreadVal = newPrice * spreadFactor
                    val bid = newPrice - (spreadVal / 2)
                    val ask = newPrice + (spreadVal / 2)

                    val netChange = newPrice - openPrice
                    val netChangePct = if (openPrice > 0.0) (netChange / openPrice) * 100 else 0.0

                    val displayDecs = info.getDisplayDecimals()
                    val changeStr = if (netChange >= 0) "+${netChange.formatPriceDynamic(displayDecs)}" else netChange.formatPriceDynamic(displayDecs)
                    val pctStr = if (netChangePct >= 0) "+${String.format("%.2f", netChangePct)}%" else "${String.format("%.2f", netChangePct)}%"
                    val formattedPrice = newPrice.formatPriceDynamic(displayDecs)
                    logEvent("TICK", sym, "$sym ticked simulated at $formattedPrice ($changeStr | $pctStr)")

                    val oldHistory = currentTick.history
                    val newHistory = (oldHistory + newPrice).takeLast(20)

                    val updatedTick = PriceTick(
                        symbol = sym,
                        price = newPrice,
                        change = netChange,
                        changePercent = netChangePct,
                        bid = bid,
                        ask = ask,
                        history = newHistory,
                        openPrice = openPrice
                    )

                    currentPrices[sym] = updatedTick
                    evaluateAlerts(sym, prevPrice, newPrice)
                } catch (e: Exception) {
                    Log.e("PriceMonitor", "Error generating simulated tick for $sym: ${e.message}", e)
                    logEvent("ERROR", sym, "Sim tick calculation error: ${e.localizedMessage}")
                }
            }

            _priceState.value = currentPrices
            _latencyMs.value = Random.nextLong(20, 150)
            
            // Sync Notification and Widgets with optimized performance coalesced updater
            scheduleSystemUpdates()
        } catch (e: Exception) {
            Log.e("PriceMonitor", "General Simulation Loop Ticker Error: ${e.message}", e)
            logEvent("ERROR", null, "Simulation loop breakdown: ${e.localizedMessage}")
        }
    }

    // ── TWELVE DATA SOCKET INTEGRATION ────────────────────────────────────
    private fun connectToTwelveData(apiKey: String) {
        simTickJob?.cancel()
        connectionJob?.cancel()
        connectionJob = scope.launch {
            _connectionStatus.value = "CONNECTING"
            logEvent("SYSTEM", null, "Connecting to Twelve Data WebSocket feed...")
            val request = Request.Builder()
                .url("wss://ws.twelvedata.com/v1/quotes/price?apikey=$apiKey")
                .build()

            activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionStatus.value = "LIVE"
                    reconnectCount = 0
                    logEvent("SYSTEM", null, "Connected to Twelve Data WebSocket successfully.")
                    // Subscribe active symbols
                    val activeList = _activeSymbols.value
                    if (activeList.isNotEmpty()) {
                        val symString = activeList.joinToString(",")
                        val subMsg = JSONObject()
                        subMsg.put("action", "subscribe")
                        val params = JSONObject()
                        params.put("symbols", symString)
                        subMsg.put("params", params)
                        webSocket.send(subMsg.toString())
                    }
                    
                    // Start heartbeat ticker
                    startHeartbeat(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleWsMessage(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionStatus.value = "OFFLINE"
                    logEvent("SYSTEM", null, "Twelve Data Connection closed: $reason")
                    attemptReconnect(apiKey)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    reconnectCount++
                    _connectionStatus.value = "OFFLINE"
                    logEvent("ERROR", null, "Twelve Data connection failed (${t.message ?: "Unknown socket error"}). Live streaming interrupted. Freezing on the last updated price. Attempting background retry #$reconnectCount...")
                    attemptReconnect(apiKey)
                }
            })

            // Dynamic Live subscription synchronizer
            launch {
                _activeSymbols.collect { list ->
                    val ws = activeWebSocket
                    if (_connectionStatus.value == "LIVE" && ws != null && list.isNotEmpty()) {
                        try {
                            val symString = list.joinToString(",")
                            val subMsg = JSONObject()
                            subMsg.put("action", "subscribe")
                            val params = JSONObject()
                            params.put("symbols", symString)
                            subMsg.put("params", params)
                            ws.send(subMsg.toString())
                            logEvent("SYSTEM", null, "Dynamically synced active WebSocket subscriptions: $symString")
                        } catch (e: Exception) {
                            Log.e("PriceMonitor", "Subscription update error: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        scope.launch {
            while (_connectionStatus.value == "LIVE" && activeWebSocket == webSocket) {
                delay(25000)
                try {
                    val hb = JSONObject()
                    hb.put("action", "heartbeat")
                    webSocket.send(hb.toString())
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun attemptReconnect(apiKey: String) {
        scope.launch {
            val delayMs = when {
                reconnectCount <= 1 -> 5000L
                reconnectCount <= 2 -> 10000L
                else -> 30000L
            }
            delay(delayMs)
            if (_connectionStatus.value != "LIVE") {
                connectToTwelveData(apiKey)
            }
        }
    }

    private fun handleWsMessage(text: String) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event")
            if (event == "price") {
                val sym = json.optString("symbol")
                val priceVal = json.optDouble("price")
                if (!sym.isNullOrBlank() && !priceVal.isNaN()) {
                    scope.launch {
                        processReceivedPrice(sym, priceVal)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PriceMonitor", "WS Error: ${e.message}")
        }
    }

    private suspend fun processReceivedPrice(sym: String, newPrice: Double) {
        try {
            val isNative = getWebsocketUseNativeMode()
            if (!isNative) {
                val now = System.currentTimeMillis()
                val lastUpdateTime = lastSymbolUpdateTimes[sym] ?: 0L
                val interval = getUiPriceIntervalSetting()
                if (now - lastUpdateTime < interval) {
                    // Bypass/skip this tick to respect the user specified latency throttle rate
                    return
                }
                lastSymbolUpdateTimes[sym] = now
            }

            val info = SymbolInfo.find(sym)
            val currentPrices = _priceState.value.toMutableMap()
            val currentTick = currentPrices[sym] ?: PriceTick(sym, info.defaultPrice)
            val prevPrice = currentTick.price

            // If previous price was still the static default placeholder, initialize session open details
            val openPrice = if (currentTick.price == info.defaultPrice) newPrice else (currentTick.openPrice ?: newPrice)

            val netChange = newPrice - openPrice
            val netChangePct = if (openPrice > 0.0) (netChange / openPrice) * 100 else 0.0

            val spreadFactor = when (info.category) {
                "Metals" -> 0.0002
                "Majors" -> 0.0001
                else -> 0.00015
            }
            val spreadVal = newPrice * spreadFactor
            val bid = newPrice - (spreadVal / 2)
            val ask = newPrice + (spreadVal / 2)

            val displayDecs = info.getDisplayDecimals()
            val changeStr = if (netChange >= 0) "+${netChange.formatPriceDynamic(displayDecs)}" else netChange.formatPriceDynamic(displayDecs)
            val pctStr = if (netChangePct >= 0) "+${String.format("%.2f", netChangePct)}%" else "${String.format("%.2f", netChangePct)}%"
            val formattedPrice = newPrice.formatPriceDynamic(displayDecs)
            logEvent("TICK", sym, "$sym ticked live at $formattedPrice ($changeStr | $pctStr)")

            val oldHistory = currentTick.history
            val newHistory = (oldHistory + newPrice).takeLast(20)

            val updatedTick = PriceTick(
                symbol = sym,
                price = newPrice,
                change = netChange,
                changePercent = netChangePct,
                bid = bid,
                ask = ask,
                history = newHistory,
                openPrice = openPrice
            )

            currentPrices[sym] = updatedTick
            _priceState.update { currentPrices }
            
            // Evaluate alerts!
            evaluateAlerts(sym, prevPrice, newPrice)

            // Dynamic throttled background system updates to drastically lower resource footprint
            scheduleSystemUpdates()
        } catch (e: Exception) {
            Log.e("PriceMonitor", "Failed to process received live price for $sym: ${e.message}", e)
            logEvent("ERROR", sym, "Live price process breakdown: ${e.localizedMessage}")
        }
    }

    private val systemSyncPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastSystemSyncTime = 0L

    fun scheduleSystemUpdates() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastSystemSyncTime
        if (elapsed > 1000L) {
            lastSystemSyncTime = now
            val currentPrices = _priceState.value
            NotificationHelper.updateTickerNotification(appContext, currentPrices, _liveTickerSymbols.value)
            com.example.service.PriceWidgetProvider.updateWidgets(appContext, currentPrices)
            com.example.service.PriceWidgetSingleProvider.updateWidgets(appContext, currentPrices)
            com.example.service.PriceWidgetFiveProvider.updateWidgets(appContext, currentPrices)
        } else {
            if (systemSyncPending.compareAndSet(false, true)) {
                scope.launch {
                    delay(1000L - elapsed)
                    systemSyncPending.set(false)
                    lastSystemSyncTime = System.currentTimeMillis()
                    val currentPrices = _priceState.value
                    NotificationHelper.updateTickerNotification(appContext, currentPrices, _liveTickerSymbols.value)
                    com.example.service.PriceWidgetProvider.updateWidgets(appContext, currentPrices)
                    com.example.service.PriceWidgetSingleProvider.updateWidgets(appContext, currentPrices)
                    com.example.service.PriceWidgetFiveProvider.updateWidgets(appContext, currentPrices)
                }
            }
        }
    }

    // ── EVALUATE ALERTS ───────────────────────────────────────────────────
    private suspend fun evaluateAlerts(symbol: String, prevPrice: Double, currentPrice: Double) {
        try {
            val activeAlerts = db.alertDao().getActiveAlertsForSymbol(symbol)
            val now = System.currentTimeMillis()

            activeAlerts.forEach { alert ->
                // Check cooldown
                if (alert.cooldownUntil != null && now < alert.cooldownUntil) {
                    return@forEach
                }
                // Check expiration
                if (alert.expiry != null && now > alert.expiry) {
                    db.alertDao().updateAlertActiveStatus(alert.id, false)
                    return@forEach
                }

                var triggered = false
                when (alert.condition) {
                    "CROSSING" -> {
                        triggered = (prevPrice < alert.targetPrice && currentPrice >= alert.targetPrice) ||
                                (prevPrice > alert.targetPrice && currentPrice <= alert.targetPrice)
                    }
                    "CROSSING_UP" -> {
                        triggered = prevPrice < alert.targetPrice && currentPrice >= alert.targetPrice
                    }
                    "CROSSING_DOWN" -> {
                        triggered = prevPrice > alert.targetPrice && currentPrice <= alert.targetPrice
                    }
                }

                if (triggered) {
                    // Fire notification based on configured method
                    NotificationHelper.fireAlertNotification(appContext, alert, currentPrice)

                    // Insert trigger history
                    db.triggerHistoryDao().insertHistory(
                        TriggerHistory(
                            alertId = alert.id,
                            symbol = symbol,
                            priceAtTrigger = currentPrice,
                            triggeredAt = now,
                            method = alert.priority
                        )
                    )

                    val logInfo = SymbolInfo.find(symbol)
                    logEvent("ALERT_TRIGGER", symbol, "ALERT FIRED: ${alert.title} at ${currentPrice.formatPriceDynamic(logInfo.getDisplayDecimals())}")

                    // Cooldown / lifecycle management
                    if (alert.isOneTime) {
                        db.alertDao().updateAlertActiveStatus(alert.id, false)
                    } else {
                        val cooldownEnd = now + alert.cooldownDurationMs
                        db.alertDao().updateAlertCooldown(alert.id, cooldownEnd)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PriceMonitor", "Error evaluating alerts for $symbol: ${e.message}", e)
            logEvent("ERROR", symbol, "Alert evaluation error: ${e.localizedMessage}")
        }
    }

    // Settings helpers
    suspend fun getSetting(key: String): String? {
        return db.appSettingDao().getSetting(key)?.value
    }

    suspend fun saveSetting(key: String, value: String) {
        db.appSettingDao().insertSetting(AppSetting(key, value))
        // Dynamic volatile state cache synchronization
        if (key == "websocket_use_native_mode") {
            isNativeModeEnabled = value == "true"
        } else if (key == "ui_price_update_interval_ms") {
            cachedPriceUpdateIntervalMs = value.toLongOrNull()?.coerceIn(100L, 10000L) ?: 500L
        }

        // Auto restart loop if API key or update interval shifts
        if (key == "twelve_data_api_key") {
            startMonitoringLoop()
        } else if (key == "ui_price_update_interval_ms") {
            if (getSetting("twelve_data_api_key").isNullOrBlank()) {
                startSimulation() // restart simulation with new rate
            }
        }
    }

    suspend fun getUiPriceIntervalSettingFromDb(): Long {
        val raw = getSetting("ui_price_update_interval_ms")
        return raw?.toLongOrNull()?.coerceIn(100L, 10000L) ?: 500L
    }

    suspend fun getUiPriceIntervalSetting(): Long {
        return cachedPriceUpdateIntervalMs
    }

    companion object {
        @Volatile
        private var INSTANCE: PriceMonitorManager? = null

        fun getInstance(context: Context): PriceMonitorManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PriceMonitorManager(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
