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
package com.nexflow.core.macrodroid

import com.nexflow.core.macrodroid.model.MdrAction
import com.nexflow.core.macrodroid.model.MdrConstraint
import com.nexflow.core.macrodroid.model.MdrMacro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MdrToFlowConverterTest {

    private fun macro(constraints: List<MdrConstraint>) = MdrMacro(
        name = "test",
        actionList = listOf(MdrAction(classType = "ToastAction")),
        constraintList = constraints,
    )

    @Test
    fun `imported constraints are reported as not evaluated`() {
        val result = MdrToFlowConverter.convert(
            // A constraint whose class type IS supported: without the warning it would convert
            // cleanly and the flow would then run unconditionally, with nothing to tell the user.
            macro(listOf(MdrConstraint(classType = "WifiConstraint"))),
        )

        assertEquals(1, result.flow.conditions.size)
        assertTrue(
            result.warnings.any { it.startsWith("Conditions:") && it.contains("NOT evaluated") },
            "expected a warning that conditions are not evaluated, got ${result.warnings}",
        )
    }

    @Test
    fun `a macro without constraints gets no condition warning`() {
        val result = MdrToFlowConverter.convert(macro(emptyList()))

        assertTrue(result.flow.conditions.isEmpty())
        assertTrue(result.warnings.none { it.startsWith("Conditions:") }, "got ${result.warnings}")
    }
}
