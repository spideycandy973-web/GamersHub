# Lossless scaling, for real — what v6.5.0 does

This document exists because "lossless scaling" is a term that is easy to fake. Here is
exactly what GamersHub does, what it measures, and what a normal Android app cannot do.

## What Lossless Scaling (the PC tool) actually is

Lossless Scaling hooks the game's swap-chain present call, takes the finished backbuffer,
spatially upscales it with a shader (LS1 / FSR / NIS / CAS / Anime4K / integer), optionally
inserts interpolated frames, and presents the result itself.

Three properties define it:

1. the **output is always native panel resolution** — nothing on screen shrinks;
2. only the **internal render buffer** shrinks, which is where the performance comes from;
3. it **never touches the game's own pipeline** and labels generated frames as generated.

## What GamersHub does now (v6.5.0)

### 1. Lossless render scale — `RenderEngine.applyLossless`

```
base   = physical panel (read from `wm size`, never from a previous override)
target = even(base * pct / 100)          // aspect ratio preserved
run    = `wm size <w>x<h>`               // the GPU fills fewer pixels
density: NOT TOUCHED                     // ← this was the 6.4.0 bug
verify = read `wm size` back, assert the override and that no density override exists
```

The compositor stretches the smaller framebuffer to the panel, so a game still fills the
screen while rendering ~51 % of the pixels at 70 %. Because the density is untouched, the
interface does not shrink — the 6.4.0 build wrote `wm density` as well and that is exactly
why it looked like "it just decreases size".

Pixel maths is reported back to the UI, e.g. `3.31 → 1.62 Mpx`, plus a measured
`before → after` FPS sample from `SurfaceFlinger --latency`.

### 2. Real GLES upscaler — `core/upscale/UpscaleEngine.kt`

OpenGL ES 2.0, no root, no vendor SDK:

| stage | what it does |
|---|---|
| sample | GPU bilinear at the target resolution |
| CAS | contrast-adaptive sharpen, 4 cross taps, strength by preset |
| anti-ringing | result clamped to the local min/max — no halos, no white edges |
| detail | Quality/Ultra add 4 diagonal taps with a micro-detail term |
| integer mode | `GL_NEAREST` sampler → pixel-perfect 2x/3x/4x for retro/emulators |

Presets: Performance (0.12), Balanced (0.28), Quality (0.45 + detail), Ultra (0.62 + full detail).

`UpscaleEngine.benchmark()` renders real frames on the device's GPU and returns
**measured** ms/frame — the panel shows that number, it is not estimated.

### 3. Upscale mirror — `core/upscale/UpscaleMirrorService.kt`

This is the only way a non-root app can upscale *another* app's frames:

```
game (render scale 70 %) → MediaProjection → external OES texture
   → UpscaleEngine shader → opaque, non-touchable, full-panel overlay
```

Touches still reach the game; nothing is resized or cropped. Live readout shows
`src → dst · fps · latency`, all measured (SurfaceTexture timestamp → present time).

Honest limits, shown in the UI:

* **Android 14+** and you must pick **the game** in the capture dialog. If you pick
  "entire screen" the overlay ends up inside the capture and mirrors itself — so on
  Android 13 and older the feature is disabled with that reason instead of pretending.
* Added latency (typically a few frames). It is a trade of sharpness against latency,
  which is why the number is displayed.

## Level 1 / 2 / 3 compatibility

| feature | level | reality |
|---|---|---|
| display size, refresh rate, FPS, frame time, thermal, battery | 1 | real, Shizuku only |
| GLES upscale + adaptive sharpen + integer scale | 2 | real, needs GLES 2.0 (every device) |
| upscale mirror of another app | 2 | real, Android 14+ single-app capture |
| frame generation inside a game | 3 | **not implemented** — requires a present hook; Android exposes nothing like it |
| texture/shadow quality inside a game | 3 | only where the engine ships a config (UE4/UE5 `.ini`), otherwise the platform GPU mode + render scale |

## What was removed and will not come back

`debug.hwui.renderer`, `debug.renderengine.backend`, `debug.hwui.disable_vsync`,
`debug.sf.hw`, `game_driver_all_apps` — global graphics props that corrupt other apps
until the next reboot. They were deleted in 6.3.4.
