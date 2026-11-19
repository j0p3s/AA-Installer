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

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.carbridge.patch.PatchPipeline

// ===================================================================== icons

/**
 * The car + download glyph, built in code. Loading the same vector from XML with
 * painterResource() crashed natively (SIGSEGV in XmlBlock) on nubia/RedMagic Android 16.
 */
val CarIcon: ImageVector by lazy {
    fun path(b: ImageVector.Builder, d: String, evenOdd: Boolean = false) = b.addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        fill = SolidColor(Color.Black),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
    )
    ImageVector.Builder("Car", 24.dp, 24.dp, 108f, 108f).apply {
        // Body with the windscreen and wheels cut out
        path(
            this,
            "M36,62 L40,50 Q41.5,46 46,46 L62,46 Q66.5,46 68,50 L72,62 Q76,63 76,67 L76,74 Q76,76 74,76 L71,76 L71,79 " +
                "Q71,81 69,81 L66,81 Q64,81 64,79 L64,76 L44,76 L44,79 Q44,81 42,81 L39,81 Q37,81 37,79 L37,76 L34,76 " +
                "Q32,76 32,74 L32,67 Q32,63 36,62 Z M42.5,61 L45,52 Q45.6,50 47.5,50 L60.5,50 Q62.4,50 63,52 L65.5,61 Z " +
                "M38,68 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 Z M64,68 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 Z",
            evenOdd = true,
        )
        path(this, "M51,24 L57,24 L57,34 L62,34 L54,42 L46,34 L51,34 Z")
    }.build()
}

// ===================================================================== theme

private val Blue = Color(0xFF1A73E8)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF8AB4F8))
        else -> lightColorScheme(primary = Blue)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun okColor() = if (isSystemInDarkTheme()) Color(0xFF81C995) else Color(0xFF188038)

@Composable
private fun warnColor() = if (isSystemInDarkTheme()) Color(0xFFFDD663) else Color(0xFFB06000)

private enum class Tab(val label: String, val icon: ImageVector) {
    INSTALL("Install", Icons.Filled.Add),
    CAR("Car copy", Icons.Filled.Build),
    FIX("Fix apps", Icons.Filled.CheckCircle),
    SETUP("Setup", Icons.Filled.Settings),
}

// ====================================================================== root

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppModel, actions: Actions) {
    var tab by rememberSaveable { mutableStateOf(Tab.INSTALL) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(vm.picked) { if (vm.picked != null || vm.loading) tab = Tab.INSTALL }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AA Installer", fontWeight = FontWeight.SemiBold)
                        Text(
                            "v${BuildConfig.VERSION_NAME} · " + when (vm.privilege) {
                                Privilege.GRANTED -> "Shizuku ready"
                                Privilege.NEEDS_PERMISSION -> "Shizuku needs permission"
                                Privilege.NOT_RUNNING -> "No Shizuku (root fallback)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (vm.privilege == Privilege.GRANTED) okColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Log")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.INSTALL -> InstallScreen(vm, actions)
                Tab.CAR -> CarScreen(vm, actions)
                Tab.FIX -> FixScreen(vm, actions)
                Tab.SETUP -> SetupScreen(vm, actions)
            }
        }
    }

    vm.job?.let { JobSheet(it, vm, actions) }
    vm.askUninstall?.let { UninstallDialog(it, vm) }
    vm.lastCrash?.let { CrashDialog(it, vm) }
    if (showHistory) HistorySheet(vm.history) { showHistory = false }
}

// ============================================================ shared pieces

@Composable
private fun PrivilegeBanner(vm: AppModel, actions: Actions) {
    if (vm.privilege == Privilege.GRANTED) return
    val needsPerm = vm.privilege == Privilege.NEEDS_PERMISSION
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (needsPerm) "Allow Shizuku access" else "Shizuku isn't running",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    if (needsPerm) "Needed to install apps as Play Store apps."
                    else "Start it via Wireless debugging. Rooted phones use root instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = actions::shizuku) { Text(if (needsPerm) "Allow" else "Open") }
        }
    }
}

