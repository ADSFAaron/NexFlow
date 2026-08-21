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
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import javax.inject.Inject

/**
 * Calls an HTTP endpoint and hands the result back to the flow as variables, which is what lets a
 * flow branch on live data: the status code always lands in [STATUS_VARIABLE], and the body (or
 * one field of it, see `json_path`) lands in the variable named by `response_var`.
 *
 * A non-2xx status is **not** a failure here. Failing would abort the run and take away the very
 * thing the status variable is for — letting the flow decide with an `IF_BLOCK` what a 404 or a
 * 500 should mean.
 */
class HttpActionExecutor @Inject constructor(
    private val httpClient: HttpClient,
) : ActionExecutor {

    override val supportedType = ActionType.HTTP_REQUEST

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val url = action.config["url"]?.takeIf { it.isNotBlank() }
            ?: return ActionResult.Failure("No URL specified")
        val methodName = action.config["method"]?.takeIf { it.isNotBlank() }?.uppercase() ?: "GET"
        val method = METHODS[methodName]
            ?: return ActionResult.Failure("Unknown HTTP method: $methodName")
        val headers = parseHeaders(action.config["headers"].orEmpty())
            .getOrElse { return ActionResult.Failure(it.message.orEmpty()) }
        val body = action.config["body"]?.takeIf { it.isNotBlank() }
        val jsonPath = action.config["json_path"]?.trim().orEmpty()

        val responseVar = action.config["response_var"]?.trim()?.takeIf { it.isNotBlank() }
        if (responseVar != null && responseVar.startsWith(FlowInterpreter.GLOBAL_PREFIX)) {
            // Only the interpreter's SET_VARIABLE path reports a global write back for persisting,
            // so writing one straight into the map here would look like it worked and then vanish
            // at the end of the run. Refuse instead of losing the value silently.
            return ActionResult.Failure(
                "\"$responseVar\" is a global variable, which this action cannot write. Store the " +
                    "response in a flow variable, then copy it over with a Set variable action.",
            )
        }

        return try {
            // Streamed rather than a plain httpClient.request(): a non-prepared call loads the
            // whole body into memory before returning, so RESPONSE_LIMIT_BYTES could not protect
            // anything. execute {} reads from the channel, and we stop at the limit.
            httpClient.prepareRequest(url) {
                this.method = method
                headers.forEach { (name, value) -> header(name, value) }
                if (body != null && method in BODY_METHODS) {
                    setBody(body)
                    // JSON is only the fallback; a Content-Type the user typed themselves wins.
                    if (headers.none { it.first.equals(HttpHeaders.ContentType, ignoreCase = true) }) {
                        contentType(ContentType.Application.Json)
                    }
                }
            }.execute { response ->
                variables[STATUS_VARIABLE] = response.status.value.toString()
                if (responseVar == null) {
                    return@execute ActionResult.Success
                }
                val text = readCapped(response)
                    .getOrElse { return@execute ActionResult.Failure(it.message.orEmpty()) }
                val value = JsonPath.extract(text, jsonPath)
                    .getOrElse { return@execute ActionResult.Failure("$url: ${it.message}") }
                variables[responseVar] = value
                ActionResult.Success
            }
        } catch (e: CancellationException) {
            // Cancelling a run must actually cancel it; a hung request swallowed as a Failure
            // would let the interpreter carry on to the next action.
            throw e
        } catch (e: Exception) {
            ActionResult.Failure("HTTP $methodName $url failed: ${e.message}", e)
        }
    }

    /**
     * Reads at most [RESPONSE_LIMIT_BYTES], and fails rather than truncating: half a JSON document
     * would fail to parse with a confusing message, and a silently cut string would compare wrong
     * in an expression. One extra byte is requested so "at the limit" and "over it" are telltale.
     */
    private suspend fun readCapped(response: HttpResponse): Result<String> {
        val bytes = response.bodyAsChannel().readRemaining(RESPONSE_LIMIT_BYTES + 1L).readByteArray()
        return when {
            bytes.size > RESPONSE_LIMIT_BYTES -> Result.failure(
                IllegalArgumentException(
                    "The response is larger than ${RESPONSE_LIMIT_BYTES / 1024} KB, too large to " +
                        "store in a variable. Ask the API for less data, or narrow it with a JSON path.",
                ),
            )

            else -> Result.success(bytes.decodeToString())
        }
    }

    /**
     * Parses the `headers` field, one `Name: Value` per line. A line without a colon is a typo,
     * and a request missing its auth header usually comes back as an unhelpful 401 — so say so
     * here instead, naming the line.
     */
    private fun parseHeaders(raw: String): Result<List<Pair<String, String>>> {
        val headers = mutableListOf<Pair<String, String>>()
        raw.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val colon = trimmed.indexOf(':')
            if (colon <= 0) {
                return Result.failure(
                    IllegalArgumentException("Header line \"$trimmed\" is not in \"Name: Value\" form"),
                )
            }
            headers += trimmed.take(colon).trim() to trimmed.substring(colon + 1).trim()
        }
        return Result.success(headers)
    }

    companion object {
        /**
         * Where the response status code is published, readable as `{{__http_status__}}`. Named
         * like [FlowInterpreter]'s other executor-written variables (`__menu_choice__`), and shared
         * by every HTTP action in a flow: it describes the request that just ran.
         */
        const val STATUS_VARIABLE = "__http_status__"

        /**
         * Generous for an API response, small enough that a wrong URL pointing at a download
         * cannot push megabytes into a variable — which is also written to the execution log and,
         * for a global, into the database.
         */
        const val RESPONSE_LIMIT_BYTES = 256 * 1024

        private val METHODS = mapOf(
            "GET" to HttpMethod.Get,
            "POST" to HttpMethod.Post,
            "PUT" to HttpMethod.Put,
            "DELETE" to HttpMethod.Delete,
            "PATCH" to HttpMethod.Patch,
        )

        private val BODY_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)
    }
}
