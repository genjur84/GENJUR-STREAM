package com.genjur.stream

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import dalvik.system.PathClassLoader
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class CloudStreamPluginStatus(
    val name: String,
    val pluginClassName: String,
    val registeredProviders: List<String>
)

/**
 * Minimal CloudStream .cs3 loader. The plugin bytecode is loaded against the
 * official CloudStream API classes packaged in GENJUR STREAM.
 */
object CloudStreamPluginRuntime {
    private data class Loaded(
        val plugin: BasePlugin,
        val classLoader: PathClassLoader,
        val status: CloudStreamPluginStatus
    )

    private val loaded = ConcurrentHashMap<String, Loaded>()
    private const val MAX_PLUGIN_BYTES = 50L * 1024L * 1024L

    suspend fun installAndLoad(
        context: Context,
        pluginUrl: String,
        internalName: String
    ): CloudStreamPluginStatus = withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(pluginUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
            "URL plugin harus HTTPS."
        }
        val key = sha256(pluginUrl)
        loaded[key]?.let { return@withContext it.status }

        val directory = File(context.filesDir, "cloudstream_plugins").apply { mkdirs() }
        val pluginFile = File(directory, "$key.cs3")
        if (!pluginFile.exists() || pluginFile.length() == 0L) {
            val connection = (URL(pluginUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "GENJUR-STREAM CloudStream-Plugin-Bridge")
            }
            try {
                val code = connection.responseCode
                require(code in 200..299) { "Unduhan plugin gagal (HTTP $code)." }
                val declaredLength = connection.contentLengthLong
                require(declaredLength <= MAX_PLUGIN_BYTES) { "Ukuran plugin melebihi batas 50 MB." }
                val temporary = File(directory, "$key.download")
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_PLUGIN_BYTES) { "Ukuran plugin melebihi batas 50 MB." }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                require(temporary.length() > 0L) { "File plugin kosong." }
                if (!temporary.renameTo(pluginFile)) {
                    temporary.copyTo(pluginFile, overwrite = true)
                    temporary.delete()
                }
            } finally {
                connection.disconnect()
            }
        }

        val loader = PathClassLoader(pluginFile.absolutePath, context.classLoader)
        val manifestText = loader.getResourceAsStream("manifest.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Plugin tidak memiliki manifest.json.")
        val manifest = JSONObject(manifestText)
        val pluginClassName = manifest.optString("pluginClassName").trim()
        require(pluginClassName.isNotBlank()) { "Manifest plugin tidak memiliki pluginClassName." }
        val pluginClass = loader.loadClass(pluginClassName)
        require(BasePlugin::class.java.isAssignableFrom(pluginClass)) {
            "Kelas plugin tidak kompatibel dengan CloudStream API yang disematkan."
        }
        val instance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
        instance.filename = pluginFile.absolutePath
        if (manifest.optBoolean("requiresResources", false) && instance is Plugin) {
            val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
            AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                .invoke(assets, pluginFile.absolutePath)
            instance.resources = Resources(
                assets,
                context.resources.displayMetrics,
                context.resources.configuration
            )
        }
        when (instance) {
            is Plugin -> instance.load(context.applicationContext)
            else -> instance.load()
        }

        val providers = APIHolder.allProviders.map { it.name }.distinct()
        val status = CloudStreamPluginStatus(
            name = manifest.optString("name").ifBlank { internalName },
            pluginClassName = pluginClassName,
            registeredProviders = providers
        )
        loaded[key] = Loaded(instance, loader, status)
        status
    }

    fun loadedStatuses(): List<CloudStreamPluginStatus> = loaded.values.map { it.status }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
