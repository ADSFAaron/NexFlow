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

import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.trigger.TriggerHandler
import com.nexflow.executor.CallPhoneActionExecutor
import com.nexflow.executor.SendSmsActionExecutor
import com.nexflow.trigger.IncomingCallTriggerHandler
import com.nexflow.trigger.SmsReceivedTriggerHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * SMS / phone-call trigger handlers and action executors. Present only in the `github`
 * flavor; the `play` flavor ships without these bindings (and without the corresponding
 * permissions) because Google Play restricts SMS/Call permissions to default handler apps.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelephonyModule {

    @Binds @IntoSet
    abstract fun bindIncomingCallTrigger(impl: IncomingCallTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindSmsReceivedTrigger(impl: SmsReceivedTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindSendSms(impl: SendSmsActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindCallPhone(impl: CallPhoneActionExecutor): ActionExecutor
}
