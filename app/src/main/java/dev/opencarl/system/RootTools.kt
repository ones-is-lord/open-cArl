package dev.opencarl.system

import dev.opencarl.privilege.PrivilegeLevel
import dev.opencarl.privilege.PrivilegeManager
import java.io.File

data class RootStatus(
    val rootGranted: Boolean = false,
    val magisk: Boolean = false,
    val magiskVersion: String = "",
    val kernelSu: Boolean = false,
    val bootSlot: String = "",
    val modules: List<String> = emptyList(),
) {
    val manager: String
        get() = when {
            magisk -> "Magisk $magiskVersion"
            kernelSu -> "KernelSU"
            rootGranted -> "su (unknown manager)"
            else -> "no root"
        }
}

/**
 * Root and recovery helpers. It detects the manager, lists and installs Magisk
 * modules, and writes a guided recovery plan; it never flashes anything itself.
 */
class RootTools(private val privilege: PrivilegeManager) {

    suspend fun status(): RootStatus {
        val root = privilege.status.value.rootGranted
        if (!root) {
            val slot = runCatching {
                File("/proc/cmdline").readText()
                    .substringAfter("androidboot.slot_suffix=", "")
                    .takeWhile { it.isLetterOrDigit() || it == '_' }
            }.getOrDefault("")
            return RootStatus(rootGranted = false, bootSlot = slot)
        }
        val magiskVersion = privilege.run("magisk -v", PrivilegeLevel.ROOT).output.trim()
        val magisk = magiskVersion.isNotEmpty() && !magiskVersion.contains("not found")
        val kernelSu = privilege.run("which ksud", PrivilegeLevel.ROOT).output.trim().isNotEmpty()
        val slot = privilege.run("getprop ro.boot.slot_suffix", PrivilegeLevel.ROOT).output.trim()
        val modules = if (magisk) {
            privilege.run("magisk --list-modules", PrivilegeLevel.ROOT).output
                .lines().map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        return RootStatus(
            rootGranted = true,
            magisk = magisk,
            magiskVersion = if (magisk) magiskVersion else "",
            kernelSu = kernelSu,
            bootSlot = slot,
            modules = modules,
        )
    }

    suspend fun installModule(zip: File): String {
        require(zip.isFile) { "no such module: ${zip.absolutePath}" }
        val outcome = privilege.run("magisk --install-module '${zip.absolutePath}'", PrivilegeLevel.ROOT, 120_000, "magisk-module")
        return if (outcome.ok) "module installed; reboot to activate\n${outcome.output.trim()}" else outcome.output
    }

    suspend fun reboot(target: String): String {
        val command = when (target.lowercase()) {
            "recovery" -> "reboot recovery"
            "bootloader" -> "reboot bootloader"
            "fastbootd" -> "reboot fastboot"
            else -> "reboot"
        }
        val outcome = privilege.run(command, PrivilegeLevel.ROOT)
        return if (outcome.ok) "rebooting: $target" else outcome.output
    }

    /** A plan the user can follow; nothing here is executed automatically. */
    fun recoveryGuide(status: RootStatus): String = buildString {
        append("Custom recovery plan\n\n")
        append("1. Back up first. Pull every partition you care about while the device still boots:\n")
        append("   adb shell su -c 'dd if=/dev/block/by-name/boot of=/sdcard/boot.img'\n")
        append("   adb pull /sdcard/boot.img\n\n")
        append("2. Unlock the bootloader. This wipes the device and voids some warranties.\n")
        append("   adb reboot bootloader && fastboot flashing unlock\n\n")
        append("3. Match the recovery to the device, not to the Android version:\n")
        append("   - TWRP or OrangeFox image for your exact model codename\n")
        append("   - current boot slot: ").append(status.bootSlot.ifBlank { "unknown (A-only device?)" }).append('\n')
        append("   fastboot flash boot_a twrp.img   # use boot_b on slot b\n")
        append("   fastboot boot twrp.img           # safer: try without flashing\n\n")
        append("4. Keep root across updates. Patch the stock boot image with Magisk on the\n")
        append("   device, then flash the patched image, or use a kernel that ships KernelSU.\n\n")
        append("5. Install modules from here once Magisk is active: the Runtime screen can\n")
        append("   push a module zip through `magisk --install-module`.\n\n")
        append("Detected: ").append(status.manager)
        if (status.modules.isNotEmpty()) {
            append("\nModules: ").append(status.modules.joinToString(", "))
        }
        append("\n\nopen cArl never flashes partitions on its own; every step above is yours to run.")
    }
}
