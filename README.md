# MicroTempo

Ultra-precise chronograph app for Android with **true** microsecond precision via advance device
calibration and custom SNTP implementation.

https://github.com/user-attachments/assets/daf5fc53-ffe2-4629-8b64-4a66079ea3b0

## Features

- **Microsecond precision**: Custom SNTP client reads NTP fractional seconds (bytes 44-47) for
  ~232ps source resolution
- **Burst sampling**: Takes 5 samples, keeps lowest-RTT for best accuracy
- **Auto-resync**: Compensates for smartphone's quartz drift every minute
- **Display latency compensation**: Advanced camera+mirror calibration for accurate displayed time
- **Terraluna-inspired dial**: Regulator layout with Arabic-Indic numerals
- **Perpetual calendar**: Date, day, month, year indicator
- **Weather & lunar**: Via wttr.in API
- **Zero dependencies**: Standard JDK DatagramSocket only

## Display Calibration

Screen-displayed time naturally lags behind your device's system time due to display pipeline
latency (GPU rendering, display controller, LCD response). MicroTempo addresses this through
calibration, measuring and compensating for this lag to bring displayed time accuracy into the
microsecond range.

**To calibrate:**

1. Long-press the "Device lag" indicator below the digital time
2. Grant camera permission when prompted
3. Position phone so front camera sees a mirror reflection of the screen
4. Tap "Start Calibration" and wait for completion (~15-30 seconds)
5. Review results and tap "Accept & Apply"

**How it works:** The screen flashes white at precise intervals. The front camera records these
flashes. By comparing flash timestamps to camera detection timestamps, the app measures your
display's actual latency and compensates future time displays accordingly.

**Precision:** Depends on your camera's max FPS:

- 240fps+ → ±0.1ms precision
- 120fps → ±0.2ms precision
- 30fps → ±0.5ms precision

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
cp local.properties.example local.properties
./gradlew assembleRelease
```

## Architecture

```
app/src/main/kotlin/com/microtempo/
├── MainActivity.kt          # Entry point
├── PreciseNtpClock.kt       # Custom SNTP client
├── ClockViewModel.kt        # Sync + weather state
├── calibration/
│   ├── CameraCapabilities.kt      # Detect max camera FPS
│   ├── CalibrationParams.kt       # Dynamic params per FPS tier
│   ├── AutoCalibrator.kt          # Flash detection + statistics
│   └── DisplayLatencyCompensator.kt # Compensation + persistence
└── ui/
    ├── ClockScreen.kt       # Main UI layout
    ├── CalibrationScreen.kt # Calibration UI
    └── AnalogClock.kt       # Terraluna regulator dial
```

## Technical Details

| Feature                   | Implementation                                        |
| ------------------------- | ----------------------------------------------------- |
| NTP Precision             | Reads bytes 40-47 (transmit timestamp with fraction)  |
| Network Filtering         | Lowest RTT from 5-sample burst                        |
| Quartz Drift Compensation | Auto-resync every 60 seconds to minimize quartz drift |
| Display Lag Compensation  | Camera-based calibration with MAD outlier rejection   |
| UI Performance            | Frame tick in UI layer, not StateFlow                 |
| Resource Safety           | `socket.use {}` for automatic cleanup                 |

## License

MIT
