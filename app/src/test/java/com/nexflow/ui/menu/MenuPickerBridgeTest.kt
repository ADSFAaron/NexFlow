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
package com.nexflow.ui.menu

import com.nexflow.ui.menu.MenuPickerBridge.Answer
import com.nexflow.ui.menu.MenuPickerBridge.Request
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Different flows run at the same time, and every SHOW_MENU goes through this one bridge. A menu
 * left without an answer suspends its run — which also keeps that flow from being started again —
 * so each of these is a way a flow used to hang until the process died.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MenuPickerBridgeTest {

    private fun request(title: String) = Request(title, listOf("yes", "no"))

    @Test
    fun `two menus at once are shown in turn and both get their own answer`() = runTest {
        val a = request("A")
        val b = request("B")
        val first = async { MenuPickerBridge.awaitChoice(a, launchFallback = {}) }
        runCurrent()
        val second = async { MenuPickerBridge.awaitChoice(b, launchFallback = {}) }
        runCurrent()

        // The second waits its turn instead of replacing the first.
        assertSame(a, MenuPickerBridge.request.value)

        MenuPickerBridge.deliver(a, "yes")
        runCurrent()
        assertSame(b, MenuPickerBridge.request.value)

        MenuPickerBridge.deliver(b, "no")
        assertEquals(Answer.Chosen("yes"), first.await())
        assertEquals(Answer.Chosen("no"), second.await())
        assertNull(MenuPickerBridge.request.value)
    }

    /**
     * MenuPickerActivity answers in onSelect and then, on its way out, dismisses "whatever is still
     * pending". By then that is the next flow's menu, which must not be closed on its behalf.
     */
    @Test
    fun `a late answer for an earlier menu does not close the next one`() = runTest {
        val a = request("A")
        val b = request("B")
        val first = async { MenuPickerBridge.awaitChoice(a, launchFallback = {}) }
        runCurrent()
        val second = async { MenuPickerBridge.awaitChoice(b, launchFallback = {}) }
        runCurrent()

        MenuPickerBridge.deliver(a, "yes")
        runCurrent()
        MenuPickerBridge.deliver(a, null) // the first renderer's onDestroy
        runCurrent()

        assertFalse(second.isCompleted)
        assertSame(b, MenuPickerBridge.request.value)
        MenuPickerBridge.deliver(b, "no")
        assertEquals(Answer.Chosen("no"), second.await())
        first.await()
    }

    /** The same menu twice in a row must still be two menus, or the second never reaches the screen. */
    @Test
    fun `identical menus are still distinct requests`() {
        assertFalse(request("A") == request("A"))
    }

    @Test
    fun `an unanswered menu gives up instead of waiting forever`() = runTest {
        val menu = async { MenuPickerBridge.awaitChoice(request("A"), launchFallback = {}) }

        advanceTimeBy(MenuPickerBridge.TIMEOUT_MS + 1)

        assertEquals(Answer.TimedOut, menu.await())
        assertNull(MenuPickerBridge.request.value)
    }

    /** A refused background start shows nothing and reports nothing; the notification takes over. */
    @Test
    fun `a sheet that never appears falls back to a notification, and it is cleared afterwards`() = runTest {
        var notified = 0
        var settled = 0
        val a = request("A")
        val menu = async {
            MenuPickerBridge.awaitChoice(
                a,
                launchFallback = {},
                onNotShown = { notified++ },
                onSettled = { settled++ },
            )
        }
        advanceTimeBy(MenuPickerBridge.SHOW_GRACE_MS + 1)
        assertEquals(1, notified)

        MenuPickerBridge.deliver(a, "yes")
        menu.await()
        assertEquals(1, settled)
    }

    @Test
    fun `a sheet that appears in time needs no notification`() = runTest {
        var notified = 0
        val a = request("A")
        val menu = async {
            MenuPickerBridge.awaitChoice(a, launchFallback = { MenuPickerBridge.markShown(a) }, onNotShown = { notified++ })
        }
        advanceTimeBy(MenuPickerBridge.SHOW_GRACE_MS * 2)
        MenuPickerBridge.deliver(a, "no")
        menu.await()

        assertEquals(0, notified)
    }

    /** A run stopped while its menu waits must not take the next flow's menu down with it. */
    @Test
    fun `cancelling a waiting run leaves the next menu alone`() = runTest {
        val a = request("A")
        val b = request("B")
        val first = async { MenuPickerBridge.awaitChoice(a, launchFallback = {}) }
        runCurrent()
        val second = async { MenuPickerBridge.awaitChoice(b, launchFallback = {}) }
        runCurrent()

        first.cancel()
        runCurrent()

        assertSame(b, MenuPickerBridge.request.value)
        MenuPickerBridge.deliver(b, "yes")
        assertEquals(Answer.Chosen("yes"), second.await())
        advanceUntilIdle()
        assertTrue(first.isCancelled)
    }
}
