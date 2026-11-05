package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import com.plumemu.R
import com.plumemu.coreui.theme.AppsGridDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * The details column of an Apps list: the app's Android icon (about 96 dp, asked of [LocalHomeAppIcons] after the first frame, a round
 * letter tile when it has none), then its name, "Android app" and its package (the row id before its "/"). It fills the column like the other entries'
 * details, so it reaches the right margin. With the systems focused ([textHidden]) the column is mostly off screen: only the icon shows, and only if it fits.
 */
@Composable
internal fun AppDetails(
    content: HomeContent,
    app: GameRowModel,
    textHidden: Boolean,
    modifier: Modifier,
) {
    Column(modifier.testTag(HomeTags.DETAILS_APP), verticalArrangement = Arrangement.Top) {
        if (!textHidden || detailsCoverFitsWithSystemsOpen(PlumTheme.layout, content.hasBoxArt)) {
            AppGameIcon(app.iconKey, app.title, DpSize(AppsGridDimens.appGameDetailsIcon, AppsGridDimens.appGameDetailsIcon))
        }
        if (!textHidden) {
            Spacer(Modifier.height(PlumDimens.detailsBlockGapWide))
            TitleBlock(PlumDimens.titleLineGap) {
                DetailsTitle(app.title)
                FactLine(stringResource(R.string.second_screen_android_app))
                // The row's id is "<package>/<activity>" (appRowId): the package is the part before the slash; an ellipsis ends a long one.
                FactLine(app.id.substringBefore('/'), Modifier.testTag(HomeTags.DETAILS_APP_PACKAGE))
            }
        }
    }
}
