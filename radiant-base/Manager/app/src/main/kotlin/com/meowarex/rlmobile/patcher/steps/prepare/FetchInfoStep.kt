package com.meowarex.rlmobile.patcher.steps.prepare

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Stable
import com.meowarex.rlmobile.R
import com.meowarex.rlmobile.BuildConfig
import com.meowarex.rlmobile.manager.CachedReleaseInfo
import com.meowarex.rlmobile.manager.PathManager
import com.meowarex.rlmobile.manager.ReleaseInfoCache
import com.meowarex.rlmobile.network.models.RLBuildInfo
import com.meowarex.rlmobile.network.services.RadiantLyricsGithubService
import com.meowarex.rlmobile.network.utils.getOrThrow
import com.meowarex.rlmobile.network.utils.SemVer
import com.meowarex.rlmobile.network.utils.preferBundledPatches
import com.meowarex.rlmobile.patcher.StepRunner
import com.meowarex.rlmobile.patcher.steps.StepGroup
import com.meowarex.rlmobile.patcher.steps.base.Step
import com.meowarex.rlmobile.util.isOnline
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

@Stable
class FetchInfoStep(private val localPatches: Boolean = false) : Step(), KoinComponent {
    private val github: RadiantLyricsGithubService by inject()
    private val context: Application by inject()
    private val paths: PathManager by inject()
    private val json: Json by inject()

    override val group = StepGroup.Prepare
    override val localizedName = R.string.patch_step_fetch_info

    lateinit var data: RLBuildInfo
        private set

    lateinit var patchesAssetUrl: String
        private set

    var bundledPatches = false
        private set

    override suspend fun execute(container: StepRunner) {
        if (localPatches) {
            loadBundled(container)
            return
        }

        val bundled = getBundledBuildInfo()
        val bundledVer = bundled?.patchesVersion

        // Fetch the latest release and refresh the cache (when online)
        if (context.isOnline()) {
            try {
                if (bundledVer != null) {
                    val release = github.getLatestRelease(force = true).getOrThrow()
                    val remoteVer = com.meowarex.rlmobile.network.utils.SemVer.parseOrNull(release.tagName)
                    if (remoteVer == null || remoteVer <= bundledVer) {
                        container.log("Bundled Liquidify patches ($bundledVer) are up-to-date with remote (${release.tagName}); skipping remote download")
                        loadBundled(container)
                        return
                    }
                }
                fetchFromRemote(container)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                container.log("Failed to fetch latest release: ${Log.getStackTraceString(t)}")
                container.log("Falling back to cached release info for a local re-patch")
            }
        } else {
            container.log("No network connection — using cached release info for a local re-patch")
        }

        if (bundled != null) {
            loadBundled(container)
            return
        }

        val cached = ReleaseInfoCache.load(paths, json)
        if (cached == null || preferBundledPatches(SemVer.parse(BuildConfig.VERSION_NAME), cached.data.patchesVersion)) {
            loadBundled(container)
            return
        }
        data = cached.data
        patchesAssetUrl = cached.patchesAssetUrl
        container.log("Using cached build info: $data")
        container.log("Cached patches asset URL: $patchesAssetUrl")
    }

    private fun getBundledBuildInfo(): RLBuildInfo? {
        return try {
            context.assets.open("liquidify/data.json").bufferedReader().use {
                json.decodeFromString<RLBuildInfo>(it.readText())
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadBundled(container: StepRunner) {
        data = context.assets.open("liquidify/data.json").bufferedReader().use {
            json.decodeFromString<RLBuildInfo>(it.readText())
        }
        patchesAssetUrl = ""
        bundledPatches = true
        container.log("Using bundled Liquidify build info; GitHub release not required: $data")
    }

    private suspend fun fetchFromRemote(container: StepRunner) {
        container.log("Fetching latest release from ${RadiantLyricsGithubService.REPO_OWNER}/${RadiantLyricsGithubService.REPO_NAME}")
        val release = github.getLatestRelease(force = true).getOrThrow()
        if (preferBundledPatches(SemVer.parse(BuildConfig.VERSION_NAME), SemVer.parseOrNull(release.tagName))) {
            loadBundled(container)
            return
        }

        val dataJsonUrl = release.assets
            .find { it.name == RadiantLyricsGithubService.DATA_JSON_ASSET_NAME }
            ?.browserDownloadUrl
            ?: throw IllegalStateException("No ${RadiantLyricsGithubService.DATA_JSON_ASSET_NAME} asset found in latest release ${release.tagName}")

        val patchesUrl = release.assets
            .find { it.name == RadiantLyricsGithubService.PATCHES_ASSET_NAME }
            ?.browserDownloadUrl
            ?: throw IllegalStateException("No ${RadiantLyricsGithubService.PATCHES_ASSET_NAME} asset found in latest release ${release.tagName}")

        container.log("Fetching build info from $dataJsonUrl")
        val buildInfo = github.getBuildInfo(dataJsonUrl, force = true).getOrThrow()

        data = buildInfo
        patchesAssetUrl = patchesUrl
        container.log("Fetched build info: $data")
        container.log("Patches asset URL: $patchesAssetUrl")

        ReleaseInfoCache.save(paths, json, CachedReleaseInfo(buildInfo, patchesUrl))
    }
}
