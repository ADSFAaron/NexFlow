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
package com.nexflow.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The languages NexFlow ships translations for. Each entry's [displayName] is
 * written in its own script so the picker is readable regardless of the current
 * locale. [tag] is the BCP-47 language tag handed to AppCompat; `null` means
 * "follow the system language" (an empty locale list).
 *
 * Keep this list in sync with res/xml/locale_config.xml.
 */
enum class AppLanguage(val tag: String?, val displayName: String) {
    SYSTEM(null, "System default"),
    ENGLISH("en", "English"),
    CHINESE_TRADITIONAL("zh-TW", "繁體中文"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    JAPANESE("ja", "日本語");

    companion object {
        /** The language currently applied via AppCompat, or [SYSTEM] when unset. */
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val currentTag = locales.get(0)?.toLanguageTag() ?: return SYSTEM
            // Loose match so e.g. "zh-Hant-TW" still resolves to CHINESE_TRADITIONAL.
            return entries.firstOrNull { lang ->
                lang.tag != null && (
                    currentTag.equals(lang.tag, ignoreCase = true) ||
                        currentTag.startsWith("${lang.tag}-", ignoreCase = true) ||
                        lang.tag.startsWith("$currentTag-", ignoreCase = true)
                )
            } ?: SYSTEM
        }

        /** Apply [language] app-wide. Recreates the visible activity. */
        fun apply(language: AppLanguage) {
            val locales = language.tag
                ?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
