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
package com.nexflow.event

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Reader mode is exclusive and device-wide — while it is on, no other app sees a tag at all.
 * So the count that decides whether it stays on has to be exact in both directions: leaking a
 * request keeps every NFC tap on the phone hostage, and dropping one below zero would let a
 * finished scan switch off reader mode that a live NFC trigger still needs.
 */
class NfcReaderModeTest {

    @AfterEach
    fun drain() {
        repeat(8) { NfcReaderMode.endScan() }
    }

    @Test
    fun `overlapping scans each hold their own request`() {
        NfcReaderMode.beginScan()
        NfcReaderMode.beginScan()
        assertEquals(2, NfcReaderMode.scanRequests.value)

        // One config screen finishing must not release the other's hold.
        NfcReaderMode.endScan()
        assertEquals(1, NfcReaderMode.scanRequests.value)

        NfcReaderMode.endScan()
        assertEquals(0, NfcReaderMode.scanRequests.value)
    }

    @Test
    fun `an unmatched release cannot drive the count negative`() {
        // A composable disposed twice, or disposed after the process reused the singleton,
        // would otherwise leave a debt that swallows the next real scan request.
        NfcReaderMode.endScan()
        assertEquals(0, NfcReaderMode.scanRequests.value)

        NfcReaderMode.beginScan()
        assertEquals(1, NfcReaderMode.scanRequests.value)
    }
}
