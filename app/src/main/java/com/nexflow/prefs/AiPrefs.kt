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
package com.nexflow.prefs

import android.content.Context
import com.nexflow.ai.CustomEndpoint

enum class AiProvider { GEMINI, CUSTOM }

object AiPrefs {
    // Separate prefs file (not "nexflow_settings") so the user-supplied API key can be
    // excluded from cloud backup via data_extraction_rules.xml without dragging the rest
    // of the app settings along.
    private const val PREFS_NAME = "nexflow_ai"
    private const val KEY_API_KEY = "gemini_api_key"
    private const val KEY_MODEL = "gemini_model"
    private const val KEY_PROVIDER = "ai_provider"
    private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
    private const val KEY_CUSTOM_API_KEY = "custom_api_key"
    private const val KEY_CUSTOM_MODEL = "custom_model"

    const val DEFAULT_MODEL = "gemini-3.5-flash"

    fun getProvider(context: Context): AiProvider =
        if (prefs(context).getString(KEY_PROVIDER, null) == AiProvider.CUSTOM.name) {
            AiProvider.CUSTOM
        } else {
            AiProvider.GEMINI
        }

    fun setProvider(context: Context, provider: AiProvider) {
        prefs(context).edit().putString(KEY_PROVIDER, provider.name).apply()
    }

    /** The saved custom service, or null until one has been set up. */
    fun getCustomEndpoint(context: Context): CustomEndpoint? {
        val prefs = prefs(context)
        val baseUrl = prefs.getString(KEY_CUSTOM_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val model = prefs.getString(KEY_CUSTOM_MODEL, null)?.takeIf { it.isNotBlank() } ?: return null
        val apiKey = prefs.getString(KEY_CUSTOM_API_KEY, null)?.takeIf { it.isNotBlank() }
        return CustomEndpoint(baseUrl, apiKey, model)
    }

    fun setCustomEndpoint(context: Context, endpoint: CustomEndpoint) {
        prefs(context).edit()
            .putString(KEY_CUSTOM_BASE_URL, endpoint.baseUrl)
            .putString(KEY_CUSTOM_API_KEY, endpoint.apiKey)
            .putString(KEY_CUSTOM_MODEL, endpoint.model)
            .apply()
    }

    /** Whether the assistant can run at all with the service that's selected. */
    fun isConfigured(context: Context): Boolean = when (getProvider(context)) {
        AiProvider.GEMINI -> getApiKey(context) != null
        AiProvider.CUSTOM -> getCustomEndpoint(context) != null
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, null)
            ?.takeIf { it.isNotBlank() }

    fun setApiKey(context: Context, key: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply { if (key.isNullOrBlank()) remove(KEY_API_KEY) else putString(KEY_API_KEY, key.trim()) }
            .apply()
    }

    fun getModel(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MODEL

    fun setModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL, model.trim())
            .apply()
    }
}
