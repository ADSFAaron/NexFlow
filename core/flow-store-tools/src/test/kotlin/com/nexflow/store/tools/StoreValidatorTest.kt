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
package com.nexflow.store.tools

import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.StoreMeta
import com.nexflow.store.tools.Fixtures.action
import com.nexflow.store.tools.Fixtures.condition
import com.nexflow.store.tools.Fixtures.flow
import com.nexflow.store.tools.Fixtures.meta
import com.nexflow.store.tools.Fixtures.setup
import com.nexflow.store.tools.Fixtures.trigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** One good and one bad case per rule; a bad case must fail for the reason it was built for. */
class StoreValidatorTest {

    private val validator = StoreValidator(Fixtures.catalog, revokedSlugs = setOf("gone"))

    private fun check(
        flow: FlowJson = flow(),
        meta: StoreMeta = meta(),
        folder: String = meta.slug,
        base: Pair<StoreMeta, FlowJson>? = null,
    ) = validator.check(folder, meta, flow, base)

    private fun assertOk(report: StoreValidator.Report) = assertTrue(report.ok, "expected ok: ${report.problems}")

    private fun assertFails(report: StoreValidator.Report, containing: String) =
        assertTrue(report.problems.any { containing in it }, "expected a problem containing \"$containing\", got ${report.problems}")

    @Test
    fun `a plain flow passes, with what the index needs worked out`() {
        val report = check(flow(actions = listOf(action("TOAST", "message" to "hi"), action("OPEN_URL", "url" to "https://example.com"))))
        assertOk(report)
        assertEquals(listOf("github", "play"), report.flavors)
        assertEquals(emptyList<String>(), report.risks)
        assertEquals(Fixtures.catalog.triggers.getValue("TIME").since, report.minAppVersion)
    }

    @Test
    fun `flavors narrow to where every step runs, and risky actions are listed`() {
        val report = check(
            flow(actions = listOf(action("SEND_SMS", "message" to "hi"))),
            meta(setup = emptyList()),
        )
        assertOk(report)
        assertEquals(listOf("github"), report.flavors)
        assertEquals(listOf("SEND_SMS"), report.risks)
    }

    @Nested
    inner class Slug {
        @Test fun `a good slug passes`() = assertOk(check(meta = meta(slug = "good-morning-2")))

        @Test fun `a slug in the wrong shape fails`() = assertFails(check(meta = meta(slug = "Good_Morning")), "must match")

        @Test fun `a slug must equal its folder`() = assertFails(check(folder = "other"), "must equal its folder")

        @Test fun `a revoked slug can never be reused`() = assertFails(check(meta = meta(slug = "gone")), "revoked")
    }

    @Nested
    inner class Version {
        private val f = flow()

        @Test fun `an unchanged flow keeps its version`() = assertOk(check(f, meta(version = 2), base = meta(version = 2) to f))

        @Test fun `a changed flow with a higher version passes`() =
            assertOk(check(f.copy(name = "Morning!"), meta(version = 3), base = meta(version = 2) to f))

        @Test fun `a changed flow must raise its version`() =
            assertFails(check(f.copy(name = "Morning!"), meta(version = 2), base = meta(version = 2) to f), "version must go above 2")
    }

    @Nested
    inner class Languages {
        @Test fun `no language fails`() = assertFails(check(meta = meta().copy(languages = emptyMap())), "at least one language")

        @Test fun `an unsupported language fails`() =
            assertFails(check(meta = meta().copy(languages = mapOf("fr" to StoreMeta.Text("Bonjour")))), "use one of")

        @Test fun `a blank title fails`() =
            assertFails(check(meta = meta().copy(languages = mapOf("en" to StoreMeta.Text(" ")))), "title: must not be blank")

        @Test fun `several languages pass`() = assertOk(
            check(meta = meta().copy(languages = mapOf("en" to StoreMeta.Text("Hi"), "zh-TW" to StoreMeta.Text("嗨")))),
        )
    }

    @Nested
    inner class Personal {
        private val wifi = trigger("WIFI", "event" to "CONNECTED", "ssid" to "MyHome")

        @Test fun `a personal value written into the flow fails`() =
            assertFails(check(flow(triggers = listOf(wifi))), "must be empty or a setup field")

        @Test fun `the same value as a setup field passes`() {
            val t = trigger("WIFI", "event" to "CONNECTED", "ssid" to "")
            assertOk(check(flow(triggers = listOf(t)), meta(setup = listOf(setup("home_wifi", "trigger", t.id, "ssid")))))
        }

        @Test fun `a personal field may not come from a variable`() =
            assertFails(
                check(flow(triggers = listOf(trigger("WIFI", "event" to "CONNECTED", "ssid" to "{{g:ssid}}")), globals = listOf("ssid"))),
                "may not come from a variable",
            )

        @Test fun `a setup field's value must be left empty`() {
            assertFails(check(flow(triggers = listOf(wifi)), meta(setup = listOf(setup("home_wifi", "trigger", wifi.id, "ssid")))), "must be left empty")
        }

        @Test fun `a setup field must point at a real step and say what it is in every language`() {
            val t = trigger("WIFI", "event" to "CONNECTED", "ssid" to "")
            val noStep = setup("a", "trigger", "nope", "ssid")
            val noLabel = setup("b", "trigger", t.id, "ssid").copy(label = emptyMap())
            val report = check(flow(triggers = listOf(t)), meta(setup = listOf(noStep, noLabel)))
            assertFails(report, "no trigger with id nope")
            assertFails(report, "label: missing for language en")
        }
    }

