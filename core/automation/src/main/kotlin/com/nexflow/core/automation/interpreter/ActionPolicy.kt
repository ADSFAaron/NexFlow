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
package com.nexflow.core.automation.interpreter

import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.LogText

/**
 * Config values that must be a literal, never built from a variable.
 *
 * A flow can be shared as a file, so its author is not necessarily the person running it. Every
 * config value is interpolated, which means a value built from `{{trigger.body}}` is chosen by
 * whoever sent the SMS. For most keys that is the point (forwarding a message, opening the app
 * that posted a notification); the keys here are the exception.
 *
 * A shortcut intent is a complete launch instruction: which exact screen of which app, with what
 * action (ACTION_CALL dials immediately), data and extras. Built from a variable, it would let a
 * stranger's message decide all of that. These keys are filled from a picker, so a legitimate
 * flow never needs a variable in them. Literal-only rather than tracking where a value came from,
 * because a global variable written by an earlier run would carry no trace of its origin.
 *
 * Opening an app by package name is not here: it only ever reaches that app's launcher screen.
 */
object ActionPolicy {

    val LITERAL_ONLY_KEYS: Map<ActionType, Set<String>> = mapOf(
        ActionType.LAUNCH_SHORTCUT to setOf("intent_uri"),
        ActionType.NOTIFICATION to setOf("tap_shortcut_uri"),
    )

    fun literalOnlyMessage(key: String) =
        LogText.of("literal_only", key)
}

/** What a rate-limited action counts against; actions of one kind share a budget per flow. */
enum class RateLimitCategory(val perMinute: Int) {
    SMS(5),
    CALL(5),
    HTTP(60),
    ;

    companion object {
        /**
         * A launched shortcut can place a call too (a contact's "direct dial" shortcut is an
         * ACTION_CALL intent), so it counts against the same budget as CALL_PHONE. The prefix
         * also covers CALL_PRIVILEGED and CALL_EMERGENCY. intent_uri is literal-only (see
         * [ActionPolicy.LITERAL_ONLY_KEYS]), so the raw config is what will be launched.
         */
        fun of(action: Action): RateLimitCategory? = when (action.type) {
            ActionType.SEND_SMS -> SMS
            ActionType.CALL_PHONE -> CALL
            ActionType.HTTP_REQUEST -> HTTP
            ActionType.LAUNCH_SHORTCUT ->
                CALL.takeIf { action.config["intent_uri"].orEmpty().contains(";action=android.intent.action.CALL") }
            else -> null
        }
    }
}

/**
 * Caps how often a flow may send SMS, place calls or make HTTP requests. Protects against a loop
 * (REPEAT is capped at 10 000) or a trigger storm turning into a phone bill, whether the flow was
 * written badly or written to do it.
 */
fun interface ActionRateLimiter {
    /** @return true to run [action] now; false to skip it. */
    fun tryAcquire(flow: Flow, action: Action, category: RateLimitCategory): Boolean

    companion object {
        val UNLIMITED = ActionRateLimiter { _, _, _ -> true }
    }
}

/**
 * Sliding one-minute window per (flow, category). [onLimited] is called for every skipped action
 * and is where the app decides whether to tell the user; it runs under the limiter's lock, so it
 * must not block.
 */
class SlidingWindowRateLimiter(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onLimited: (Flow, RateLimitCategory) -> Unit = { _, _ -> },
) : ActionRateLimiter {

    private val windows = HashMap<Pair<String, RateLimitCategory>, ArrayDeque<Long>>()

    @Synchronized
    override fun tryAcquire(flow: Flow, action: Action, category: RateLimitCategory): Boolean {
        val now = nowMillis()
        val window = windows.getOrPut(flow.id to category) { ArrayDeque() }
        while (window.isNotEmpty() && now - window.first() >= WINDOW_MS) window.removeFirst()
        if (window.size >= category.perMinute) {
            onLimited(flow, category)
            return false
        }
        window.addLast(now)
        return true
    }

    private companion object {
        const val WINDOW_MS = 60_000L
    }
}