@Composable
private fun AppIcon(bitmap: ImageBitmap?, size: Int = 44) {
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)))
    } else {
        Box(
            Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun LazyAppIcon(vm: AppModel, e: AppEntry, size: Int = 44) {
    val icon by produceState<ImageBitmap?>(null, e.pkg) {
        value = withContext(Dispatchers.IO) { vm.loadIcon(e.info) }
    }
    AppIcon(icon, size)
}

@Composable
private fun StatusChip(text: String, color: Color) {
    SuggestionChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        colors = SuggestionChipDefaults.suggestionChipColors(labelColor = color),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun CarOptions(vm: AppModel) {
    SwitchRow(
        "Fill the car screen",
        "Removes portrait-only locks so screens fill the wide display.",
        vm.fillScreen,
    ) { vm.fillScreen = it }
    SwitchRow(
        "Keep media services",
        "Music and podcast apps keep Android Auto's audio player instead of the full app.",
        vm.keepMedia,
    ) { vm.keepMedia = it }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, "Clear") }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

// =================================================================== install

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstallScreen(vm: AppModel, actions: Actions) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::load)
    }
    val pick = {
        picker.launch(
            arrayOf(
                "application/vnd.android.package-archive", "application/octet-stream",
                "application/zip", "application/x-zip-compressed", "application/vnd.apkm",
            ),
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrivilegeBanner(vm, actions)
        val p = vm.picked
        when {
            vm.loading -> OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Reading the file…")
                }
            }

            p == null -> OutlinedCard(onClick = pick, modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Choose an app to install", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "APK, APKS, XAPK or APKM. You can also share or open an APK from your file manager.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    vm.pickError?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            else -> {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(vm.pickedIcon, 56)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Version ${p.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = vm::clearPicked) { Icon(Icons.Filled.Clear, "Clear") }
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (p.androidAuto) StatusChip("Android Auto ✓", okColor())
                            else StatusChip("No Android Auto support", warnColor())
                            if (p.apks.size > 1) StatusChip("${p.apks.size} parts", MaterialTheme.colorScheme.onSurfaceVariant)
                            vm.pickedInstaller?.let {
                                StatusChip(
                                    if (it == Installer.PLAY_STORE) "Installed · Play Store" else "Installed · $it",
                                    if (it == Installer.PLAY_STORE) okColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SwitchRow(
                            "Car-enable before installing",
                            "CarBridge patch: shows the full app on the car screen. Re-signs the APK, so uninstall an existing copy first.",
                            vm.carEnable,
                        ) { vm.carEnable = it }
                        AnimatedVisibility(vm.carEnable) {
                            Column {
                                HorizontalDivider()
                                CarOptions(vm)
                            }
                        }
                    }
                }

                if (!p.androidAuto && !vm.carEnable) {
                    Text(
                        "This app doesn't declare Android Auto support, so it won't show in the car unless you car-enable it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = warnColor(),
                    )
                }

                Button(
                    onClick = vm::installPicked,
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (vm.checking) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(if (vm.pickedInstaller != null) "Update as Play Store app" else "Install as Play Store app", fontSize = 16.sp)
                }
                TextButton(onClick = pick, modifier = Modifier.fillMaxWidth()) { Text("Choose another file") }
            }
        }

        HowItWorksCard()
    }
}

@Composable
private fun HowItWorksCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                "Installs like `adb install -i com.android.vending`, so Android Auto treats the app as a Play Store app. " +
                    "Turn on Unknown sources in Android Auto's developer settings (Setup tab).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ================================================================= car copy

