package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.FAVORITE_STAR
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.ArtPicture
import com.plumemu.home.CoverImages
import com.plumemu.home.FakeCoverImages
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A saved large box front of [aspect] (width over height) in the 1240 x 1080 box ScreenScraper pictures are saved in. */
internal fun savedFront(
    path: String,
    aspect: Float,
): ArtPicture {
    val fitsWidth = aspect >= 1240f / 1080
    return ArtPicture(path, if (fitsWidth) 1240 else (1080 * aspect).toInt(), if (fitsWidth) (1240 / aspect).toInt() else 1080, version = 1)
}

internal val LANDSCAPE_FRONT = savedFront("/media/1/front-landscape.webp", 1.40f)
internal val PORTRAIT_FRONT = savedFront("/media/2/front-portrait.webp", 0.58f)
internal val VERY_WIDE_FRONT = savedFront("/media/3/front-wide.webp", 3.0f)

internal fun SecondScreenContent.Game.withArt(picture: ArtPicture?) = copy(scrapeKey = "crc:1", art = picture)

/**
 * Measured: where the cover sits on the details card (layout C), what is beside and under it, and
 * every way the card falls back to none.
 */
abstract class SecondScreenBoxArtBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private var images by mutableStateOf<CoverImages>(FakeCoverImages())

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content, images = images) } }
    }

    private fun show(
        content: SecondScreenContent,
        settings: PlumThemeSettings = PlumThemeSettings.Default,
        images: CoverImages = FakeCoverImages(),
    ) {
        this.settings = settings
        this.images = images
        this.content = content
        compose.waitForIdle()
    }

    private val dimens get() = if (size == SecondSize.Roomy) SecondScreenDimens.Roomy else SecondScreenDimens.Compact

    private fun density(): Float = RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun px(dp: Dp) = dp.value * density()

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun all(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()

    private fun hasCover() = all(SecondScreenTags.COVER).isNotEmpty()

    private fun titleLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(SecondScreenTags.TITLE).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    @Test
    fun theCoverSitsTopLeftInItsSquareAtItsOwnShape() {
        val slot = px(dimens.artSlot)
        val margin = px(dimens.margin)
        for (picture in listOf(LANDSCAPE_FRONT, PORTRAIT_FRONT, VERY_WIDE_FRONT)) {
            show(DETAILS_GAME.copy(art = picture))
            val cover = bounds(SecondScreenTags.COVER)
            val label = picture.path
            assertEquals("$label left margin", margin, cover.left, 0.5f)
            assertEquals("$label top margin", margin, cover.top, 0.5f)
            assertTrue("$label inside its square: ${cover.width} x ${cover.height}", cover.width <= slot + 0.5f && cover.height <= slot + 0.5f)
            // A portrait box takes the square's height, a landscape one its width.
            if (picture.aspect < 1f) assertEquals("$label fills the height", slot, cover.height, 0.5f) else assertEquals("$label fills the width", slot, cover.width, 0.5f)
            assertEquals("$label aspect", picture.aspect, cover.width / cover.height, 0.02f)
        }
    }

    @Test
    fun theTextBesideStartsTwentyFourAfterTheCoverAtItsTop() {
        for (picture in listOf(LANDSCAPE_FRONT, PORTRAIT_FRONT)) {
            show(DETAILS_GAME.copy(art = picture))
            val cover = bounds(SecondScreenTags.COVER)
            val title = bounds(SecondScreenTags.TITLE)
            val top = bounds(SecondScreenTags.TOP)
            assertEquals("${picture.path}: the title starts 24 after the cover", px(SecondScreenDimens.artToTextGap), title.left - cover.right, 0.5f)
            assertTrue("${picture.path}: the top line is level with the cover's top (${top.top} vs ${cover.top})", top.top >= cover.top - 0.5f && top.top <= cover.top + px(SecondScreenDimens.topDot))
            val card = bounds(SecondScreenTags.CARD)
            assertTrue("${picture.path}: the title ends inside the right margin", title.right <= card.right - px(dimens.margin) + 0.5f)
        }
    }

    @Test
    fun theDescriptionIsTwentyUnderTheCoverOrTheTextBesideAndFullWidth() {
        for (picture in listOf(LANDSCAPE_FRONT, PORTRAIT_FRONT)) {
            show(DETAILS_GAME.copy(art = picture))
            val above = maxOf(bounds(SecondScreenTags.COVER).bottom, all(SecondScreenTags.FACT_LINE).maxOf { it.boundsInRoot.bottom })
            val description = bounds(SecondScreenTags.DESCRIPTION)
            assertEquals("${picture.path}: 20 under what is above", px(SecondScreenDimens.artToDescriptionGap), description.top - above, 0.5f)
            assertEquals("${picture.path}: from the left margin", px(dimens.margin), description.left, 0.5f)
        }
    }

    @Test
    fun aCoverAloneIsEnoughForTheDetailsCard() {
        // A result with a box front and no facts: the cover, the top line, the title and the played line.
        show(GAME_WITH_STAR.withDetails(EMPTY_DETAILS, LANDSCAPE_FRONT))
        assertTrue(hasCover())
        assertTrue(all(SecondScreenTags.DETAILS).isNotEmpty())
        assertTrue("no fact lines", all(SecondScreenTags.FACT_LINE).isEmpty())
        assertTrue("no description", all(SecondScreenTags.DESCRIPTION).isEmpty())
        assertEquals(bounds(SecondScreenTags.CARD).bottom - px(dimens.margin), bounds(SecondScreenTags.FACTS).bottom, 0.5f)
    }

    @Test
    fun aGameWithoutArtOrDetailsIsThePickingCardAsBefore() {
        show(GAME_WITH_STAR)
        val plain = bounds(SecondScreenTags.BLOCK)
        val plainSize = titleLayout().layoutInput.style.fontSize
        // A scraped game whose result has no box front and says nothing: a key, no picture, empty details.
        for (each in listOf(GAME_WITH_STAR.withArt(null), GAME_WITH_STAR.withDetails(EMPTY_DETAILS))) {
            show(each)
            assertFalse(hasCover())
            assertTrue("no details card", all(SecondScreenTags.DETAILS).isEmpty())
            assertEquals(plain, bounds(SecondScreenTags.BLOCK))
            assertEquals(plainSize, titleLayout().layoutInput.style.fontSize)
        }
    }

    @Test
    fun aPictureThatCannotBeReadLeavesTheCardWithoutArtAndWithoutABlankBox() {
        show(GAME_LONG_TITLE)
        val plain = bounds(SecondScreenTags.BLOCK)
        val plainSize = titleLayout().layoutInput.style.fontSize
        val unreadable = FakeCoverImages(unreadable = setOf(LANDSCAPE_FRONT.path))
        // Nothing else known: the picking card.
        show(GAME_LONG_TITLE.withArt(LANDSCAPE_FRONT), images = unreadable)
        assertFalse("no box where the cover would be", hasCover())
        assertEquals("the title is back at its size", plainSize, titleLayout().layoutInput.style.fontSize)
        assertEquals(plain, bounds(SecondScreenTags.BLOCK))
        // With details: the details card without art, its title from the left margin.
        show(GAME_LONG_TITLE.withDetails(FULL_DETAILS, PORTRAIT_FRONT), images = FakeCoverImages(unreadable = setOf(PORTRAIT_FRONT.path)))
        assertFalse(hasCover())
        assertTrue(all(SecondScreenTags.DETAILS).isNotEmpty())
        assertEquals(px(dimens.margin), bounds(SecondScreenTags.TITLE).left, 0.5f)
    }

    @Test
    fun aSlowPictureNeverHoldsBackTheTextAndItsPlaceIsKept() {
        show(DETAILS_GAME.copy(art = LANDSCAPE_FRONT), images = FakeCoverImages(pending = setOf(LANDSCAPE_FRONT.path)))
        // The text is there, the cover's place is reserved at its shape, nothing is drawn in it yet.
        assertEquals(GAME_WITH_STAR.title + " " + FAVORITE_STAR, compose.onNodeWithTag(SecondScreenTags.TITLE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""))
        val cover = bounds(SecondScreenTags.COVER)
        assertEquals(LANDSCAPE_FRONT.aspect, cover.width / cover.height, 0.01f)
    }

    @Test
    fun theCoverIsDecodedAtTheSizeItIsDrawn() {
        val images = FakeCoverImages()
        show(DETAILS_GAME.copy(art = LANDSCAPE_FRONT), images = images)
        val cover = bounds(SecondScreenTags.COVER)
        val (path, w, h) = images.decoded.last()
        assertEquals(LANDSCAPE_FRONT.path, path)
        assertEquals(cover.width, w.toFloat(), 1f)
        assertEquals(cover.height, h.toFloat(), 1f)
    }

    @Test
    fun theCoverStaysInItsSquareAtEveryTextSize() {
        for (textSize in TextSize.entries) {
            show(PORTRAIT_GAME.copy(art = PORTRAIT_FRONT), PlumThemeSettings.Default.copy(textSize = textSize))
            val cover = bounds(SecondScreenTags.COVER)
            assertEquals("$textSize: top margin", px(dimens.margin), cover.top, 0.5f)
            assertTrue("$textSize: inside its square", cover.height <= px(dimens.artSlot) + 0.5f)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBoxArtRoomyTest : SecondScreenBoxArtBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBoxArtCompactTest : SecondScreenBoxArtBase(SecondSize.Compact)
