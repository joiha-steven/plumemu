package com.plumemu.setup

import com.plumemu.account.AccountStatus
import com.plumemu.account.FakeScraperAccounts
import com.plumemu.account.SignInForm
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInResult
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Setup's "Box art from ScreenScraper" step: only in a build with developer
 * credentials, after the folder step (and the second screen question), while the scan runs; the
 * question first (Use my account / Not now), the form only after Use my account; every sign-in
 * outcome; Not now, Skip, Continue and Back; gamepad and touch; the library step's box art line.
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

    /** Continue on the folders, then Use my account (focused) on the question. */
    private fun TestScope.openForm(model: SetupModel) {
        press(model, Action.Enter)
        assertNull("the question first", model.state.value.boxArt)
        press(model, Action.Enter)
    }

    /** Both fields filled, then down to Sign in and A (A on a field only opens its keyboard). */
    private fun TestScope.typeAndSignIn(model: SetupModel) {
        val form = model.form()
        form.onUsernameChange("plumtester")
        form.onPasswordChange("hunter2")
        press(model, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)
    }

    @Test
    fun withoutDeveloperCredentialsThereIsNoStepAndNoDot() = runTest {
        val accounts = FakeScraperAccounts(isAvailable = false)
        val model = modelWithAFolder(accounts)
        assertEquals(2, model.state.value.stepDots)
        press(model, Action.Enter)
        assertEquals(SetupStep.Scan, model.state.value.step)
        assertNull(model.state.value.boxArt)
        assertTrue("nothing asked", accounts.signIns.isEmpty())
    }

    @Test
    fun continueOpensTheStepWhileTheScanRunsWithItsOwnDot() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        assertEquals("folder, box art, library", 3, model.state.value.stepDots)
        press(model, Action.Enter)
        val state = model.state.value
        assertEquals(SetupStep.BoxArt, state.step)
        assertEquals(1, state.stepDotIndex)
        assertTrue("the scan was asked for", source.scanRequests > 0)
        assertNull("it asks first: no form", state.boxArt)
        assertEquals("Use my account focused, like the second screen's Use it", SetupControl.UseBoxArt, state.focus)
        assertEquals(listOf(SetupControl.UseBoxArt, SetupControl.SkipBoxArt), state.controls)

        press(model, Action.Enter)
        assertEquals("the form shares the step's dot", 1, model.state.value.stepDotIndex)
        assertEquals(SignInItem.Username, model.form().focus)
        assertFalse("the cursor rests on the username with the keyboard down", model.form().inField)
    }

    @Test
    fun theQuestionsTwoButtonsWalkLeftRightUpAndDown() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        press(model, Action.Enter)
        press(model, Action.Move(Direction.Left))
        assertEquals("nothing left of Use my account", SetupControl.UseBoxArt, model.state.value.focus)
        press(model, Action.Move(Direction.Right))
        assertEquals(SetupControl.SkipBoxArt, model.state.value.focus)
        press(model, Action.Move(Direction.Right))
        assertEquals("nothing right of Not now", SetupControl.SkipBoxArt, model.state.value.focus)
        press(model, Action.Move(Direction.Left))
        assertEquals(SetupControl.UseBoxArt, model.state.value.focus)
        press(model, Action.Move(Direction.Down))
        assertEquals(SetupControl.SkipBoxArt, model.state.value.focus)
        press(model, Action.Move(Direction.Up))
        assertEquals(SetupControl.UseBoxArt, model.state.value.focus)
    }

    @Test
    fun notNowGoesOnToTheScanWithNoFormAndNoLine() = runTest {
        val accounts = FakeScraperAccounts()
        val model = modelWithAFolder(accounts)
        press(model, Action.Enter, Action.Move(Direction.Right), Action.Enter)
        assertEquals(SetupStep.Scan, model.state.value.step)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertFalse("no box art line after Not now", model.state.value.isBoxArtComing)
        assertTrue(accounts.signIns.isEmpty())
    }

    @Test
    fun aTapAnswersEitherWayInOnePress() = runTest {
        val tapUse = modelWithAFolder(FakeScraperAccounts())
        press(tapUse, Action.Enter, Action.TapItem(Column.Games, 0))
        assertEquals(SignInItem.Username, tapUse.form().focus)

        val tapNotNow = modelWithAFolder(FakeScraperAccounts())
        press(tapNotNow, Action.Enter, Action.TapItem(Column.Games, 1))
        assertEquals(SetupStep.Scan, tapNotNow.state.value.step)
    }

    @Test
    fun backFromTheQuestionGoesToTheFoldersAndFromTheFormToTheQuestion() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        // Android's keyboard: Back while typing leaves the form at once (Plumemu's tray goes down first, SetupOwnKeyboardTest).
        model.keyboardChoice = KeyboardChoice.Android
        openForm(model)
        val form = model.form()
        form.onUsernameChange("plumtester")
        press(model, Action.Enter)
        assertTrue(form.inField)
        press(model, Action.Back)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        assertNull("back to the question", model.state.value.boxArt)
        assertEquals(SetupControl.UseBoxArt, model.state.value.focus)

        press(model, Action.Enter)
        assertEquals("the name typed is still there", "plumtester", model.form().username)
        assertFalse("and the keyboard does not come back by itself", model.form().inField)

        press(model, Action.Back, Action.Back)
        assertEquals(SetupStep.Folders, model.state.value.step)
    }

    @Test
    fun aSignInThatWorksShowsTheNameThenContinueGoesOnToTheLibrary() = runTest {
        val accounts = FakeScraperAccounts()
        val model = modelWithAFolder(accounts)
        openForm(model)
        typeAndSignIn(model)
        assertEquals(listOf("plumtester" to "hunter2"), accounts.signIns)
        assertEquals("plumtester", model.form().signedInAs)
        assertEquals("it stays to show the name", SetupStep.BoxArt, model.state.value.step)

        press(model, Action.Enter)
        assertEquals(SetupStep.Scan, model.state.value.step)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertTrue("the library step says box art is coming", model.state.value.isBoxArtComing)
    }

    @Test
    fun theLibraryWaitsWhileThePersonIsSigningIn() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        press(model, Action.Enter)
        source.endScan()
        runCurrent()
        assertEquals("the scan ended, the question stays", SetupStep.BoxArt, model.state.value.step)
        press(model, Action.Enter)
        assertEquals("the form stays too", SetupStep.BoxArt, model.state.value.step)
        press(model, Action.TapItem(Column.Games, 3))
        assertEquals("Skip: straight to the library", SetupStep.Ready, model.state.value.step)
        assertFalse("no box art line after Skip", model.state.value.isBoxArtComing)
    }

    @Test
    fun everyFailureIsShownAndTheStepStays() = runTest {
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
        assertEquals("signed in: Back goes to the folders, not to the question", SetupStep.Folders, model.state.value.step)
    }

    @Test
    fun skipByGamepadGoesOnWithoutAnAccount() = runTest {
        val accounts = FakeScraperAccounts()
        val model = modelWithAFolder(accounts)
        openForm(model)
        press(model, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Right))
        assertEquals(SignInItem.Leave, model.form().focus)
        press(model, Action.Enter)
        assertEquals(SetupStep.Scan, model.state.value.step)
        assertTrue(accounts.signIns.isEmpty())
    }

    @Test
    fun touchTypesInTheFieldsAndPressesSignIn() = runTest {
        val accounts = FakeScraperAccounts()
        val model = modelWithAFolder(accounts)
        press(model, Action.Enter, Action.TapItem(Column.Games, 0))
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
    fun aStoredAccountOpensTheStepSignedIn() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts(initial = AccountStatus.SignedIn("plumtester")))
        press(model, Action.Enter)
        assertEquals("no question with an account stored", "plumtester", model.form().signedInAs)
        assertEquals(SignInItem.Continue, model.form().focus)
        press(model, Action.Back)
        assertEquals("Back there goes to the folders", SetupStep.Folders, model.state.value.step)

        press(model, Action.Enter, Action.Enter)
        assertEquals(SetupStep.Scan, model.state.value.step)
        source.endScan()
        runCurrent()
        assertTrue("the account is stored: the library step says box art is coming", model.state.value.isBoxArtComing)
    }

    @Test
    fun aRefusedAccountOpensTheFormWithItsNameAndBackGoesToTheQuestion() = runTest {
        val accounts = FakeScraperAccounts(initial = AccountStatus.SignedIn("plumtester", isRefused = true))
        val model = modelWithAFolder(accounts)
        press(model, Action.Enter)
        val form = model.form()
        assertNull("a refused password is not signed in", form.signedInAs)
        assertEquals("the name is filled in, to sign in again", "plumtester", form.username)
        assertFalse(form.inField)

        press(model, Action.Back)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        assertNull("Back: the question", model.state.value.boxArt)
        press(model, Action.Move(Direction.Right), Action.Enter)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertFalse("a refused account downloads nothing: no line", model.state.value.isBoxArtComing)
    }

    @Test
    fun aSignInCountsFromItsAnswerEvenBeforeTheStoredStatusShowsIt() = runTest {
        val accounts = FakeScraperAccounts().apply { publishesStatusLate = true }
        val model = modelWithAFolder(accounts)
        openForm(model)
        typeAndSignIn(model)
        assertEquals("plumtester", model.form().signedInAs)
        assertEquals("the stored status has not caught up", AccountStatus.SignedOut, accounts.status.value)

        press(model, Action.Back, Action.Enter)
        assertEquals("still signed in, no question", "plumtester", model.form().signedInAs)
        assertEquals(SignInItem.Continue, model.form().focus)
        press(model, Action.Enter)
        source.endScan()
        runCurrent()
        assertTrue("the line shows before the status does", model.state.value.isBoxArtComing)
    }

    @Test
    fun afterASignInInSetupTheQuestionIsNotAskedAgainAndTheLineShows() = runTest {
        val model = modelWithAFolder(FakeScraperAccounts())
        openForm(model)
        typeAndSignIn(model)
        assertEquals("plumtester", model.form().signedInAs)

        press(model, Action.Back)
        assertEquals("Back on Signed in as … goes to the folders", SetupStep.Folders, model.state.value.step)
        press(model, Action.Enter)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        assertEquals("no question: Signed in as … and Continue", "plumtester", model.form().signedInAs)
        assertEquals(SignInItem.Continue, model.form().focus)

        press(model, Action.Enter)
        source.endScan()
        runCurrent()
        assertEquals(SetupStep.Ready, model.state.value.step)
        assertTrue(model.state.value.isBoxArtComing)
    }

    @Test
    fun afterTheSecondScreenQuestionComesBoxArtThenTheScan() = runTest {
        source.secondScreenQuestion.value = ScreenKind.BuiltIn
        val model = modelWithAFolder(FakeScraperAccounts())
        press(model, Action.Enter)
        assertEquals(SetupStep.SecondScreen, model.state.value.step)
        assertEquals("folder, second screen, box art, library", 4, model.state.value.stepDots)
        press(model, Action.Enter)
        assertEquals(SetupStep.BoxArt, model.state.value.step)
        assertEquals(2, model.state.value.stepDotIndex)
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
    }
}
