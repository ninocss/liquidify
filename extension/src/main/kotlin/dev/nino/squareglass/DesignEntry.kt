/* GPL-3.0-or-later. Patch preferences live in the main native Settings list. */
package dev.nino.squareglass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

object DesignEntry {
    @JvmStatic fun append(scope: LazyListScope, items: List<*>) {
        if (SquareSettings.themeEnabled() == 0) return
        // The same list renderer also serves every subpage. The pinned Offline
        // model's callback belongs exclusively to the main Settings page.
        if (!items.any { it is jm.f.a && it.e?.javaClass?.name ==
            "com.aspiro.wamp.settings.items.profile.SettingsItemOfflineMode\$viewState\$1" }) return

        scope.item(key = "squareglass.design.v2") { Entry() }
    }

    private fun activity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> activity(context.baseContext)
        else -> null
    }

    @Composable
    fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
        val currentOnBack by rememberUpdatedState(onBack)
        val backCallback = remember {
            object : androidx.activity.OnBackPressedCallback(enabled) {
                override fun handleOnBackPressed() {
                    currentOnBack()
                }
            }
        }
        SideEffect {
            backCallback.isEnabled = enabled
        }
        val context = LocalContext.current
        DisposableEffect(context) {
            val act = activity(context)
            if (act is androidx.activity.ComponentActivity) {
                act.onBackPressedDispatcher.addCallback(backCallback)
            }
            onDispose {
                backCallback.remove()
            }
        }
    }

    @Composable fun CategoryCard(
        title: String,
        icon: String? = null,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        val dark = NativeGlass.dark
        val ink = Color(SquarePalette.ink(dark))
        Column(
            modifier = GlassPanels.panel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                18f,
            ).clip(RoundedCornerShape(18.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (icon != null) {
                    Text(
                        text = icon,
                        color = Color(0xFF8054D9),
                        fontSize = 16.sp,
                        fontFamily = SquareTypography.family,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = title,
                    color = ink,
                    fontSize = 15.sp,
                    fontFamily = SquareTypography.family,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ink.copy(alpha = 0.08f)),
            )
            content()
            Spacer(Modifier.height(4.dp))
        }
    }

    @Composable fun ActionRow(
        title: String,
        subtitle: String? = null,
        badge: String? = null,
        onClick: () -> Unit,
    ) {
        val dark = NativeGlass.dark
        val ink = Color(SquarePalette.ink(dark))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = ink,
                    fontSize = 15.sp,
                    fontFamily = SquareTypography.family,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = ink.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontFamily = SquareTypography.family,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            if (badge != null) {
                Text(
                    text = badge,
                    color = Color(0xFF8054D9),
                    fontSize = 13.sp,
                    fontFamily = SquareTypography.family,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text(
                text = "›",
                color = ink.copy(alpha = 0.45f),
                fontSize = 18.sp,
                fontFamily = SquareTypography.family,
                fontWeight = FontWeight.Light,
            )
        }
    }

    @Composable fun CardToggle(
        title: String,
        subtitle: String? = null,
        key: String,
        fallback: Boolean = true,
    ) {
        DesignState.revision
        val checked = SquareSettings.flag(key, fallback)
        val dark = NativeGlass.dark
        val ink = Color(SquarePalette.ink(dark))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { SquareSettings.put(key, !checked) }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    color = ink,
                    fontSize = 15.sp,
                    fontFamily = SquareTypography.family,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = ink.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontFamily = SquareTypography.family,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = { SquareSettings.put(key, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF8054D9),
                    uncheckedThumbColor = ink.copy(alpha = 0.65f),
                    uncheckedTrackColor = ink.copy(alpha = 0.12f),
                    uncheckedBorderColor = ink.copy(alpha = 0.25f),
                ),
            )
        }
    }

    @Composable fun SegmentedPillRow(
        title: String? = null,
        subtitle: String? = null,
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
    ) {
        val dark = NativeGlass.dark
        val ink = Color(SquarePalette.ink(dark))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    color = ink,
                    fontSize = 15.sp,
                    fontFamily = SquareTypography.family,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = ink.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontFamily = SquareTypography.family,
                        fontWeight = FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ink.copy(alpha = 0.08f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEachIndexed { index, label ->
                    val isSelected = index == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) Color(0xFF8054D9) else Color.Transparent)
                            .clickable(role = Role.RadioButton) { onSelect(index) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else ink.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            fontFamily = SquareTypography.family,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable fun DirectCardSlider(
        title: String,
        subtitle: String? = null,
        key: String,
        low: Int,
        high: Int,
        current: Int,
        suffix: String,
    ) {
        val dark = NativeGlass.dark
        val ink = Color(SquarePalette.ink(dark))
        var sliderRatio by remember(current) {
            mutableFloatStateOf(((current - low).toFloat() / (high - low)).coerceIn(0f, 1f))
        }
        val currentVal = (low + sliderRatio * (high - low)).roundToInt()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = ink,
                        fontSize = 15.sp,
                        fontFamily = SquareTypography.family,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = ink.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontFamily = SquareTypography.family,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF8054D9).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "$currentVal $suffix",
                        color = Color(0xFF8054D9),
                        fontSize = 12.sp,
                        fontFamily = SquareTypography.family,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            val interactions = remember { MutableInteractionSource() }
            Slider(
                value = sliderRatio,
                onValueChange = {
                    sliderRatio = it
                    val newVal = (low + it * (high - low)).roundToInt()
                    SquareSettings.put(key, newVal)
                },
                interactionSource = interactions,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF8054D9),
                    activeTrackColor = Color(0xFF8054D9),
                    inactiveTrackColor = ink.copy(alpha = 0.15f),
                ),
                thumb = {
                    SliderDefaults.Thumb(
                        interactions,
                        modifier = SliderMotion.thumb(Modifier, interactions),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF8054D9)),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }

    @Composable fun LiveGlassPreview() {
        DesignState.revision
        val dark = NativeGlass.dark
        val ink = Color(SquarePalette.ink(dark))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "LIVE GLASS PREVIEW",
                color = ink.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = SquareTypography.family,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF8054D9),
                                Color(0xFF2E1C59),
                                Color(0xFF0D1B2A),
                                Color(0xFF0077B6),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Floating Glass Panel inside preview
                Box(
                    modifier = GlassPanels.panel(
                        Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        16f,
                        emphasized = true,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF9D4EDD), Color(0xFF5A189A))
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "♬",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Liquid Atmosphere",
                                color = ink,
                                fontSize = 14.sp,
                                fontFamily = SquareTypography.family,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Now Playing • Dynamic Glass",
                                color = ink.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontFamily = SquareTypography.family,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(Color(0xFF8054D9)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "▶",
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable fun LiquidifySettingsSubscreen(onDismiss: () -> Unit) {
        BackHandler(onBack = onDismiss)
        val context = LocalContext.current
        val dark = NativeGlass.dark
        val ink = Color(SquarePalette.ink(dark))
        val bg = Color(SquarePalette.base(dark))

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top App Bar
                    Surface(
                        color = bg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(56.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onDismiss) {
                                Text(
                                    text = "←",
                                    color = ink,
                                    fontSize = 22.sp,
                                    fontFamily = SquareTypography.family,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Liquidify Settings",
                                    color = ink,
                                    fontSize = 18.sp,
                                    fontFamily = SquareTypography.family,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "v1.0 Beta",
                                color = Color(0xFF8054D9),
                                fontSize = 12.sp,
                                fontFamily = SquareTypography.family,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    }

                    // Scrollable Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        LiveGlassPreview()

                        Spacer(Modifier.height(4.dp))

                        // Card 1: Appearance & Design
                        CategoryCard(title = "Design & Appearance", icon = "✦") {
                            SegmentedPillRow(
                                title = "Color Scheme",
                                subtitle = "System, Light, or Dark",
                                options = listOf("System", "Light", "Dark"),
                                selected = SquareSettings.themeMode(),
                            ) {
                                SquareSettings.put("theme", it)
                                activity(context)?.recreate()
                            }
                            SegmentedPillRow(
                                title = "Glass Style",
                                subtitle = "Dynamic refraction or matte frosted",
                                options = listOf("Liquid", "Frosted", "Off"),
                                selected = SquareSettings.preset(),
                            ) {
                                SquareSettings.preset(it)
                            }
                            DirectCardSlider(
                                title = "Blur Strength",
                                subtitle = "Background blur radius",
                                key = "blur",
                                low = 0,
                                high = 24,
                                current = SquareSettings.blur().roundToInt(),
                                suffix = "dp",
                            )
                            DirectCardSlider(
                                title = "Surface Opacity",
                                subtitle = "Tinted glass layer opacity",
                                key = "opacity",
                                low = 20,
                                high = 90,
                                current = (SquareSettings.opacity() * 100).roundToInt(),
                                suffix = "%",
                            )
                            DirectCardSlider(
                                title = "Refraction (Lens)",
                                subtitle = "Prismatic curvature (AGSL shader)",
                                key = "lens",
                                low = 0,
                                high = 100,
                                current = (SquareSettings.refraction() * 100).roundToInt(),
                                suffix = "%",
                            )
                            CardToggle(
                                title = "Reduce Motion",
                                subtitle = "Disables shader refractions and inertial animations",
                                key = "reduceMotion",
                                fallback = false,
                            )
                        }

                        // Card 2: Navigation & Chrome
                        CategoryCard(title = "Navigation & Surfaces", icon = "❖") {
                            CardToggle(
                                title = "Glass Navigation Bar",
                                subtitle = "Translucent tab bar with light refraction",
                                key = "nav",
                            )
                            CardToggle(
                                title = "Glass Mini-Player",
                                subtitle = "Floating bottom playback bar",
                                key = "mini",
                            )
                            CardToggle(
                                title = "Glass Full Player",
                                subtitle = "Translucent controls in full-screen player",
                                key = "player",
                            )
                        }

                        // Card 3: Player & Gestures
                        CategoryCard(title = "Player & Gestures", icon = "▶") {
                            CardToggle(
                                title = "Mini-Player Swipe Gestures",
                                subtitle = "Swipe left/right to quickly skip tracks",
                                key = "miniGestures",
                            )
                            val actions = listOf("Off", "Next", "Queue", "Fav")
                            SegmentedPillRow(
                                title = "Track Swipe Right",
                                subtitle = "Action when swiping right on tracks",
                                options = actions,
                                selected = SquareSettings.integer("swipeRight", 1, 0, 3),
                            ) {
                                SquareSettings.put("swipeRight", it)
                            }
                            SegmentedPillRow(
                                title = "Track Swipe Left",
                                subtitle = "Action when swiping left on tracks",
                                options = actions,
                                selected = SquareSettings.integer("swipeLeft", 2, 0, 3),
                            ) {
                                SquareSettings.put("swipeLeft", it)
                            }
                        }

                        // Card 4: Home & Content
                        CategoryCard(title = "Home & Listening Habits", icon = "⌂") {
                            SegmentedPillRow(
                                title = "Home Quick Actions",
                                subtitle = "Top action buttons on home screen",
                                options = listOf("Recommendations", "Autoplay & History"),
                                selected = SquareSettings.integer("homeActions", 0, 0, 1),
                            ) {
                                SquareSettings.put("homeActions", it)
                            }
                            CardToggle(
                                title = "Time-of-Day Habits",
                                subtitle = "Adapt recommendations locally based on time of day",
                                key = "learnTime",
                            )
                            ActionRow(
                                title = "Reset Listening Profile",
                                subtitle = "Clears local listening history on this device",
                            ) {
                                NativePlayback.clearHistory()
                                Toast.makeText(context, "Listening profile reset", Toast.LENGTH_SHORT).show()
                            }
                        }

                        // Card 5: Audio & Output
                        CategoryCard(title = "Audio & Streaming", icon = "♬") {
                            ActionRow(
                                title = "Output Device Picker",
                                subtitle = "Select TIDAL Connect & Linn OpenHome renderers",
                                badge = "Open",
                            ) {
                                activity(context)?.let { SquareOutput.showFromSettings(it) }
                            }
                        }

                        // Card 6: About & Reset
                        CategoryCard(title = "System & Maintenance", icon = "⚙") {
                            ActionRow(
                                title = "Reset Design to Defaults",
                                subtitle = "Resets all Liquidify preferences",
                            ) {
                                SquareSettings.reset()
                                activity(context)?.recreate()
                            }
                        }

                        Spacer(Modifier.height(110.dp))
                    }
                }
            }
        }
    }

    @Composable private fun Entry() {
        DesignState.revision
        var isSubscreenOpen by remember { mutableStateOf(false) }
        val dark = NativeGlass.dark
        val ink = Color(SquarePalette.ink(dark))

        // Single clean row in TIDAL's main settings list
        Row(
            modifier = SquareScreens.settingsRow(Modifier.fillMaxWidth())
                .clickable(role = Role.Button) { isSubscreenOpen = true }
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Liquidify",
                    color = ink,
                    fontSize = 16.sp,
                    fontFamily = SquareTypography.family,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Customize Design, Gestures & Modules",
                    color = ink.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    fontFamily = SquareTypography.family,
                    fontWeight = FontWeight.Normal,
                )
            }
            Text(
                text = "v1.0 Beta",
                color = Color(0xFF8054D9),
                fontSize = 12.sp,
                fontFamily = SquareTypography.family,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = "›",
                color = ink.copy(alpha = 0.55f),
                fontSize = 18.sp,
                fontFamily = SquareTypography.family,
                fontWeight = FontWeight.Light,
            )
        }

        if (isSubscreenOpen) {
            LiquidifySettingsSubscreen(onDismiss = { isSubscreenOpen = false })
        }
    }
}
