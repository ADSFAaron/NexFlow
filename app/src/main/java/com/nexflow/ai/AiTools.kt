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

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The function declarations exposed to Gemini. */
object AiTools {
    const val CREATE_FLOW = "create_flow"
    const val SEARCH_INSTALLED_APPS = "search_installed_apps"
    const val SEARCH_APP_SHORTCUTS = "search_app_shortcuts"

    fun tools(): List<GeminiTool> = listOf(
        GeminiTool(
            functionDeclarations = listOf(
                GeminiFunctionDeclaration(
                    name = CREATE_FLOW,
                    description = "Create an automation flow from the collected requirements. " +
                        "The app validates the arguments; on validation errors, fix them and call again.",
                    parameters = createFlowSchema(),
                ),
                GeminiFunctionDeclaration(
                    name = SEARCH_INSTALLED_APPS,
                    description = "Search the apps installed on this device by name, or by the kind " +
                        "of app when the user did not name one. Returns entries of {label, " +
                        "package_name} plus, when the device knows them, \"category\" (one of GAME, " +
                        "AUDIO, VIDEO, IMAGE, SOCIAL, NEWS, MAPS, PRODUCTIVITY, ACCESSIBILITY), " +
                        "\"roles\" the app declares about itself (BROWSER, CALCULATOR, CALENDAR, " +
                        "CONTACTS, EMAIL, FILES, GALLERY, MAPS, MARKET, MESSAGING, MUSIC, WEATHER, " +
                        "FITNESS) and the app's own \"description\". Use those to tell apps apart " +
                        "and to identify an app whose name means nothing to you. They are absent for " +
                        "most apps — absence says nothing about what the app does, so never rule an " +
                        "app out because it has no category or role. " +
                        "Required before setting any package_name config value.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("query") {
                                put("type", "string")
                                put(
                                    "description",
                                    "App name or partial name, e.g. \"spotify\"; or a category or " +
                                        "role name to list apps of that kind, e.g. \"MUSIC\" for " +
                                        "\"open a music app\". Matches labels, package names, " +
                                        "categories and roles.",
                                )
                            }
                        }
                        putJsonArray("required") { add("query") }
                    },
                ),
                GeminiFunctionDeclaration(
                    name = SEARCH_APP_SHORTCUTS,
                    description = "List the launchable shortcuts an installed app publishes — the same " +
                        "entries a launcher shows on long-press, e.g. a payment app's \"Pay\" code screen. " +
                        "Returns {label, intent_uri} entries; pass an intent_uri straight to a " +
                        "LAUNCH_SHORTCUT action. Required before setting any intent_uri config value.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("package_name") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Package name of the app to inspect, as returned by search_installed_apps",
                                )
                            }
                        }
                        putJsonArray("required") { add("package_name") }
                    },
                ),
            ),
        ),
    )

    // The Gemini `parameters` schema rejects OBJECT types with empty `properties`
    // (and `additionalProperties` support varies by model), so free-form trigger/action
    // config travels as a JSON-encoded STRING; FlowDraftMapper parses it back.
    private fun createFlowSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("name") {
                put("type", "string")
                put("description", "Short flow name in the user's language, max 100 characters")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "One-sentence summary of what the flow does, in the user's language")
            }
            putJsonObject("trigger_logic") {
                put("type", "string")
                putJsonArray("enum") { add("ANY"); add("ALL") }
            }
            putJsonObject("triggers") {
                put("type", "array")
                putJsonObject("items") { typeAndConfig("TRIGGER TYPES") }
            }
            putJsonObject("actions") {
                put("type", "array")
                put(
                    "description",
                    "Ordered action list. Control-flow markers (IF_BLOCK/END_IF, REPEAT_BLOCK/END_REPEAT, " +
                        "SHOW_MENU/MENU_CASE/END_MENU) must be balanced.",
                )
                putJsonObject("items") { typeAndConfig("ACTION TYPES") }
            }
        }
        putJsonArray("required") {
            add("name"); add("description"); add("triggers"); add("actions")
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.typeAndConfig(catalogName: String) {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("type") {
                put("type", "string")
                put("description", "One of the $catalogName listed in the system instruction, exact uppercase name")
            }
            putJsonObject("config") {
                put("type", "string")
                put(
                    "description",
                    "The config keys for this type as a JSON object encoded as a string, " +
                        "e.g. \"{\\\"message\\\":\\\"Hello\\\"}\". Use \"{}\" when the type has no config.",
                )
            }
        }
        putJsonArray("required") { add("type") }
    }
}
