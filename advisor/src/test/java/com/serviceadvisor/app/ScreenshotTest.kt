package com.serviceadvisor.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.serviceadvisor.app.data.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app under Robolectric, checks the TPMS section only appears when the vehicle
 * has TPMS, and records screenshots of the main screens to advisor/build/outputs/roborazzi.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = compose.activity.application as ServiceAdvisorApp

    private fun waitForText(text: String) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMillis = 20_000)

    private fun click(text: String) = compose.onAllNodesWithText(text).onFirst().performClick()
    private fun back() = compose.activity.onBackPressedDispatcher.onBackPressed()

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun mainScreens() {
        runBlocking { app.settings.setTheme(ThemeMode.Light) }
        waitForText("Browse by make")
        shot("01_lookup")

        compose.onNodeWithText("Model, engine, year or Ryco no.").performTextInput("hilux")
        waitForText("2 matches")
        shot("02_search")
        click("Toyota Hilux")
        waitForText("RYCO OIL FILTER")
        shot("03_sheet_no_tpms")
        // The Hilux sample has no TPMS: the section must not be there at all.
        compose.onAllNodesWithText("TPMS RESET").assertCountEquals(0)
        back()

        compose.onNodeWithContentDescription("Clear search").performClick()
        click("Mazda")
        waitForText("CX-5")
        shot("04_make_models")
        click("CX-5")
        waitForText("RYCO OIL FILTER")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("TPMS RESET"))
        shot("05_sheet_with_tpms")

        click("Step by step")
        waitForText("BEFORE YOU START")
        shot("06_procedure_before")
        click("Next")
        waitForText("STEP 1 OF")
        shot("07_procedure_step")
        compose.onNodeWithContentDescription("Show all steps").performClick()
        shot("08_procedure_all_steps")
        back()
        back()
        back()

        click("Settings")
        waitForText("Service data")
        shot("09_settings")

        runBlocking { app.settings.setTheme(ThemeMode.Dark) }
        click("Saved")
        waitForText("Saved vehicles")
        shot("10_saved_dark")
        click("Mazda CX-5")
        waitForText("ENGINE OIL")
        shot("11_sheet_dark")
    }
}
