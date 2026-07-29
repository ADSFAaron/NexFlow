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
 * A global (cross-flow) variable. Unlike [VariableEntity] it has no flow_id — the name is the
 * primary key and its [currentValue] persists between runs so flows can share state.
 */
@Entity(tableName = "global_variables")
data class GlobalVariableEntity(
    @PrimaryKey val name: String,
    /** STRING | INTEGER | BOOLEAN | DECIMAL */
    val type: String,
    @ColumnInfo(name = "default_value") val defaultValue: String,
    /** Current runtime value; equals defaultValue until a flow sets it. */
    @ColumnInfo(name = "current_value") val currentValue: String,
)
