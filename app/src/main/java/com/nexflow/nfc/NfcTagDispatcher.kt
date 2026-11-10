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
package com.nexflow.nfc

import android.content.Context
import com.nexflow.event.NfcEventSource
import com.nexflow.service.FlowExecutionService

/**
 * Where a scanned tag goes. The one decision every tag path makes the same way.
 *
 * With NFC triggers listening, the tag is an event like any other and every one of them sees it.
 * With nothing listening — the tag woke a closed app, or the engine is still coming up — there is
 * no one to hear an event, so the service is asked to run the matching flows directly, the way
 * a TIME alarm is served.
 */
object NfcTagDispatcher {

    /** @return false only when the service could not be started to serve the tag. */
    fun dispatch(context: Context, tagId: String): Boolean =
        if (NfcEventSource.hasListeners) {
            NfcEventSource.emit(tagId)
            true
        } else {
            FlowExecutionService.dispatchNfcTag(context, tagId)
        }
}
