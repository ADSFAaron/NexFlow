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
package com.nexflow.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.nexflow.ui.LocaleThemePreviews
import com.nexflow.ui.PreviewSurface

/**
 * Onboarding is the highest-value screen to render in all four locales: every word on it comes
 * from strings.xml, it is a fixed full-page layout with no scrolling to rescue an overflow, and
 * it is the first thing a new user sees — so a clipped Japanese heading here is the worst
 * possible first impression and the least likely to be noticed by a developer working in
 * English or Chinese.
 *
 * It takes no injected dependencies, so the real composable renders as-is.
 */
@PreviewTest
@LocaleThemePreviews
@Composable
fun OnboardingFirstPage() {
    PreviewSurface(padding = 0.dp) {
        OnboardingScreen(onFinished = {})
    }
}
