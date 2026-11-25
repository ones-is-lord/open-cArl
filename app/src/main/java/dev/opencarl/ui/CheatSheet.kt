package dev.opencarl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.ui.components.ScreenScaffold

private data class Entry(val command: String, val what: String, val example: String)

private data class Group(val title: String, val entries: List<Entry>)

private val groups = listOf(
    Group("Shell and files", listOf(
        Entry("bash", "Run a command in the live shell, same session the terminal shows.", "bash: ls -la ~ | head"),
        Entry("read_file", "Read a text file.", "read_file: notes.md"),
        Entry("write_file", "Create or overwrite a file.", "write_file: todo.md, body"),
        Entry("list_dir", "List a directory.", "list_dir: /sdcard/Download"),
        Entry("code", "Search and replace across files.", "code: sed -i s/old/new/g *.md"),
        Entry("clipboard", "Read or set the system clipboard.", "clipboard: set the build log"),
    )),
    Group("Memory and knowledge", listOf(
        Entry("remember", "Store a durable fact.", "remember: the tablet is a Tab S9, arm64"),
        Entry("recall", "Search memory.", "recall: what device is this"),
        Entry("kb import", "Index a folder of notes or code.", "kb: import ~/notes, pack=notes"),
        Entry("kb search", "Search imported packs with provenance.", "kb: search proot bootstrap"),
        Entry("kb list", "Show packs and chunk counts.", "kb: list"),
        Entry("think", "Scratch reasoning that does not need an action.", "think: which of these two layouts is better"),
    )),
    Group("Device and system", listOf(
        Entry("device_info", "Model, Android version, RAM, storage, battery.", "device_info"),
        Entry("diagnostics", "Collect a support bundle of runtime facts.", "diagnostics: full"),
        Entry("apps", "List, launch, stop, or inspect apps.", "apps: launch com.termux"),
        Entry("packages", "Query and manage installed packages.", "packages: list user"),
        Entry("storage", "Disk usage and cleanup targets.", "storage: largest"),
        Entry("notify", "Post a notification.", "notify: build finished"),
        Entry("wifi", "Scan, inspect, or connect to networks.", "wifi: scan"),
        Entry("net", "HTTP requests, DNS, reachability.", "net: GET https://example.com"),
        Entry("screen", "Read what is on screen.", "screen: current"),
        Entry("clipboard", "Share text in and out of the device.", "clipboard: get"),
    )),
    Group("Privileged", listOf(
        Entry("privileged_shell", "Run as root through the privileged bridge.", "privileged_shell: id"),
        Entry("root", "Request or check root access.", "root: status"),
        Entry("device_info", "Hardware truth without shelling out.", "device_info"),
    )),
    Group("Automation", listOf(
        Entry("schedule", "One-off or repeating prompt or command.", "schedule: every day 08:00, prompt=summarise the news"),
        Entry("triggers", "Fire on battery, charging, thermal, wifi, screen, storage.", "triggers: add battery_below 20, prompt=charge me"),
        Entry("snapshot", "Capture and restore a session.", "snapshot: create before the refactor"),
        Entry("plan", "Track a multi-step job.", "plan: add step 1 write the parser"),
        Entry("spawn", "Hand a job to a sub-agent and get a report.", "spawn: audit every TODO in this repo"),
        Entry("profile", "Time a command and report the cost.", "profile: ./gradlew assembleDebug"),
        Entry("study", "Flashcards and spaced repetition.", "study: add question, answer"),
        Entry("persona", "Switch the agent's tone.", "persona: set pirate"),
    )),
    Group("Skills and tools", listOf(
        Entry("list_skills", "Show installed skills.", "list_skills"),
        Entry("use_skill", "Load a skill's instructions.", "use_skill: github"),
        Entry("fetch_url", "Fetch a page as text.", "fetch_url: https://docs.example.com"),
        Entry("deep_research", "Multi-source research with citations.", "deep_research: compare three proot builds"),
        Entry("script", "Write and run a script file.", "script: backup.sh"),
        Entry("run_script", "Run an existing script.", "run_script: ~/bin/backup.sh"),
        Entry("emulator", "Drive the Android emulator surfaces.", "emulator: tap 540 1200"),
        Entry("datasets", "Load and query local data files.", "datasets: csv sales.csv"),
    )),
)

@Composable
fun CheatSheetScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    ScreenScaffold(title = "Cheat sheet", appearance = appearance, onBack = onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Ask in plain English; these are what the agent can actually do. The shell prompt takes real Linux commands directly.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(group.title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    group.entries.forEach { entry ->
                        Column {
                            Text(entry.command, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(entry.what, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "e.g. " + entry.example,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
        }
    }
}
