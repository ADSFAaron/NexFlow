/*
 * Copyright 2026 NexFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nexflow.ui.flows.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexflow.R
import com.nexflow.core.automation.model.ActionType
import com.nexflow.ui.flows.detail.config.info
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The editor's rows no longer carry a delete button: tapping a row opens its dialog, and Delete
 * lives there. So the dialog is now the only way to remove a trigger, condition or action, and
 * these pin that it offers Delete when editing — and only then, since a thing being added has
 * nothing to delete yet.
 *
 * A dialog is a separate window, which screenshot tests render blank; Robolectric sees it.
 */
@RunWith(AndroidJUnit4::class)
class ConfigDialogDeleteTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val delete = context.getString(R.string.action_delete)

    private var deletes = 0
    private var confirms = 0
    private var dismisses = 0

    private fun showDialog(onDelete: (() -> Unit)?) {
        val info = ActionType.DELAY.info(context)
        rule.setContent {
            ConfigDialog(
                title = info.label,
                fields = info.fields,
                initialValues = emptyMap(),
                availableVariables = emptyList(),
                onConfirm = { confirms++ },
                onDismiss = { dismisses++ },
                onDelete = onDelete,
            )
        }
    }

    @Test
    fun editing_offersDelete_andDeleteNeitherSavesNorCancels() {
        showDialog(onDelete = { deletes++ })

        rule.onNodeWithText(delete).assertIsDisplayed().performClick()

        assertEquals(1, deletes)
        assertEquals(0, confirms)
        assertEquals(0, dismisses)
    }

    /**
     * Delete sits at the far start, away from Cancel: next to it, a tap meant for Cancel could
     * delete the item instead. Pinned as a gap of at least a button's width.
     */
    @Test
    fun editing_keepsDeleteWellAwayFromCancel() {
        showDialog(onDelete = { deletes++ })

        val deleteBounds = rule.onNodeWithText(delete).getUnclippedBoundsInRoot()
        val cancelBounds = rule.onNodeWithText(context.getString(R.string.action_cancel)).getUnclippedBoundsInRoot()
        val gap = cancelBounds.left - deleteBounds.right

        assertTrue("Delete must be left of Cancel, with room between: gap=$gap", gap >= 64.dp)
    }

    @Test
    fun adding_hasNoDelete() {
        showDialog(onDelete = null)

        rule.onNodeWithText(context.getString(R.string.action_cancel)).assertIsDisplayed()
        rule.onNodeWithText(delete).assertDoesNotExist()
    }
}
