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
package com.nexflow.ui.flowimport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.flowschema.ImportItemKind
import com.nexflow.core.flowschema.ImportWarning
import com.nexflow.core.flowschema.ImportWarnings
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/** Every warning code reads as a sentence in every language, never as its raw code. */
@RunWith(AndroidJUnit4::class)
class ImportWarningTextTest {

    private val codes = ImportWarnings::class.java.declaredFields
        .filter { it.type == String::class.java }
        .map { it.isAccessible = true; it.get(null) as String }

    @Test
    fun `every code has a title in every locale`() {
        for (locale in listOf("en", "zh-rTW", "zh-rCN", "ja")) {
            RuntimeEnvironment.setQualifiers(locale)
            val context = ApplicationProvider.getApplicationContext<Context>()
            codes.forEach { code ->
                assertNotEquals("$locale: $code has no text", code, ImportWarning(code).title(context))
            }
        }
    }

    @Test
    fun `a hidden type is named by its label, not its enum name`() {
        RuntimeEnvironment.setQualifiers("en")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val body = ImportWarning(
            ImportWarnings.NOT_IN_THIS_BUILD, listOf("SEND_SMS"), itemKind = ImportItemKind.ACTION,
        ).body(context)
        assertTrue(body, "“Send SMS”" in body)
    }
}
