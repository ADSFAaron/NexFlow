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
package com.nexflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted representation of a Flow.
 *
 * Complex sub-structures (triggers, actions, conditions, variables) are stored as JSON strings
 * and converted to domain models by the repository layer. This avoids deep Room TypeConverters
 * and keeps schema migrations simple.
 */
@Entity(tableName = "flows")
data class FlowEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int = 1,
    val name: String,
    val description: String,
    val author: String?,
    /** Key into the built-in icon catalog; null = default icon */
    @ColumnInfo(defaultValue = "NULL") val icon: String? = null,
    /** ARGB hex background color, e.g. "#FF6750A4"; null = theme default */
    @ColumnInfo(name = "icon_color", defaultValue = "NULL") val iconColor: String? = null,
    /** Serialized as a JSON array of strings, e.g. ["morning","daily"] */
    val tags: String,
    val enabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** JSON array of TriggerJson objects */
    val triggers: String,
    @ColumnInfo(name = "trigger_logic") val triggerLogic: String,
    /** JSON array of ConditionJson objects */
    val conditions: String,
    /** JSON array of ActionJson objects */
    val actions: String,
    /** JSON array of VariableJson objects */
    val variables: String,
)
