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
package com.nexflow

import com.nexflow.core.automation.condition.ConditionEvaluator
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ConditionType
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerHandler
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Verifies the real Hilt graph wires up every option offered in the Flows UI:
 * each TriggerType has a TriggerHandler, each executable ActionType an ActionExecutor,
 * and each ConditionType a ConditionEvaluator. Catches a forgotten @Binds @IntoSet in
 * ExecutionModule, which would otherwise fail only at runtime ("No executor for …") — or,
 * for a condition, by silently refusing to run every flow that uses it.
 */
@HiltAndroidTest
class ExecutionBindingsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var triggerHandlers: Set<@JvmSuppressWildcards TriggerHandler>

    @Inject
    lateinit var actionExecutors: Set<@JvmSuppressWildcards ActionExecutor>

    @Inject
    lateinit var conditionEvaluators: Set<@JvmSuppressWildcards ConditionEvaluator>

    /** Handled inline by FlowInterpreter — no executor needed. */
    private val interpreterHandled = setOf(
        ActionType.IF_BLOCK, ActionType.ELSE_BLOCK, ActionType.END_IF,
        ActionType.REPEAT_BLOCK, ActionType.END_REPEAT, ActionType.SET_VARIABLE,
        // SHOW_MENU has an executor; MENU_CASE/END_MENU are control-flow markers (FlowInterpreter).
        ActionType.MENU_CASE, ActionType.END_MENU,
    )

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun everyTriggerTypeHasAHandler() {
        val supported = triggerHandlers.map { it.supportedType }
        assertEquals(
            "Duplicate trigger handlers for: ${supported.groupBy { it }.filterValues { it.size > 1 }.keys}",
            supported.size, supported.toSet().size,
        )
        val missing = TriggerType.entries
            .filter { it !in FlavorFeatures.hiddenTriggerTypes } // SMS/Call absent in play flavor
            .filter { it !in supported.toSet() }
        assertTrue("Trigger types without a handler: $missing", missing.isEmpty())
    }

    @Test
    fun everyExecutableActionTypeHasAnExecutor() {
        val supported = actionExecutors.map { it.supportedType }
        assertEquals(
            "Duplicate action executors for: ${supported.groupBy { it }.filterValues { it.size > 1 }.keys}",
            supported.size, supported.toSet().size,
        )
        val missing = ActionType.entries
            .filter { it !in interpreterHandled }
            .filter { it !in FlavorFeatures.hiddenActionTypes } // SMS/Call absent in play flavor
            .filter { it !in supported.toSet() }
        assertTrue("Action types without an executor: $missing", missing.isEmpty())
    }

    @Test
    fun everyConditionTypeHasAnEvaluator() {
        val supported = conditionEvaluators.map { it.supportedType }
        assertEquals(
            "Duplicate condition evaluators for: ${supported.groupBy { it }.filterValues { it.size > 1 }.keys}",
            supported.size, supported.toSet().size,
        )
        val missing = ConditionType.entries.filter { it !in supported.toSet() }
        assertTrue("Condition types without an evaluator: $missing", missing.isEmpty())
    }
}
