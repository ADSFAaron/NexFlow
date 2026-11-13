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
package com.nexflow.core.flowschema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The store CLI reads docs/flow-catalog.json with this module alone, no Android. The app's
 * FlowCatalogTest keeps the file current; this keeps it readable from the CLI's side.
 */
class FlowCatalogFileTest {

    private val catalog = FlowCatalog.decode(File("../../docs/flow-catalog.json").readText())

    @Test
    fun `the checked-in catalog decodes at the current version`() {
        assertEquals(FlowCatalog.CURRENT_VERSION, catalog.catalogVersion)
        assertTrue(catalog.triggers.isNotEmpty() && catalog.conditions.isNotEmpty() && catalog.actions.isNotEmpty())
    }

    @Test
    fun `it agrees with FieldPolicy`() {
        val keys = (catalog.triggers.values + catalog.conditions.values + catalog.actions.values)
            .flatMap { it.keys.entries }
        keys.forEach { (key, entry) ->
            assertEquals(FieldPolicy.isPersonal(key), entry.personal, key)
            assertEquals(FieldPolicy.isSink(key), entry.sink, key)
        }
    }
}
