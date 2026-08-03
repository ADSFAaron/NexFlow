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
package com.nexflow.core.automation.condition

import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.ConditionType
import kotlinx.coroutines.CancellationException

/**
 * Decides whether a triggered flow may actually run: every condition must hold (AND), with
 * [Condition.negate] inverting the individual result.
 *
 * Fails closed. A condition whose type this build doesn't know, or whose evaluator throws, is
 * treated as *not* satisfied and the flow is skipped — a constraint the app cannot check is the
 * one case where running would do the thing the user explicitly asked not to do. The reason
 * travels back to the caller so it lands in the execution log instead of looking like a silent
 * no-op.
 */
class ConditionGate(
    private val evaluators: Map<ConditionType, ConditionEvaluator>,
) {

    suspend fun evaluate(
        conditions: List<Condition>,
        variables: Map<String, String> = emptyMap(),
    ): ConditionResult {
        conditions.forEach { condition ->
            val type = ConditionType.fromId(condition.type)
                ?: return ConditionResult.Unsatisfied(condition, unknownTypeMessage(condition.type))
            val evaluator = evaluators[type]
                ?: return ConditionResult.Unsatisfied(condition, noEvaluatorMessage(type))

            val satisfied = try {
                evaluator.isSatisfied(condition, variables) != condition.negate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ConditionResult.Unsatisfied(condition, "Condition $type failed: ${e.message}")
            }
            if (!satisfied) {
                return ConditionResult.Unsatisfied(condition, "Condition $type not met")
            }
        }
        return ConditionResult.Satisfied
    }

    companion object {
        fun unknownTypeMessage(type: String): String =
            "Unknown condition type '$type' — cannot verify it, so the flow was not run"

        fun noEvaluatorMessage(type: ConditionType): String =
            "Condition $type is not supported in this build — the flow was not run"
    }
}

sealed class ConditionResult {
    data object Satisfied : ConditionResult()

    /** [reason] is written to the execution log so a skipped run is explainable. */
    data class Unsatisfied(val condition: Condition, val reason: String) : ConditionResult()
}