@Composable
private fun CarScreen(vm: AppModel, actions: Actions) {
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<AppEntry?>(null) }
    val apps = vm.apps

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PrivilegeBanner(vm, actions) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp)) {
                    Icon(CarIcon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Put any app on the car screen", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "Builds “App (Car)”, a renamed copy that Android Auto lists and shows full-screen (meant for use while parked). " +
                                "The original stays untouched; the copy starts with empty data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { CarOptions(vm) }
            }
        }
        item {
            SearchField(query) { query = it }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text("System apps") })
            }
        }
        if (apps == null) {
            item { Loading() }
        } else {
            val shown = apps.filter {
                it.launchable && !it.pkg.endsWith(".car") && (showSystem || !it.system) &&
                    (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true))
            }
            if (shown.isEmpty()) item { EmptyText("No apps match.") }
            items(shown, key = { it.pkg }) { e ->
                AppRow(vm, e, onClick = { confirm = e }) {
                    if (e.hasCarCopy) StatusChip("Car copy", okColor())
                }
            }
        }
    }

    confirm?.let { e ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { LazyAppIcon(vm, e, 48) },
            title = { Text(if (e.hasCarCopy) "Rebuild ${e.label} (Car)?" else "Make ${e.label} (Car)?") },
            text = {
                Text(
                    "Creates ${PatchPipeline.carPackageName(e.pkg)} next to the original. " +
                        "Fill screen: ${if (vm.fillScreen) "on" else "off"} · Keep media services: ${if (vm.keepMedia) "on" else "off"}.\n\n" +
                        "Apps that check Play Integrity or their own signature may not work in the copy.",
                )
            },
            confirmButton = {
                Button(onClick = { confirm = null; vm.carCopy(e) }, enabled = !vm.busy) { Text(if (e.hasCarCopy) "Rebuild" else "Build") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AppRow(vm: AppModel, e: AppEntry, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { LazyAppIcon(vm, e) },
            headlineContent = { Text(e.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(e.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            },
            trailingContent = trailing,
        )
    }
}

@Composable
private fun EmptyText(s: String) {
    Text(
        s,
        Modifier.fillMaxWidth().padding(32.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

// ====================================================================== fix

@Composable
private fun FixScreen(vm: AppModel, actions: Actions) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<AppEntry?>(null) }
    val apps = vm.apps

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PrivilegeBanner(vm, actions) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Android Auto apps on this phone", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Apps marked ⚠ weren't installed by the Play Store, so Android Auto may hide them. Tap one to fix it without losing data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = vm::refreshApps) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
        }
        item { SearchField(query) { query = it } }
        if (apps == null) {
            item { Loading() }
        } else {
            val shown = apps.filter {
                it.androidAuto && !it.system &&
                    (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true))
            }.sortedBy { it.playTagged }
            if (shown.isEmpty()) item { EmptyText("No Android Auto apps found.") }
            items(shown, key = { it.pkg }) { e ->
                AppRow(vm, e, onClick = { confirm = e }) {
                    if (e.playTagged) Icon(Icons.Filled.CheckCircle, "Play Store", tint = okColor())
                    else Icon(Icons.Filled.Warning, "Not from Play Store", tint = warnColor())
                }
            }
        }
    }

    confirm?.let { e ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { LazyAppIcon(vm, e, 48) },
            title = { Text(e.label) },
            text = {
                Text(
                    "Installer: ${e.installer ?: "none"}\n\n" +
                        if (e.playTagged) "Already registered as a Play Store install. You can re-apply it anyway."
                        else "Reinstall it in place with the Play Store as installer? The app and its data are kept.",
                )
            },
            confirmButton = {
                Button(onClick = { confirm = null; vm.retag(e) }, enabled = !vm.busy) { Text(if (e.playTagged) "Re-apply" else "Fix") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirm = null; actions.launch(e.pkg) }) { Text("Open") }
                    TextButton(onClick = { confirm = null }) { Text("Cancel") }
                }
            },
        )
    }
}

// ==================================================================== setup

@Composable
private fun SetupScreen(vm: AppModel, actions: Actions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val granted = vm.privilege == Privilege.GRANTED
        StepCard(
            1, "Shizuku", done = granted,
            text = when (vm.privilege) {
                Privilege.GRANTED -> "Running and allowed. Installs go through the shell, like adb."
                Privilege.NEEDS_PERMISSION -> "Running. Allow AA Installer to use it."
                Privilege.NOT_RUNNING -> "Install Shizuku, then start it with Wireless debugging (Developer options). Rooted phones can skip this."
            },
            button = if (granted) null else (if (vm.privilege == Privilege.NEEDS_PERMISSION) "Allow" else "Open Shizuku") to actions::shizuku,
        )
        StepCard(
            2, "Android Auto developer mode",
            text = "Open Android Auto settings, tap Version 10×, then ⋮ → Developer settings → turn on Unknown sources.",
            button = "Open Android Auto" to actions::openAndroidAuto,
        )
        StepCard(
            3, "Keep Android Auto alive",
            text = "Set Android Auto's battery usage to Unrestricted so the phone's power manager doesn't kill it.",
            button = "Android Auto app info" to { actions.openAppInfo(MainActivity.AA_PKG) },
        )
        StepCard(
            4, "RedMagic / nubia only",
            text = "If installs fail with INSTALL_FAILED_USER_RESTRICTED, enable 'Install via USB' in Developer options.",
            button = "Developer options" to actions::openDeveloperOptions,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "AA Installer v${BuildConfig.VERSION_NAME} · GPL-3.0\n" +
                        "Car patch ported from CarBridge by jmwerk (GPL-3.0). Uses Shizuku, ARSCLib and apksig.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { actions.openUrl("https://github.com/j0p3s/AA-Installer") }) { Text("GitHub") }
                    TextButton(onClick = { actions.openUrl("https://github.com/jmwerk/CarBridge") }) { Text("CarBridge") }
                }
            }
        }
    }
}

