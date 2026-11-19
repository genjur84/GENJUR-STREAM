package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * Compatibility bridge for CloudStream extensions compiled against the app Plugin base class.
 * The shared BasePlugin API itself comes from the official CloudStream extension library.
 */
abstract class Plugin : BasePlugin() {
    open fun load(context: Context) {
        load()
    }

    var resources: Resources? = null
    var openSettings: ((context: Context) -> Unit)? = null
}
