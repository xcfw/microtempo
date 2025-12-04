# MicroTempo

Ultra-precise chronograph app for Android with true microsecond precision via custom SNTP implementation.

## Features

- **Microsecond precision**: Custom SNTP client reads NTP fractional seconds (bytes 44-47) for ~232ps source resolution
- **Burst sampling**: Takes 5 samples, keeps lowest-RTT for best accuracy
- **Auto-resync**: Compensates for smartphone's quartz drift every minute
- **Terraluna-inspired dial**: Regulator layout with Arabic-Indic numerals
- **Perpetual calendar**: Date, day, month, year indicator
- **Weather & lunar**: Via wttr.in API
- **Zero dependencies**: Standard JDK DatagramSocket only

## Build

```bash
./gradlew assembleDebug
```

## Architecture

```
app/src/main/kotlin/com/microtempo/
├── MainActivity.kt          # Entry point
├── PreciseNtpClock.kt       # Custom SNTP client (~120 lines)
├── ClockViewModel.kt        # Sync + weather state
└── ui/
    ├── ClockScreen.kt       # Main UI layout
    └── AnalogClock.kt       # Terraluna regulator dial
```

## Technical Details

| Feature | Implementation |
|---------|---------------|
| NTP Precision | Reads bytes 40-47 (transmit timestamp with fraction) |
| Network Filtering | Lowest RTT from 5-sample burst |
| Drift Compensation | Auto-resync every 60 seconds |
| UI Performance | Frame tick in UI layer, not StateFlow |
| Resource Safety | `socket.use {}` for automatic cleanup |

## License

MIT
