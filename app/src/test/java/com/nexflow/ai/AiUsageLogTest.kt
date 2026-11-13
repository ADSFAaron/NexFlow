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
package com.nexflow.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AiUsageLogTest {

    private val step = GeminiUsageMetadata(
        promptTokenCount = 5000,
        cachedContentTokenCount = 2500,
        candidatesTokenCount = 100,
        thoughtsTokenCount = 900,
        totalTokenCount = 6000,
        promptTokensDetails = listOf(
            GeminiModalityTokenCount("TEXT", 3800),
            GeminiModalityTokenCount("IMAGE", 1200),
        ),
    )

    @Test
    fun `a step line names each cost and what the step did`() {
        assertEquals(
            "step=2 contents=5 images=1 sig_chars=3000 prompt=5000 cached=2500 image=1200 " +
                "thoughts=900 output=100 tool_prompt=0 total=6000 -> create_flow",
            step.stepLine(step = 2, contents = 5, images = 1, signatureChars = 3000, outcome = "create_flow"),
        )
    }

    @Test
    fun `a turn adds its steps up`() {
        val turn = TurnUsage().apply {
            add(step)
            add(step)
        }

        assertEquals(
            "turn requests=2 prompt=10000 cached=5000 (50% of prompt) image=2400 " +
                "thoughts=1800 (15% of total) output=200 total=12000",
            turn.summaryLine(),
        )
    }

    @Test
    fun `a response that reports nothing doesn't divide by zero`() {
        val turn = TurnUsage().apply { add(GeminiUsageMetadata()) }

        assertEquals(
            "turn requests=1 prompt=0 cached=0 (0% of prompt) image=0 " +
                "thoughts=0 (0% of total) output=0 total=0",
            turn.summaryLine(),
        )
    }
}
