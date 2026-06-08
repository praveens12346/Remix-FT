package com.example.data.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.data.model.Alert
import com.example.data.model.AppSetting
import com.example.data.model.SymbolState
import com.example.data.model.TriggerHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun getAllAlertsFlow(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts")
    suspend fun getAllAlerts(): List<Alert>

    @Query("SELECT * FROM alerts WHERE symbol = :symbol AND isActive = 1")
    suspend fun getActiveAlertsForSymbol(symbol: String): List<Alert>

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getAlertById(id: Int): Alert?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: Alert): Long

    @Update
    suspend fun updateAlert(alert: Alert)

    @Delete
    suspend fun deleteAlert(alert: Alert)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteAlertById(id: Int)

    @Query("UPDATE alerts SET isActive = :isActive WHERE id = :id")
    suspend fun updateAlertActiveStatus(id: Int, isActive: Boolean)
    
    @Query("UPDATE alerts SET cooldownUntil = :cooldownUntil WHERE id = :id")
    suspend fun updateAlertCooldown(id: Int, cooldownUntil: Long?)
    
    @Query("DELETE FROM alerts")
    suspend fun deleteAllAlerts()
}

@Dao
interface TriggerHistoryDao {
    @Query("SELECT * FROM trigger_history ORDER BY triggeredAt DESC")
    fun getAllHistoryFlow(): Flow<List<TriggerHistory>>

    @Query("SELECT * FROM trigger_history WHERE alertId = :alertId ORDER BY triggeredAt DESC")
    fun getHistoryForAlert(alertId: Int): Flow<List<TriggerHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TriggerHistory): Long

    @Query("DELETE FROM trigger_history WHERE alertId = :alertId")
    suspend fun deleteHistoryForAlert(alertId: Int)

    @Query("DELETE FROM trigger_history")
    suspend fun clearAllHistory()
}

@Dao
interface SymbolStateDao {
    @Query("SELECT * FROM symbol_states ORDER BY orderIndex ASC")
    fun getAllSymbolStatesFlow(): Flow<List<SymbolState>>

    @Query("SELECT * FROM symbol_states ORDER BY orderIndex ASC")
    suspend fun getAllSymbolStates(): List<SymbolState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymbolStates(states: List<SymbolState>)

    @Update
    suspend fun updateSymbolState(state: SymbolState)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): AppSetting?

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    fun getSettingFlow(key: String): Flow<AppSetting?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    @Query("DELETE FROM app_settings")
    suspend fun clearSettings()
}

@Dao
interface AppLogDao {
    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogsFlow(): Flow<List<com.example.data.model.AppLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: com.example.data.model.AppLog): Long

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsForExport(): List<com.example.data.model.AppLog>

    @Query("SELECT COUNT(*) FROM app_logs")
    suspend fun getLogCount(): Int

    @Query("DELETE FROM app_logs WHERE id NOT IN (SELECT id FROM app_logs ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun deleteOldestLogsExcept(keepCount: Int)

    @Query("DELETE FROM app_logs WHERE id NOT IN (SELECT id FROM app_logs ORDER BY timestamp DESC LIMIT 100)")
    suspend fun pruneOldLogs()

    @Query("DELETE FROM app_logs")
    suspend fun clearAllLogs()
}

@Database(
    entities = [Alert::class, TriggerHistory::class, SymbolState::class, AppSetting::class, com.example.data.model.AppLog::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun triggerHistoryDao(): TriggerHistoryDao
    abstract fun symbolStateDao(): SymbolStateDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun appLogDao(): AppLogDao
}
