package com.microtempo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * ViewModel handles SLOW state changes only:
 * - NTP sync status
 * - Weather & lunar data (fetched once on start)
 * - Server selection
 * - Debug logs
 *
 * Time ticking happens in UI layer via LaunchedEffect (zero-allocation path).
 */
data class WeatherData(
    val tempC: Int,
    val condition: String,
    val moonPhase: String,
    val moonIllumination: Int
)

data class ClockState(
    val synced: Boolean = false,
    val syncing: Boolean = false,
    val selectedServer: NtpServer = PreciseNtpClock.NTP_SERVERS[0],
    val lastSync: SyncInfo? = null,
    val weather: WeatherData? = null,
    val logs: List<String> = emptyList()
)
// NOTE: No "time" field - that's handled in UI for performance

class ClockViewModel : ViewModel() {
    private val _state = MutableStateFlow(ClockState())
    val state: StateFlow<ClockState> = _state.asStateFlow()

    companion object {
        private const val AUTO_SYNC_INTERVAL_MS = 1 * 60 * 1000L // 1 minute
    }

    init {
        sync()
        fetchWeatherOnce()
        startAutoSync()
    }

    /**
     * Fetch weather & lunar data once on app start.
     * Uses wttr.in which auto-detects location via IP.
     */
    private fun fetchWeatherOnce() {
        viewModelScope.launch {
            val weather = fetchWeather()
            weather?.let { w ->
                _state.update { it.copy(weather = w) }
                _state.update { it.copy(logs = (it.logs + "Moon: ${w.moonPhase} (${w.moonIllumination}%)").takeLast(20)) }
            }
        }
    }

    private suspend fun fetchWeather(): WeatherData? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(URL("https://wttr.in/?format=j1").readText())
            val current = json.getJSONArray("current_condition").getJSONObject(0)
            val astro = json.getJSONArray("weather").getJSONObject(0)
                .getJSONArray("astronomy").getJSONObject(0)
            WeatherData(
                tempC = current.getString("temp_C").toInt(),
                condition = current.getJSONArray("weatherDesc").getJSONObject(0).getString("value"),
                moonPhase = astro.getString("moon_phase"),
                moonIllumination = astro.getString("moon_illumination").toInt()
            )
        } catch (e: Exception) { null }
    }

    /**
     * Auto-resync every minute to compensate for quartz drift.
     */
    private fun startAutoSync() {
        viewModelScope.launch {
            while (isActive) {
                delay(AUTO_SYNC_INTERVAL_MS)
                if (!_state.value.syncing) {
                    syncInternal(isAutoSync = true)
                }
            }
        }
    }

    fun sync() {
        viewModelScope.launch {
            syncInternal(isAutoSync = false)
        }
    }

    private suspend fun syncInternal(isAutoSync: Boolean) {
        _state.update { it.copy(syncing = true) }

        val result = PreciseNtpClock.sync(_state.value.selectedServer)

        _state.update { state ->
            if (result != null) {
                val logPrefix = if (isAutoSync) "[auto] " else ""
                state.copy(
                    syncing = false,
                    synced = true,
                    lastSync = result,
                    logs = (state.logs + "$logPrefix${result.toLogString()}").takeLast(20)
                )
            } else {
                state.copy(
                    syncing = false,
                    logs = (state.logs + "Sync failed: ${state.selectedServer.name}").takeLast(20)
                )
            }
        }
    }

    fun selectServer(server: NtpServer) {
        _state.update { it.copy(selectedServer = server) }
    }

    /**
     * Toggle between Cloudflare and Google servers, then re-sync.
     */
    fun toggleServer() {
        val current = _state.value.selectedServer
        val newServer = if (current.host == "time.cloudflare.com") {
            PreciseNtpClock.NTP_SERVERS.find { it.host == "time.google.com" }!!
        } else {
            PreciseNtpClock.NTP_SERVERS.find { it.host == "time.cloudflare.com" }!!
        }
        _state.update { it.copy(selectedServer = newServer) }
        sync()
    }

    private fun SyncInfo.toLogString(): String {
        val sign = if (offsetMs >= 0) "+" else ""
        return String.format(
            java.util.Locale.US,
            "%s: %s%.2fms RTT:%.1fms (%d/%d)",
            server.name, sign, offsetMs, rttMs, samplesUsed, 5
        )
    }
}
