package com.mangareader.app

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * Registers the dependencies Tachiyomi extensions look up via Injekt.
 *
 * Extension constructors and lazy properties call Injekt.get()/injectLazy()
 * for these three types. Without the bindings the classes still load, but the
 * first real call fails — so this must run before ExtensionLoader.loadAll().
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Injekt.importModule(AppModule(this))
        // Runs for every process entry point, not just the Activity — including
        // the system restarting DownloadService on its own, which is the case
        // that makes a queue survive being killed.
        DownloadQueue.restore(this)
    }
}

class AppModule(private val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        // ConfigurableSource extensions do:
        //   Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
        addSingleton<Application>(app)

        // HttpSource.client / .headers come from here.
        addSingletonFactory { NetworkHelper(app) }

        // Common in API-based extensions: `val json: Json by injectLazy()`
        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
    }
}
