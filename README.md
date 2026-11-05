# GamersHub

**A complete AX-MODE game bar for Android: floating panel, live monitor, crosshair, ping optimizer, gyro calibration, mapping keys, mouse pointer, screenshot and an in-game Game Space — all driven by real system calls through [Shizuku](https://shizuku.rikka.app/). No root required.**

GamersHub is a from-scratch, clean-room Android application (package `com.gamershub`) that reproduces the
full feature surface of the *Red Corner / AXERON* game bar — rebuilt in Kotlin + Jetpack Compose, with every
control wired to a real device API instead of a mock-up.

Every overlay, every reading and every toggle is real: FPS comes from the game's own `gfxinfo` frame counter,
the ping numbers are TCP handshakes to live game endpoints, the mapping keys inject real touches, and the
screenshot is a real `screencap` frame.

---

## 📲 Get the APK

The **[GitHub Releases](../../releases)** page hosts the CI-built, signed `GamersHub-*.apk`.
Download it, install it (allow "install unknown apps"), open it, and let the intro play.

---

## ✨ What is inside

| Feature | What it actually does | Needs |
|---|---|---|
| **Gamepad intro** | Full-screen intro video on every cold start, skippable with one tap | — |
| **Lobby** | Your installed games and apps with real icons, search, "added" library, per-game play time, launch modes | — |
| **Game-Labs** | Display/network/thermal tuning switches, preset export, session monitor | partly Shizuku |
| **Profile** | Live CPU, RAM, storage, clock, ping, environment score, device identity | — |
| **AX-MODE floating panel** | The Red-Corner style pop-up: tab pills, live stats strip, AX-MODE band, 3×3 icon grid around the AXERON wordmark, CPU/RAM chevron bars, draggable bubble | Overlay |
| **Information Monitor** | Floating, draggable HUD: real FPS, CPU, GPU load, RAM, temperature, battery, ping, refresh rate and clock — each field toggleable, scalable | Overlay + Shizuku |
| **Crosshair** | 7 real crosshair styles (the original vectors), size, colour, opacity, rotation, draggable | Overlay |
| **Mapping Editor** | Unlimited macro keys as separate overlay windows; drag them anywhere in edit mode, tap them in play mode and a real `input tap/swipe/keyevent/multi-tap` command is executed in the game | Overlay + Shizuku |
| **Mouse Cursor** | Virtual pointer with a trackpad pad: drag to aim, tap to send a real touch at the pointer, adjustable sensitivity | Overlay + Shizuku |
| **Ping Optimizer** | TCP-handshake latency to 8 real game endpoints, jitter, packet loss, grading; one-tap private-DNS switch (Google/Cloudflare/Quad9) + radio-scanning tweaks with a before/after report | Shizuku |
| **Gyro Calibration** | Live gyroscope readout, 90-sample bias capture, drift quality score, sensitivity, invert-Y, saved to prefs | gyroscope |
| **Screenshot** | Real `screencap` through shell identity into `Pictures/GamersHub`, verified on disk | Shizuku |
| **Quick Boost** | Kills background processes, drops caches, frees RAM, reports the freed megabytes | partly Shizuku |
| **Game Space** | Landscape in-game hub with the game card, live stats, Control Center and per-game texture filtering | Overlay |
| **Shizuku activation** | Real permission flow, real user-service shell (`sh -c` as shell/uid 2000), graceful fallback when absent | Shizuku |
| **Themes** | Four accent palettes, animated icons everywhere, spring-driven toggles and gauges | — |

Everything that needs system rights degrades honestly: the UI says *"Needs Shizuku"* instead of pretending.

---

## 🏗 Building from source

1. Android Studio (Ladybug or newer), JDK 17.
2. `File → Open` this folder, let Gradle sync.
3. `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.

AGP 8.5.2 · Kotlin 2.0.20 · Compose BOM 2024.09.02 · Gradle 8.9 · minSdk 26 · target/compileSdk 34 · Shizuku 13.1.5.
Release builds are produced by CI on every `v*` tag (`release.yml`).

---

## ▶️ First run

1. Install **Shizuku** and start it (*Wireless debugging*, `adb`, or root).
2. Open GamersHub → the permission sheet walks through **Notifications → Display over other apps → Write settings → Usage access → Shizuku**.
3. Tap **Allow** on the real Shizuku prompt — the shell user-service binds instantly.
4. Open **Tools** and switch on whatever you need: panel, monitor, crosshair, mapping, mouse.
5. In a game, tap the floating bubble to open the AX-MODE panel; the grid opens the matching tool.

---

## 🧠 Architecture

```
app/src/main/java/com/gamershub/
├── MainActivity.kt                 # Compose host, deep-links a tool sheet from the panel
├── GameSpaceActivity.kt            # landscape in-game hub + system-setting helpers
├── core/
│   ├── shizuku/Shell.kt            # Shizuku user-service shell: exec / ok / appExec
│   ├── shizuku/GamersHubShellService.kt   # runs as shell/root, returns exit code
│   ├── PingEngine.kt               # TCP latency, jitter, loss, private DNS, radio tweaks
│   ├── FpsMeter.kt                 # gfxinfo frame counter, foreground pkg, refresh rate, GPU load
│   ├── GyroEngine.kt               # sensor access, bias calibration, correction
│   ├── ScreenshotEngine.kt         # verified screencap to Pictures/GamersHub
│   ├── MappingStore.kt             # macro-key model, JSON persistence, command compiler
│   └── MonitorPrefs.kt             # monitor fields, scale, colours
├── data/
│   ├── AppRepo.kt                  # installed apps, library, play time, per-game gfx
│   └── DeviceStats.kt              # CPU / RAM / storage / battery / ICMP ping
├── overlay/
│   ├── FloatingPanelService.kt     # AX-MODE bubble + panel
│   ├── MonitorOverlayService.kt    # floating information monitor
│   ├── CrosshairOverlayService.kt  # 7-style crosshair
│   ├── MappingOverlayService.kt    # one overlay window per macro key
│   └── MouseCursorService.kt       # virtual pointer + trackpad
└── ui/
    ├── RootShell.kt                # tabs, sheets, live state, intro
    ├── LobbyScreen.kt / GameLabsScreen.kt / ProfileScreen.kt / ToolsScreen.kt
    ├── components/Parts.kt         # animated icons, toggles, gauges, bars, emblem
    ├── sheets/Sheets.kt            # launch modes, app picker, permissions, settings, activation
    └── sheets/FeatureSheets.kt     # ping, gyro, mapping, monitor, mouse, crosshair, screenshot
```

All UI artwork that ships here was extracted from the original application package
(`frb.axeron.redcorner` 2.5.3) with `apktool`/`jadx`/REA + Ghidra analysis: the AXGC font family,
the AXERON/GAME CORNER marks, the seven crosshair vectors, the info icons and the intro video.

---

## ⚖️ Notes

* No ads, no monetisation, no telemetry, no cloud sync — everything stays on the device.
* Settings are never applied before Shizuku consent is granted.
* `com.gamershub` is an independent reimplementation; it is not affiliated with Axeron.
