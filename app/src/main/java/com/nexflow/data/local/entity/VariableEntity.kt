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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Runtime-mutable variables for a Flow.
 *
 * default_value is always stored as a string; the repository converts it
 * to the correct Kotlin type based on [type].
 */
@Entity(
    tableName = "variables",
    foreignKeys = [
        ForeignKey(
            entity = FlowEntity::class,
            parentColumns = ["id"],
            childColumns = ["flow_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["flow_id"])],
)
data class VariableEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "flow_id") val flowId: String,
    val name: String,
    /** STRING | INTEGER | BOOLEAN | DECIMAL */
    val type: String,
    @ColumnInfo(name = "default_value") val defaultValue: String,
    /** Current runtime value; equals defaultValue until the flow sets it. */
    @ColumnInfo(name = "current_value") val currentValue: String,
)
