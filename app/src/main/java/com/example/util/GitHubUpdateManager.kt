package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long,
    val publishedAt: String
)

object GitHubUpdateManager {
    const val GITHUB_OWNER = "devsamikhan"
    const val GITHUB_REPO = "theBLS"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Checks GitHub Releases for the latest version and compares with local version
     */
    suspend fun checkForUpdate(currentVersion: String = "1.0.2"): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("User-Agent", "BLS-Cash-Record-Android")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    // No releases published yet on repository
                    return@withContext Result.success(
                        AppUpdateInfo(
                            isUpdateAvailable = false,
                            latestVersion = currentVersion,
                            currentVersion = currentVersion,
                            releaseTitle = "Latest Version",
                            releaseNotes = "No new updates published yet on GitHub.",
                            downloadUrl = "",
                            apkSize = 0L,
                            publishedAt = ""
                        )
                    )
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("GitHub API Error: ${response.code} ${response.message}"))
                }

                val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
                val json = JSONObject(bodyString)

                val rawTagName = json.optString("tag_name", "")
                val latestVer = rawTagName.trimStart('v', 'V', ' ').trim()
                val releaseTitle = json.optString("name", "Version $latestVer")
                val releaseNotes = json.optString("body", "General performance improvements and feature updates.")
                val publishedAt = json.optString("published_at", "")

                // Find APK asset
                var downloadUrl = ""
                var apkSize = 0L
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                val hasNewerVersion = isVersionNewer(latest = latestVer, current = currentVersion)
                val updateAvailable = hasNewerVersion && downloadUrl.isNotBlank()

                Result.success(
                    AppUpdateInfo(
                        isUpdateAvailable = updateAvailable,
                        latestVersion = latestVer.ifBlank { currentVersion },
                        currentVersion = currentVersion,
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        downloadUrl = downloadUrl,
                        apkSize = apkSize,
                        publishedAt = publishedAt
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Downloads APK file while reporting progress (0.0 to 1.0)
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        targetVersion: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "BLS-Cash-Record-Android")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()

                val destinationFile = File(context.cacheDir, "BLS_Update_v${targetVersion}.apk")
                if (destinationFile.exists()) destinationFile.delete()

                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                        withContext(Dispatchers.Main) {
                            onProgress(progress.coerceIn(0f, 1f))
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    onProgress(1.0f)
                }

                destinationFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Launches Android Package Installer to install downloaded APK
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                android.widget.Toast.makeText(context, "Update file not found. Please re-download.", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            // Android 8.0+ (API 26+) Permission check for installing unknown apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    android.widget.Toast.makeText(
                        context,
                        "Please allow install permission from this source, then tap Install Now",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Installation error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Compares semantic versioning: returns true if latest > current
     */
    fun isVersionNewer(latest: String, current: String): Boolean {
        if (latest.isBlank() || current.isBlank()) return false
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * Formats bytes to human readable string (e.g. 15.4 MB)
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown size"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}
