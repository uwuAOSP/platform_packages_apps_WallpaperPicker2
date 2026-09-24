/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.customization.picker.clock.data.repository

import android.content.Context
import android.view.LayoutInflater
import com.android.systemui.plugins.PluginManager
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.systemui.shared.clocks.DefaultClockProvider
import com.android.systemui.shared.plugins.PluginEnabler
import com.android.systemui.shared.plugins.PluginManagerImpl
import com.android.systemui.shared.system.UncaughtExceptionPreHandlerManager_Factory
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** Provides a clock registry that discovers every installed clock provider plugin. */
class ClockRegistryProvider(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val backgroundDispatcher: CoroutineDispatcher,
) {
    private val clockRegistry: ClockRegistry by lazy {
        ClockRegistry(
            context,
            createPluginManager(context),
            coroutineScope,
            mainDispatcher,
            backgroundDispatcher,
            handleAllUsers = false,
            DefaultClockProvider(
                layoutInflater = LayoutInflater.from(context),
                resources = context.resources,
                vibrator = null,
            ),
            keepAllLoaded = true,
            subTag = "Picker",
        )
    }

    init {
        // Listeners in ClockRegistry get cleaned up when app ended.
        clockRegistry.registerListeners()
    }

    fun get() = clockRegistry

    private fun createPluginManager(context: Context): PluginManager {
        return PluginManagerImpl.create(
            context,
            // ClockProviderPlugin instances are identified by their action. They do not need to
            // be duplicated in a package-name allowlist when running as system applications.
            emptyList(),
            PluginEnabler.AlwaysEnabled(),
            Executors.newSingleThreadExecutor(),
            UncaughtExceptionPreHandlerManager_Factory.create().get(),
        )
    }
}
