package com.plumemu.game

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.plumemu.coreui.components.focusPill
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.OptionRowLook
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.rememberListGlide
import com.plumemu.home.ArtPicture
import com.plumemu.home.KeepFocusedVisible
import com.plumemu.home.LocalHomeArt
import com.plumemu.home.fitInside
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import com.plumemu.coreinput.Column as StripColumn

/**
 * Fix box art… in the game panel, in Fix name's visual language: the field
 * ([TypingField], search key), then one list that follows the cursor: the plain line of the phase
 * ([fixBoxArtLine]) first, then the rows of [fixBoxArtRows]: Search, each ScreenScraper game
 * ([BoxArtHitRow]) and "No box art". A tap on row i sends `TapItem(Details, i)`, as the state
 * expects. The state is in [FixBoxArtState]; this only draws it.
 */
@Composable
internal fun FixBoxArtBody(
    state: FixBoxArtState,
    onAction: (Action) -> Unit,
) {
    TypingField(
        value = state.query,
        onValueChange = state::onQueryChange,
        typing = state,
        placeholder = stringResource(R.string.game_fix_box_art_prompt),
        hint = stringResource(R.string.game_name_hint),
        options = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
        onAction = onAction,
        tag = FixBoxArtTags.FIELD,
        label = stringResource(R.string.game_fix_box_art_field_label),
    )
    Spacer(Modifier.height(PlumDimens.listRowGap))
    // Search is usable again once the daily limit's time passes with the panel open: one delayed effect, no polling.
    LaunchedEffect(state.phase) { state.millisUntilSearchAllowed()?.let { delay(it); state.limitEnded() } }
    val line = fixBoxArtLine(state)
    val rows = fixBoxArtRows(state)
    val listState = rememberLazyListState()
    val glide = rememberListGlide(stride = PlumTheme.layout.rowHeight + PlumDimens.listRowGap)
    // What a store (a pick or No box art) says is drawn under the picked row, inside its list item,
    // so the focused row keeps it on screen: an error must be seen, however deep the row is. The
    // search phases' line is the list's first item (the cursor is on Search then, at the top, and
    // a long line pinned above the list would leave no room for the focused game at large sizes).
    val isStorePhase = state.phase.let { it == FixBoxArtPhase.Storing || it == FixBoxArtPhase.WaitingForPictures || it is FixBoxArtPhase.StoreFailed }
    val pickedRow = if (isStorePhase) state.pickedRow else null
    val topLine = if (pickedRow == null) line else null
    val lineItems = if (topLine == null) 0 else 1
    // The size of each thumbnail file, kept here: a row scrolled out and back draws a decoded picture at once.
    val pictures = remember(state) { mutableStateMapOf<String, ArtPicture>() }
    val pill = rememberPanelPill(listState, glide, if (state.cursor < 0) -1 else state.cursor + lineItems)
    pill.Provide {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().testTag(FixBoxArtTags.ROWS).then(pill.modifier).then(glide.modifier),
        contentPadding = PaddingValues(vertical = glide.extra),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap),
    ) {
        if (topLine != null) item(key = "line") { StatusLine(topLine, Modifier) }
        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
            val look =
                when {
                    state.cursor == index -> OptionRowLook.Focused
                    !row.isEnabled -> OptionRowLook.Disabled
                    else -> OptionRowLook.Normal
                }
            val hit = state.hitAt(index)
            val storeLine = if (index == pickedRow) line else null
            when {
                hit != null -> BoxArtHitRow(row, hit, state, pictures, storeLine, look, index, onAction)
                storeLine != null -> PanelRowView(row.copy(note = null), look, index, onAction, lead = { StatusLine(storeLine, Modifier) })
                else -> PanelRowView(row, look, index, onAction)
            }
        }
    }
    }
    KeepFocusedVisible(listState, glide, contentKey = pickedRow to line) { if (state.cursor < 0) 0 else state.cursor + lineItems }
}

/** The phase's plain line: wraps whole, never cut; it is what tells the person what happened. */
@Composable
private fun StatusLine(
    line: UiText,
    modifier: Modifier,
) {
    BasicText(
        text = line.resolve(),
        style = PlumTheme.type.smallFact.copy(color = PlumTheme.colors.muted),
        modifier = modifier.fillMaxWidth().padding(horizontal = PlumDimens.gameRowPadding, vertical = PlumDimens.factsRowGap).testTag(FixBoxArtTags.LINE),
    )
}

/**
 * One ScreenScraper game: the focus fill of an option row over a thumbnail in its slot, the name
 * (wraps to three lines) and the system and "year · publisher" on two meta lines under it, all inside the fill. The focus dot's room is kept in every row so a
 * thumbnail never moves when the cursor does. Mirrors `OptionRow` (core-ui keeps its fill and dot
 * private): change the two together.
 */
