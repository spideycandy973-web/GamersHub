package com.gamershub.core.unlock

/**
 * The unlock database: real config recipes per game family.
 *
 * PUBG family  — the documented `UserCustom.ini` CVars that expose
 *                Extreme+ 120 FPS and Ultra/HDR quality (the exact values the
 *                community GFX tools write).
 * Delta Force  — the saved `config.ini` slider values (`fps`, `graphics`)
 *                bumped 0–4 → 5, the unlock for 144 FPS + max graphics.
 * UE4 generic  — standard Unreal `GameUserSettings.ini` scalability overrides.
 */
object GameUnlockPresets {

    val ALL: List<GameUnlockPreset> by lazy { registry.values.toList() }

    private val registry = LinkedHashMap<String, GameUnlockPreset>()

    fun forPackage(packageName: String): GameUnlockPreset? = registry[packageName]

    /** Best preset for any package: known → family fallback. */
    fun forAnyPackage(packageName: String, label: String): GameUnlockPreset {
        registry[packageName]?.let { return it }
        val family = detectFamily(packageName)
        return GameUnlockPreset(
            id = "auto_$packageName",
            displayName = label,
            packageName = packageName,
            notes = family.notes,
            patchFor = family.patchFor
        )
    }

    init {
        // ---- PUBG family -------------------------------------------------
        listOf(
            "com.tencent.ig" to "PUBG Mobile",
            "com.pubg.krmobile" to "PUBG Mobile KR",
            "com.pubg.imobile" to "BATTLEGROUNDS MOBILE INDIA",
            "com.vng.pubgmobile" to "PUBG Mobile VN",
            "com.rekoo.pubgm" to "PUBG Mobile TW"
        ).forEach { (pkg, name) ->
            registry[pkg] = GameUnlockPreset(
                id = "pubg_$pkg",
                displayName = name,
                packageName = pkg,
                notes = "Writes the UserCustom DeviceProfile CVars that unlock " +
                        "Extreme 120 FPS + Ultra/HDR quality (the GFX-tool method).",
                patchFor = ::pubgPatch
            )
        }

        // ---- Delta Force -------------------------------------------------
        listOf(
            "com.tencent.tmgp.dfm" to "Delta Force: Hawk Ops (CN)",
            "com.levelinfinite.hawkOps.global" to "Delta Force (Global)",
            "com.proximabeta.mf.uamo" to "Delta Force (Test)"
        ).forEach { (pkg, name) ->
            registry[pkg] = GameUnlockPreset(
                id = "delta_$pkg",
                displayName = name,
                packageName = pkg,
                notes = "Bumps the saved fps/graphics slider values to max (5) — " +
                        "the documented unlock for 144 FPS + highest graphics.",
                patchFor = ::deltaForcePatch
            )
        }

        // ---- Free Fire ---------------------------------------------------
        registry["com.dts.freefireth"] = GameUnlockPreset(
            id = "freefire",
            displayName = "Free Fire",
            packageName = "com.dts.freefireth",
            notes = "Unlocks the hidden Ultra/90 FPS options via its saved prefs.",
            patchFor = ::freeFirePatch
        )

        // ---- Call of Duty Mobile ----------------------------------------
        registry["com.activision.callofduty.shooter"] = GameUnlockPreset(
            id = "codm",
            displayName = "Call of Duty Mobile",
            packageName = "com.activision.callofduty.shooter",
            notes = "Forces Very High graphics + Max frame rate in the saved config.",
            patchFor = ::codmPatch
        )
    }

    // ------------------------------------------------------------- matchers

    /** Any Unreal-engine game (UE4/UE5) gets the generic scalability patch. */
    private fun detectFamily(packageName: String): GameUnlockPreset = GameUnlockPreset(
        id = "ue_generic",
        displayName = packageName,
        packageName = packageName,
        notes = "Applies standard Unreal Engine scalability overrides. " +
                "Works for most UE4/UE5 titles; unknown engines are left untouched.",
        patchFor = ::uePatch
    )

    // --------------------------------------------------------------- PUBG

    private fun pubgPatch(file: String): GameUnlockPreset.Patch? {
        val name = file.substringAfterLast("/").lowercase()
        return when {
            name == "usercustom.ini" -> GameUnlockPreset.Patch(
                description = "PUBG CVars → 120 FPS + Ultra",
                merge = false,
                content = PUBG_USER_CUSTOM
            )
            name == "gameusersettings.ini" -> GameUnlockPreset.Patch(
                description = "PUBG scalability → max",
                merge = false,
                content = PUBG_GAME_USER_SETTINGS
            )
            else -> null
        }
    }

    // --------------------------------------------------------- Delta Force

