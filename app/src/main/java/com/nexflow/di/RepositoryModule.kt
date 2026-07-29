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
package com.nexflow.di

import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.data.repository.FlowRepositoryImpl
import com.nexflow.data.repository.GlobalVariableRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFlowRepository(impl: FlowRepositoryImpl): FlowRepository

    @Binds
    @Singleton
    abstract fun bindGlobalVariableRepository(impl: GlobalVariableRepositoryImpl): GlobalVariableRepository
}
