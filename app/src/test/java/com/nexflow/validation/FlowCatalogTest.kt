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
import com.nexflow.BuildConfig
import com.nexflow.FlavorGates
import com.nexflow.ai.AiCatalog
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ConditionType
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.flowschema.FieldPolicy
import com.nexflow.core.flowschema.FlowCatalog
import com.nexflow.ui.flows.detail.config.ConfigField
import com.nexflow.ui.flows.detail.config.info
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Keeps `docs/flow-catalog.json` equal to what the editors actually offer. The flow store CLI
 * validates submissions against that file, so a key added to an editor without regenerating it
 * would make the store reject flows the app accepts, or worse, accept ones it does not.
 *
 * To regenerate after an intentional change:
 * `./gradlew :app:testGithubDebugUnitTest --tests '*FlowCatalogTest' -PupdateCatalog`
 * New steps and keys get `since` = the current versionName; existing ones keep theirs.
 */
@RunWith(AndroidJUnit4::class)
class FlowCatalogTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(checkNotNull(System.getProperty("nexflow.catalogFile")) { "run through Gradle" })
    private val update = System.getProperty("nexflow.updateCatalog").toBoolean()

    @Test
    fun `the checked-in catalog matches the editors`() {
        val previous = if (file.exists()) FlowCatalog.decode(file.readText()) else null
        val generated = generate(previous).encode()

        // Both flavors generate the same file; let only one write it.
        if (update && BuildConfig.FLAVOR == "github") {
            file.writeText(generated)
            return
        }
        assertTrue("${file.name} is missing. Generate it with -PupdateCatalog.", previous != null)
        assertEquals(
            "${file.name} is out of date. If the editor change is intended, regenerate with\n" +
                "./gradlew :app:testGithubDebugUnitTest --tests '*FlowCatalogTest' -PupdateCatalog\n",
            file.readText(),
            generated,
        )
    }

    private fun generate(previous: FlowCatalog?): FlowCatalog {
        val version = BuildConfig.VERSION_NAME
        fun steps(
            old: Map<String, FlowCatalog.StepEntry>?,
            all: List<Pair<String, List<ConfigField>>>,
            playHidden: (String) -> Boolean,
        ): Map<String, FlowCatalog.StepEntry> = all.associate { (name, fields) ->
            val oldStep = old?.get(name)
            name to FlowCatalog.StepEntry(
                since = oldStep?.since ?: version,
                flavors = if (playHidden(name)) listOf("github") else listOf("github", "play"),
                keys = keys(fields, oldStep?.keys, version),
            )
        }.toSortedMap()

        return FlowCatalog(
            triggers = steps(
                previous?.triggers,
                TriggerType.entries.map { it.name to it.info(context).fields },
            ) { name -> FlavorGates.PLAY_HIDDEN_TRIGGERS.any { it.name == name } },
            conditions = steps(
                previous?.conditions,
                ConditionType.entries.map { it.name to it.info(context).fields },
            ) { false },
            actions = steps(
                previous?.actions,
                ActionType.entries.map { it.name to it.info(context).fields },
            ) { name -> FlavorGates.PLAY_HIDDEN_ACTIONS.any { it.name == name } },
        )
    }

    private fun keys(
        fields: List<ConfigField>,
        old: Map<String, FlowCatalog.KeyEntry>?,
        version: String,
    ): Map<String, FlowCatalog.KeyEntry> {
        // The control that writes each key; a picker can write a key besides its own.
        val writer = buildMap {
            fields.forEach { field ->
                put(field.key, field)
                when (field) {
                    is ConfigField.UnitSlider -> put(field.unitKey, field)
                    is ConfigField.ShortcutPicker -> { put(field.labelKey, field); put(field.packageKey, field) }
                    else -> Unit
                }
            }
        }
        return AiCatalog.capturableKeys(fields).associateWith { key ->
            val field = writer.getValue(key)
            val range = AiCatalog.numericRange(fields, key)
            val unitRanges = (field as? ConfigField.UnitSlider)
                ?.takeIf { key == it.key }
                ?.units?.associate { it.id to listOf(it.min, it.max) }
            FlowCatalog.KeyEntry(
                since = old?.get(key)?.since ?: version,
                input = when {
                    field is ConfigField.ShortcutPicker && key == field.labelKey -> "ShortcutLabel"
                    field is ConfigField.ShortcutPicker && key == field.packageKey -> "ShortcutPackage"
                    field is ConfigField.UnitSlider && key == field.unitKey -> "Unit"
                    else -> field::class.simpleName.orEmpty()
                },
                values = AiCatalog.allowedValues(fields, key)?.toList(),
                min = range?.first,
                max = range?.last,
                unitRanges = unitRanges,
                personal = FieldPolicy.isPersonal(key),
                sink = FieldPolicy.isSink(key),
            )
        }.toSortedMap()
    }
}
