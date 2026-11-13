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

/**
 * Where a turn's tokens went, for `adb logcat -s AiUsage`. The running total the chat header
 * shows can't answer that: it is one number, and it counts everything each step replays.
 *
 * Lines are key=value so a captured log can be added up with a script.
 */
internal class TurnUsage {
    var requests = 0
        private set
    private var prompt = 0
    private var cached = 0
    private var thoughts = 0
    private var output = 0
    private var images = 0
    private var total = 0

    fun add(usage: GeminiUsageMetadata) {
        requests++
        prompt += usage.promptTokenCount
        cached += usage.cachedContentTokenCount
        thoughts += usage.thoughtsTokenCount
        output += usage.candidatesTokenCount
        images += usage.imageTokens()
        total += usage.totalTokenCount
    }

    fun summaryLine(): String =
        "turn requests=$requests prompt=$prompt cached=$cached (${percent(cached, prompt)}% of prompt) " +
            "image=$images thoughts=$thoughts (${percent(thoughts, total)}% of total) " +
            "output=$output total=$total"

    private fun percent(part: Int, whole: Int): Int = if (whole == 0) 0 else part * 100 / whole
}

/** @param signatureChars thoughtSignature text the request replays, which dwarfs the visible text. */
internal fun GeminiUsageMetadata.stepLine(
    step: Int,
    contents: Int,
    images: Int,
    signatureChars: Int,
    outcome: String,
): String =
    "step=$step contents=$contents images=$images sig_chars=$signatureChars prompt=$promptTokenCount " +
        "cached=$cachedContentTokenCount image=${imageTokens()} thoughts=$thoughtsTokenCount " +
        "output=$candidatesTokenCount tool_prompt=$toolUsePromptTokenCount " +
        "total=$totalTokenCount -> $outcome"

private fun GeminiUsageMetadata.imageTokens(): Int =
    promptTokensDetails.filter { it.modality == "IMAGE" }.sumOf { it.tokenCount }
