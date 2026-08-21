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
package com.nexflow.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexflow.ui.theme.NexFlowTheme

/**
 * The eight renders every previewed composable gets: four shipped locales against light and
 * dark.
 *
 * Translation length is the thing this actually guards. zh-TW/zh-CN run far shorter than
 * English and Japanese far longer, so a layout tuned on English can clip or wrap badly in
 * exactly one language and go unnoticed until a user reports it — the kind of break no
 * assertion-based UI test looks for, because nothing throws. The light/dark pairing catches
 * the other silent one: a hardcoded color that only loses contrast in one theme.
 */
@Preview(name = "en · light", locale = "en", uiMode = Configuration.UI_MODE_NIGHT_NO, device = PHONE)
@Preview(name = "en · dark", locale = "en", uiMode = Configuration.UI_MODE_NIGHT_YES, device = PHONE)
@Preview(name = "zh-TW · light", locale = "zh-rTW", uiMode = Configuration.UI_MODE_NIGHT_NO, device = PHONE)
@Preview(name = "zh-TW · dark", locale = "zh-rTW", uiMode = Configuration.UI_MODE_NIGHT_YES, device = PHONE)
@Preview(name = "zh-CN · light", locale = "zh-rCN", uiMode = Configuration.UI_MODE_NIGHT_NO, device = PHONE)
@Preview(name = "zh-CN · dark", locale = "zh-rCN", uiMode = Configuration.UI_MODE_NIGHT_YES, device = PHONE)
@Preview(name = "ja · light", locale = "ja", uiMode = Configuration.UI_MODE_NIGHT_NO, device = PHONE)
@Preview(name = "ja · dark", locale = "ja", uiMode = Configuration.UI_MODE_NIGHT_YES, device = PHONE)
annotation class LocaleThemePreviews

/**
 * A fixed phone viewport rather than wrap-content. Text that only overflows at a real screen
 * width is precisely what these tests exist to catch, and a pinned size keeps the images
 * comparable when a layout's intrinsic height shifts.
 */
const val PHONE = "spec:width=411dp,height=891dp,dpi=420"

/**
 * Wraps preview content the way the app wraps its screens, with one deliberate difference:
 * `dynamicColor = false`.
 *
 * On API 31+ NexFlowTheme defaults to the wallpaper-derived palette, which is whatever the
 * render host happens to produce — the images would then differ between machines for reasons
 * that have nothing to do with the code. Pinning to the app's own scheme also means these
 * screenshots actually test *our* palette. Light vs dark comes from each @Preview's uiMode,
 * which is what isSystemInDarkTheme() reads.
 */
@Composable
fun PreviewSurface(padding: Dp = 12.dp, content: @Composable () -> Unit) {
    NexFlowTheme(dynamicColor = false) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(Modifier.padding(padding)) { content() }
        }
    }
}
