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

/**
 * How each config key is treated when a flow comes from someone else: a flow store entry, or a
 * file shared to be published there. The one list the catalog, the store CLI and the export
 * wizard all read.
 *
 * Keys mean the same thing in every step that has them (`number` is a phone number in a call
 * trigger and in Send SMS alike), so they are classified by name. Every key an editor offers must
 * be in at least one set; a test enumerates the editors and fails on a new, unclassified key.
 *
 * Runtime rules that hold for every flow, wherever it came from, live in the interpreter
 * (`ActionPolicy`); these are stricter and apply only to flows from other people.
 */
object FieldPolicy {

    /**
     * Identifies the user: their places, numbers, contacts, devices, apps, files or screen. A store
     * flow ships these empty, or as a setup field the user fills in at install, and never builds
     * them from a variable.
     */
    val PERSONAL: Set<String> = setOf(
        "ssid", "lat", "lng", "number", "contact", "sender", "tag_id", "device_name",
        "path", "image", "headers",
        "package_name", "tap_package",
        "intent_uri", "tap_shortcut_uri", "tap_shortcut_package", "tap_shortcut_label", "label",
        "x", "y", "x1", "y1", "x2", "y2",
    )

    /**
     * Where data from outside the phone (an SMS body, a notification's text, an HTTP reply) must
     * not flow: it would let a stranger choose what runs, who is called, where data goes, or which
     * variable is overwritten.
     */
    val SINKS: Set<String> = setOf(
        "url", "tap_url",
        "intent_uri", "tap_shortcut_uri", "package_name", "tap_package",
        "number", "path", "variable_name", "response_var", "expression",
        "x", "y", "x1", "y1", "x2", "y2",
    )

    /** Everything else the editors offer: fine to ship with a value and to fill from a variable. */
    val NEUTRAL: Set<String> = setOf(
        "_negate", "action", "body", "body_keyword", "brightness_unit", "charging", "content", "count",
        "days", "direction", "duration", "duration_unit", "duration_value", "end", "event",
        "extra_dim", "json_path", "keyword", "level", "match_field", "match_mode", "message",
        "method", "mode", "option", "options", "radius_m", "repeat", "sensitivity", "start", "state",
        "stream", "tap_action", "target", "text", "threshold_lux", "time", "title", "value", "volume_percent",
    )

    fun isPersonal(key: String): Boolean = key in PERSONAL

    fun isSink(key: String): Boolean = key in SINKS

    fun isClassified(key: String): Boolean = key in PERSONAL || key in SINKS || key in NEUTRAL
}
