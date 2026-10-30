# GamersHub

**FPS & graphics booster, game unlocker, lossless scaling, 4K recorder and multi-platform live streamer for Android — powered by Shizuku. No root required.**

Everything runs through the [Shizuku](https://shizuku.rikka.app/) service (adb / wireless-debugging identity, or root/Sui when available) and a fully custom **liquid-glass** UI (light & dark), with a floating pop-up widget over your games.

---

## ✨ Feature map

| Area | What it does | Access level |
|---|---|---|
| 🔓 **Game Unlock** | writes the games' own saved configs (PUBG `UserCustom.ini` CVars, Delta Force `config.ini` sliders, Free Fire, CODM, generic UE4/UE5) so the hidden **Ultra graphics / 90–120–144 FPS** options appear — the same files root GFX tools edit, backed up and reversible | Shizuku (shell) |
| 🎭 **Device spoof** | flagship gaming-phone presets (ROG 8 Pro, REDMAGIC 9 Pro, S24 Ultra…): display/density/refresh spoof via shell; full model identity via `resetprop` when root exists | Shizuku / root |
| ⚡ **Boost engine** | fixed-performance mode, refresh-rate lock (60–144 Hz), render-resolution scaling, background kill, gaming DND, animation tweaks — with exact restore | Shizuku (shell) |
| 🌡 **Thermal control** | `cmd thermalservice override-status` anti-throttling + live governor with per-game profiles (COOL / BALANCED / BEAST / EXTREME) | Shizuku (shell) |
| 🎚 **Lossless Scaling** | real-time upscaling on the system display surface (`wm size` + density rescale): the game renders at ½ / ⅔ / ¾ / custom resolution and the hardware compositor upscales to the panel — **integer scaling** when the ratio divides evenly, zero added lag | Shizuku (shell) |
| 🧊 **Frame generation** | (1) **capture-side real FG** — GPU block-matching motion estimation + motion-compensated mid-frame synthesis, output at 2× frame rate with genuine generated frames (not duplicates); (2) **hardware MEMC** probe & drive where the SoC exposes it; (3) GameMode frame-rate vote + panel cadence lock | Shizuku + GPU |
| 📊 **Monitoring** | 1 Hz CPU/freq, thermal zones, battery temp, RAM + **live FPS of the foreground game** (`dumpsys gfxinfo`) — in-app gauges and the floating HUD | shell accelerates FPS |
| 🎛 **Floating widget** | draggable bubble (tap = glass panel, double-tap/long-press = gestures), FPS HUD, glass control panel, live-chat overlay | system alert window |
| 🎮 **Game launcher + app selector** | OS-detected games **plus any app you add** — searchable picker, per-game profiles auto-applied by the foreground watcher | Shizuku |
| 🔋 **Battery** | bypass charging via kernel power-supply nodes (probed at runtime), kills heat in long sessions | shell/root (device dependent) |
| ⏺ **Screen recorder** | zero-copy hardware H.264/HEVC up to 4K@120 (device-limited), MP4 + optional mic AAC, robust muxer (audio-timeout watchdog, empty-file detection) | MediaProjection |
| 📡 **Live streaming** | **simultaneous** YouTube + Facebook + Instagram via a hand-rolled RTMP/RTMPS client (AMF0, chunking, FLV tags, ping/ack), per-target stats | — |
| 💬 **Stream chat** | in-game chat overlay — YouTube Live Chat polling built in | API keys |
| 📈 **Analytics** | rolling FPS/thermal/CPU charts, capture-session history, all-time counters | local |

## 📦 Get the APK

The **[GitHub Releases](../../releases)** page hosts installable APKs built by CI
(`Build & Release APK` workflow — signed release build). Download `GamersHub-*.apk`,
install it on your phone, done. The `nightly` prerelease always carries the latest build.

## 🏗 Building from source (Android Studio)

1. Install **Android Studio** (Ladybug or newer).
2. `File → Open` this repository's root folder.
3. Let Gradle sync (AGP 8.5.2 · Kotlin 2.0.20 · Compose BOM 2024.09.02 · wrapper 8.9 included).
4. **Run → Run 'app'** or `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.

> JDK 17, minSdk 26, target/compileSdk 34.

## ▶️ First run

1. Install **Shizuku** and start it:
   - Android 11+: *Developer options → Wireless debugging*, then Shizuku → *Start via wireless debugging*.
   - PC: `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
   - Rooted: Shizuku/Sui starts directly (full identity spoof + root battery nodes unlock).
2. Open GamersHub → tap **Allow** on the Shizuku prompt. If the state looks stale, hit **Reconnect** (it resets the binder listeners and rebinds the shell service).
3. Grant **Display over other apps** (floating widget) and **Notifications**.
4. **Game Unlock** → pick your game → *Unlock max*. Launch the game — the new graphics/FPS options are in its settings.
5. **Lossless Scaling** → pick a render scale (½ is pixel-perfect integer scaling) → everything in-game renders faster while the compositor keeps the picture full-screen.

## 🧠 Architecture

```
app/src/main/java/com/gamershub/
├── GamersHubApp / MainActivity          # app + single Compose host (window blur on A12+)
├── core/
│   ├── shizuku/                        # ShizukuManager (pingBinder + checkSelfPermission
│   │                                   #   state machine, manual reconnect) + AIDL shell
│   ├── unlock/                         # GameConfigUnlocker + preset DB (PUBG/DF/FF/CODM/UE)
│   ├── spoof/                          # DeviceSpoofer (display spoof / root identity)
│   ├── scaling/                        # LosslessScalingEngine (wm size integer scaling)
│   │                                   #   + FrameGenEngine (MEMC probe, GameMode pacing)
│   │                                   #   + FrameGenStage (GPU motion-interpolation pipeline)
│   ├── boost/                          # BoostEngine + ThermalManager + governor
│   ├── battery/                        # bypass-charging node probing
│   ├── monitor/                        # HardwareMonitor (sysfs + dumpsys telemetry)
│   ├── overlay/                        # FloatingWidgetService (Compose in WindowManager)
│   ├── record/                         # CapturePipeline (zero-copy OR FrameGen feed),
│   │                                   #   RecordingSink (watchdog muxer), ScreenRecordService
│   ├── stream/                         # StreamEngine (multi-target RTMP fan-out) + rtmp/ + chat/
│   ├── launcher/                       # GameLibrary (games + user-pinned apps, app selector)
│   ├── profiles/                       # per-game profiles (DataStore + serialization)
│   ├── analytics/                      # history ring buffer + session records (JSONL)
│   ├── settings/                       # SettingsRepository (DataStore)
│   ├── service/                        # auto-boost watcher + BootReceiver
│   └── notifications/
└── ui/                                 # liquid-glass theme + components + navigable screens
```

### Honest engineering notes

- **Game unlock** edits the games' own saved configs — the mechanism that really
  unlocks hidden options — with automatic backups (`*.ghbak`) and one-tap restore.
  Game updates can reset configs; re-run Unlock after big patches.
- **Lossless scaling** is display-surface scaling: the game renders fewer pixels,
  SurfaceFlinger's hardware scaler upscales in real time. Integer ratios (½, ⅓…)
  give pixel-perfect block scaling; no per-game support needed, no added latency.
- **Capture frame generation** synthesises genuine motion-compensated mid-frames
  on the GPU (block-matching estimation → warp+blend), doubling output FPS with
  real content. If GL setup fails on a device it silently falls back to zero-copy —
  nothing breaks. Display-side FG uses vendor MEMC hardware when the SoC exposes it.
- **Thermal strategy:** `override-status` stops apps from self-throttling; the
  governor prevents real SoC throttling by easing the refresh lock before the
  skin-temp target is crossed.
- **Shizuku identity = shell (UID 2000)** — enough for `cmd power`, `cmd thermalservice`,
  `wm`, `settings`, `dumpsys`, `am kill-all`, and writing `Android/data` game configs.
  Kernel frequency locking and `ro.*` props need root; detected and upgraded automatically.
- **Streaming:** self-contained RTMP/RTMPS implementation in `core/stream/rtmp/`.

## 🔐 Permissions

| Permission | Used for |
|---|---|
| Shizuku API | every privileged operation (no root) |
| SYSTEM_ALERT_WINDOW | floating widget / HUD / chat overlay |
| MediaProjection (runtime) | recorder + streaming |
| RECORD_AUDIO (runtime, opt-in) | mic commentary — **off by default** |
| POST_NOTIFICATIONS | engine/capture status |
| QUERY_ALL_PACKAGES | app selector + game detection |
| FOREGROUND_SERVICE* | core engine, widget (specialUse), capture (mediaProjection) |
| WRITE_SETTINGS / WAKE_LOCK / RECEIVE_BOOT_COMPLETED | session tuning, engine, autostart |

## 🛣 Roadmap

- `ChatClient` interface — plug in Meta Graph API chat when you have OAuth tokens.
- `StreamEngine.PLATFORMS` — add custom RTMP targets.

---

Built with Kotlin · Jetpack Compose · Shizuku API 13 · MediaProjection/MediaCodec · OpenGL ES 3.