    private fun deltaForcePatch(file: String): GameUnlockPreset.Patch? {
        val name = file.substringAfterLast("/").lowercase()
        if (name != "config.ini" && name != "gameusersettings.ini" && !name.endsWith(".cfg")) return null
        return GameUnlockPreset.Patch(
            description = "fps / graphics sliders → max (5)",
            merge = false,
            transform = ::deltaForceRewrite
        )
    }

    /**
     * Bumps the recognised quality/FPS slider keys to their maximum without
     * touching unrelated lines (the whole file is re-emitted, so nothing is lost).
     */
    internal fun deltaForceRewrite(body: String): String {
        val keys = Regex(
            "(?im)^([ \\t]*(?:fps|frame_?rate|frame_?mode|graphics|graphic_?quality|" +
                    "picture_?quality|quality|hdr|resolution_?scale|render_?scale|fps_?mode)" +
                    "[ \\t]*=[ \\t]*)([0-9]+)"
        )
        val bumped = keys.replace(body) { m -> m.groupValues[1] + "5" }
        return if (bumped == body) {
            // nothing recognised — append an override block instead
            body + "\n\n" + DELTA_FORCE_OVERRIDE
        } else bumped
    }

    // ----------------------------------------------------------- Free Fire

    private fun freeFirePatch(file: String): GameUnlockPreset.Patch? {
        val name = file.substringAfterLast("/").lowercase()
        if (!name.endsWith(".ini") && !name.endsWith(".cfg") && !name.endsWith(".txt")) return null
        return GameUnlockPreset.Patch(
            description = "FF quality/FPS keys → max",
            merge = false,
            transform = { body -> genericQualityRewrite(body, FREE_FIRE_OVERRIDE) }
        )
    }

    // ---------------------------------------------------------------- CODM

    private fun codmPatch(file: String): GameUnlockPreset.Patch? {
        val name = file.substringAfterLast("/").lowercase()
        if (!name.endsWith(".ini") && !name.endsWith(".cfg")) return null
        return GameUnlockPreset.Patch(
            description = "CODM graphics/FPS keys → max",
            merge = false,
            transform = { body -> genericQualityRewrite(body, CODM_OVERRIDE) }
        )
    }

    // ------------------------------------------------------------ UE generic

    private fun uePatch(file: String): GameUnlockPreset.Patch? {
        val name = file.substringAfterLast("/").lowercase()
        return when {
            name == "gameusersettings.ini" -> GameUnlockPreset.Patch(
                description = "UE scalability groups → max",
                merge = false,
                transform = { body -> mergeIniSections(body, UE_GAME_USER_SETTINGS) }
            )
            name == "usercustom.ini" -> GameUnlockPreset.Patch(
                description = "UE user CVars appended",
                merge = true,
                content = UE_USER_CUSTOM_APPEND
            )
            else -> null
        }
    }

    /** Bumps quality/fps-like keys, else appends the family override block. */
    private fun genericQualityRewrite(body: String, fallback: String): String {
        val keys = Regex(
            "(?im)^([ \\t]*(?:fps|frame_?rate|frame_?mode|graphics|graphic_?quality|" +
                    "picture_?quality|quality|hdr|resolution_?scale|render_?scale|fps_?mode|smooth|" +
                    "shadow|texture|effect)[ \\t]*=[ \\t]*)([0-9]+)"
        )
        val bumped = keys.replace(body) { m -> m.groupValues[1] + "5" }
        return if (bumped == body) body + "\n\n" + fallback else bumped
    }

    /**
     * Merges the [overrides] INI sections into the existing file: keys present
     * in the override win; everything else in the original survives.
     */
    private fun mergeIniSections(body: String, overrides: String): String {
        val out = StringBuilder()
        val overrideKeys = parseIni(overrides)          // section -> (key -> line)
        val consumed = mutableMapOf<String, MutableSet<String>>()
        overrideKeys.keys.forEach { consumed[it] = mutableSetOf() }

        var currentSection = ""
        for (raw in body.lines()) {
            val line = raw.trimEnd()
            val secMatch = Regex("^\\s*\\[([^]]+)]").find(line)
            if (secMatch != null) {
                currentSection = secMatch.groupValues[1].trim()
                out.appendLine(line)
                // inject override keys for this section right after the header
                overrideKeys[currentSection]?.forEach { (key, oline) ->
                    out.appendLine(oline)
                    consumed[currentSection]!!.add(key)
                }
                continue
            }
            // drop original lines that the override replaces
            val kv = parseKey(line)
            if (kv != null && overrideKeys[currentSection]?.containsKey(kv) == true) {
                continue
            }
            out.appendLine(line)
        }
        // sections that never appeared in the original: append whole
        overrideKeys.forEach { (section, kvs) ->
            val already = consumed[section]?.size ?: 0
            if (already < kvs.size && !body.contains("[$section]")) {
                out.appendLine()
                out.appendLine("[$section]")
                kvs.values.forEach { out.appendLine(it) }
            }
        }
        return out.toString()
    }

