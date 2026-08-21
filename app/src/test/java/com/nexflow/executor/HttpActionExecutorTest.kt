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

import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.interpreter.InterpreterResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerLogic
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpActionExecutorTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun executor(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "{}",
        contentType: String = "application/json",
    ): HttpActionExecutor {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = ByteReadChannel(body.toByteArray()),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, contentType),
            )
        }
        return HttpActionExecutor(HttpClient(engine))
    }

    private fun action(vararg config: Pair<String, String>) =
        Action(id = "a1", type = ActionType.HTTP_REQUEST, config = config.toMap(), order = 0, enabled = true)

    private fun assertFailureContains(result: ActionResult, substring: String) {
        val failure = assertInstanceOf(ActionResult.Failure::class.java, result)
        assertTrue(substring in failure.message, "expected \"$substring\" in: ${failure.message}")
    }

    @Test
    fun `the status code is always published`() = runTest {
        val variables = mutableMapOf<String, String>()

        val result = executor(status = HttpStatusCode.Created)
            .execute(action("url" to "https://api.test/x"), variables)

        assertEquals(ActionResult.Success, result)
        assertEquals("201", variables[HttpActionExecutor.STATUS_VARIABLE])
    }

    /**
     * The whole reason the status is a variable: a flow has to be able to decide for itself what a
     * 404 means. Failing the action would abort the run before any IF_BLOCK could look.
     */
    @Test
    fun `an error status is not a failure but is readable`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound, "nope") }
        val variables = mutableMapOf<String, String>()

        val result = HttpActionExecutor(HttpClient(engine))
            .execute(action("url" to "https://api.test/missing"), variables)

        assertEquals(ActionResult.Success, result)
        assertEquals("404", variables[HttpActionExecutor.STATUS_VARIABLE])
    }

    @Test
    fun `the body is stored under response_var when no json path is given`() = runTest {
        val variables = mutableMapOf<String, String>()

        executor(body = """{"a":1}""").execute(
            action("url" to "https://api.test/x", "response_var" to "raw"),
            variables,
        )

        assertEquals("""{"a":1}""", variables["raw"])
    }

    @Test
    fun `a json path narrows the stored value to one field`() = runTest {
        val variables = mutableMapOf<String, String>()

        val result = executor(body = """{"main":{"temp":31.2}}""").execute(
            action(
                "url" to "https://api.test/weather",
                "response_var" to "temp",
                "json_path" to "main.temp",
            ),
            variables,
        )

        assertEquals(ActionResult.Success, result)
        assertEquals("31.2", variables["temp"])
    }

    @Test
    fun `a json path that does not match fails with the url and the reason`() = runTest {
        val variables = mutableMapOf<String, String>()

        val result = executor(body = """{"main":{"temp":31.2}}""").execute(
            action(
                "url" to "https://api.test/weather",
                "response_var" to "temp",
                "json_path" to "main.pressure",
            ),
            variables,
        )

        assertFailureContains(result, "https://api.test/weather")
        assertFailureContains(result, "pressure")
        assertNull(variables["temp"])
        // The status still made it through: the request itself did succeed.
        assertEquals("200", variables[HttpActionExecutor.STATUS_VARIABLE])
    }

    @Test
    fun `nothing is stored when no response_var is named`() = runTest {
        val variables = mutableMapOf<String, String>()

        executor(body = """{"a":1}""").execute(action("url" to "https://api.test/x"), variables)

        assertEquals(setOf(HttpActionExecutor.STATUS_VARIABLE), variables.keys)
    }

    @Test
    fun `headers are sent as typed`() = runTest {
        executor().execute(
            action(
                "url" to "https://api.test/x",
                "headers" to "Authorization: Bearer abc123\n\nX-Api-Key:  k1  ",
            ),
            mutableMapOf(),
        )

        val sent = requests.single().headers
        assertEquals("Bearer abc123", sent["Authorization"])
        assertEquals("k1", sent["X-Api-Key"])
    }

    @Test
    fun `a header line without a colon is rejected before the request goes out`() = runTest {
        val result = executor().execute(
            action("url" to "https://api.test/x", "headers" to "Authorization Bearer abc"),
            mutableMapOf(),
        )

        assertFailureContains(result, "Name: Value")
        assertTrue(requests.isEmpty(), "no request should have been sent")
    }

    @Test
    fun `a post sends the body as json by default`() = runTest {
        executor().execute(
            action("url" to "https://api.test/x", "method" to "POST", "body" to """{"k":1}"""),
            mutableMapOf(),
        )

        val content = assertInstanceOf(TextContent::class.java, requests.single().body)
        assertEquals("""{"k":1}""", content.text)
        assertEquals("application/json", content.contentType.toString())
    }

    /** Plenty of APIs only accept form encoding; the typed header has to win over the default. */
    @Test
    fun `a Content-Type header overrides the json default`() = runTest {
        executor().execute(
            action(
                "url" to "https://api.test/x",
                "method" to "POST",
                "body" to "k=1",
                "headers" to "Content-Type: application/x-www-form-urlencoded",
            ),
            mutableMapOf(),
        )

        val contentType = requests.single().let { it.body.contentType ?: it.headers[HttpHeaders.ContentType] }
        assertTrue(
            contentType.toString().startsWith("application/x-www-form-urlencoded"),
            "got $contentType",
        )
    }

    @Test
    fun `a response over the size cap fails instead of storing a truncated value`() = runTest {
        val variables = mutableMapOf<String, String>()
        val oversized = "x".repeat(HttpActionExecutor.RESPONSE_LIMIT_BYTES + 1)

        val result = executor(body = oversized).execute(
            action("url" to "https://api.test/big", "response_var" to "blob"),
            variables,
        )

        assertFailureContains(result, "larger than")
        assertNull(variables["blob"])
    }

    /** Exactly at the cap is still fine — the limit is not off by one. */
    @Test
    fun `a response at the size cap is stored`() = runTest {
        val variables = mutableMapOf<String, String>()
        val atLimit = "x".repeat(HttpActionExecutor.RESPONSE_LIMIT_BYTES)

        val result = executor(body = atLimit).execute(
            action("url" to "https://api.test/big", "response_var" to "blob"),
            variables,
        )

        assertEquals(ActionResult.Success, result)
        assertEquals(HttpActionExecutor.RESPONSE_LIMIT_BYTES, variables["blob"]?.length)
    }

    /**
     * Executors cannot report a global write back for persisting, so writing one would look like it
     * worked and then be dropped at the end of the run.
     */
    @Test
    fun `a global response_var is refused rather than silently dropped`() = runTest {
        val result = executor().execute(
            action("url" to "https://api.test/x", "response_var" to "g:temp"),
            mutableMapOf(),
        )

        assertFailureContains(result, "global")
        assertTrue(requests.isEmpty(), "no request should have been sent")
    }

    @Test
    fun `an unknown method is rejected`() = runTest {
        val result = executor().execute(
            action("url" to "https://api.test/x", "method" to "FETCH"),
            mutableMapOf(),
        )

        assertFailureContains(result, "FETCH")
    }

    @Test
    fun `a missing url is rejected`() = runTest {
        assertFailureContains(executor().execute(action(), mutableMapOf()), "No URL")
    }

    /**
     * The whole point of the feature, end to end through the interpreter: fetch, pick one field out
     * of the reply, and let an IF_BLOCK route on it. Proves the variable the executor writes is the
     * same map the expression is later evaluated against.
     */
    @Test
    fun `a fetched field drives an IF_BLOCK`() = runTest {
        val taken = mutableListOf<String>()
        val recorder = object : ActionExecutor {
            override val supportedType = ActionType.TOAST
            override suspend fun execute(
                action: Action,
                variables: MutableMap<String, String>,
            ): ActionResult {
                taken += action.config.getValue("message")
                return ActionResult.Success
            }
        }
        val http = executor(body = """{"main":{"temp":31.2}}""")
        val interpreter = FlowInterpreter(
            mapOf(ActionType.HTTP_REQUEST to http, ActionType.TOAST to recorder),
        )

        val result = interpreter.execute(
            flowOf(
                Action("a1", ActionType.HTTP_REQUEST, order = 0, enabled = true, config = mapOf(
                    "url" to "https://api.test/weather",
                    "response_var" to "temp",
                    "json_path" to "main.temp",
                )),
                Action("a2", ActionType.IF_BLOCK, mapOf("expression" to "{{temp}} > 30"), 1, true),
                Action("a3", ActionType.TOAST, mapOf("message" to "hot: {{temp}}"), 2, true),
                Action("a4", ActionType.ELSE_BLOCK, emptyMap(), 3, true),
                Action("a5", ActionType.TOAST, mapOf("message" to "mild"), 4, true),
                Action("a6", ActionType.END_IF, emptyMap(), 5, true),
            ),
        )

        assertInstanceOf(InterpreterResult.Success::class.java, result)
        assertEquals(listOf("hot: 31.2"), taken)
    }

    private fun flowOf(vararg actions: Action) = Flow(
        id = "f1",
        schemaVersion = 1,
        name = "weather",
        description = "",
        author = null,
        tags = emptyList(),
        enabled = true,
        createdAt = 0,
        updatedAt = 0,
        triggers = emptyList(),
        triggerLogic = TriggerLogic.ANY,
        conditions = emptyList(),
        actions = actions.toList(),
        variables = emptyList(),
    )
}