@Composable
private fun BoxArtHitRow(
    row: PanelRow,
    hit: BoxArtHit,
    state: FixBoxArtState,
    pictures: MutableMap<String, ArtPicture>,
    storeLine: UiText?,
    look: OptionRowLook,
    index: Int,
    onAction: (Action) -> Unit,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val focused = look == OptionRowLook.Focused
    val tap = remember(index, onAction) { Modifier.tapItem(StripColumn.Details, index, onAction) }
    val metaLines = if (PlumTheme.layout.textScale >= TextSize.Largest.heightScale) 2 else 1
    val (labelColor, valueColor) = if (focused) colors.strong to colors.meta else colors.text2 to colors.muted
    Column(tap.testTag(GamePanelTags.row(row.key))) {
        // A store's line leads the item: at the largest sizes the item is taller than the list, and
        // what is cut then is the end of the note, not what the person must read.
        storeLine?.let { StatusLine(it, Modifier) }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxOf(PlumTheme.layout.rowHeight, THUMBNAIL_SLOT + PlumDimens.factsRowGap * 2))
                    .focusPill(focused)
                    .testTag(FixBoxArtTags.fill(row.key))
                    .padding(horizontal = PlumDimens.gameRowPadding, vertical = PlumDimens.factsRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HitThumbnail(state.thumbnailOf(hit.id), pictures, Modifier.testTag(FixBoxArtTags.thumbnail(hit.id)))
            Spacer(Modifier.width(THUMBNAIL_GAP))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = row.label.resolve(),
                    style = (if (focused) type.settingsOptionFocused else type.settingsOption).copy(color = labelColor),
                    // As every option row: up to three lines, then cut; two while a line is shown, to leave it room.
                    maxLines = if (storeLine == null) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // The system, then "year · publisher" on its own line: both inside the fill, so the whole result is in it.
                // Each is one line, ellipsized when it cannot fit. The layout checks fail an ellipsis at every size unless a screen allows it, so at the Largest size it may wrap to two lines instead. The note is dropped while the store line shows: the room is the line's.
                row.value?.let { BasicText(text = it.resolve(), style = type.meta.copy(color = valueColor), maxLines = metaLines, overflow = TextOverflow.Ellipsis) }
                row.note?.takeIf { storeLine == null }?.let { BasicText(text = it.resolve(), style = type.meta.copy(color = valueColor), maxLines = metaLines, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/**
 * A game's thumbnail in a fixed [THUMBNAIL_SLOT] square: the file's own shape fitted inside it
 * (never cropped or stretched), the small corners of the home grid. Until the picture is decoded,
 * and for good when there is none or it cannot be read, a plain tile of the same kind stands in,
 * so a slow thumbnail changes nothing around it. The file's size is read and the picture decoded
 * at the drawn size (4a's loader) off the main thread.
 */
@Composable
private fun HitThumbnail(
    file: File?,
    pictures: MutableMap<String, ArtPicture>,
    modifier: Modifier,
) {
    val images = LocalHomeArt.current.images
    val density = LocalDensity.current
    val reading = LocalThumbnailDispatcher.current
    val known = file?.let { pictures[it.path] }
    LaunchedEffect(file) {
        if (file != null && file.path !in pictures) withContext(reading) { pictureOf(file) }?.let { pictures[file.path] = it }
    }
    val picture = known
    val shape = picture?.aspect ?: PLACEHOLDER_ASPECT
    val drawn = fitInside(shape, DpSize(THUMBNAIL_SLOT, THUMBNAIL_SLOT))
    val widthPx = with(density) { drawn.width.roundToPx() }.coerceAtLeast(1)
    val heightPx = with(density) { drawn.height.roundToPx() }.coerceAtLeast(1)
    val bitmap by produceState(picture?.let { images.cached(it, widthPx, heightPx) }, picture, widthPx, heightPx) {
        value = if (picture == null) null else images.cached(picture, widthPx, heightPx) ?: images.load(picture, widthPx, heightPx)
    }
    val corner = if (shape >= 1f) PlumDimens.cornerCoverLandscape else PlumDimens.cornerCoverPortrait
    Box(modifier.size(THUMBNAIL_SLOT), contentAlignment = Alignment.Center) {
        val image = bitmap
        val tile = Modifier.size(drawn).clip(RoundedCornerShape(corner))
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = tile.testTag(FixBoxArtTags.PICTURE))
        } else {
            Box(tile.background(PlumTheme.colors.chip).testTag(FixBoxArtTags.TILE))
        }
    }
}

/** Where a thumbnail's size is read: off the main thread; a test swaps in a dispatcher that runs at once. */
internal val LocalThumbnailDispatcher = staticCompositionLocalOf<CoroutineDispatcher> { Dispatchers.IO }

/** The saved file's size and time, read without decoding it; null (logged) when it is not a picture. */
private fun pictureOf(file: File): ArtPicture? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        Log.w(TAG, "Box art thumbnail could not be read")
        return null
    }
    return ArtPicture(file.path, bounds.outWidth, bounds.outHeight, file.lastModified())
}

/** Test tags of Fix box art. */
internal object FixBoxArtTags {
    const val FIELD = "fix_box_art_field"
    const val LINE = "fix_box_art_line"
    const val ROWS = "fix_box_art_rows"

    /** Inside a thumbnail slot: the decoded picture, or the stand-in tile. */
    const val PICTURE = "fix_box_art_picture"
    const val TILE = "fix_box_art_tile"

    /** The focus-filled part of a game row (the row without the line and the note around it). */
    fun fill(rowKey: String) = "fix_box_art_fill_$rowKey"

    fun thumbnail(hitId: String) = "fix_box_art_thumbnail_$hitId"
}

/** The thumbnail's slot (a row's 40 less its padding, 36): square, so rows are the same for a portrait box and a landscape one. */
internal val THUMBNAIL_SLOT: Dp = PlumDimens.rowHeight - PlumDimens.factsRowGap
private val THUMBNAIL_GAP: Dp = PlumDimens.focusDotGap

/** The stand-in tile's shape before the file's own is known: a portrait box. */
private const val PLACEHOLDER_ASPECT = 0.7f
private const val TAG = "PlumBoxArt"