    private fun parseIni(text: String): Map<String, LinkedHashMap<String, String>> {
        val result = mutableMapOf<String, LinkedHashMap<String, String>>()
        var section = ""
        for (raw in text.lines()) {
            val secMatch = Regex("^\\s*\\[([^]]+)]").find(raw)
            if (secMatch != null) {
                section = secMatch.groupValues[1].trim()
                result.getOrPut(section) { LinkedHashMap() }
                continue
            }
            val key = parseKey(raw) ?: continue
            result.getOrPut(section) { LinkedHashMap() }[key] = raw.trim()
        }
        return result
    }

    private fun parseKey(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith(";") || t.startsWith("#") || t.startsWith("[")) return null
        val eq = t.indexOf('=')
        if (eq <= 0) return null
        if (t.startsWith("+CVars=")) return t.substringBefore('=')
        return t.substring(0, eq).trim().lowercase()
    }
}

// ---------------------------------------------------------------------------
// Config templates — the real values the community GFX tools write
// ---------------------------------------------------------------------------

private val PUBG_USER_CUSTOM = """
[UserCustom DeviceProfile]
+CVars=r.PUBGMobileFrameRate=120
+CVars=r.PUBGDeviceFPSLow=120
+CVars=r.PUBGDeviceFPSMid=120
+CVars=r.PUBGDeviceFPSHigh=120
+CVars=r.PUBGDeviceFPSHDR=120
+CVars=r.UserQualitySetting=3
+CVars=r.UserHDRSetting=1
+CVars=r.MobileHDR=1
+CVars=r.MobileContentScaleFactor=1.0
+CVars=r.PUBGMaxSupportQualityLevel=4
+CVars=r.PUBGMSAASupport=1
+CVars=r.MobileMSAA=1
+CVars=r.DefaultFeature.AntiAliasing=3
+CVars=r.MaterialQualityLevel=1
+CVars=r.ShadowQuality=3
+CVars=r.MaxAnisotropy=4
+CVars=r.DetailMode=2

[ScalabilityGroups]
sg.ResolutionQuality=100
sg.ViewDistanceQuality=3
sg.AntiAliasingQuality=3
sg.ShadowQuality=3
sg.PostProcessQuality=3
sg.TextureQuality=3
sg.EffectsQuality=3
sg.FoliageQuality=3
sg.TrueSkyQuality=3
sg.GroundClutterQuality=3
sg.IBLQuality=2
sg.FrameRateLimit=120
sg.FrameRateCap=0
""".trimIndent()

private val PUBG_GAME_USER_SETTINGS = """
[/Script/ShadowTrackerExtra.STExtraGameUserSettings]
QualityLevel=3
FPSLevel=5
HDRSetting=1
ShadowQuality=3
TextureQuality=3
EffectQuality=3
AntiAliasing=2
FrameRate=120

[ScalabilityGroups]
sg.ResolutionQuality=100
sg.ViewDistanceQuality=3
sg.AntiAliasingQuality=3
sg.ShadowQuality=3
sg.PostProcessQuality=3
sg.TextureQuality=3
sg.EffectsQuality=3
sg.FoliageQuality=3
sg.FrameRateLimit=120
""".trimIndent()

private val DELTA_FORCE_OVERRIDE = """
[Graphics]
fps = 5
graphics = 5
quality = 5
frame_mode = 5
""".trimIndent()

private val FREE_FIRE_OVERRIDE = """
[Quality]
graphics = 5
fps = 5
shadow = 5
texture = 5
effect = 5
""".trimIndent()

private val CODM_OVERRIDE = """
[Graphic]
GraphicQuality = 5
FrameRate = 5
ShadowQuality = 5
""".trimIndent()

private val UE_GAME_USER_SETTINGS = """
[/Script/Engine.GameUserSettings]
bUseVSync=0
FrameRateLimit=240.000000
ScalabilityLevel=3

[ScalabilityGroups]
sg.ResolutionQuality=100
sg.ViewDistanceQuality=3
sg.AntiAliasingQuality=3
sg.ShadowQuality=3
sg.PostProcessQuality=3
sg.TextureQuality=3
sg.EffectsQuality=3
sg.FoliageQuality=3
sg.ShadingQuality=3
""".trimIndent()

private val UE_USER_CUSTOM_APPEND = """
[UserCustom DeviceProfile]
+CVars=r.MobileContentScaleFactor=1.0
+CVars=r.MobileHDR=1
+CVars=r.MaterialQualityLevel=1
+CVars=r.ShadowQuality=3
""".trimIndent()
