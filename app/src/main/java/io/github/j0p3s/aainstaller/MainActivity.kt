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

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import rikka.shizuku.Shizuku

/** Things the UI asks the system to do. */
interface Actions {
    fun shizuku()
    fun openAndroidAuto()
    fun openAppInfo(pkg: String)
    fun openDeveloperOptions()
    fun openUrl(url: String)
    fun launch(pkg: String)
}

class MainActivity : ComponentActivity(), Actions {

    private val vm: AppModel by viewModels()

    private val binderReceived = Shizuku.OnBinderReceivedListener { runOnUiThread { vm.refreshPrivilege() } }
    private val binderDead = Shizuku.OnBinderDeadListener { runOnUiThread { vm.refreshPrivilege() } }
    private val permResult = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { vm.refreshPrivilege() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permResult)
        vm.refreshPrivilege()
        vm.refreshApps()
        vm.loadCrash()
        setContent { AppTheme { AppRoot(vm, this) } }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPrivilege()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permResult)
        super.onDestroy()
    }

    private fun handleIntent(i: Intent?) {
        val uri: Uri? = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        uri?.let(vm::load)
    }

    // ---------------------------------------------------------------- Actions

    override fun shizuku() {
        when {
            Shell.shizukuRunning() ->
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    toast("Permission was denied. Allow AA Installer inside the Shizuku app.")
                    launch(SHIZUKU_PKG)
                } else {
                    Shizuku.requestPermission(42)
                }
            vm.isInstalled(SHIZUKU_PKG) -> launch(SHIZUKU_PKG)
            else -> openUrl("https://github.com/RikkaApps/Shizuku/releases")
        }
    }

    override fun openAndroidAuto() {
        if (!vm.isInstalled(AA_PKG)) {
            toast("Android Auto is not installed")
            return
        }
        val components = listOf(
            "$AA_PKG/com.google.android.apps.auto.carservice.service.impl.CarServiceSettingsActivity",
            "$AA_PKG/com.google.android.projection.gearhead.companion.settings.SettingsActivity",
        )
        for (c in components) {
            try {
                startActivity(Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(c)))
                toast("Tap 'Version' 10× → ⋮ → Developer settings → Unknown sources")
                return
            } catch (_: Exception) {
            }
        }
        packageManager.getLaunchIntentForPackage(AA_PKG)?.let { startActivity(it); return }
        toast("Open Settings → Connected devices → Android Auto")
        openAppInfo(AA_PKG)
    }

    override fun openAppInfo(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        } catch (_: Exception) {
            toast("Cannot open app info")
        }
    }

    override fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: Exception) {
            toast("Enable Developer options first (tap Build number 7×)")
        }
    }

    override fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast(url)
        }
    }

    override fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i) else openAppInfo(pkg)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    companion object {
        const val AA_PKG = "com.google.android.projection.gearhead"
        const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    }
}
