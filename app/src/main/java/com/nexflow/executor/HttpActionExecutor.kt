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
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

class HttpActionExecutor @Inject constructor(
    private val httpClient: HttpClient,
) : ActionExecutor {

    override val supportedType = ActionType.HTTP_REQUEST

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val url = action.config["url"]?.takeIf { it.isNotBlank() }
            ?: return ActionResult.Failure("No URL specified")
        val method = action.config["method"]?.uppercase() ?: "GET"
        val body = action.config["body"]

        return try {
            when (method) {
                "GET" -> httpClient.get(url)
                "POST" -> httpClient.post(url) {
                    body?.let { setBody(it); contentType(ContentType.Application.Json) }
                }
                "PUT" -> httpClient.put(url) {
                    body?.let { setBody(it); contentType(ContentType.Application.Json) }
                }
                "DELETE" -> httpClient.delete(url)
                "PATCH" -> httpClient.patch(url) {
                    body?.let { setBody(it); contentType(ContentType.Application.Json) }
                }
                else -> return ActionResult.Failure("Unknown HTTP method: $method")
            }
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure("HTTP $method $url failed: ${e.message}", e)
        }
    }
}
