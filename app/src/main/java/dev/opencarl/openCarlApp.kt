package dev.opencarl

import android.app.Application
import dev.opencarl.app.AppContainer

/**
 * One process-wide container: the PTY, settings, skills, memory and MCP
 * connections must outlive any single activity or screen.
 */
class openCarlApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
