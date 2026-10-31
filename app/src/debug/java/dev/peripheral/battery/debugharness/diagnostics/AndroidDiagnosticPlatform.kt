package dev.peripheral.battery.debugharness.diagnostics

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.peripheral.battery.MainActivity
import dev.peripheral.battery.debugharness.DebugHarnessActivity

/** Reads only packaged assets and nonunique app/OS metadata, never serials, IDs, accounts or network state. */
class AndroidDiagnosticPlatform(private val context: Context) : DiagnosticPlatform, DiagnosticAssets {
    override fun read(path: String): ByteArray {
        require(path in allowedPaths)
        return context.assets.open(path).use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 65536)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
    override fun debugAssetsAccessible() = read("contracts/golden/demo.json").isNotEmpty()
    @Suppress("DEPRECATION")
    override fun activityDeclarationsIsolated(): Boolean {
        val main = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val debug = context.packageManager.getActivityInfo(ComponentName(context, DebugHarnessActivity::class.java), 0)
        return main.name != debug.name && main.enabled && debug.enabled &&
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    @Suppress("DEPRECATION")
    override fun noInternetOrStoragePermissions(): Boolean {
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        return requested.none { it == "android.permission.INTERNET" || it == "android.permission.MANAGE_EXTERNAL_STORAGE" || it == "android.permission.READ_EXTERNAL_STORAGE" || it == "android.permission.WRITE_EXTERNAL_STORAGE" }
    }
    override fun offMainThread() = android.os.Looper.myLooper() != android.os.Looper.getMainLooper()
    override fun deviceMetadataAvailable() = Build.VERSION.RELEASE.isNotBlank() && Build.MODEL.isNotBlank()
    @Suppress("DEPRECATION")
    fun metadata(): DiagnosticMetadata = DiagnosticMetadata(
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown",
        Build.VERSION.SDK_INT, Build.MODEL, "AUTOMATED ON-DEVICE",
    )
    companion object {
        private val allowedPaths = DiagnosticCatalog.HASHES.keys.map { "contracts/$it" }.toSet() +
            dev.peripheral.battery.debugharness.HarnessFixture.entries.map { it.assetPath }
    }
}
