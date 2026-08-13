package com.example.agriscout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.agriscout.ui.components.EmptyState
import com.example.agriscout.ui.theme.AgriScoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgriScoutUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateDisplaysHelpfulTitleAndMessage() {
        composeRule.setContent {
            AgriScoutTheme {
                EmptyState(
                    title = "No field reports",
                    message = "No reports yet. Reports work offline and sync later."
                )
            }
        }

        composeRule.onNodeWithText("No field reports").assertIsDisplayed()
        composeRule.onNodeWithText("No reports yet. Reports work offline and sync later.").assertIsDisplayed()
    }
}