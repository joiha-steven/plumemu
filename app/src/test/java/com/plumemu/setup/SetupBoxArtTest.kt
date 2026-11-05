package com.plumemu.setup

import com.plumemu.account.AccountStatus
import com.plumemu.account.FakeScraperAccounts
import com.plumemu.account.SignInForm
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInResult
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.secondscreen.ScreenKind
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Setup's box art step: three choices (two without ScreenScraper keys) after the look step, while the
 * scan runs; each writes the setting Settings › Accounts › Box art writes; the sign-in form opens only
 * on "Box art and game info" without a usable account; every sign-in outcome; Skip, Continue and Back.
 */
class SetupBoxArtTest {
    private val source = FakeSetupSource()

    private fun TestScope.modelWithAFolder(accounts: FakeScraperAccounts): SetupModel {
        val model = SetupModel(backgroundScope, source, accounts) { }
        backgroundScope.launch { model.state.collect { } }
        runCurrent()
        model.onPicked(PickResult.Picked(TREE))
        runCurrent()
        return model
    }

    private fun TestScope.press(
        model: SetupModel,
        vararg actions: Action,
    ) {
        for (action in actions) {
            model.onAction(action)
            runCurrent()
        }
    }

    private fun SetupModel.form(): SignInForm = requireNotNull(state.value.boxArt) { "the box art step shows its form" }

    /** Continue on the folders, Enter on the look step's tile: the box art choices. */
    private fun TestScope.toChoices(model: SetupModel) {
        press(model, Action.Enter, Action.Enter)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        assertNull("the choices first", model.state.value.boxArt)
    }

    /**
     * To the choices, then "Box art and game info" (focused): the form opens on the username with the keyboard down, A brings it up.
     * With Plumemu's keyboard B then puts the tray away and the form stays; [keepTyping] leaves it up.
     */
    private fun TestScope.openForm(
        model: SetupModel,
        keepTyping: Boolean = false,
    ) {
        toChoices(model)
        press(model, Action.Enter)
        val form = model.form()
        assertEquals("the cursor opens on the empty username", SignInItem.Username, form.focus)
        assertFalse("the form opens with the keyboard down", form.isTyping)
        press(model, Action.Enter)
        assertTrue("A on the username brings the keyboard up", form.inField)
        if (keepTyping) return
        press(model, Action.Back)
        assertSame("B puts the tray away and the form stays", form, model.state.value.boxArt)
        assertFalse(form.isTyping)
        assertEquals(SignInItem.Username, form.focus)
    }

    /** Both fields filled, then down to Sign in and A (A on a field only opens its keyboard). */
    private fun TestScope.typeAndSignIn(model: SetupModel) {
        val form = model.form()
        form.onUsernameChange("plumtester")
        form.onPasswordChange("hunter2")
        press(model, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)
    }

    @Test
    fun withoutDeveloperKeysThereAreTwoChoicesAndNoAccountIsAsked() = runTest {
        val accounts = FakeScraperAccounts(isAvailable = false)
        val model = modelWithAFolder(accounts)
        toChoices(model)
        assertEquals(listOf(SetupControl.BoxArtLibretro, SetupControl.BoxArtOwnCovers), model.state.value.controls)
        assertEquals("Box art only is focused", SetupControl.BoxArtLibretro, model.state.value.focus)
        press(model, Action.Enter)
        assertEquals(SetupStep.Scan, model.state.value.step)
        assertEquals(BoxArtSourceSetting.LibretroOnly, source.boxArtSource.value)
        assertTrue("nothing asked", accounts.signIns.isEmpty())
    }

