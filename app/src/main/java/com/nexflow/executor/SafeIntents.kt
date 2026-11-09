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
package com.nexflow.executor

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo

/**
 * Turns a stored `intent:` URI into something safe for NexFlow to launch.
 *
 * The URI comes from a flow, and a flow can come from anyone (a shared .flow file). Whatever it
 * describes, NexFlow starts it with its own identity, so without these checks a flow could:
 * - open NexFlow's own non-exported activities, which only NexFlow itself may start;
 * - carry FLAG_GRANT_*_URI_PERMISSION, handing the receiving app NexFlow's access to a
 *   content:// URI (our FileProvider);
 * - hide its real target in a selector intent, which is resolved instead of the outer intent.
 *
 * Other apps' non-exported activities need no check here: the system already refuses those with
 * a SecurityException. NexFlow's own are the gap, because an app may always start itself.
 */
object SafeIntents {

    sealed interface Parsed {
        data class Ok(val intent: Intent) : Parsed
        data class Invalid(val reason: String) : Parsed

        /** Targets an activity of NexFlow that is not exported. */
        data object Blocked : Parsed
    }

    private const val GRANT_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    fun parse(context: Context, uri: String): Parsed {
        val intent = try {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            return Parsed.Invalid(e.message ?: e.javaClass.simpleName)
        }
        intent.selector = null
        intent.removeFlags(GRANT_FLAGS)
        // ClipData is the other channel grant flags act on; with the flags gone it carries nothing.
        intent.clipData = null

        val target = resolve(context, intent)
        if (target != null && target.packageName == context.packageName && !target.exported) {
            return Parsed.Blocked
        }
        return Parsed.Ok(intent)
    }

    private fun resolve(context: Context, intent: Intent): ActivityInfo? {
        val pm = context.packageManager
        val component = intent.component
        return if (component != null) {
            runCatching { pm.getActivityInfo(component, 0) }.getOrNull()
        } else {
            pm.resolveActivity(intent, 0)?.activityInfo
        }
    }
}
