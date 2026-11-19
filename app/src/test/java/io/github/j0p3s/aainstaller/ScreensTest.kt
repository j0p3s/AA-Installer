/*
 * AA Installer - install Android Auto apps as Play Store apps
 * Copyright (C) 2026 j0p3s
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.j0p3s.aainstaller

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    private val noop = object : Actions {
        override fun shizuku() {}
        override fun openAndroidAuto() {}
        override fun openAppInfo(pkg: String) {}
        override fun openDeveloperOptions() {}
        override fun openUrl(url: String) {}
        override fun launch(pkg: String) {}
    }

    private fun entry(label: String, pkg: String, inst: String?, aa: Boolean = true, car: Boolean = false) =
        AppEntry(ApplicationInfo().apply { packageName = pkg }, label, pkg, inst, aa, true, false, car)

    private fun model(granted: Boolean = true) = AppModel(ApplicationProvider.getApplicationContext<Application>()).apply {
        privilege = if (granted) Privilege.GRANTED else Privilege.NOT_RUNNING
        apps = listOf(
            entry("Spotify", "com.spotify.music", Installer.PLAY_STORE),
            entry("Sygic", "com.sygic.aura", Installer.PLAY_STORE),
            entry("Waze", "com.waze", Installer.PLAY_STORE, car = true),
            entry("YouTube Music", "app.morphe.android.apps.youtube.music", "app.morphe.manager"),
            entry("SmartThings", "com.samsung.android.oneconnect", "com.google.android.packageinstaller"),
            entry("VLC", "org.videolan.vlc", Installer.PLAY_STORE),
            entry("Firefox", "org.mozilla.firefox", Installer.PLAY_STORE, aa = false),
        )
    }

    private fun shot(name: String) = rule.onRoot().captureRoboImage(File("/tmp/shots/$name.png").path)
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
        onNode(androidx.compose.ui.test.isRoot())

    @Test fun install_empty() {
        val vm = model(granted = false)
        rule.setContent { AppTheme { AppRoot(vm, noop) } }
        shot("1_install_empty")
    }

    @Test fun install_picked() {
        val vm = model()
        vm.picked = Installer.Package().apply {
            label = "Waze"; packageName = "com.waze"; version = "5.12.0.2 (1050)"; androidAuto = true
            apks.add(File("base.apk")); apks.add(File("split_config.arm64_v8a.apk"))
        }
        vm.pickedInstaller = Installer.PLAY_STORE
        vm.carEnable = true
        rule.setContent { AppTheme { AppRoot(vm, noop) } }
        shot("2_install_picked")
    }

    @Test fun car_tab() {
        val vm = model()
        rule.setContent { AppTheme { AppRoot(vm, noop) } }
        rule.onNodeWithText("Car copy").performClick()
        shot("3_car_copy")
    }

    @Test fun fix_tab() {
        val vm = model()
        rule.setContent { AppTheme { AppRoot(vm, noop) } }
        rule.onNodeWithText("Fix apps").performClick()
        shot("4_fix")
    }

    @Test fun setup_tab() {
        val vm = model()
        rule.setContent { AppTheme { AppRoot(vm, noop) } }
        rule.onNodeWithText("Setup").performClick()
        shot("5_setup")
    }

    @Test fun job_sheet() {
        val vm = model()
        vm.job = Job("Car copy of Waze", null).apply {
            state = JobState.SUCCESS; step = "Done"
            message = "Registered as a Play Store install. Reconnect to Android Auto to see it."
            log.addAll(listOf("▶ Getting shell access", "Using Shizuku (adb)", "▶ Building Waze (Car)",
                "  Reading 182 MB in 3 APK(s)", "  Car-enabled: com.waze.FreeMapAppActivity", "  Renamed package com.waze -> com.waze.car",
                "  Signing", "  Verifying signatures", "▶ Installing as a Play Store app",
                "$ pm install-commit 1294811", "Success", "✅ Installer is now com.android.vending"))
        }
        rule.setContent { AppTheme { AppRoot(vm, noop) } }
        rule.waitForIdle()
        shot("6_progress_sheet")
    }

    @Test fun uninstall_prompt() {
        val vm = model()
        vm.picked = Installer.Package().apply {
            label = "Waze"; packageName = "com.waze"; version = "5.12.0.2 (1050)"; androidAuto = true
            apks.add(File("base.apk"))
        }
        vm.pickedInstaller = Installer.PLAY_STORE
        vm.askUninstall = UninstallAsk("Waze", "com.waze",
            "The installed Waze is signed with a different key than this APK, so Android can't update it.") {}
        rule.setContent { AppTheme { AppRoot(vm, noop) } }
        shot("7_uninstall_prompt")
    }

    @Test fun failed_retry() {
        val vm = model()
        vm.job = Job("Installing Waze", null).apply {
            state = JobState.FAILED; step = "Failed"
            message = "The installed version is signed with a different key, so Android won't update it. Uninstall the installed app (its data is lost), then install again."
            retryWithUninstall = {}
            log.addAll(listOf("▶ Getting shell access", "Using Shizuku (adb)", "▶ Installing as a Play Store app",
                "$ pm install-commit 1294812", "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.waze signatures do not match newer version; ignoring!]"))
        }
        rule.setContent { AppTheme { AppRoot(vm, noop) } }
        shot("8_failed_retry")
    }
}