    @Nested
    inner class Taint {
        @Test fun `trigger data may not pick a URL`() = assertFails(
            check(flow(actions = listOf(action("OPEN_URL", "url" to "https://example.com/{{trigger.body}}")))),
            "data from outside the phone",
        )

        @Test fun `nor through a variable it was copied into`() {
            val copy = action("SET_VARIABLE", "variable_name" to "x", "value" to "{{trigger.body}}")
            val copy2 = action("SET_VARIABLE", "variable_name" to "y", "value" to "id={{x}}")
            val open = action("OPEN_URL", "url" to "https://example.com/?{{y}}")
            assertFails(check(flow(actions = listOf(copy, copy2, open))), "data from outside the phone")
        }

        @Test fun `nor from an HTTP reply`() {
            val get = action("HTTP_REQUEST", "url" to "https://api.example.com/next", "method" to "GET", "response_var" to "next")
            val open = action("OPEN_URL", "url" to "https://example.com/{{next}}")
            assertFails(check(flow(actions = listOf(get, open))), "data from outside the phone")
        }

        @Test fun `a variable the flow sets itself is fine`() {
            val set = action("SET_VARIABLE", "variable_name" to "page", "value" to "news")
            assertOk(check(flow(actions = listOf(set, action("OPEN_URL", "url" to "https://example.com/{{page}}")))))
        }

        @Test fun `trigger data may go where it does no harm`() = assertOk(
            check(flow(actions = listOf(action("TOAST", "message" to "New: {{trigger.body}}")))),
        )
    }

    @Nested
    inner class Host {
        private fun url(u: String) = check(flow(actions = listOf(action("OPEN_URL", "url" to u))))

        @Test fun `a fixed public https host passes`() = assertOk(url("https://api.example.com/v1?q=1"))

        @Test fun `plain http fails`() = assertFails(url("http://example.com"), "must start with https://")

        @Test fun `localhost fails`() = assertFails(url("https://localhost:8080/"), "local or private")

        @Test fun `a LAN address fails`() = assertFails(url("https://192.168.1.10/"), "local or private")

        @Test fun `a link-local address fails`() = assertFails(url("https://169.254.169.254/latest"), "local or private")

        @Test fun `a host built from a variable fails`() = assertFails(url("https://{{g:h}}.example.com"), "host must be written out")

        @Test fun `a URL the user fills in at install passes`() {
            val open = action("OPEN_URL", "url" to "")
            assertOk(check(flow(actions = listOf(open)), meta(setup = listOf(setup("site", "action", open.id, "url")))))
        }
    }

    @Nested
    inner class Globals {
        @Test fun `a declared global may be used`() =
            assertOk(check(flow(actions = listOf(action("TOAST", "message" to "{{g:count}}")), globals = listOf("count"))))

        @Test fun `reading an undeclared global fails`() =
            assertFails(check(flow(actions = listOf(action("TOAST", "message" to "{{g:bank}}")))), "does not declare")

        @Test fun `writing an undeclared global fails`() = assertFails(
            check(flow(actions = listOf(action("SET_VARIABLE", "variable_name" to "g:bank", "value" to "0")))),
            "does not declare",
        )
    }

    @Nested
    inner class Limits {
        private fun repeat(count: String) = flow(
            actions = listOf(action("REPEAT_BLOCK", "count" to count), action("TOAST", "message" to "x"), action("END_REPEAT")),
        )

        @Test fun `a repeat of 100 passes`() = assertOk(check(repeat("100")))

        @Test fun `a repeat over 100 fails`() = assertFails(check(repeat("101")), "1..100")

        private fun notif(pattern: String) = flow(
            triggers = listOf(trigger("NOTIFICATION_RECEIVED", "match_mode" to "REGEX", "keyword" to pattern)),
        )

        @Test fun `a short valid regex passes`() = assertOk(check(notif("^OTP \\d{6}$")))

        @Test fun `an overlong regex fails`() = assertFails(check(notif("a".repeat(501))), "at most 500")

        @Test fun `an invalid regex fails`() = assertFails(check(notif("(unclosed")), "not a valid regex")
    }

    @Nested
    inner class Catalog {
        @Test fun `an unknown action type fails`() = assertFails(check(flow(actions = listOf(action("TELEPORT")))), "unknown action type")

        @Test fun `an unknown setting fails`() =
            assertFails(check(flow(actions = listOf(action("TOAST", "colour" to "red")))), "not a setting of TOAST")

        @Test fun `a value outside its dropdown fails`() =
            assertFails(check(flow(triggers = listOf(trigger("TIME", "time" to "07:30", "repeat" to "SOMETIMES")))), "is not one of")

        @Test fun `conditions are checked too`() = assertFails(
            check(flow(conditions = listOf(condition("BATTERY_LEVEL", "direction" to "SIDEWAYS", "level" to "20")))),
            "is not one of",
        )

        @Test fun `a flow must arrive switched off`() = assertFails(check(flow().copy(enabled = true)), "must be false")

        @Test fun `ids must be UUIDs in the store`() = assertFails(check(flow().copy(id = "123")), "UUID")
    }
}
