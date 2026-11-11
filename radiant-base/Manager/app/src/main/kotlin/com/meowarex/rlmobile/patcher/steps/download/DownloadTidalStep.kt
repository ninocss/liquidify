package com.meowarex.rlmobile.patcher.steps.download

import android.app.Application
import androidx.compose.runtime.Stable
import com.meowarex.rlmobile.R
import com.meowarex.rlmobile.manager.PathManager
import com.meowarex.rlmobile.manager.TidalApkSource
import com.meowarex.rlmobile.patcher.StepRunner
import com.meowarex.rlmobile.patcher.steps.base.DownloadStep
import com.meowarex.rlmobile.patcher.steps.base.StepState
import com.meowarex.rlmobile.patcher.steps.prepare.FetchInfoStep
import com.meowarex.rlmobile.patcher.util.sha256
import com.meowarex.rlmobile.ui.screens.componentopts.PatchComponent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException

@Stable
class DownloadTidalStep(
    private val custom: PatchComponent?,
) : DownloadStep<Int>(), KoinComponent {
    private val paths: PathManager by inject()
    private val context: Application by inject()
    private val source: TidalApkSource by inject()

    override val localizedName = R.string.patch_step_dl_tidal_apk

    override fun getVersion(container: StepRunner) =
        container.getStep<FetchInfoStep>().data.tidalVersionCode

    override fun getRemoteUrl(container: StepRunner) =
        container.getStep<FetchInfoStep>().data.tidalApkUrl

    override fun getStoredFile(container: StepRunner) =
        custom?.getFile(paths) ?: paths.cachedTidalApk(getVersion(container))

    override suspend fun verify(container: StepRunner) {
        super.verify(container)
        val file = getStoredFile(container)
        val expectedSha = container.getStep<FetchInfoStep>().data.tidalSha256
        if (expectedSha != null) {
            val actualSha = sha256(file)
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                throw Error("TIDAL APK SHA256 mismatch: expected $expectedSha, got $actualSha")
            }
        }
    }

    override suspend fun execute(container: StepRunner) {
        if (custom != null) {
            container.log("Using custom TIDAL APK with version ${custom.version} imported ${custom.timestamp}")

            if (!custom.getFile(paths).exists()) {
                throw FileNotFoundException(
                    "Selected custom TIDAL APK does not exist on disk! If this is an update, " +
                        "updates cannot occur when the originally selected custom component has been deleted."
                )
            }

            state = StepState.Skipped
            return
        }

        val version = getVersion(container)
        val file = getStoredFile(container)

        // 1. If file already exists in cache and passes verification, use it
        if (file.exists()) {
            container.log("Checking cached file: ${file.absolutePath}")
            try {
                verify(container)
                state = StepState.Skipped
                container.log("Cached file verified, skipping download")
                return
            } catch (t: Throwable) {
                file.delete()
                container.log("Cached file invalid (${t.message}), removing")
            }
        }

        // 2. Try extracting from installed TIDAL app if available on device
        try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo("com.aspiro.tidal", 0)
            @Suppress("DEPRECATION")
            val installedCode = if (android.os.Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode.toInt() else packageInfo.versionCode
            if (installedCode == version) {
                val sourceDir = packageInfo.applicationInfo?.sourceDir
                if (sourceDir != null) {
                    val installedApk = File(sourceDir)
                    if (installedApk.exists() && installedApk.canRead()) {
                        container.log("Extracting installed TIDAL app ($installedCode) from $sourceDir")
                        file.parentFile?.mkdirs()
                        installedApk.copyTo(file, overwrite = true)
                        try {
                            verify(container)
                            state = StepState.Skipped
                            container.log("Successfully extracted installed TIDAL APK (${file.length() / (1024 * 1024)} MB)")
                            return
                        } catch (t: Throwable) {
                            file.delete()
                            container.log("Extracted TIDAL APK verification failed: ${t.message}")
                        }
                    }
                }
            } else {
                container.log("Installed TIDAL version code ($installedCode) does not match required $version")
            }
        } catch (t: Throwable) {
            container.log("Could not auto-extract installed TIDAL: ${t.message}")
        }

        // 3. Try automatic download via APKPure
        try {
            container.log("Attempting automatic stock TIDAL APK download via APKPure")
            source.download(
                versionCode = version,
                out = file,
                log = container::log,
                onProgress = { progress = it ?: -1f }
            )
            verify(container)
            container.log("Successfully downloaded and verified stock TIDAL APK via APKPure")
            return
        } catch (e: CancellationException) {
            file.delete()
            throw e
        } catch (t: Throwable) {
            file.delete()
            container.log("APKPure download failed: ${t.message ?: t.toString()}")
        }

        // 4. Fall back to remote release download URL if configured
        val remoteUrl = container.getStep<FetchInfoStep>().data.tidalApkUrl
        if (remoteUrl != null) {
            try {
                super.execute(container)
                return
            } catch (t: Throwable) {
                container.log("Remote URL download failed: ${t.message}")
            }
        }

        throw Error(
            "Could not automatically download stock TIDAL APK ($version). " +
            "Please download 'tidal-stock-2.200.0.apk' (versionCode $version) and select it via 'Custom TIDAL APK' under Options."
        )
    }
}
