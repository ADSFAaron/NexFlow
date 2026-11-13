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
package com.nexflow.validation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.ai.AiCatalog
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ConditionType
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.flowschema.FieldPolicy
import com.nexflow.ui.flows.detail.config.info
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FieldPolicy] is only as good as its coverage: a key nobody classified would ship in a store
 * flow unchecked. These walk every editor and fail on a key that is new, or a listed key that no
 * editor has any more.
 */
@RunWith(AndroidJUnit4::class)
class FieldPolicyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Every config key any trigger, condition or action editor can write. */
    private val editorKeys: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        fun collect(owner: String, keys: Set<String>) = keys.forEach { getOrPut(it) { mutableListOf() } += owner }
        TriggerType.entries.forEach { collect("trigger ${it.name}", AiCatalog.capturableKeys(it.info(context).fields)) }
        ConditionType.entries.forEach { collect("condition ${it.name}", AiCatalog.capturableKeys(it.info(context).fields)) }
        ActionType.entries.forEach { collect("action ${it.name}", AiCatalog.capturableKeys(it.info(context).fields)) }
    }

    @Test
    fun `every key an editor offers is classified`() {
        val unclassified = editorKeys.filterKeys { !FieldPolicy.isClassified(it) }
        assertTrue(
            "Classify these in FieldPolicy (PERSONAL, SINKS or NEUTRAL): $unclassified",
            unclassified.isEmpty(),
        )
    }

    @Test
    fun `every classified key still exists in some editor`() {
        val listed = FieldPolicy.PERSONAL + FieldPolicy.SINKS + FieldPolicy.NEUTRAL
        assertEquals("Stale keys in FieldPolicy", emptySet<String>(), listed - editorKeys.keys)
    }

    @Test
    fun `a neutral key is neither personal nor a sink`() {
        assertEquals(emptySet<String>(), FieldPolicy.NEUTRAL intersect (FieldPolicy.PERSONAL + FieldPolicy.SINKS))
    }
}
