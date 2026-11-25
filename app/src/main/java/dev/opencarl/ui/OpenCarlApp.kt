package dev.opencarl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.OpenCarlViewModel
import dev.opencarl.appearance.ColorRole
import dev.opencarl.appearance.FontLibrary
import dev.opencarl.appearance.NeonWordmark
import dev.opencarl.ui.components.CarlHeader
import kotlinx.coroutines.launch

private enum class Route(val label: String, val icon: ImageVector) {
    PROVIDERS("Providers", Icons.Filled.Psychology),
    APPEARANCE("Appearance", Icons.Filled.Brush),
    SKILLS("Skills", Icons.Filled.Extension),
    MCP("MCP servers", Icons.Filled.Settings),
    MEMORY("Memory", Icons.Filled.Memory),
    MODELS("Models", Icons.Filled.Tune),
    RUNTIME("Runtime", Icons.Filled.Terminal),
    SYSTEM("System", Icons.Filled.Build),
    DGAF("D.G.A.F.", Icons.Filled.Warning),
    PERSONAS("Personas", Icons.Filled.Psychology),
    SNAPSHOTS("Snapshots", Icons.Filled.Refresh),
    DIAGNOSTICS("Diagnostics", Icons.Filled.BugReport),
    CHEATSHEET("Cheat sheet", Icons.Filled.MenuBook),
}

@Composable
fun OpenCarlApp(vm: OpenCarlViewModel) {
    val settings by vm.container.settings.settings.collectAsStateSafe()
    val appearance = settings.appearance
    var route by remember { mutableStateOf<Route?>(null) }
    var paneMode by remember { mutableStateOf(PaneMode.SPLIT) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val runtimeLabel = remember(settings.runtime.backend, settings.runtime.distro) {
        vm.container.runtime.label(settings.runtime)
    }

    LaunchedEffect(Unit) {
        if (settings.autoUpdateOnLaunch) {
            runCatching { vm.container.updates.runAll(vm.container.updates.versionName()) }
        }
        vm.container.privilege.register()
    }

    OpenCarlTheme(appearance) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        NeonWordmark(
                            text = appearance.brandLabel,
                            appearance = appearance,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            maxSp = 46f,
                            minSp = 18f,
                        )
                        Text(
                            runtimeLabel + " · " + settings.activeProvider.name,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        NavigationDrawerItem(
                            label = { Text("Terminal") },
                            icon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                            selected = route == null,
                            onClick = {
                                route = null
                                scope.launch { drawer.close() }
                            },
                        )
                        NavigationDrawerItem(
                            label = { Text("Agent") },
                            icon = { Icon(Icons.Filled.SmartToy, contentDescription = null) },
                            selected = false,
                            onClick = {
                                paneMode = PaneMode.CHAT
                                route = null
                                scope.launch { drawer.close() }
                            },
                        )
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Route.entries.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                selected = route == item,
                                onClick = {
                                    route = item
                                    scope.launch { drawer.close() }
                                },
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        NavigationDrawerItem(
                            label = { Text("Restart shell") },
                            icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            selected = false,
                            onClick = {
                                vm.container.restartSession()
                                scope.launch { drawer.close() }
                            },
                        )
                    }
                }
            },
        ) {
            if (!settings.onboarded) {
                dev.opencarl.ui.onboarding.OnboardingScreen(appearance = appearance) { modelId ->
                    vm.container.settings.update { it.copy(onboarded = true, chosenModelId = modelId) }
                }
                return@ModalNavigationDrawer
            }
            Column(Modifier.fillMaxSize()) {
                CarlHeader(
                    appearance = appearance,
                    subtitle = runtimeLabel + " · " + settings.activeProvider.name,
                    onMenu = { scope.launch { drawer.open() } },
                    onChat = { paneMode = if (paneMode == PaneMode.CHAT) PaneMode.TERMINAL else PaneMode.CHAT },
                    onSettings = { route = Route.APPEARANCE },
                )
                if (settings.dgafEnabled) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(appearance.colorOf(ColorRole.BANNER))
                            .clickable {
                                vm.container.settings.update { it.copy(dgafEnabled = false, dgafAcceptedAt = 0L) }
                                route = Route.DGAF
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("D.G.A.F. is on — the agent does not ask", fontSize = 10.sp, color = Color.Black)
                        Text("revoke", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                StatusStrip()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                    val stacked = maxWidth < 600.dp
                    when (paneMode) {
                        PaneMode.TERMINAL -> CliBox("terminal") {
                            TerminalPane(vm.container, appearance, Modifier.fillMaxSize())
                        }
                        PaneMode.CHAT -> CliBox("agent") {
                            ChatPanel(vm = vm, appearance = appearance, onClose = { paneMode = PaneMode.TERMINAL })
                        }
                        PaneMode.SPLIT -> SplitPanes(vm = vm, appearance = appearance, stacked = stacked)
                    }
                }
                PaneBar(
                    mode = paneMode,
                    onMode = { paneMode = it },
                    onGuide = { route = Route.CHEATSHEET },
                    onMore = { scope.launch { drawer.open() } },
                )
            }
        }

        route?.let { current ->
            when (current) {
                Route.PROVIDERS -> ProvidersScreen(vm.container, appearance) { route = null }
                Route.APPEARANCE -> AppearanceScreen(vm.container, appearance) { route = null }
                Route.SKILLS -> SkillsScreen(vm.container, appearance) { route = null }
                Route.MCP -> McpScreen(vm.container, appearance) { route = null }
                Route.MEMORY -> MemoryScreen(vm.container, appearance) { route = null }
                Route.MODELS -> ModelsScreen(vm.container, appearance) { route = null }
                Route.RUNTIME -> RuntimeScreen(vm.container, appearance) { route = null }
                Route.SYSTEM -> SystemScreen(vm.container, appearance) { route = null }
                Route.DGAF -> DgafScreen(vm.container, appearance) { route = null }
                Route.PERSONAS -> PersonasScreen(vm.container, appearance) { route = null }
                Route.SNAPSHOTS -> SnapshotsScreen(vm.container, appearance) { route = null }
                Route.DIAGNOSTICS -> DiagnosticsScreen(vm.container, appearance) { route = null }
                Route.CHEATSHEET -> CheatSheetScreen(vm.container, appearance) { route = null }
            }
        }
    }
}
