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
package com.nexflow.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises every schema migration against the JSON schemas exported to app/schemas.
 *
 * This is the only automated check that a NexFlow update installed over an existing
 * install still opens: [com.nexflow.di.DatabaseModule] builds the database *without*
 * fallbackToDestructiveMigration, so a migration Room cannot apply is not a silent data
 * wipe — it throws on first access and the app is dead on arrival for every existing user.
 *
 * Every migration so far is an [androidx.room.AutoMigration], which is exactly why they
 * need covering: nobody hand-writes the SQL, so nobody reviews it either. Room only
 * generates one when the schema delta is expressible without instructions (adding a column,
 * adding a table). The moment a change needs a @DeleteColumn/@RenameColumn/@RenameTable spec
 * — or a real Migration — and it is missing, that failure surfaces here rather than on a
 * user's phone.
 *
 * MigrationTestHelper reads the schemas from androidTest assets; the Room Gradle plugin's
 * schemaDirectory("$projectDir/schemas") in app/build.gradle.kts puts them there.
 */
@RunWith(AndroidJUnit4::class)
class NexFlowDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // Passing the database class (rather than an assets-folder string) is what lets the helper
    // resolve the generated AutoMigrations — the String overload cannot test them at all.
    @get:Rule
    val helper = MigrationTestHelper(instrumentation, NexFlowDatabase::class.java)

    @Test
    fun migrate1To2_addsIconColumnsAndKeepsFlows() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.insertV1Flow(id = "flow-a", name = "Morning routine")
        }

        // No Migration arguments: the AutoMigration generated for 1 -> 2 must cover this on its own.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        db.query("SELECT name, icon, icon_color FROM flows WHERE id = 'flow-a'").use { c ->
            assertEquals("the v1 row must survive the migration", 1, c.count)
            c.moveToFirst()
            assertEquals("Morning routine", c.getString(0))
            // v2 adds both columns as nullable with no default, so existing rows read back null.
            assertNull("icon must be null for a row created before v2", c.getString(1))
            assertNull("icon_color must be null for a row created before v2", c.getString(2))
        }
    }

    @Test
    fun migrate2To3_addsGlobalVariablesTable() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            // v2 already has the icon columns, so set them here — a row carrying real v2 data
            // is what the next migration has to preserve.
            db.insertV1Flow(id = "flow-b", name = "Battery saver")
            db.execSQL("UPDATE flows SET icon = 'Bolt', icon_color = '#FF9800' WHERE id = 'flow-b'")
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true)

        db.query("SELECT icon, icon_color FROM flows WHERE id = 'flow-b'").use { c ->
            c.moveToFirst()
            assertEquals("Bolt", c.getString(0))
            assertEquals("#FF9800", c.getString(1))
        }

        // The new table must exist and be writable — validate() only compares the schema.
        db.execSQL(
            "INSERT INTO global_variables (name, type, default_value, current_value) " +
                "VALUES ('counter', 'NUMBER', '0', '3')",
        )
        db.query("SELECT current_value FROM global_variables WHERE name = 'counter'").use { c ->
            c.moveToFirst()
            assertEquals("3", c.getString(0))
        }
    }

    @Test
    fun migrate3To4_addsAiConversationsTable() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insertV1Flow(id = "flow-d", name = "Home wifi")
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        db.execSQL(
            "INSERT INTO ai_conversations " +
                "(flow_id, messages_json, history_json, saved_flow_id, tokens_used, updated_at) " +
                "VALUES ('flow-d', '[]', '[]', NULL, 0, 1)",
        )
        db.query("SELECT COUNT(*) FROM ai_conversations").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun migrate4To5_addsExecutionStepsTableAndKeepsLogs() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.insertV1Flow(id = "flow-f", name = "Weather check")
            db.execSQL(
                "INSERT INTO execution_logs " +
                    "(id, flow_id, triggered_at, status, error_message, execution_duration_ms) " +
                    "VALUES ('log-2', 'flow-f', 1700000000000, 'FAIL', 'boom', 7)",
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true)

        // Runs recorded before v5 have no steps, and the detail screen must render that as
        // "this run predates step logging" rather than appearing to have executed nothing.
        db.query("SELECT COUNT(*) FROM execution_steps WHERE log_id = 'log-2'").use { c ->
            c.moveToFirst()
            assertEquals("a pre-v5 run must migrate to zero steps, not fail", 0, c.getInt(0))
        }

        db.execSQL(
            "INSERT INTO execution_steps " +
                "(log_id, seq, action_id, action_type, depth, iteration, status, " +
                "error_message, note, resolved_config, duration_ms) " +
                "VALUES ('log-2', 0, 'act-1', 'HTTP_REQUEST', 0, 0, 'FAIL', 'no url', NULL, NULL, 3)",
        )
        db.query("SELECT action_type FROM execution_steps WHERE log_id = 'log-2' AND seq = 0").use { c ->
            c.moveToFirst()
            assertEquals("HTTP_REQUEST", c.getString(0))
        }

        // The cascade is what keeps this table bounded — LogPrunerWorker only deletes parent logs,
        // so a cascade that is declared but not enforced leaks a row per step, forever.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM execution_logs WHERE id = 'log-2'")
        db.query("SELECT COUNT(*) FROM execution_steps").use { c ->
            c.moveToFirst()
            assertEquals("steps must be deleted with the run they belong to", 0, c.getInt(0))
        }
    }

    /**
     * The upgrade path an actual user takes: 1.0 installed, every later release applied in one
     * go. Chained AutoMigrations can validate individually and still break in sequence.
     */
    @Test
    fun migrateAllFrom1To5_preservesUserData() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.insertV1Flow(id = "flow-e", name = "Work mode")
            db.execSQL(
                "INSERT INTO variables (id, flow_id, name, type, default_value, current_value) " +
                    "VALUES ('var-1', 'flow-e', 'threshold', 'NUMBER', '20', '15')",
            )
            db.execSQL(
                "INSERT INTO execution_logs " +
                    "(id, flow_id, triggered_at, status, error_message, execution_duration_ms) " +
                    "VALUES ('log-1', 'flow-e', 1700000000000, 'SUCCESS', NULL, 42)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, LATEST_VERSION, true).close()

        // Reopen through the real builder — this is the code path DatabaseModule uses, and it
        // makes Room compare the migrated database's identity hash against the latest schema. A hash
        // mismatch here means the AutoMigrations produced something that only *looks* right.
        val db = Room.databaseBuilder(
            instrumentation.targetContext,
            NexFlowDatabase::class.java,
            TEST_DB,
        ).build()

        try {
            db.openHelper.readableDatabase
                .query("SELECT name FROM flows WHERE id = 'flow-e'").use { c ->
                    c.moveToFirst()
                    assertEquals("Work mode", c.getString(0))
                }
            db.openHelper.readableDatabase
                .query("SELECT current_value FROM variables WHERE id = 'var-1'").use { c ->
                    c.moveToFirst()
                    assertEquals("a flow's variable state must survive four upgrades", "15", c.getString(0))
                }
            db.openHelper.readableDatabase
                .query("SELECT execution_duration_ms FROM execution_logs WHERE id = 'log-1'").use { c ->
                    c.moveToFirst()
                    assertEquals(42, c.getInt(0))
                }
        } finally {
            db.close()
        }
    }

    /**
     * A fresh install must land on the same schema as an upgraded one. Room derives the
     * identity hash from the entity classes, so this fails if the latest schema's exported JSON has
     * drifted from the @Entity definitions — the situation where a developer changed an
     * entity and bumped the version but never regenerated the schema file.
     */
    @Test
    fun freshInstallOpensAtLatestSchema() {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
        val db = Room.databaseBuilder(
            instrumentation.targetContext,
            NexFlowDatabase::class.java,
            TEST_DB,
        ).build()
        try {
            assertEquals(LATEST_VERSION, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    private fun SupportSQLiteDatabase.insertV1Flow(id: String, name: String) {
        execSQL(
            """
            INSERT INTO flows (
                id, schema_version, name, description, author, tags, enabled,
                created_at, updated_at, triggers, trigger_logic, conditions, actions, variables
            ) VALUES (
                '$id', 1, '$name', '', NULL, '[]', 1,
                1700000000000, 1700000000000, '[]', 'ANY', '[]', '[]', '[]'
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val TEST_DB = "nexflow-migration-test.db"
        const val LATEST_VERSION = 5
    }
}
