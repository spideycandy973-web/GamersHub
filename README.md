# ⚡ GamersHub

**Real-time FPS & graphics booster, thermal governor, 4K screen recorder and multi-platform live streamer for Android — powered by Shizuku. No root required.**

GamersHub performs deep system-level optimizations through the [Shizuku](https://shizuku.rikka.app/)
service (adb / wireless-debugging identity, or root/Sui when available), wraps everything in a
glass-morphism UI (light & dark), and puts a floating pop-up widget on top of your games so you
never leave the action.

---

## ✨ Feature map

| Area | What it does | Access level |
|---|---|---|
| ⚡ Boost engine | fixed-performance mode, refresh-rate lock (60–144 Hz), render-resolution scaling, background kill, gaming DND, animation speed tweaks | Shizuku (shell) |
| 🌡 Thermal profiles | `cmd thermalservice override-status` anti-throttling + live governor with per-game profiles (COOL / BALANCED / BEAST / EXTREME) | Shizuku (shell) |
| 🎭 Device spoof | flagship gaming-phone presets (ROG 8 Pro, REDMAGIC 9 Pro, S24 Ultra…). Display/density/refresh spoof via shell; full model-identity spoof when root (`resetprop`) exists | Shizuku / root |
| 🔋 Battery | battery telemetry, **bypass charging** via kernel power-supply nodes (probed at runtime), used to kill heat during sessions | shell/root (device dependent) |
| 📊 Monitoring | 1 Hz CPU freq + usage, thermal zones, battery temp, RAM, **live FPS of the foreground game** (`dumpsys gfxinfo` deltas) — shown in-app and in the floating HUD | shell accelerates FPS |
| 🎛 Floating widget | draggable bubble (tap = panel, double-tap/long-press = configurable gestures), FPS HUD, glass control panel, live-chat overlay | system alert window |
| 🎮 Game launcher | auto-detected game library, per-game profiles (refresh, res-scale, thermal, spoof, HUD) auto-applied by the background engine | Shizuku |
| ⏺ Screen recorder | zero-copy hardware H.264/HEVC capture up to 4K@120 (device-limited), MP4 with optional mic AAC | MediaProjection |
| 📡 Live streaming | **simultaneous** YouTube + Facebook + Instagram over a hand-rolled RTMP/RTMPS client (AMF0 + chunking + FLV tags), per-target stats, auto-retry | — |
| 💬 Stream chat | in-game chat overlay — YouTube Live Chat polling built in; Meta platforms pluggable | API keys |
| 📈 Analytics | rolling FPS/thermal/CPU charts, capture-session history, all-time counters | local |
| ☁️ Cloud sync | profiles + settings synced via a user-picked file in any cloud folder (Drive/Dropbox/…) **or** REST endpoint | — |

## 🏗 Building the APK (Android Studio)

1. Install **Android Studio** (Ladybug or newer recommended; anything with AGP 8.5+ support).
2. `File → Open` this repository's root folder (the one containing `settings.gradle.kts`).
3. Let Gradle sync finish (AGP 8.5.2 · Kotlin 2.0.20 · Compose BOM 2024.09.02 · Gradle 8.9 wrapper included).
4. **Run → Run 'app'** for a debug build, or **Build → Generate Signed App Bundle / APK** for release.
   - CLI: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.

> Requires JDK 17 (bundled with Android Studio), minSdk 26, target/compileSdk 34.

## ▶️ First run

1. Install **Shizuku** and start it:
   - Android 11+: *Developer options → Wireless debugging*, then Shizuku → *Start via wireless debugging*.
   - Or from a PC: `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
   - Rooted: Shizuku/Sui starts directly (GamersHub then runs as **root** — full spoof + root-only battery nodes unlock automatically).
2. Open GamersHub → tap **Allow** when Shizuku asks.
3. Grant **Display over other apps** for the floating widget, and **Notifications** permission.
4. Long-press a game in **Games** → bind a profile. The auto engine (Booster → *AUTO ENGINE*) applies it the moment the game comes to the foreground and restores the system when you leave.

## 🧠 Architecture

```
app/src/main/java/com/gamershub/
├── GamersHubApp.kt                # Application + manual DI container
├── MainActivity.kt                # single-activity Compose host (window blur enabled)
├── core/
│   ├── shizuku/                   # ShizukuManager + AIDL user-service (runs as shell/root)
│   ├── boost/                     # BoostEngine (apply/restore) + ThermalManager + governor
│   ├── spoof/                     # DeviceSpoofer with capability detection
│   ├── battery/                   # bypass-charging node probing + control
│   ├── monitor/                   # HardwareMonitor: sysfs + dumpsys telemetry
│   ├── overlay/                   # FloatingWidgetService (Compose inside WindowManager)
│   ├── record/                    # CapturePipeline (MediaCodec), RecordingSink,
│   │                              #   MicAudioPipeline, ScreenRecordService, controller
│   ├── stream/                    # StreamEngine (multi-target fan-out)
│   │   ├── rtmp/                  # Amf0 codec + full RtmpConnection client
│   │   └── chat/                  # ChatHub + YouTubeLiveChatClient + stubs
│   ├── launcher/                  # game library + launch intents
│   ├── profiles/                  # per-game profiles (DataStore + kotlinx.serialization)
│   ├── cloud/                     # CloudSyncManager (SAF file / REST backends)
│   ├── analytics/                 # history ring buffer + session records (JSONL)
│   ├── settings/                  # SettingsRepository (DataStore)
│   ├── service/                   # GamersHubCoreService (auto-boost watcher), BootReceiver
│   └── notifications/
└── ui/                            # theme (glass tokens), components (glass/gauges/charts),
    │                              #   navigable screens
    └── screens/                   # Dashboard, Booster, Games, Recorder, Thermal, Spoof,
                                   #   Stream, Analytics, Settings, More
```

### Design notes (the honest engineering)

- **Shizuku identity = shell (UID 2000)** — enough for `cmd power`, `cmd thermalservice`,
  `settings put secure/system`, `wm size/density`, `dumpsys`, `am kill-all`. Kernel frequency
  locking and `ro.*` props still require root; GamersHub detects root (Magisk `resetprop`)
  and upgrades automatically.
- **Thermal strategy:** `override-status` stops *apps* from self-throttling; the governor
  then prevents *real* SoC throttling by briefly easing the refresh-rate lock before the
  skin-temp target is crossed, and restoring right after — sustained smooth FPS without
  cooking the battery.
- **Recording/streaming never re-encodes:** the compositor renders directly into the
  hardware encoder's input surface; the same encoded frames feed the MP4 muxer **and**
  every RTMP target — near-zero capture overhead.
- **Streaming protocol:** self-contained RTMP/RTMPS implementation (3-way handshake,
  AMF0 connect/createStream/publish, 60 KB chunking, ping/pong + ack bookkeeping,
  FLV/AVC/AAC tag muxing) in `core/stream/rtmp/`.
- **UI:** Compose + custom glass system (translucent panels, hairline gradient borders,
  spring-press buttons, animated switches/gauges) with system window blur on Android 12+.
  Light & dark themes, 4 accent palettes.

## 🔐 Permissions

| Permission | Used for |
|---|---|
| Shizuku API | every privileged operation (no root) |
| SYSTEM_ALERT_WINDOW | floating widget / HUD / chat overlay |
| MediaProjection (runtime) | screen recorder + streaming |
| RECORD_AUDIO (runtime) | mic commentary |
| POST_NOTIFICATIONS | engine/capture status |
| QUERY_ALL_PACKAGES | game-library detection (swap for `<queries>` + picker before Play release) |
| FOREGROUND_SERVICE* | core engine (specialUse), widget (specialUse), capture (mediaProjection) |
| WRITE_SETTINGS / WAKE_LOCK / RECEIVE_BOOT_COMPLETED | session tuning, engine, autostart |

## ⚠️ Fair-use/hardware notes

- Thermal *prevention* + bypass charging only act through interfaces your OEM/kernel
  actually exposes — the app probes and reports support instead of pretending.
- `override-status` does not disable kernel thermal protection (it can't, without root);
  GamersHub's governor is the safety layer. Keep vents clear on EXTREME.

## 🛣 Roadmap hooks

- `StreamEngine.Companion.PLATFORMS` — add custom RTMP targets.
- `ChatClient` interface — plug in Meta Graph API chat when you have OAuth tokens.
- Firebase can replace the REST cloud backend (`CloudSyncManager` is one class).

---

Built with Kotlin · Jetpack Compose · Shizuku API 13 · MediaProjection/MediaCodec.
