package com.meowarex.rlmobile.patcher.steps.patch

import com.meowarex.rlmobile.R
import com.meowarex.rlmobile.patcher.StepRunner
import com.meowarex.rlmobile.patcher.steps.StepGroup
import com.meowarex.rlmobile.patcher.steps.base.Step
import com.meowarex.rlmobile.patcher.steps.download.CopyDependenciesStep
import com.meowarex.rlmobile.patcher.steps.download.DownloadPatchesStep
import com.meowarex.rlmobile.patcher.util.sha256
import com.meowarex.rlmobile.ui.screens.patchopts.PatchOptions
import com.github.diamondminer88.zip.ZipReader
import com.github.diamondminer88.zip.ZipWriter
import dev.nino.squareglass.patching.ResourcePalettePatcher
import dev.nino.squareglass.patching.ColorSelectorPatcher

/** Resource IDs, localized strings, and native actions remain owned by TIDAL. */
class DesignResourcesStep(private val options: PatchOptions) : Step() {
    override val group = StepGroup.Patch
    override val localizedName = R.string.squareglass_patch_resources

    override suspend fun execute(container: StepRunner) {
        val smali = container.getStep<SmaliPatchStep>()
        val design = smali.designMetadata?.designResources ?: return
        fun enabled(id: String): Boolean = options.patchStates[id]
            ?: smali.specs.firstOrNull { it.id == id }?.defaultEnabled ?: false
        val apk = container.getStep<CopyDependenciesStep>().apk
        val patchZip = container.getStep<DownloadPatchesStep>().getStoredFile(container)
        val replacements = linkedMapOf<String, ByteArray>()
        val paletteSpec = smali.specs.firstOrNull { it.id == design.patchId }
        if (paletteSpec == null) {
            container.log("Warning: Design resources patchId '${design.patchId}' not found in patch specs")
        }
        if (enabled(design.patchId)) {
            val modeText = options.smaliSubstitutions(smali.specs)["__RL_SQUARE_THEME_MODE__"] ?: "0x0"
            val mode = if (smali.designMetadata?.compatibility?.capabilities?.contains("squareRuntimeThemeV1") == true) 0
                else modeText.removePrefix("0x").toInt(16)
            val original = ZipReader(apk).use { it.openEntry("resources.arsc")?.read() }
                ?: error("TIDAL resources.arsc is missing")
            val changes = design.palette.map {
                ResourcePalettePatcher.ColorChange(it.id.toLong(16).toInt(), it.name, it.expectedType,
                    it.expectedData.toLong(16).toInt(), it.light.toLong(16).toInt(), it.dark.toLong(16).toInt())
            }
            replacements["resources.arsc"] = ResourcePalettePatcher.patch(original, changes, mode)
            container.log("Applied ${changes.size} Square palette resources with unchanged IDs")
            ZipReader(apk).use { base ->
                for (xml in design.themeXml) {
                    require(xml.target.matches(Regex("res/(color|layout|drawable)(-[a-z0-9-]+)?/[a-z0-9_]+\\.xml"))) { "Invalid theme XML target" }
                    require(xml.expectedType in 1..2 && xml.expectedCount in 1..100) { "Invalid theme XML rule" }
                    val originalXml = base.openEntry(xml.target)?.read() ?: error("Missing theme XML")
                    require(sha256(originalXml) == xml.originalSha256) { "Unexpected theme XML ${xml.target}" }
                    val replacement = ColorSelectorPatcher.patch(originalXml, xml.attributeId.toLong(16).toInt(),
                        xml.expectedType, xml.fromResource.toLong(16).toInt(), xml.toResource.toLong(16).toInt(), xml.expectedCount)
                    require(sha256(replacement) == xml.sha256) { "Theme XML output mismatch" }
                    replacements[xml.target] = replacement
                }
            }
            container.log("Applied ${design.themeXml.size} fingerprinted native theme XML rules")
        } else {
            container.log("Design palette resources skipped: patch '${design.patchId}' is disabled (spec present: ${paletteSpec != null})")
        }
        val fontsSpec = smali.specs.firstOrNull { it.id == design.fontsPatchId }
        if (fontsSpec == null) {
            container.log("Warning: Design resources fontsPatchId '${design.fontsPatchId}' not found in patch specs")
        }
        if (enabled(design.fontsPatchId)) {
            ZipReader(apk).use { base ->
                ZipReader(patchZip).use { pack ->
                    for (font in design.fonts) {
                        require(font.target.matches(Regex("res/(font|raw)/[a-z0-9_]+\\.(otf|ttf)"))) { "Invalid font target" }
                        require(font.source.matches(Regex("fonts/[a-z0-9_]+\\.ttf"))) { "Invalid font source" }
                        val original = base.openEntry(font.target)?.read() ?: error("Missing font ${font.target}")
                        require(sha256(original) == font.originalSha256) { "Unexpected font ${font.target}" }
                        val replacement = pack.openEntry(font.source)?.read() ?: error("Missing design font")
                        require(sha256(replacement) == font.sha256) { "Design font checksum mismatch" }
                        require(replacement.size in 1024..4_000_000) { "Invalid design font size" }
                        replacements[font.target] = replacement
                    }
                }
            }
            container.log("Applied Square typography to ${design.fonts.size} native font resources")
        }
        if (replacements.isEmpty()) return
        ZipWriter(apk, true).use { writer ->
            for ((name, bytes) in replacements) {
                writer.deleteEntry(name)
                writer.writeEntry(name, bytes)
            }
        }
    }
}
