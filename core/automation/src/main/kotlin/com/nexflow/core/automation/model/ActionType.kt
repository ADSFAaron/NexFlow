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
package com.nexflow.core.automation.model

enum class ActionType {
    OPEN_APP,
    SEND_SMS,
    CALL_PHONE,
    WIFI_TOGGLE,
    BLUETOOTH_TOGGLE,
    DND_TOGGLE,
    VOLUME_ADJUST,
    NOTIFICATION,
    TOAST,
    HTTP_REQUEST,
    CLIPBOARD_COPY,
    OPEN_URL,
    MEDIA_PLAY_PAUSE,
    TTS,
    BRIGHTNESS_ADJUST,
    AIRPLANE_TOGGLE,
    DELAY,
    IF_BLOCK,
    ELSE_BLOCK,
    END_IF,
    REPEAT_BLOCK,
    END_REPEAT,
    SET_VARIABLE,
    WRITE_FILE,
    SHARE,
    SCREENSHOT,
}
