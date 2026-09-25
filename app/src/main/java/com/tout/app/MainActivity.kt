package com.tout.app

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ponytail: draw first frame immediately, no file/db init on start
        setContent { App() }
    }
}

private enum class Tab { Money, Food, Note }

@Composable
private fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.Money) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var amount by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    // ponytail: dial center dot jumps here — one requester shared by both first-input fields (only one is shown)
    val entryFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun save() {
        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val entry = when (tab) {
            Tab.Money -> {
                val a = amount.toDoubleOrNull()
                if (a == null) {
                    status = "Enter an amount"
                    return
                }
                Entry(date = date.toString(), type = "money", amount = a, tags = tagList)
            }
            Tab.Food -> {
                if (text.isBlank()) {
                    status = "Describe the food"
                    return
                }
                Entry(date = date.toString(), type = "food", text = text.trim(), tags = tagList)
            }
            Tab.Note -> {
                if (text.isBlank()) {
                    status = "Write something"
                    return
                }
                Entry(date = date.toString(), type = "note", text = text.trim(), tags = tagList)
            }
        }
        scope.launch(Dispatchers.IO) {
            Store.append(ctx, entry)
            withContext(Dispatchers.Main) {
                amount = ""
                text = ""
                status = "Saved ✓"
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching { Store.exportTo(ctx, uri) }
            withContext(Dispatchers.Main) { status = "Exported ✓" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val (ok, bad) = Store.importFrom(ctx, uri)
            withContext(Dispatchers.Main) { status = "Imported $ok, skipped $bad" }
        }
    }

    // ponytail: forced pure-black dark scheme, no toggle — system theme when asked
    MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
            // top bar: export/import always visible — popup menus vanish on pure black
            Row(Modifier.fillMaxWidth(), Arrangement.End) {
                TextButton(onClick = { exportLauncher.launch("tout-entries.jsonl") }) {
                    Text("Export", color = Color.Gray)
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Text("Import", color = Color.Gray)
                }
            }
            // date, defaults today, tap to change
            Row(Modifier.fillMaxWidth(), Arrangement.Center) {
                TextButton(onClick = {
                    DatePickerDialog(
                        ctx,
                        { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                        date.year, date.monthValue - 1, date.dayOfMonth
                    ).show()
                }) { Text(date.toString()) }
            }
            Spacer(Modifier.height(24.dp))
            // main input: big amount for Money, big text otherwise
            if (tab == Tab.Money) {
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    // ponytail: input matches placeholder size — M3 defaults to 16sp under a 40sp hint
                    textStyle = TextStyle(fontSize = 40.sp),
                    prefix = { Text("₹", fontSize = 40.sp) },
                    placeholder = { Text("30", fontSize = 40.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(entryFocus)
                )
            } else {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(if (tab == Tab.Food) "What did you eat?" else "What's on your mind?") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(entryFocus)
                )
            }
            Spacer(Modifier.height(16.dp))
            TextField(
                value = tags,
                onValueChange = { tags = it },
                placeholder = { Text("Tags: Cafe BBG, Samosa") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { save() }, Modifier.fillMaxWidth()) { Text("Save") }
            // ponytail: fresh check each composition — appears right after PhonePe is installed, no restart needed
            if (tab == Tab.Money && PhonePe.installed(ctx)) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { if (!PhonePe.open(ctx)) status = "Couldn't open PhonePe" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open PhonePe") }
            }
            if (status.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), Arrangement.Center) {
                    Text(status, Modifier.padding(top = 8.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            // bottom dial: draggable rotary knob, selected journal sits at top
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Dial(
                    onSelect = { tab = it },
                    onCenterTap = {
                        entryFocus.requestFocus()
                        keyboard?.show()
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ponytail: long-press shortcut unwraps to this native route (traced in PhonePe v26.08.28 smali:
// ACTION_VIEW phonepe://shortcuts + PATH -> DeepLinkHandler -> phonepe://native?id=scanQR -> scanner).
// DeepLinkHandlerActivity is exported w/ phonepe scheme, scanner activity is not — so fire the route directly.
object PhonePe {
    const val PKG = "com.phonepe.app"
    const val SCAN_URI = "phonepe://native?id=scanQR"

    fun installed(ctx: Context): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(PKG) != null

    fun scanIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(SCAN_URI)).apply { setPackage(PKG) }

    /** True if PhonePe opened (scanner ideally, home as fallback). */
    fun open(ctx: Context): Boolean = runCatching {
        try {
            ctx.startActivity(scanIntent())
        } catch (_: ActivityNotFoundException) {
            ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(PKG)!!)
        }
        true
    }.getOrDefault(false)
}

// ponytail: direct Vibrator — View haptics are swallowed when the system touch-feedback toggle is off
object Haptics {
    fun tick(ctx: Context) {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        } ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 29) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION") v.vibrate(20)
        }
    }
}

// ponytail: pure dial math, tested — snap lands the chosen journal exactly at top
object DialMath {
    // ponytail: Kotlin % keeps the sign, so wrap manually — naive % picks the wrong detent past ±630°
    fun norm(d: Float): Float = ((d + 180f) % 360f + 360f) % 360f - 180f

    fun snapDelta(base: Float, rot: Float, top: Float = -90f): Float =
        norm(top - (base + rot))

    fun nearestIndex(bases: List<Float>, rot: Float, top: Float = -90f): Int =
        bases.indices.minBy { i -> kotlin.math.abs(snapDelta(bases[i], rot, top)) }
}

// ponytail: one rotary dial, no pager/nav lib — labels ride the wheel, snap to top
@Composable
private fun Dial(onSelect: (Tab) -> Unit, onCenterTap: () -> Unit) {
    fun baseAngle(t: Tab) = when (t) {
        Tab.Money -> -90f
        Tab.Food -> 30f
        Tab.Note -> 150f
    }
    val bases = remember { Tab.entries.map { baseAngle(it) } }
    var targetRot by remember { mutableFloatStateOf(0f) }
    val rot by animateFloatAsState(targetRot, label = "dial")
    val hapticCtx = LocalContext.current
    var tickIndex by remember { mutableIntStateOf(0) } // rot=0 → Money on top

    fun tickIfChanged(i: Int) {
        if (i != tickIndex) {
            tickIndex = i
            Haptics.tick(hapticCtx)
        }
    }

    fun snap() {
        val i = DialMath.nearestIndex(bases, targetRot)
        targetRot += DialMath.snapDelta(bases[i], targetRot)
        tickIfChanged(i)
        onSelect(Tab.entries[i])
    }

    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun angleOf(p: Offset): Float {
        val c = coords ?: return 0f
        val cx = c.size.width / 2f
        val cy = c.size.height / 2f
        return Math.toDegrees(kotlin.math.atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat()
    }

    val density = LocalDensity.current
    val radiusPx = with(density) { 68.dp.toPx() }
    // ponytail: uniform labels — same size/weight, selected reads via color only
    val labelStyle = MaterialTheme.typography.bodyLarge

    Box(
        Modifier
            .size(220.dp)
            .onGloballyPositioned { coords = it }
            .pointerInput(Unit) {
                var prev = 0f
                var moved = false
                detectDragGestures(
                    onDragStart = { prev = angleOf(it) },
                    onDragEnd = { if (moved) snap() },
                ) { change, _ ->
                    val cur = angleOf(change.position)
                    var d = cur - prev
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    if (kotlin.math.abs(d) > 0.5f) moved = true
                    targetRot += d
                    prev = cur
                    tickIfChanged(DialMath.nearestIndex(bases, targetRot))
                    change.consume()
                }
            }
    ) {
        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(1.dp, Color.DarkGray),
            modifier = Modifier.fillMaxSize()
        ) {}
        // center dot: 48dp hit area, 6dp visual — taps jump to the entry field
        Box(
            Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .clickable(onClickLabel = "New entry", onClick = onCenterTap),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(6.dp).background(Color.DarkGray, CircleShape))
        }
        Tab.entries.forEach { t ->
            val a = Math.toRadians((baseAngle(t) + rot).toDouble())
            val dx = (kotlin.math.cos(a) * radiusPx).roundToInt()
            val dy = (kotlin.math.sin(a) * radiusPx).roundToInt()
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(dx, dy) }
            ) {
                TextButton(onClick = {
                    val i = Tab.entries.indexOf(t)
                    targetRot += DialMath.snapDelta(bases[i], targetRot)
                    tickIfChanged(i)
                    onSelect(t)
                }) {
                    Text(
                        when (t) {
                            Tab.Money -> "Money"
                            Tab.Food -> "Food"
                            Tab.Note -> "Journal"
                        },
                        style = labelStyle,
                        color = if (Tab.entries.indexOf(t) == tickIndex) Color.White else Color.Gray
                    )
                }
            }
        }
    }
}
