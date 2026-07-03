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
package com.nexflow.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * Warning ↔ CheckCircle status icon used by the permission rows in Settings and
 * onboarding. Granting a permission is a moment worth confirming visually, so the
 * icon swap scales/fades (spatial) and the tint animates on the effects token
 * instead of hard-switching.
 */
@Composable
fun PermissionStatusIcon(granted: Boolean) {
    val tint by animateColorAsState(
        targetValue = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "perm_tint",
    )
    AnimatedContent(
        targetState = granted,
        transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) },
        label = "perm_icon",
    ) { ok ->
        Icon(
            imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
            contentDescription = null,
            tint = tint,
        )
    }
}
