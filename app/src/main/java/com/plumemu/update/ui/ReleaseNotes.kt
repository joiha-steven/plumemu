package com.plumemu.update.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.settings.screen.glideSpec
import com.plumemu.update.SectionKind
import com.plumemu.update.UpdateManifest

/**
 * A release's notes as the update panel (Settings) and the What's new card (Home) both show them: grouped under New, Improved,
 * Fixed and Known issues when the release has `sections`, else its `notes` as bullets alone; an accent dot in a fixed column with
 * the text hanging beside it; scrolled by touch and by [scroll]'s presses; the bottom edge fading while more is below.
 * [tags] and [lineModifier] let each screen keep its own test tags and text-line alignment.
 */
@Composable
internal fun ScrollingReleaseNotes(
    release: UpdateManifest,
    scroll: NotesScroll,
    tags: ReleaseNotesTags,
    modifier: Modifier = Modifier,
    lineModifier: Modifier = Modifier,
) {
    val state = rememberScrollState()
    // The model cannot know the notes' size: it asks, so a press that cannot scroll stays silent.
    SideEffect { scroll.canScroll = { down -> if (down) state.canScrollForward else state.canScrollBackward } }
    val presses = scroll.presses
    val motion = PlumTheme.motion
    val isMotionOn = motion.isOn
    val stepPx = with(LocalDensity.current) { (PlumTheme.layout.rowHeight * SCROLL_ROWS).toPx() }
    var applied by remember { mutableIntStateOf(presses) }
    LaunchedEffect(presses) {
        val delta = presses - applied
        applied = presses
        val target = (state.value + delta * stepPx).toInt().coerceIn(0, state.maxValue)
        if (delta != 0) if (isMotionOn) state.animateScrollTo(target, motion.glideSpec()) else state.scrollTo(target)
    }
    Column(
        modifier.fadeWhenMoreBelow(state).verticalScroll(state).testTag(tags.notes),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap),
    ) {
        if (release.sections.isEmpty()) {
            for (note in release.notes) NoteBullet(note, tags, lineModifier)
        } else {
            for (section in release.sections) {
                Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap)) {
                    BasicText(
                        stringResource(sectionHeading(section.kind)),
                        style = PlumTheme.type.meta.copy(color = PlumTheme.colors.meta),
                        modifier = lineModifier.testTag(tags.heading),
                    )
                    for (item in section.items) NoteBullet(item, tags, lineModifier)
                }
            }
        }
    }
}

/** The test tags of one use of [ScrollingReleaseNotes]. */
internal class ReleaseNotesTags(
    val notes: String,
    val heading: String,
    val note: String,
    val dot: String,
)

/** One note: the dot in a column of [PlumDimens.settingsNoteIndent], the text beside it wrapping under the text, not under the dot. */
@Composable
private fun NoteBullet(
    text: String,
    tags: ReleaseNotesTags,
    lineModifier: Modifier,
) {
    val style = PlumTheme.type.panelBody
    val textColor = PlumTheme.colors.text2
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Row(lineModifier.testTag(tags.note)) {
        Box(Modifier.width(PlumDimens.settingsNoteIndent).padding(top = (lineHeight - PlumDimens.settingsNoteDot) / 2)) {
            Box(Modifier.size(PlumDimens.settingsNoteDot).clip(CircleShape).background(PlumTheme.colors.accent).testTag(tags.dot))
        }
        BasicText(text, style = style, color = { textColor }, modifier = Modifier.weight(1f))
    }
}

/**
 * Fades the bottom edge into the background while [scroll] can go further, so a cut-off heading reads as "more below";
 * nothing at the end. Everything is read while drawing.
 */
private fun Modifier.fadeWhenMoreBelow(scroll: ScrollState): Modifier =
    composed {
        val colors = PlumTheme.colors
        val fade = PlumDimens.settingsNoteFade
        graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
            drawContent()
            if (scroll.canScrollForward) {
                val height = fade.toPx().coerceAtMost(size.height)
                val top = size.height - height
                val clear = colors.bg.copy(alpha = 0f)
                drawRect(Brush.verticalGradient(listOf(clear, colors.bg), startY = top, endY = size.height), Offset(0f, top), Size(size.width, height), blendMode = BlendMode.DstOut)
            }
        }
    }

private fun sectionHeading(kind: SectionKind): Int =
    when (kind) {
        SectionKind.New -> R.string.settings_update_notes_new
        SectionKind.Improved -> R.string.settings_update_notes_improved
        SectionKind.Fixed -> R.string.settings_update_notes_fixed
        SectionKind.Known -> R.string.settings_update_notes_known
    }

private const val SCROLL_ROWS = 3
