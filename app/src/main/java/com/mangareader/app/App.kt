package com.mangareader.app

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import kotlin.concurrent.thread

/**
 * Registers the dependencies Tachiyomi extensions look up via Injekt.
 *
 * Extension constructors and lazy properties call Injekt.get()/injectLazy()
 * for these three types. Without the bindings the classes still load, but the
 * first real call fails — so this must run before ExtensionLoader.loadAll().
 */
class App : Application(), ImageLoaderFactory {

    private val animeDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            thread(name = "anime-download-complete", isDaemon = true) {
                AnimeDirectDownloadReconciler.reconcileId(context.applicationContext, id)
            }
        }
    }

    /**
     * The earliest hook this app has.
     *
     * `onCreate` is not first. Android runs attachBaseContext, then every
     * ContentProvider, then onCreate — and WorkManager registers its own
     * `androidx.startup.InitializationProvider` through manifest merging, so a
     * failure there happens in a window `onCreate` never sees. 0.138 died in
     * exactly that window under R8 and wrote nothing, because the handler it
     * shipped was installed one phase too late.
     *
     * `base` rather than `this`: the Application's own context is not usable
     * yet at this point, and CrashLog only needs filesDir.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(base)
    }

    override fun onCreate() {
        super.onCreate()
        Injekt.importModule(AppModule(this))

        ContextCompat.registerReceiver(
            this,
            animeDownloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        thread(name = "anime-download-reconcile", isDaemon = true) {
            AnimeDirectDownloadReconciler.reconcileAll(this)
        }
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
            .components {
                // Without this, Coil's default BitmapFactoryDecoder still
                // "succeeds" on a GIF or animated WebP cover — it just decodes
                // frame zero and stops. No error, no grey box, nothing
                // CoverImage's failure overlay could ever catch: the cover is
                // there and looks completely normal, it just never moves.
                // That is the whole shape of "some series have animated
                // pictures" — the app was never told the format could animate.
                //
                // ImageDecoderDecoder wraps Android's own ImageDecoder and
                // covers GIF, animated WebP and animated HEIF in one pass, but
                // it's API 28+ only. GifDecoder (Movie-based) is the fallback
                // for minSdk 24..27 and only understands GIF — an animated
                // WebP cover on API 24-27 still renders as a still frame,
                // which is a platform ceiling, not something to chase here.
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
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
