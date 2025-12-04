package com.microtempo

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * Production-grade SNTP client with true microsecond precision.
 *
 * Features:
 * - Reads NTP fractional seconds (bytes 44-47) for ~232ps resolution
 * - Burst sampling: takes best of 5 to filter asymmetric network paths
 * - Resource-safe: uses socket.use{} for automatic cleanup
 * - Thread-safe: atomic offset storage, lock-free reads
 */
object PreciseNtpClock {
    private const val NTP_PORT = 123
    private const val TIMEOUT_MS = 3000
    private const val NTP_PACKET_SIZE = 48
    private const val BURST_SAMPLES = 5
    private const val BURST_DELAY_MS = 50L

    // NTP epoch starts 1900, Unix epoch starts 1970
    private const val NTP_TO_UNIX_SECONDS = 2208988800L

    // Atomic references for thread-safe, lock-free access
    private val clockOffsetNanos = AtomicReference<Long?>(null)
    private val lastSyncInfo = AtomicReference<SyncInfo?>(null)

    // Servers that use leap smear (recommended for consistent time)
    // Cloudflare first = default
    val NTP_SERVERS = listOf(
        NtpServer("time.cloudflare.com", "Cloudflare"),
        NtpServer("time.google.com", "Google"),
    )

    /**
     * Burst sync: sends 5 NTP requests, keeps the one with lowest RTT.
     * Lowest RTT = most symmetric network path = most accurate offset.
     *
     * @return SyncInfo on success (best sample), null if all samples failed
     */
    suspend fun sync(server: NtpServer = NTP_SERVERS[0]): SyncInfo? = withContext(Dispatchers.IO) {
        var bestSync: SyncInfo? = null

        DatagramSocket().use { socket ->
            socket.soTimeout = TIMEOUT_MS
            val address = try {
                InetAddress.getByName(server.host)
            } catch (e: Exception) {
                return@withContext null
            }

            val buffer = ByteArray(NTP_PACKET_SIZE)

            repeat(BURST_SAMPLES) { iteration ->
                try {
                    // Clear and prepare NTP request packet
                    buffer.fill(0)
                    buffer[0] = 0x1B.toByte() // Version 3, Client mode

                    val packet = DatagramPacket(buffer, buffer.size, address, NTP_PORT)

                    // T1: Client transmit timestamp
                    val t1Nanos = SystemClock.elapsedRealtimeNanos()
                    socket.send(packet)

                    // Receive response
                    socket.receive(DatagramPacket(buffer, buffer.size))

                    // T4: Client receive timestamp
                    val t4Nanos = SystemClock.elapsedRealtimeNanos()

                    // Parse T3: Server transmit timestamp (bytes 40-47)
                    val t3Seconds = buffer.readUInt32(40)
                    val t3Fraction = buffer.readUInt32(44)

                    // Convert NTP fraction to nanoseconds: fraction * 1e9 / 2^32
                    val t3FractionNanos = ((t3Fraction and 0xFFFFFFFFL) * 1_000_000_000L) / 4294967296L
                    val t3UnixNanos = ((t3Seconds - NTP_TO_UNIX_SECONDS) * 1_000_000_000L) + t3FractionNanos

                    // Calculate RTT and offset
                    val rttNanos = t4Nanos - t1Nanos

                    // Assume symmetric path: true time at T4 = T3 + RTT/2
                    val trueTimeAtT4 = t3UnixNanos + (rttNanos / 2)
                    val offsetNanos = trueTimeAtT4 - t4Nanos

                    // FILTER: Keep only the sample with LOWEST RTT
                    // Lowest RTT = least queuing delay = most symmetric path
                    if (bestSync == null || rttNanos < bestSync!!.rttNanos) {
                        bestSync = SyncInfo(
                            offsetNanos = offsetNanos,
                            rttNanos = rttNanos,
                            server = server,
                            timestamp = System.currentTimeMillis(),
                            samplesUsed = iteration + 1
                        )
                    }

                    // Small delay between burst packets to avoid congestion
                    if (iteration < BURST_SAMPLES - 1) {
                        delay(BURST_DELAY_MS)
                    }
                } catch (e: Exception) {
                    // Continue to next sample if one fails
                }
            }
        } // Socket automatically closed here

        // Apply the best offset found
        bestSync?.let {
            clockOffsetNanos.set(it.offsetNanos)
            lastSyncInfo.set(it)
        }

        bestSync
    }

    /**
     * Returns current precise time. Fast, allocation-minimal.
     * Only performs math on cached offset - no network, no locks.
     *
     * @throws IllegalStateException if sync() hasn't been called yet
     */
    fun now(): PreciseTime {
        val offset = clockOffsetNanos.get()
            ?: throw IllegalStateException("Clock not synced. Call sync() first.")

        val currentTrueNanos = SystemClock.elapsedRealtimeNanos() + offset

        return PreciseTime(
            millis = currentTrueNanos / 1_000_000,
            micros = ((currentTrueNanos / 1_000) % 1000).toInt(),
            nanos = (currentTrueNanos % 1000).toInt()
        )
    }

    /**
     * Returns current time or null if not synced (no exception).
     */
    fun nowOrNull(): PreciseTime? = try { now() } catch (e: Exception) { null }

    /**
     * Check if clock has been synced at least once.
     */
    fun isSynced(): Boolean = clockOffsetNanos.get() != null

    /**
     * Get info about the last successful sync.
     */
    fun getLastSyncInfo(): SyncInfo? = lastSyncInfo.get()

    // Read 32-bit unsigned integer from buffer (big-endian)
    private fun ByteArray.readUInt32(offset: Int): Long {
        return ((this[offset].toLong() and 0xFF) shl 24) or
               ((this[offset + 1].toLong() and 0xFF) shl 16) or
               ((this[offset + 2].toLong() and 0xFF) shl 8) or
               (this[offset + 3].toLong() and 0xFF)
    }
}

data class PreciseTime(
    val millis: Long,
    val micros: Int,
    val nanos: Int
)

data class SyncInfo(
    val offsetNanos: Long,
    val rttNanos: Long,
    val server: NtpServer,
    val timestamp: Long,
    val samplesUsed: Int = 1
) {
    val offsetMs: Double get() = offsetNanos / 1_000_000.0
    val rttMs: Double get() = rttNanos / 1_000_000.0
}

data class NtpServer(
    val host: String,
    val name: String
)
