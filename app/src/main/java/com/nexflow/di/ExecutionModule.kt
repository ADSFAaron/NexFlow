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
import com.nexflow.executor.AirplaneModeActionExecutor
import com.nexflow.executor.BluetoothActionExecutor
import com.nexflow.executor.BrightnessActionExecutor
import com.nexflow.executor.ClipboardActionExecutor
import com.nexflow.executor.DelayActionExecutor
import com.nexflow.executor.DndActionExecutor
import com.nexflow.executor.HttpActionExecutor
import com.nexflow.executor.MediaActionExecutor
import com.nexflow.executor.MenuActionExecutor
import com.nexflow.executor.NotificationActionExecutor
import com.nexflow.executor.LaunchShortcutActionExecutor
import com.nexflow.executor.OpenAppActionExecutor
import com.nexflow.executor.OpenUrlActionExecutor
import com.nexflow.executor.ScreenshotActionExecutor
import com.nexflow.executor.SetWallpaperActionExecutor
import com.nexflow.executor.ShareActionExecutor
import com.nexflow.executor.ToastActionExecutor
import com.nexflow.executor.TtsActionExecutor
import com.nexflow.executor.VolumeActionExecutor
import com.nexflow.executor.WifiActionExecutor
import com.nexflow.executor.WriteFileActionExecutor
import com.nexflow.trigger.AppLaunchTriggerHandler
import com.nexflow.trigger.BatteryTriggerHandler
import com.nexflow.trigger.BluetoothTriggerHandler
import com.nexflow.trigger.BootTriggerHandler
import com.nexflow.trigger.GeofenceTriggerHandler
import com.nexflow.trigger.HeadsetTriggerHandler
import com.nexflow.trigger.ManualTriggerHandler
import com.nexflow.trigger.NfcTagTriggerHandler
import com.nexflow.trigger.NotificationTriggerHandler
import com.nexflow.trigger.ScreenTriggerHandler
import com.nexflow.trigger.TimeTriggerHandler
import com.nexflow.trigger.WifiTriggerHandler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExecutionModule {

    // ----- TriggerHandlers -----

    @Binds @IntoSet
    abstract fun bindManualTrigger(impl: ManualTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindTimeTrigger(impl: TimeTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindBatteryTrigger(impl: BatteryTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindScreenTrigger(impl: ScreenTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindBootTrigger(impl: BootTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindBluetoothTrigger(impl: BluetoothTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindWifiTrigger(impl: WifiTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindAppLaunchTrigger(impl: AppLaunchTriggerHandler): TriggerHandler

    // IncomingCall + SmsReceived trigger bindings live in the `github` flavor (TelephonyModule).

    @Binds @IntoSet
    abstract fun bindNotificationTrigger(impl: NotificationTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindHeadsetTrigger(impl: HeadsetTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindGeofenceTrigger(impl: GeofenceTriggerHandler): TriggerHandler

    @Binds @IntoSet
    abstract fun bindNfcTagTrigger(impl: NfcTagTriggerHandler): TriggerHandler

    // ----- ActionExecutors -----

    @Binds @IntoSet
    abstract fun bindToast(impl: ToastActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindNotification(impl: NotificationActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindDelay(impl: DelayActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindTts(impl: TtsActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindClipboard(impl: ClipboardActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindOpenUrl(impl: OpenUrlActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindOpenApp(impl: OpenAppActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindLaunchShortcut(impl: LaunchShortcutActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindVolume(impl: VolumeActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindHttp(impl: HttpActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindMedia(impl: MediaActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindWifi(impl: WifiActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindBluetooth(impl: BluetoothActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindBrightness(impl: BrightnessActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindDnd(impl: DndActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindAirplaneMode(impl: AirplaneModeActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindScreenshot(impl: ScreenshotActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindSetWallpaper(impl: SetWallpaperActionExecutor): ActionExecutor

    // SendSms + CallPhone action bindings live in the `github` flavor (TelephonyModule).

    @Binds @IntoSet
    abstract fun bindWriteFile(impl: WriteFileActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindShare(impl: ShareActionExecutor): ActionExecutor

    @Binds @IntoSet
    abstract fun bindMenu(impl: MenuActionExecutor): ActionExecutor

    companion object {
        @Provides
        @Singleton
        fun provideHttpClient(): HttpClient = HttpClient(Android) {
            install(ContentNegotiation) { json() }
            // Without timeouts a hung request blocks the executor coroutine (and the
            // foreground service) indefinitely. Bound every HTTP action.
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}
