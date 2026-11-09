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
package com.nexflow.store.tools

import com.nexflow.core.automation.interpreter.ExpressionEvaluator
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.util.TextMatcher
import com.nexflow.core.flowschema.FieldPolicy
import com.nexflow.core.flowschema.FlowCatalog
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.FlowSchemaValidator
import com.nexflow.core.flowschema.SetupField
import com.nexflow.core.flowschema.StoreJson
import com.nexflow.core.flowschema.StoreMeta
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Every rule a flow store submission must pass (docs/flow-store/DESIGN.md, P1 `validate`).
 *
 * The threat model is a hostile author: the person who installs a store flow did not write it,
 * and anything a trigger hands the flow (an SMS body, a notification's text, an HTTP reply) comes
 * from a stranger. Stricter than import on purpose: import keeps what it can and warns; the
 * store refuses.
 */
class StoreValidator(
    private val catalog: FlowCatalog,
    /** Slugs in revoked.json: never usable again. */
    private val revokedSlugs: Set<String> = emptySet(),
) {

    /** What [check] found, and what build-index records about a flow that passed. */
    data class Report(
        val problems: List<String>,
        val minAppVersion: String,
        val flavors: List<String>,
        val risks: List<String>,
    ) {
        val ok: Boolean get() = problems.isEmpty()
    }

    /** One step as the rules see it, whatever its kind. */
    private data class Step(val kind: String, val index: Int, val id: String, val type: String, val config: JsonObject) {
        val where: String get() = "${kind}s[$index] ($type)"
    }

    /**
     * @param folder the name of the flow's directory, which must equal its slug.
     * @param base the same flow on the target branch, when it already exists there.
     */
    fun check(folder: String, meta: StoreMeta, flow: FlowJson, base: Pair<StoreMeta, FlowJson>? = null): Report {
        val problems = mutableListOf<String>()
        checkMeta(folder, meta, flow, base, problems)

        FlowSchemaValidator.validate(flow).forEach { problems += "flow.json ${it.field}: ${it.message}" }

        val steps = flow.triggers.mapIndexed { i, t -> Step("trigger", i, t.id, t.type, t.config) } +
            flow.conditions.mapIndexed { i, c -> Step("condition", i, c.id, c.type, c.config) } +
            flow.actions.mapIndexed { i, a -> Step("action", i, a.id, a.type, a.config) }
        val setupTargets = checkSetup(meta, steps, problems)
        val entries = steps.associateWith { step -> catalogEntry(step) }

        steps.forEach { step ->
            val entry = entries.getValue(step)
            if (entry == null) {
                problems += "${step.where}: unknown ${step.kind} type"
                return@forEach
            }
            step.config.forEach { (key, value) ->
                val text = value.text()
                val isSetup = Triple(step.kind, step.id, key) in setupTargets
                val keyEntry = entry.keys[key]
                if (keyEntry == null) {
                    problems += "${step.where}.$key: not a setting of ${step.type}"
                    return@forEach
                }
                // Blank is "not set", which every control allows; the editor saves it that way too.
                if (!isSetup && text.isNotBlank()) checkValue(step, key, text, keyEntry, entry, problems)
                if (FieldPolicy.isPersonal(key)) {
                    when {
                        isSetup -> Unit
                        "{{" in text -> problems += "${step.where}.$key: personal data may not come from a variable"
                        text.isNotBlank() -> problems += "${step.where}.$key: personal data must be empty or a setup field"
                    }
                }
                if (key in URL_KEYS && !isSetup) checkUrl(step, key, text, problems)
            }
            checkRegex(step, problems)
        }

        checkTaint(flow, steps, problems)
        checkGlobals(flow, steps, problems)

        val used = steps.mapNotNull { step -> entries.getValue(step)?.let { step to it } }
        return Report(
            problems = problems,
            minAppVersion = used.flatMap { (step, entry) ->
                listOf(entry.since) + step.config.keys.mapNotNull { entry.keys[it]?.since }
            }.maxWithOrNull(VERSION_ORDER) ?: "0",
            flavors = used.map { it.second.flavors.toSet() }
                .fold(setOf("github", "play")) { acc, f -> acc intersect f }
                .sorted(),
            risks = flow.actions.map { it.type }.filter { it in HIGH_RISK }.distinct().sorted(),
        )
    }

    private fun catalogEntry(step: Step): FlowCatalog.StepEntry? = when (step.kind) {
        "trigger" -> catalog.triggers[step.type]
        "condition" -> catalog.conditions[step.type.uppercase()]
        else -> catalog.actions[step.type]
    }

    private fun checkMeta(
        folder: String,
        meta: StoreMeta,
        flow: FlowJson,
        base: Pair<StoreMeta, FlowJson>?,
        problems: MutableList<String>,
    ) {
        if (!SLUG.matches(meta.slug)) problems += "meta.json slug: \"${meta.slug}\" must match ${SLUG.pattern}"
        if (meta.slug != folder) problems += "meta.json slug: \"${meta.slug}\" must equal its folder name \"$folder\""
        if (meta.slug in revokedSlugs) problems += "meta.json slug: \"${meta.slug}\" was revoked and can never be reused"
        if (meta.version < 1) problems += "meta.json version: must be 1 or more"
        if (base != null) {
            val (baseMeta, baseFlow) = base
            val changed = baseMeta.copy(version = meta.version) != meta || baseFlow != flow
            if (changed && meta.version <= baseMeta.version) {
                problems += "meta.json version: the flow changed, so version must go above ${baseMeta.version}"
            }
        }
        if (meta.languages.isEmpty()) problems += "meta.json languages: at least one language is required"
        meta.languages.forEach { (lang, text) ->
            if (lang !in StoreJson.LANGUAGES) problems += "meta.json languages.$lang: use one of ${StoreJson.LANGUAGES}"
            if (text.title.isBlank()) problems += "meta.json languages.$lang.title: must not be blank"
            if (text.title.length > 60) problems += "meta.json languages.$lang.title: at most 60 characters"
            if (text.description.length > 2000) problems += "meta.json languages.$lang.description: at most 2000 characters"
        }
        if (meta.tags.size > 10) problems += "meta.json tags: at most 10"
        meta.tags.filterNot { TAG.matches(it) }.forEach { problems += "meta.json tags: \"$it\" must match ${TAG.pattern}" }
        if (flow.enabled) problems += "flow.json enabled: must be false; the user turns a store flow on"
    }

    /** @return the (kind, step id, key) triples the setup fields take over. */
    private fun checkSetup(meta: StoreMeta, steps: List<Step>, problems: MutableList<String>): Set<Triple<String, String, String>> {
        val targets = mutableSetOf<Triple<String, String, String>>()
        val ids = mutableSetOf<String>()
        meta.setup.forEachIndexed { i, field: SetupField ->
            val where = "meta.json setup[$i]"
            if (!SETUP_ID.matches(field.id)) problems += "$where.id: \"${field.id}\" must match ${SETUP_ID.pattern}"
            if (!ids.add(field.id)) problems += "$where.id: \"${field.id}\" is used twice"
            val t = field.target
            val step = steps.find { it.kind == t.kind && it.id == t.item }
            if (step == null) {
                problems += "$where.target: no ${t.kind} with id ${t.item}"
            } else {
                val value = step.config[t.key]?.text()
                if (catalogEntry(step)?.keys?.containsKey(t.key) != true) {
                    problems += "$where.target.key: \"${t.key}\" is not a setting of ${step.type}"
                }
                if (!value.isNullOrEmpty()) problems += "$where.target: ${step.where}.${t.key} must be left empty in flow.json"
            }
            if (!targets.add(Triple(t.kind, t.item, t.key))) problems += "$where.target: already taken by another setup field"
            meta.languages.keys.filter { field.label[it].isNullOrBlank() }
                .forEach { problems += "$where.label: missing for language $it" }
        }
        return targets
    }

    private fun checkValue(
        step: Step,
        key: String,
        text: String,
        keyEntry: FlowCatalog.KeyEntry,
        stepEntry: FlowCatalog.StepEntry,
        problems: MutableList<String>,
    ) {
        val where = "${step.where}.$key"
        keyEntry.values?.let { allowed ->
            if (text !in allowed) problems += "$where: \"$text\" is not one of $allowed"
        }
        val min = keyEntry.min
        val max = keyEntry.max
        if (min != null && max != null) {
            val n = text.toIntOrNull()
            if (n == null || n !in min..max) problems += "$where: must be a whole number in $min..$max"
        }
        keyEntry.unitRanges?.let { ranges ->
            val unitKey = stepEntry.keys.entries.firstOrNull { it.value.input == "Unit" }?.key
            val unit = unitKey?.let { step.config[it]?.text() } ?: ranges.keys.first()
            val range = ranges[unit] ?: return@let
            val n = text.toIntOrNull()
            if (n == null || n !in range[0]..range[1]) problems += "$where: must be a whole number in ${range[0]}..${range[1]} ($unit)"
        }
    }

    /**
     * A URL is either filled in by the user at install, or a fixed https address on a fixed public
     * host. Variables may vary the path and query but never the host: otherwise the flow decides
     * at run time where to send things, and `localhost` or a LAN address reaches services on the
     * phone or its network that were never meant to face the internet.
     */
    private fun checkUrl(step: Step, key: String, text: String, problems: MutableList<String>) {
        val where = "${step.where}.$key"
        if (text.isBlank()) {
            // A notification's tap URL only matters when tapping opens a URL.
            if (key != "tap_url") problems += "$where: must be a fixed https URL or a setup field"
            return
        }
        if (!text.startsWith("https://")) {
            problems += "$where: must start with https://"
            return
        }
        val host = text.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').lowercase()
        when {
            "{{" in host || host.isBlank() -> problems += "$where: the host must be written out, not built from a variable"
            !isPublicHost(host.substringBefore(':').trim('[', ']')) ->
                problems += "$where: \"$host\" is a local or private address"
        }
    }

    private fun checkRegex(step: Step, problems: MutableList<String>) {
        if (step.config["match_mode"]?.text() != TextMatcher.MODE_REGEX) return
        PATTERN_KEYS.forEach { key ->
            val pattern = step.config[key]?.text() ?: return@forEach
            when {
                pattern.length > TextMatcher.MAX_PATTERN_LENGTH ->
                    problems += "${step.where}.$key: a regex may be at most ${TextMatcher.MAX_PATTERN_LENGTH} characters"
                runCatching { Regex(pattern) }.isFailure -> problems += "${step.where}.$key: not a valid regex"
            }
        }
    }

    /**
     * Data from outside the phone may not reach a [FieldPolicy.SINKS] field, directly or through a
     * variable it was copied into. The sources are trigger values, an HTTP reply, and whatever an
     * action other than Set variable stores into a variable (a clipboard, say).
     */
    private fun checkTaint(flow: FlowJson, steps: List<Step>, problems: MutableList<String>) {
        val tainted = mutableSetOf(HTTP_STATUS)
        val actions = steps.filter { it.kind == "action" }
        actions.forEach { a ->
            if (a.type != "SET_VARIABLE") {
                listOf("response_var", "variable_name").mapNotNullTo(tainted) { a.config[it]?.text()?.trim()?.takeIf(String::isNotEmpty) }
            }
        }
        // Set variable copies taint from its value to its target; repeat until nothing changes.
        do {
            val before = tainted.size
            actions.filter { it.type == "SET_VARIABLE" }.forEach { a ->
                val target = a.config["variable_name"]?.text()?.trim().orEmpty()
                val value = a.config.values.joinToString(" ") { it.text() }
                if (target.isNotEmpty() && isTainted(value, tainted)) tainted += target
            }
        } while (tainted.size != before)

        steps.forEach { step ->
            step.config.forEach { (key, value) ->
                if (FieldPolicy.isSink(key) && isTainted(value.text(), tainted)) {
                    problems += "${step.where}.$key: may not use data from outside the phone (trigger values, HTTP replies, or variables copied from them)"
                }
            }
        }
    }

    private fun isTainted(text: String, tainted: Set<String>): Boolean =
        ExpressionEvaluator.references(text).any { it.startsWith(FlowInterpreter.TRIGGER_PREFIX) || it in tainted }

    /** A store flow may use only the globals it declares; anything else is another flow's data. */
    private fun checkGlobals(flow: FlowJson, steps: List<Step>, problems: MutableList<String>) {
        val declared = flow.globalVariables.map { it.name }.toSet()
        val prefix = FlowInterpreter.GLOBAL_PREFIX
        steps.forEach { step ->
            step.config.forEach { (key, value) ->
                val text = value.text()
                val names = ExpressionEvaluator.references(text).filter { it.startsWith(prefix) }.toMutableSet()
                if (key in VARIABLE_TARGET_KEYS && text.trim().startsWith(prefix)) names += text.trim()
                names.map { it.removePrefix(prefix) }.filter { it !in declared }.forEach {
                    problems += "${step.where}.$key: uses global $prefix$it, which this flow does not declare"
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonElement.text(): String =
        (this as? JsonPrimitive)?.contentOrNull ?: toString()

    companion object {
        val SLUG = Regex("^[a-z0-9-]{3,50}$")
        val TAG = Regex("^[a-z0-9-]{1,30}$")
        val SETUP_ID = Regex("^[a-z0-9_]{1,40}$")

        private val URL_KEYS = setOf("url", "tap_url")
        private val PATTERN_KEYS = listOf("keyword", "body_keyword")
        private val VARIABLE_TARGET_KEYS = setOf("variable_name", "response_var")
        private const val HTTP_STATUS = "__http_status__"

        /** Actions that cost money, send data off the phone, act for the user, or write files. */
        val HIGH_RISK = setOf(
            "SEND_SMS", "CALL_PHONE", "HTTP_REQUEST", "LAUNCH_SHORTCUT",
            "SIMULATE_TAP", "SIMULATE_SWIPE", "WRITE_FILE", "SET_WALLPAPER",
        )

        /** "1.10.0" after "1.9.2". */
        val VERSION_ORDER: Comparator<String> = Comparator { a, b ->
            val x = a.split('.').map { it.toIntOrNull() ?: 0 }
            val y = b.split('.').map { it.toIntOrNull() ?: 0 }
            (0 until maxOf(x.size, y.size)).asSequence()
                .map { (x.getOrElse(it) { 0 }).compareTo(y.getOrElse(it) { 0 }) }
                .firstOrNull { it != 0 } ?: 0
        }

        /** Not loopback, private, link-local, carrier-grade NAT, or a bare IPv6 literal. */
        fun isPublicHost(host: String): Boolean {
            if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return false
            if (':' in host) return false
            val octets = host.split('.').map { it.toIntOrNull() }
            if (octets.size == 4 && octets.all { it != null && it in 0..255 }) {
                val (a, b) = octets[0]!! to octets[1]!!
                return !(a == 10 || a == 127 || a == 0 || (a == 169 && b == 254) || (a == 172 && b in 16..31) ||
                    (a == 192 && b == 168) || (a == 100 && b in 64..127))
            }
            return '.' in host
        }
    }
}