    @Test
    fun theStepHasThreeChoicesAndItsOwnDot() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        assertEquals("folder, look, box art, library", 4, model.state.value.stepDots)
        toChoices(model)
        val state = model.state.value
        assertEquals(2, state.stepDotIndex)
        assertTrue("the scan was asked for", source.scanRequests > 0)
        assertEquals(SetupControl.UseBoxArt, state.focus)
        assertEquals(listOf(SetupControl.UseBoxArt, SetupControl.BoxArtLibretro, SetupControl.BoxArtOwnCovers), state.controls)
    }

    @Test
    fun theChoicesWalkUpAndDownAndStopAtTheEnds() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        toChoices(model)
        press(model, Action.Move(Direction.Up))
        assertEquals(SetupControl.UseBoxArt, model.state.value.focus)
        press(model, Action.Move(Direction.Down))
        assertEquals(SetupControl.BoxArtLibretro, model.state.value.focus)
        press(model, Action.Move(Direction.Down), Action.Move(Direction.Down))
        assertEquals(SetupControl.BoxArtOwnCovers, model.state.value.focus)
        press(model, Action.Move(Direction.Up))
        assertEquals(SetupControl.BoxArtLibretro, model.state.value.focus)
    }

    @Test
    fun boxArtOnlyWritesLibretroOnlyAndGoesOnToTheScan() = runTest {
        val accounts = FakeScraperAccounts()
        val model = modelWithAFolder(accounts)
        toChoices(model)
        press(model, Action.Move(Direction.Down), Action.Enter)
        assertEquals(BoxArtSourceSetting.LibretroOnly, source.boxArtSource.value)
        assertEquals(SetupStep.Scan, model.state.value.step)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertTrue("box art is coming", model.state.value.isBoxArtComing)
        assertTrue(accounts.signIns.isEmpty())
    }

    @Test
    fun ownCoversWritesTheSettingAndTheLibraryStepHasNoBoxArtLine() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        toChoices(model)
        press(model, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)
        assertEquals(BoxArtSourceSetting.OwnCovers, source.boxArtSource.value)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertFalse(model.state.value.isBoxArtComing)
    }

    @Test
    fun aTapAnswersInOnePress() = runTest {
        val tapOwn = modelWithAFolder(FakeScraperAccounts())
        toChoices(tapOwn)
        press(tapOwn, Action.TapItem(Column.Games, 2))
        assertEquals(BoxArtSourceSetting.OwnCovers, source.boxArtSource.value)
        assertEquals(SetupStep.Scan, tapOwn.state.value.step)

        val tapUse = modelWithAFolder(FakeScraperAccounts())
        toChoices(tapUse)
        press(tapUse, Action.TapItem(Column.Games, 0))
        assertEquals(SignInItem.Username, tapUse.form().focus)
    }

    @Test
    fun aFailedWriteSaysSoAndStaysOnTheChoices() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        toChoices(model)
        source.boxArtFailure = IllegalStateException("disk")
        press(model, Action.Move(Direction.Down), Action.Enter)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        assertEquals(SetupMessage.SaveFailed, model.state.value.message)
    }

    @Test
    fun backFromTheChoicesGoesToTheLookAndFromTheFormToTheChoices() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        // Android's keyboard: Back while typing leaves the form at once (Plumemu's tray goes down first, SetupOwnKeyboardTest).
        model.keyboardChoice = KeyboardChoice.Android
        openForm(model, keepTyping = true)
        val form = model.form()
        form.onUsernameChange("plumtester")
        press(model, Action.Back)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        assertNull("back to the choices", model.state.value.boxArt)
        assertEquals(SetupControl.UseBoxArt, model.state.value.focus)
        assertNull("nothing was written", source.boxArtSource.value)

        press(model, Action.Enter)
        assertEquals("the name typed is still there", "plumtester", model.form().username)
        assertFalse("and the form opens with the keyboard down", model.form().isTyping)

        press(model, Action.Back, Action.Back)
        assertEquals(SetupStep.Look, model.state.value.step)
        press(model, Action.Back)
        assertEquals(SetupStep.Folders, model.state.value.step)
    }

    @Test
    fun aSignInThatWorksWritesScreenScraperShowsTheNameThenContinueGoesOn() = runTest {
        val accounts = FakeScraperAccounts()
        val model = modelWithAFolder(accounts)
        openForm(model)
        typeAndSignIn(model)
        assertEquals(listOf("plumtester" to "hunter2"), accounts.signIns)
        assertEquals("plumtester", model.form().signedInAs)
        assertEquals("it stays to show the name", SetupStep.BoxArt, model.state.value.step)
        assertEquals(BoxArtSourceSetting.ScreenScraper, source.boxArtSource.value)

        press(model, Action.Enter)
        assertEquals(SetupStep.Scan, model.state.value.step)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertTrue(model.state.value.isBoxArtComing)
    }

    @Test
    fun skippingTheFormGoesOnWithNothingWritten() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        toChoices(model)
        source.endScan()
        runCurrent()
        assertEquals("the scan ended, the step stays", SetupStep.BoxArt, model.state.value.step)
        press(model, Action.Enter)
        assertEquals("the form stays too", SetupStep.BoxArt, model.state.value.step)
        press(model, Action.TapItem(Column.Games, 3))
        assertEquals("Skip: on to the next step (the scan has ended)", SetupStep.Ready, model.state.value.step)
        assertNull(model.state.value.boxArt)
        assertNull("no source is stored: Plumemu's own covers until an account is usable", source.boxArtSource.value)
    }

    @Test
    fun everyFailureIsShownAndTheStepStaysWithNothingWritten() = runTest {
        val failures =
            listOf(
                SignInResult.WrongCredentials,
                SignInResult.Offline,
                SignInResult.ServerClosed,
                SignInResult.DailyLimit(86_400_000L),
                SignInResult.ServerError(503),
                SignInResult.AppRefused,
                SignInResult.NotSaved,
            )
        for (failure in failures) {
            val accounts = FakeScraperAccounts().apply { answer = { _, _ -> failure } }
            val model = modelWithAFolder(accounts)
            openForm(model)
            typeAndSignIn(model)
            assertEquals("$failure", failure, model.form().message)
            assertEquals("$failure", SetupStep.BoxArt, model.state.value.step)
            assertNull("$failure", model.form().signedInAs)
            assertNull("$failure", source.boxArtSource.value)
        }
    }

    @Test
    fun whileCheckingTheFormWaitsAndBackStillLeaves() = runTest {
        val accounts = FakeScraperAccounts().apply { holdsAnswers = true }
        val model = modelWithAFolder(accounts)
        openForm(model)
        typeAndSignIn(model)
        assertTrue(model.form().isChecking)
        press(model, Action.Enter, Action.Move(Direction.Down))
        assertEquals("one sign-in only", 1, accounts.signIns.size)
        accounts.release()
        assertEquals("plumtester", model.form().signedInAs)

        press(model, Action.Back)
        assertEquals("signed in: Back goes to the look step", SetupStep.Look, model.state.value.step)
    }

    @Test
    fun skipByGamepadGoesOnToTheScan() = runTest {
        val accounts = FakeScraperAccounts()
        val model = modelWithAFolder(accounts)
        openForm(model)
        press(model, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Right))
        assertEquals(SignInItem.Leave, model.form().focus)
        press(model, Action.Enter)
        assertEquals(SetupStep.Scan, model.state.value.step)
        assertNull(model.state.value.boxArt)
        assertNull(source.boxArtSource.value)
        assertTrue(accounts.signIns.isEmpty())
    }

    @Test
    fun touchTypesInTheFieldsAndPressesSignIn() = runTest {
        val accounts = FakeScraperAccounts()
        val model = modelWithAFolder(accounts)
        toChoices(model)
        press(model, Action.TapItem(Column.Games, 0))
        val form = model.form()
        press(model, Action.TapItem(Column.Games, 1))
        assertEquals(SignInItem.Password, form.focus)
        assertTrue("a tap on a field opens its keyboard", form.inField)
        form.onUsernameChange("plumtester")
        form.onPasswordChange("hunter2")
        press(model, Action.TapItem(Column.Games, 2))
        assertEquals(listOf("plumtester" to "hunter2"), accounts.signIns)
    }

    @Test
    fun aStoredAccountMakesTheFirstChoiceTheAnswerAtOnce() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts(initial = AccountStatus.SignedIn("plumtester")))
        toChoices(model)
        press(model, Action.Enter)
        assertEquals("no form with an account stored", SetupStep.Scan, model.state.value.step)
        assertEquals(BoxArtSourceSetting.ScreenScraper, source.boxArtSource.value)
        source.endScan()
        runCurrent()
        assertTrue(model.state.value.isBoxArtComing)
    }

    @Test
    fun aRefusedAccountOpensTheFormWithItsNameToSignInAgain() = runTest {
        val accounts = FakeScraperAccounts(initial = AccountStatus.SignedIn("plumtester", isRefused = true))
        val model = modelWithAFolder(accounts)
        toChoices(model)
        press(model, Action.Enter)
        val form = model.form()
        assertNull("a refused password is not signed in", form.signedInAs)
        assertEquals("the name is filled in", "plumtester", form.username)
        assertEquals("the cursor opens on the empty password", SignInItem.Password, form.focus)
        assertFalse("with its keyboard down", form.isTyping)
        press(model, Action.Enter)
        assertTrue("A on the password brings the keyboard up", form.inField)

        press(model, Action.Back)
        assertSame("the first B puts the tray away and the form stays", form, model.state.value.boxArt)
        press(model, Action.Back)
        assertNull("Back: the choices", model.state.value.boxArt)
        press(model, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertFalse(model.state.value.isBoxArtComing)
    }

    @Test
    fun aSignInCountsFromItsAnswerEvenBeforeTheStoredStatusShowsIt() = runTest {
        val accounts = FakeScraperAccounts().apply { publishesStatusLate = true }
        val model = modelWithAFolder(accounts)
        openForm(model)
        typeAndSignIn(model)
        assertEquals("plumtester", model.form().signedInAs)
        assertEquals("the stored status has not caught up", AccountStatus.SignedOut, accounts.status.value)

        press(model, Action.Enter)
        source.endScan()
        runCurrent()
        assertTrue("the line shows before the status does", model.state.value.isBoxArtComing)
    }

    @Test
    fun afterBoxArtComesTheSecondScreenQuestionThenTheScan() = runTest {
        source.secondScreenQuestion.value = ScreenKind.BuiltIn
        val model = modelWithAFolder(FakeScraperAccounts())
        toChoices(model)
        assertEquals("folder, look, box art, second screen, library", 5, model.state.value.stepDots)
        press(model, Action.Move(Direction.Down), Action.Enter)
        assertEquals(SetupStep.SecondScreen, model.state.value.step)
        assertEquals(3, model.state.value.stepDotIndex)
    }

    @Test
    fun aLateSignInAnswerAfterTheFormWasLeftDoesNotOverwriteAnotherChoice() = runTest {
        val accounts = FakeScraperAccounts().apply { holdsAnswers = true }
        val model = modelWithAFolder(accounts)
        openForm(model)
        typeAndSignIn(model)
        assertTrue(model.form().isChecking)
        // B leaves the form (the check goes on), then Box art only is chosen.
        press(model, Action.Back)
        assertNull("the form is left", model.state.value.boxArt)
        press(model, Action.Move(Direction.Down), Action.Enter)
        assertEquals(BoxArtSourceSetting.LibretroOnly, source.boxArtSource.value)
        accounts.release()
        runCurrent()
        assertEquals("the late answer leaves the choice", BoxArtSourceSetting.LibretroOnly, source.boxArtSource.value)
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
    }
}
