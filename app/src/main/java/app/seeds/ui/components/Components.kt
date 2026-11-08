package app.seeds.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.seeds.R
import app.seeds.data.db.Session
import app.seeds.data.db.SessionKind
import app.seeds.domain.Overview
import app.seeds.ui.theme.CardShape
import app.seeds.ui.theme.Dimens
import app.seeds.ui.theme.Ink
import app.seeds.ui.theme.Jakarta
import app.seeds.ui.theme.Pill
import app.seeds.ui.theme.SeedIcons
import app.seeds.ui.theme.SeedPalette
import app.seeds.ui.theme.SeedsTheme
import app.seeds.ui.theme.SeedsType
import app.seeds.ui.theme.seedTone
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Time
// ---------------------------------------------------------------------------------------------

@Composable
fun rememberNow(periodMs: Long = 1000): State<Long> = produceState(System.currentTimeMillis(), periodMs) {
    while (true) {
        value = System.currentTimeMillis()
        delay(periodMs - value % periodMs)
    }
}

// ---------------------------------------------------------------------------------------------
// Brand
// ---------------------------------------------------------------------------------------------

@Composable
fun SeedMark(size: Dp, tint: Color = SeedsTheme.colors.accent, modifier: Modifier = Modifier) {
    Icon(painterResource(R.drawable.ic_seeds_mark), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun Wordmark(size: Dp = 22.dp, color: Color = SeedsTheme.colors.ink) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SeedMark(size)
        Spacer(Modifier.width(size * 0.3f))
        Text(
            "seeds",
            style = TextStyle(fontFamily = Jakarta, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = (size.value * 0.95f).sp, letterSpacing = (-0.04f * size.value).sp),
            color = color,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------

@Composable
fun SeedsCard(
    modifier: Modifier = Modifier,
    color: Color = SeedsTheme.colors.surface,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    Column(
        modifier
            .scale(scale)
            .clip(CardShape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = SeedsType.overline, color = SeedsTheme.colors.inkMuted, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = SeedsType.label, color = SeedsTheme.colors.accent, modifier = Modifier.clip(Pill).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

@Composable
fun ScreenTitle(overline: String?, title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (overline != null) Text(overline, style = SeedsType.label, color = SeedsTheme.colors.inkMuted)
            Text(title, style = SeedsType.headline, color = SeedsTheme.colors.ink)
        }
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

enum class ButtonStyle { Primary, Secondary, Ghost, Danger, Sprout }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val c = SeedsTheme.colors
    val haptics = LocalHapticFeedback.current
    val (bg, fg) = when (style) {
        ButtonStyle.Primary -> c.accent to c.onAccent
        ButtonStyle.Sprout -> c.sprout to c.onSprout
        ButtonStyle.Secondary -> c.surfaceHi to c.ink
        ButtonStyle.Ghost -> Color.Transparent to c.ink
        ButtonStyle.Danger -> c.drift.copy(alpha = 0.14f) to c.drift
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "btn")
    Row(
        modifier
            .scale(scale)
            .heightIn(min = height)
            .clip(Pill)
            .background(if (enabled) bg else c.surfaceHi)
            .then(if (style == ButtonStyle.Ghost) Modifier.border(1.dp, c.line, Pill) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) fg else c.inkFaint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = SeedsType.bodyStrong, color = if (enabled) fg else c.inkFaint, maxLines = 1)
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color? = null) {
    val c = SeedsTheme.colors
    Row(
        modifier
            .clip(Pill)
            .background(if (selected) (tint ?: c.accent) else c.surfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) (if (tint != null) Ink else c.onAccent) else c.ink
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = SeedsType.label, color = fg, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------------------------

@Composable
fun Ring(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 6.dp,
    track: Color = SeedsTheme.colors.line,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow), label = "ring")
    Canvas(modifier) {
        val s = stroke.toPx()
        val d = size.minDimension - s
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
        if (p > 0f) drawArc(color, -90f, 360f * p.coerceAtLeast(0.015f), false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
    }
}

/** A seed's face: ring of progress around its icon. When grown, it fills in and blooms. */
@Composable
fun SeedGlyph(icon: String, color: Int, progress: Float, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val tone = seedTone(color)
    val grown = progress >= 1f
    val bloom by animateFloatAsState(if (grown) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "bloom")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Ring(progress, tone, Modifier.size(size), stroke = (size.value / 13).dp)
        Box(
            Modifier
                .size(size * (0.64f + 0.36f * bloom))
                .clip(CircleShape)
                .background(if (grown) SeedPalette.fillColor(color) else tone.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (grown && size < 40.dp) Icons.Rounded.Check else SeedIcons.of(icon),
                null,
                tint = if (grown) Ink else tone,
                modifier = Modifier.size(size * 0.4f),
            )
        }
    }
}

@Composable
fun Meter(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    marker: Float? = null,
    track: Color = SeedsTheme.colors.line,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow), label = "meter")
    val markerColor = SeedsTheme.colors.ink
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (p > 0f) drawRoundRect(color, size = Size((size.width * p).coerceAtLeast(size.height), size.height), cornerRadius = r)
        marker?.let { m ->
            val x = size.width * m.coerceIn(0f, 1f)
            drawLine(markerColor.copy(alpha = 0.6f), Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Wake → sleep as a single line, painted with what actually happened. */
@Composable
fun DayStrip(o: Overview, seedColor: (Long?) -> Int?, modifier: Modifier = Modifier) {
    val c = SeedsTheme.colors
    val dark = c.isDark
    val span = (o.sleepAt - o.wakeAt).coerceAtLeast(1)
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp)) {
            val w = maxWidth
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val r = CornerRadius(8.dp.toPx())
                drawRoundRect(c.surfaceHi, cornerRadius = r)
                fun x(t: Long) = (size.width * ((t - o.wakeAt).toFloat() / span)).coerceIn(0f, size.width)
                o.todaySessions.forEach { s: Session ->
                    val x0 = x(s.startedAt)
                    val x1 = x(s.endOr(o.now).coerceAtMost(o.now))
                    if (x1 - x0 < 0.5f) return@forEach
                    val col = when (s.kind) {
                        SessionKind.SEED -> seedColor(s.seedId)?.let { Color(if (dark) SeedPalette.fill(it) else SeedPalette.deep(it)) } ?: c.inkMuted
                        SessionKind.BREAK -> c.inkFaint
                        SessionKind.DRIFT -> c.drift
                    }
                    drawRoundRect(col, Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(3f), size.height), CornerRadius(4.dp.toPx()))
                }
                if (o.now in o.wakeAt..o.sleepAt) {
                    val nx = x(o.now)
                    drawLine(c.ink, Offset(nx, -4f), Offset(nx, size.height + 4f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            if (o.now in o.wakeAt..o.sleepAt) {
                val frac = (o.now - o.wakeAt).toFloat() / span
                Box(Modifier.offset(x = w * frac - 3.dp, y = (-6).dp).size(6.dp).clip(CircleShape).background(c.ink))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(app.seeds.domain.Fmt.clock(o.wakeAt), style = SeedsType.caption, color = c.inkFaint, modifier = Modifier.weight(1f))
            Text(app.seeds.domain.Fmt.clock(o.sleepAt), style = SeedsType.caption, color = c.inkFaint)
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = SeedsTheme.colors.ink, sub: String? = null) {
    Column(modifier) {
        Text(label.uppercase(), style = SeedsType.overline, color = SeedsTheme.colors.inkMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = SeedsType.number, color = valueColor, maxLines = 1)
        if (sub != null) Text(sub, style = SeedsType.caption, color = SeedsTheme.colors.inkFaint, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val c = SeedsTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceHi), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.ink, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = SeedsType.bodyStrong, color = c.ink)
            if (subtitle != null) Text(subtitle, style = SeedsType.caption, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    val c = SeedsTheme.colors
    SettingRow(title, subtitle, icon, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent, checkedTrackColor = c.accent,
                uncheckedThumbColor = c.inkMuted, uncheckedTrackColor = c.surfaceHi, uncheckedBorderColor = c.line,
            ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Dialogs & sheets
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedsSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = SeedsTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
        contentColor = c.ink,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(40.dp, 4.dp).clip(Pill).background(c.line)) },
    ) {
        Column(Modifier.padding(horizontal = Dimens.gutter).padding(bottom = 28.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, minuteOfDay: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = SeedsTheme.colors
    val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bg,
        title = { Text(title, style = SeedsType.title) },
        text = {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.surfaceHi, selectorColor = c.accent, timeSelectorSelectedContainerColor = c.accentSoft,
                    timeSelectorUnselectedContainerColor = c.surfaceHi, timeSelectorSelectedContentColor = c.ink,
                    timeSelectorUnselectedContentColor = c.ink, clockDialSelectedContentColor = c.onAccent, clockDialUnselectedContentColor = c.ink,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Set", color = c.accent, style = SeedsType.bodyStrong) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.inkMuted, style = SeedsType.bodyStrong) } },
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SeedMark(36.dp, SeedsTheme.colors.inkFaint)
        Spacer(Modifier.height(12.dp))
        Text(title, style = SeedsType.subtitle, color = SeedsTheme.colors.ink)
        Spacer(Modifier.height(4.dp))
        Text(body, style = SeedsType.body, color = SeedsTheme.colors.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))
