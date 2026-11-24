package dev.opencarl.scripts

import dev.opencarl.privilege.CommandOutcome
import dev.opencarl.privilege.PrivilegeLevel
import dev.opencarl.privilege.PrivilegeManager

data class ScriptRun(
    val script: ShellScript,
    val dryRun: Boolean,
    val results: List<CommandOutcome>,
    val aborted: Boolean,
) {
    val ok: Boolean get() = !aborted && results.all { it.ok }
    fun report(): String = buildString {
        append(if (dryRun) "dry run: " else "run: ").append(script.name).append('\n')
        if (dryRun) {
            script.steps.forEach { append("  $ ").append(it.command).append("\n    ").append(it.explain).append('\n') }
        } else {
            results.forEach { outcome ->
                append("  $ ").append(outcome.command).append(" → exit ").append(outcome.exitCode).append('\n')
                if (outcome.output.isNotBlank()) {
                    append(outcome.output.trimEnd().lines().take(12).joinToString("\n") { "    $it" }).append('\n')
                }
            }
            if (aborted) append("  stopped at the first failure\n")
        }
    }
}

class ScriptRunner(private val privilege: PrivilegeManager) {

    suspend fun run(
        script: ShellScript,
        dryRun: Boolean = false,
        level: PrivilegeLevel = script.needs,
        stopOnError: Boolean = true,
        onStep: (String) -> Unit = {},
    ): ScriptRun {
        if (dryRun) return ScriptRun(script, true, emptyList(), false)
        val results = mutableListOf<CommandOutcome>()
        var aborted = false
        for (step in script.steps) {
            onStep(step.command)
            val outcome = privilege.run(step.command, level, note = "script:${script.id}")
            results += outcome
            if (!outcome.ok && stopOnError) {
                aborted = true
                break
            }
        }
        return ScriptRun(script, false, results, aborted)
    }
}