@Composable
private fun StepCard(n: Int, title: String, text: String, done: Boolean = false, button: Pair<String, () -> Unit>?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Surface(
                shape = CircleShape,
                color = if (done) okColor() else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp))
                    else Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (button != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = button.second) { Text(button.first) }
                }
            }
        }
    }
}

// ================================================================== sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobSheet(job: Job, vm: AppModel, actions: Actions) {
    val running = job.state == JobState.RUNNING
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { job.state != JobState.RUNNING },
    )
    ModalBottomSheet(onDismissRequest = vm::dismissJob, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(job.icon, 48)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.title, style = MaterialTheme.typography.titleMedium)
                    Text(job.step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (job.state) {
                    JobState.RUNNING -> CircularProgressIndicator(Modifier.size(28.dp))
                    JobState.SUCCESS -> Icon(Icons.Filled.CheckCircle, null, tint = okColor(), modifier = Modifier.size(32.dp))
                    JobState.WARNING -> Icon(Icons.Filled.Warning, null, tint = warnColor(), modifier = Modifier.size(32.dp))
                    JobState.FAILED -> Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (job.retryWithUninstall != null) {
                Text(
                    "Uninstalling removes the installed app and its data, then installs this one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (job.message.isNotEmpty()) {
                Text(
                    job.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (job.state == JobState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            LogBox(job.log, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp))
            Spacer(Modifier.height(16.dp))
            if (!running) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    job.retryWithUninstall?.let { retry ->
                        Button(
                            onClick = retry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text("Uninstall & retry") }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (job.state != JobState.FAILED) {
                        TextButton(onClick = { vm.dismissJob(); actions.openAndroidAuto() }) { Text("Android Auto settings") }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (job.retryWithUninstall != null) TextButton(onClick = vm::dismissJob) { Text("Close") }
                    else Button(onClick = vm::dismissJob) { Text("Done") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(lines: List<String>, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text("Log", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (lines.isEmpty()) EmptyText("Nothing yet.")
            else LogBox(lines, Modifier.fillMaxWidth().heightIn(max = 480.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LogBox(lines: List<String>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier) {
        LazyColumn(state = listState, contentPadding = PaddingValues(12.dp)) {
            items(lines) { l ->
                Text(l, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

// ================================================================= dialogs

@Composable
private fun UninstallDialog(ask: UninstallAsk, vm: AppModel) {
    AlertDialog(
        onDismissRequest = vm::cancelUninstall,
        icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Uninstall ${ask.label} first?") },
        text = {
            Text(
                ask.reason + "\n\nAA Installer can uninstall the installed app and then install the new one. " +
                    "Its data (sign-in, settings, downloads) will be lost.",
            )
        },
        confirmButton = {
            Button(
                onClick = vm::confirmUninstall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Uninstall & install") }
        },
        dismissButton = { TextButton(onClick = vm::cancelUninstall) { Text("Cancel") } },
    )
}

@Composable
private fun CrashDialog(text: String, vm: AppModel) {
    val clipboard = LocalContext.current.getSystemService(android.content.ClipboardManager::class.java)
    AlertDialog(
        onDismissRequest = vm::clearCrash,
        icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("AA Installer crashed last time") },
        text = {
            Column {
                Text("Copy this report and send it so the problem can be fixed.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LogBox(text.lines(), Modifier.fillMaxWidth().heightIn(max = 320.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("AA Installer crash", text))
                vm.clearCrash()
            }) { Text("Copy & close") }
        },
        dismissButton = { TextButton(onClick = vm::clearCrash) { Text("Close") } },
    )
}
