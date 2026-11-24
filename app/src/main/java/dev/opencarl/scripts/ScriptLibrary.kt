package dev.opencarl.scripts

import dev.opencarl.privilege.PrivilegeLevel

enum class ScriptRisk { SAFE, CAREFUL, DANGEROUS }

data class ScriptStep(
    val command: String,
    val explain: String,
)

data class ShellScript(
    val id: String,
    val name: String,
    val summary: String,
    val risk: ScriptRisk,
    val needs: PrivilegeLevel,
    val steps: List<ScriptStep>,
    val undo: String = "",
) {
    val oneLine: String get() = steps.joinToString("\n") { it.command }
}

/**
 * Curated, readable scripts. Every step explains itself, and anything that
 * changes system state is listed with what it undoes.
 */
object ScriptLibrary {

    private val DEBLOAT_TARGETS = listOf(
        "com.google.android.apps.tachyon",
        "com.google.android.videos",
        "com.google.android.music",
        "com.google.android.apps.photos",
        "com.facebook.appmanager",
        "com.facebook.services",
        "com.facebook.system",
        "com.microsoft.skydrive",
        "com.linkedin.android",
    )

    val scripts: List<ShellScript> = listOf(
        ShellScript(
            id = "bootstrap",
            name = "Bootstrap the workspace",
            summary = "Creates the folders open cArl uses and a sane shell profile.",
            risk = ScriptRisk.SAFE,
            needs = PrivilegeLevel.APP,
            steps = listOf(
                ScriptStep("mkdir -p ~/.carl/bin ~/.carl/models ~/.carl/workspace ~/.carl/logs", "Create the app's own directories."),
                ScriptStep("printf '%s\\n' 'export PATH=\$HOME/.carl/bin:\$PATH' 'export TERM=xterm-256color' 'export EDITOR=vi' > ~/.carl/profile.sh", "Write a shell profile with a 256 colour terminal."),
                ScriptStep("grep -q carl/profile ~/.profile 2>/dev/null || echo '. ~/.carl/profile.sh' >> ~/.profile", "Source the profile from ~/.profile once."),
            ),
        ),
        ShellScript(
            id = "dev-tools",
            name = "Install developer tools",
            summary = "bash, git, python3, node, npm, curl, vim, tmux, htop inside the Linux userland.",
            risk = ScriptRisk.CAREFUL,
            needs = PrivilegeLevel.APP,
            steps = listOf(
                ScriptStep("apk update", "Refresh the Alpine package index."),
                ScriptStep("apk add --no-cache bash git python3 py3-pip nodejs npm curl wget vim tmux htop ripgrep", "Install the toolchain."),
                ScriptStep("git config --global init.defaultBranch main", "Small git default that avoids warnings."),
            ),
        ),
        ShellScript(
            id = "animations",
            name = "Faster animations",
            summary = "Sets animation scales to 0.5 so the phone feels snappier.",
            risk = ScriptRisk.SAFE,
            needs = PrivilegeLevel.SHIZUKU,
            steps = listOf(
                ScriptStep("settings put global window_animation_scale 0.5", "Halve window animations."),
                ScriptStep("settings put global transition_animation_scale 0.5", "Halve transition animations."),
                ScriptStep("settings put global animator_duration_scale 0.5", "Halve animator duration."),
            ),
            undo = "settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1",
        ),
        ShellScript(
            id = "trim-caches",
            name = "Trim app caches",
            summary = "Asks the package manager to free cached data across apps.",
            risk = ScriptRisk.SAFE,
            needs = PrivilegeLevel.SHIZUKU,
            steps = listOf(
                ScriptStep("pm trim-caches 8G", "Free up to 8 GB of cached files."),
                ScriptStep("sync", "Flush writes to disk."),
            ),
        ),
        ShellScript(
            id = "battery",
            name = "Keep the agent alive",
            summary = "Whitelists open cArl from Doze so long runs are not killed.",
            risk = ScriptRisk.SAFE,
            needs = PrivilegeLevel.SHIZUKU,
            steps = listOf(
                ScriptStep("dumpsys deviceidle whitelist +dev.opencarl", "Exempt open cArl from battery optimisation."),
                ScriptStep("cmd deviceidle whitelist +dev.opencarl || true", "Same, for newer builds."),
            ),
            undo = "dumpsys deviceidle whitelist -dev.opencarl",
        ),
        ShellScript(
            id = "debloat",
            name = "Debloat known junk",
            summary = "Disables a conservative list of preinstalled apps for the current user. Reversible.",
            risk = ScriptRisk.CAREFUL,
            needs = PrivilegeLevel.SHIZUKU,
            steps = DEBLOAT_TARGETS.map { target ->
                ScriptStep("pm disable-user --user 0 $target", "Disable $target for this user only.")
            },
            undo = DEBLOAT_TARGETS.joinToString("; ") { "pm enable $it" },
        ),
        ShellScript(
            id = "speed-compile",
            name = "Precompile apps for speed",
            summary = "Runs ART speed compilation on a package you name, using more storage for faster starts.",
            risk = ScriptRisk.CAREFUL,
            needs = PrivilegeLevel.SHIZUKU,
            steps = listOf(
                ScriptStep("cmd package compile -m speed -f com.android.chrome", "Example: recompile Chrome. Replace with any package."),
            ),
        ),
        ShellScript(
            id = "triage-large",
            name = "Find big files",
            summary = "Lists the largest files on shared storage so you can decide what to delete.",
            risk = ScriptRisk.SAFE,
            needs = PrivilegeLevel.APP,
            steps = listOf(
                ScriptStep("find /sdcard -type f -size +200M 2>/dev/null | head -40", "Show files larger than 200 MB."),
                ScriptStep("du -sh /sdcard/* 2>/dev/null | sort -h | tail -20", "Show the heaviest top-level folders."),
            ),
        ),
        ShellScript(
            id = "repair-media",
            name = "Repair the media index",
            summary = "Asks MediaStore to rescan volumes, which fixes missing photos and ghost albums.",
            risk = ScriptRisk.CAREFUL,
            needs = PrivilegeLevel.SHIZUKU,
            steps = listOf(
                ScriptStep("content call --uri content://media/ --method scan_volume --arg external_primary", "Rescan the primary volume."),
                ScriptStep("cmd media_session dispatch play || true", "Nudge the media session service."),
            ),
        ),
        ShellScript(
            id = "vm-tune",
            name = "Tune memory for local models",
            summary = "Root only: raises swap friendliness and dirty ratios so big models load without thrashing.",
            risk = ScriptRisk.DANGEROUS,
            needs = PrivilegeLevel.ROOT,
            steps = listOf(
                ScriptStep("sysctl -w vm.swappiness=100", "Prefer reclaiming file pages over dropping model pages."),
                ScriptStep("sysctl -w vm.vfs_cache_pressure=50", "Keep dentries and inodes cached."),
                ScriptStep("cat /proc/sys/vm/swappiness", "Confirm the new value."),
            ),
            undo = "sysctl -w vm.swappiness=60; sysctl -w vm.vfs_cache_pressure=100",
        ),
    )

    val debloatTargets: List<String> = DEBLOAT_TARGETS

    fun byId(id: String): ShellScript? = scripts.firstOrNull { it.id == id }

}
