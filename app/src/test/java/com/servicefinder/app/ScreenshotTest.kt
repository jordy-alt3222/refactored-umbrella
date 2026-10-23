package com.servicefinder.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.servicefinder.app.data.Selection
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app (catalogue import, DataStore, navigation) under Robolectric and
 * records screenshots of the main screens to app/build/outputs/roborazzi.
 * Run with: ./gradlew :app:recordRoborazziDebug
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = compose.activity.application as ServiceFinderApp

    private fun waitForText(text: String) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMillis = 20_000)

    private fun click(text: String) = compose.onAllNodesWithText(text).onFirst().performClick()

    private fun scrollTo(text: String) =
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun select(selection: Selection) = runBlocking { app.selection.select(selection) }

    @Test
    fun mainScreens() {
        waitForText("Choose a vehicle")
        shot("01_no_vehicle")

        click("Choose vehicle")
        waitForText("European")
        shot("02_pick_make")
        click("Mercedes-Benz")
        waitForText("C-Class")
        shot("03_pick_model")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.activity.onBackPressedDispatcher.onBackPressed()

        select(Selection("Mercedes-Benz", "C-Class", "mercedes-c-w205", 2021, "c300"))
        waitForText("Wheel nut torque")
        shot("04_home_mercedes")

        click("Wheel nut torque")
        waitForText("Engine oil capacity")
        shot("05_specs_mercedes")
        compose.activity.onBackPressedDispatcher.onBackPressed()

        select(Selection("Kia", "Carnival", "kia-carnival-ka4", 2022, null))
        runBlocking { app.selection.setOdometer(58_400) }
        waitForText("Engine oil")
        shot("06_home_kia")
        scrollTo("Service")
        shot("06b_home_kia_scrolled")
        click("Service")
        waitForText("Due at the")
        shot("07_service_kia")
        compose.activity.onBackPressedDispatcher.onBackPressed()

        select(Selection("Peugeot", "308", "peugeot-308-t9", 2018, null))
        waitForText("Wheel nut torque")
        click("Fuses")
        waitForText("fuses.")
        shot("08_fuses_peugeot")

        select(Selection("Volkswagen", "Golf", null, 2012, null))
        click("Problems")
        waitForText("Coolant leak")
        shot("09_problems_golf")
        click("Coolant leak, or repeat leak, from the water pump area (2.0L CCTA/CBFA)")
        waitForText("Bulletin summaries")
        shot("10_problem_detail")
        compose.activity.onBackPressedDispatcher.onBackPressed()

        click("Vehicle")
        waitForText("Service Finder")
        waitForText("Specifications not available")
        shot("11_home_problems_only")

        click("Tests")
        waitForText("Electrical tests")
        shot("12_tests")
        scrollTo("Starter circuit: cranking voltage and voltage drop")
        click("Starter circuit: cranking voltage and voltage drop")
        waitForText("Start")
        shot("13_test_overview")
        click("Start")
        waitForText("Step 1 of")
        shot("14_test_step")
    }
}
