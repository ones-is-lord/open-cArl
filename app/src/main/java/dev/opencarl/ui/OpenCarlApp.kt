package dev.opencarl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.OpenCarlViewModel
import dev.opencarl.appearance.ColorRole
import dev.opencarl.appearance.FontLibrary
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
}

@Composable
fun OpenCarlApp(vm: OpenCarlViewModel) {
    val settings by vm.container.settings.settings.collectAsStateSafe()
    val appearance = settings.appearance
    var route by remember { mutableStateOf<Route?>(null) }
    var chatOpen by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val runtimeLabel = remember(settings.runtime.backend, settings.runtime.distro) {
        vm.container.runtime.label(settings.runtime)
    }

    OpenCarlTheme(appearance) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            appearance.bannerLabel,
                            fontFamily = FontLibrary.familyOf(appearance.bannerFont),
                            color = appearance.colorOf(ColorRole.BANNER),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp),
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
                            selected = chatOpen,
                            onClick = {
                                chatOpen = true
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
            Column(Modifier.fillMaxSize()) {
                CarlHeader(
                    appearance = appearance,
                    subtitle = runtimeLabel + " · " + settings.activeProvider.name,
                    onMenu = { scope.launch { drawer.open() } },
                    onChat = { chatOpen = !chatOpen },
                    onSettings = { route = Route.APPEARANCE },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    TerminalPane(
                        container = vm.container,
                        appearance = appearance,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (chatOpen) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.66f)
                                .background(MaterialTheme.colorScheme.surface),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            ChatPanel(
                                vm = vm,
                                appearance = appearance,
                                onClose = { chatOpen = false },
                            )
                        }
                    }
                }
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
            }
        }
    }
}
