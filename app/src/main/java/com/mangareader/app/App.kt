package com.mangareader.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
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
class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Injekt.importModule(AppModule(this))
        // Runs for every process entry point, not just the Activity — including
        // the system restarting DownloadService on its own, which is the case
        // that makes a queue survive being killed.
        DownloadQueue.restore(this)
    }

    /**
     * Makes Coil load images through the same client the extensions use.
     *
     * Coil builds its own OkHttpClient by default, which is a plain one: no
     * cookie jar, no User-Agent, no Cloudflare interceptor. So covers went out
     * as bare requests while the catalogue that named them went out
     * authenticated — a protected source would list its series correctly and
     * then show a grid of empty placeholders, because every image 403'd.
     *
     * Returning the shared client fixes that for covers, thumbnails and
     * anything else Coil fetches, and it costs nothing: the connection pool and
     * cache are shared rather than duplicated.
     *
     * **The shared client was necessary and not sufficient.** It carries the
     * User-Agent, the cookie jar and the Cloudflare interceptor, but `Referer`
     * comes from the *source*, not the client — see [CoverHeaders], which adds
     * it per host. Without that, a hotlink-protected source still 403s every
     * cover while its page images load fine, because those go out through
     * `HttpSource.getImage`.
     *
     * The lambda form defers building [NetworkHelper] until the first image is
     * actually requested, so this doesn't drag network setup into onCreate.
     * [CoverHeaders] is built inside the same lambda for the same reason — it
     * classloads extensions on first use.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                Injekt.get<NetworkHelper>().client
                    .newBuilder()
                    .addInterceptor(CoverHeaders.interceptor(this))
                    .build()
            }
            .build()
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
