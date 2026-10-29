/* GPL-3.0-or-later. Patch preferences live in the main native Settings list. */
package dev.nino.squareglass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import dev.nino.squareglass.material.LiquidMotion
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

object DesignEntry {
    @JvmStatic @Composable
    fun Screen(modifier: Modifier, padding: PaddingValues,
        nativeContent: LazyListScope.() -> Unit, items: List<*>) {
        val main = SquareSettings.themeEnabled() != 0 && items.any {
            it is jm.f.a && it.e?.javaClass?.name ==
                "com.aspiro.wamp.settings.items.profile.SettingsItemOfflineMode\$viewState\$1"
        }
        if (!main) {
            LazyColumn(modifier, contentPadding = padding, content = nativeContent)
            return
        }
        var selected by rememberSaveable { mutableIntStateOf(0) }
        val ink = Color(SquarePalette.ink(NativeGlass.dark))
        Column(modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selected, containerColor = Color.Transparent, contentColor = ink) {
                listOf("TIDAL", "Liquidify").forEachIndexed { index, title ->
                    Tab(selected = selected == index, onClick = { selected = index },
                        text = { Label(title) }, selectedContentColor = ink,
                        unselectedContentColor = ink.copy(alpha = .6f))
                }
            }
            // Separate list identities keep native scroll state out of Liquidify.
            key(selected) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = padding) {
                    if (selected == 0) nativeContent(this)
                    else item(key = "liquidify.preferences") { Entry() }
                }
            }
        }
    }
    private fun activity(context: Context): Activity? = when(context) {
        is Activity->context
        is ContextWrapper->activity(context.baseContext)
        else->null
    }
    @Composable private fun Label(text: String,secondary: Boolean=false) {
        val dark=NativeGlass.dark
        Text(text,color=Color(SquarePalette.ink(dark)).copy(alpha=if(secondary) .65f else 1f),
            fontSize=if(secondary) 14.sp else 16.sp,fontFamily=SquareTypography.family)
    }
    @Composable private fun Section(text: String) {
        Box(Modifier.fillMaxWidth().padding(horizontal=36.dp,vertical=16.dp)) { Label(text,true) }
    }
    @Composable private fun Row(label: String,value: String?=null,onClick: ()->Unit) {
        androidx.compose.foundation.layout.Row(SquareScreens.settingsRow(Modifier.fillMaxWidth())
            .clickable(role=Role.Button,onClick=onClick).padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Label(label); if(value!=null) { Spacer(Modifier.height(4.dp)); Label(value,true) } }
            Label("›",true)
        }
    }
    @Composable private fun Toggle(label: String,key: String,fallback: Boolean=true) {
        DesignState.revision
        val checked=SquareSettings.flag(key,fallback)
        val dark=NativeGlass.dark; val ink=Color(SquarePalette.ink(dark))
        androidx.compose.foundation.layout.Row(SquareScreens.settingsRow(Modifier.fillMaxWidth())
            .clickable(role=Role.Switch) { SquareSettings.put(key,!checked) }.padding(horizontal=20.dp,vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(end=12.dp)) { Label(label) }
            Switch(checked,onCheckedChange={SquareSettings.put(key,it)},colors=SwitchDefaults.colors(
                checkedThumbColor=ink,checkedTrackColor=ink.copy(alpha=.30f),
                uncheckedThumbColor=ink.copy(alpha=.65f),uncheckedTrackColor=ink.copy(alpha=.10f),
                uncheckedBorderColor=ink.copy(alpha=.25f)))
        }
    }
    @Composable private fun Choice(label: String,values: List<String>,selected: Int,select: (Int)->Unit) {
        var expanded by remember { mutableStateOf(false) }
        Row(label,values[selected.coerceIn(0,values.size-1)]) { expanded=!expanded }
        if(expanded) Column(SquareScreens.settingsRow(Modifier.fillMaxWidth()).padding(horizontal=16.dp)) {
            values.forEachIndexed { index,text ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().selectable(index==selected,role=Role.RadioButton) {
                    expanded=false; select(index)
                }.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(index==selected,onClick=null,colors=RadioButtonDefaults.colors(
                        selectedColor=Color(SquarePalette.ink(NativeGlass.dark)),unselectedColor=Color(SquarePalette.ink(NativeGlass.dark)).copy(alpha=.55f)))
                    Box(Modifier.padding(start=12.dp,top=12.dp,bottom=12.dp)) { Label(text) }
                }
            }
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Range(label: String,key: String,low: Int,high: Int,selected: Int,suffix: String) {
        var expanded by remember { mutableStateOf(false) }
        var value by remember(selected) { mutableFloatStateOf((selected-low).toFloat()/(high-low)) }
        Row(label,"$selected $suffix") { expanded=!expanded }
        if(expanded) Column(SquareScreens.settingsRow(Modifier.fillMaxWidth()).padding(horizontal=20.dp,vertical=12.dp)) {
            val interactions=remember {MutableInteractionSource()}
            val ink=Color(SquarePalette.ink(NativeGlass.dark))
            Label("${(low+value*(high-low)).roundToInt()} $suffix",true)
            Slider(value,onValueChange={value=it},interactionSource=interactions,
                colors=SliderDefaults.colors(thumbColor=ink,activeTrackColor=ink,inactiveTrackColor=ink.copy(alpha=.15f)),
                thumb={SliderDefaults.Thumb(interactions,modifier=SliderMotion.thumb(Modifier,interactions),
                    colors=SliderDefaults.colors(thumbColor=ink))},
                onValueChangeFinished={SquareSettings.put(key,(low+value*(high-low)).roundToInt())})
        }
    }
    @Composable private fun Entry() {
        DesignState.revision
        val context=LocalContext.current
        Column(Modifier.animateContentSize(if(SquareSettings.motion())LiquidMotion.shape() else snap())) {
                Section("Appearance")
                Choice("Color theme",listOf("System","Light","Dark"),SquareSettings.themeMode()) {
                    SquareSettings.put("theme",it); activity(context)?.recreate()
                }
                Choice("Glass",listOf("Liquid","Frosted","Off"),SquareSettings.preset()) { SquareSettings.preset(it) }
                Toggle("Glass navigation","nav")
                Section("Navigation tabs")
                NativeTabs.options.forEach { option ->
                    val enabled = SquareSettings.flag(option.key, true)
                    val count = NativeTabs.options.count { SquareSettings.flag(it.key, true) }
                    if (enabled && count <= 1) {
                        Row(option.label, "At least one tab must remain visible") { }
                    } else Toggle(option.label, option.key)
                }
                Toggle("Glass mini-player","mini")
                Toggle("Glass player","player")
                Toggle("Reduce motion","reduceMotion",false)
                Section("Player & Home")
                Toggle("Mini-player swipe gestures","miniGestures")
                Choice("Home shortcuts",listOf("For You, Staff Picks, Uploads","Autoplay, Rediscover, Shuffle history"),
                    SquareSettings.integer("homeActions",0,0,1)) { SquareSettings.put("homeActions",it) }
                Toggle("Learn listening habits by time of day","learnTime")
                Row("Clear listening profile","Listening history stays on this device") {
                    NativePlayback.clearHistory(); Toast.makeText(context,"Listening profile cleared",Toast.LENGTH_SHORT).show()
                }
                Section("Advanced glass")
                Range("Blur","blur",0,24,SquareSettings.blur().roundToInt(),"dp")
                Range("Surface tint","opacity",20,90,(SquareSettings.opacity()*100).roundToInt(),"%")
                Range("Refraction","lens",0,100,(SquareSettings.refraction()*100).roundToInt(),"%")
                Section("Track gestures")
                val actions=listOf("Off","Play next","Add to queue","Favorite")
                Choice("Swipe right",actions,SquareSettings.integer("swipeRight",1,0,3)) { SquareSettings.put("swipeRight",it) }
                Choice("Swipe left",actions,SquareSettings.integer("swipeLeft",2,0,3)) { SquareSettings.put("swipeLeft",it) }
                Row("Reset design") { SquareSettings.reset(); activity(context)?.recreate() }
        }
    }
}
