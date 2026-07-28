package com.mangareader.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import java.io.File

/**
 * Where downloads and automatic backups are written.
 *
 * **This is a real filesystem path, not a SAF tree, and that is the whole
 * design.** The obvious way to let a user pick any folder on Android 11+ is
 * `ACTION_OPEN_DOCUMENT_TREE` and `DocumentFile`, which is what Mihon does — and
 * it would have cost this app a rewrite of its entire page pipeline, because a
 * page would stop being a [File] and become a [Uri]. That type is in
 * `Source.loadPages`, `loadPagesProgressively`, `TachiyomiSourceAdapter`,
 * `LocalSource`, `DownloadIndex`, `YomuApp`'s reader state and
 * `ReaderScreen`'s parameter list. Six files, every read and write rerouted
 * through `ContentResolver`, with no compiler in the loop.
 *
 * `MANAGE_EXTERNAL_STORAGE` gets the same user-visible result — any folder,
 * visible in a file manager, surviving a reinstall — while a page stays a
 * `File`, so **nothing below [Downloads.root] changes at all**. The permission
 * is the price: Google restricts it on Play, and this app is sideloaded from
 * GitHub Releases, so that restriction doesn't reach it. Mihon can't make this
 * trade. This app can.
 *
 * The picker is still `ACTION_OPEN_DOCUMENT_TREE`, because it's the folder UI
 * people know — [pathFromTreeUri] turns what it returns back into a path.
 */
internal object StorageLocation {

    private const val KEY = "storage_dir"

    /**
     * Everything lives under a folder of this name inside whatever the user
     * picked, rather than directly in it.
     *
     * The picker hands back real folders — Documents, the SD card root, an
     * existing manga folder with other things in it — and scattering `downloads`
     * and `backups` loose among their contents makes the app impossible to
     * uninstall tidily and easy to break by tidying. One named folder is also
     * what makes "delete everything Yomu wrote" a single gesture in a file
     * manager.
     */
    const val APP_FOLDER = "Yomu"

    /** `<base>/downloads/<Source>/<Series>/<Chapter>` — see [DownloadPaths]. */
    const val DOWNLOADS = "downloads"

    /**
     * The flat `<md5>` layout that predates [DOWNLOADS].
     *
     * Still read, never written to. Chapters downloaded before the readable tree
     * existed stay here and keep working; [Downloads.dirFor] checks it whenever
     * the path index has nothing.
     */
    const val CHAPTERS = "chapters"

    /** Automatic backups. */
    const val BACKUPS = "backups"

    // Resolving the base means a permission check and a write probe, and
    // `Downloads.dirFor` is called once per page. Memoised against the stored
    // string, so it re-resolves when the setting changes and not otherwise.
    @Volatile
    private var cachedBase: File? = null

    @Volatile
    private var cachedKey: String? = null

    /** The chosen folder, whether or not it currently works. Null means default. */
    fun chosen(context: Context): File? =
        prefs(context).getString(KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }

    /**
     * The folder actually in use.
     *
     * Falls back to `filesDir` whenever the chosen one can't be written —
     * permission revoked, SD card pulled, folder deleted by a file manager.
     * Falling back rather than failing is deliberate: a download that errors
     * because a card is missing is a bug report, while one that quietly lands
     * in internal storage is a full library and a wrong-looking path on one
     * settings row.
     */
    fun base(context: Context): File {
        val want = prefs(context).getString(KEY, null)
        val hit = cachedBase
        if (hit != null && cachedKey == want) return hit
        val resolved = resolve(context, want)
        cachedBase = resolved
        cachedKey = want
        return resolved
    }

    private fun resolve(context: Context, want: String?): File {
        val fallback = context.applicationContext.filesDir
        if (want.isNullOrBlank()) return fallback
        if (!hasAccess(context)) return fallback
        val dir = appFolderIn(File(want))
        return if (ensureWritable(dir)) dir else fallback
    }

    /**
     * `<picked>/Yomu`, unless they picked a folder already called that.
     *
     * Without the second half, choosing the folder the app made last time would
     * nest a `Yomu` inside a `Yomu` — which is exactly what someone re-picking
     * their existing location does, and it would silently orphan everything in
     * the outer one.
     */
    fun appFolderIn(picked: File): File =
        if (picked.name.equals(APP_FOLDER, ignoreCase = true)) picked
        else File(picked, APP_FOLDER)

    /** True when the chosen folder is the one actually being used. */
    fun active(context: Context): Boolean {
        val want = chosen(context) ?: return false
        // Against the app folder inside the choice, not the choice itself —
        // base() nests one level down, so comparing to the raw pick would say
        // "not active" every time and permanently show the fallback warning.
        return base(context).absolutePath == appFolderIn(want).absolutePath
    }

    fun set(context: Context, dir: File) {
        prefs(context).edit().putString(KEY, dir.absolutePath).apply()
        invalidate()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
        invalidate()
    }

    fun invalidate() {
        cachedBase = null
        cachedKey = null
    }

    // ---------- permission ----------

    /**
     * Three eras, one question.
     *
     * API 30+ wants All files access, granted from a system settings page
     * rather than a dialog. API 29 has scoped storage but honours
     * `requestLegacyExternalStorage`, so the old runtime permission still
     * works. Below that it's the plain runtime permission.
     */
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /**
     * The settings page that grants All files access, or null below API 30
     * where a runtime permission request is the right move instead.
     *
     * Falls back to the whole-list page when the per-app one isn't handled —
     * some OEM builds don't implement the targeted action.
     */
    fun accessIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val direct = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )
        val handled = context.packageManager.resolveActivity(direct, 0) != null
        return if (handled) direct else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    // ---------- folders ----------

    /**
     * Writable is proved by writing, not by [File.canWrite].
     *
     * `canWrite()` consults permission bits, and on external storage those say
     * yes to paths the platform will still refuse — it reports the filesystem's
     * opinion, not scoped storage's. The only honest test is a real file.
     */
    fun ensureWritable(dir: File): Boolean = runCatching {
        if (!dir.exists() && !dir.mkdirs()) return false
        if (!dir.isDirectory) return false
        val probe = File(dir, ".yomu_write_test")
        probe.writeText("ok")
        val ok = probe.exists()
        probe.delete()
        ok
    }.getOrDefault(false)

    /**
     * `content://…/tree/primary%3AManga%2FYomu` → `/storage/emulated/0/Manga/Yomu`.
     *
     * The document id of an external-storage tree is `<volume>:<relative path>`,
     * where `primary` is the built-in card and anything else is a volume UUID
     * mounted at `/storage/<uuid>`. Returns null for a provider that isn't
     * external storage — Drive and Downloads both hand out trees that have no
     * path behind them, and the caller has to say so rather than build a
     * plausible-looking path that doesn't exist.
     */
    fun pathFromTreeUri(uri: Uri): File? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null
        val parts = docId.split(':', limit = 2)
        val volume = parts.getOrNull(0).orEmpty()
        val relative = parts.getOrNull(1).orEmpty()
        val root = when {
            volume.equals("primary", ignoreCase = true) ->
                Environment.getExternalStorageDirectory()
            volume.isBlank() -> return null
            else -> File("/storage/" + volume)
        }
        if (!root.exists()) return null
        if (relative.isBlank()) root else File(root, relative)
    }.getOrNull()

    /**
     * Everywhere a chapter downloaded by an older build might be sitting.
     *
     * Three eras: the current base's own legacy folder, the folder chosen before
     * the `Yomu` subfolder existed, and app storage from before the location was
     * settable at all. Checked in that order and only when the path index comes
     * up empty, so this costs nothing for chapters downloaded since.
     */
    fun legacyRoots(context: Context): List<File> {
        val out = mutableListOf(File(base(context), CHAPTERS))
        chosen(context)?.let { out += File(it, CHAPTERS) }
        out += File(context.applicationContext.filesDir, CHAPTERS)
        return out.distinctBy { it.absolutePath }
    }

    /** For the settings row: `/storage/emulated/0/Manga` reads better than the raw path. */
    fun label(context: Context): String {
        val dir = base(context)
        return if (dir == context.applicationContext.filesDir) "App storage (default)"
        else dir.absolutePath
    }

    // ---------- moving ----------

    /**
     * Moves the contents of [from] into [to], returning how many entries moved.
     *
     * `renameTo` across mount points fails — internal storage to an SD card is
     * exactly that case — and it fails by returning false rather than throwing,
     * which would silently move nothing. Copy-then-delete is the fallback, and
     * it's why this can take a while on a large library and is called off the
     * main thread.
     *
     * A partial move is safe to retry: anything already at the destination is
     * replaced, and the source entry is only deleted once its copy exists.
     */
    fun move(from: File, to: File): Result<Int> = runCatching {
        if (!from.exists() || !from.isDirectory) return@runCatching 0
        // Resetting to app storage while the chosen folder was already falling
        // back to it lands here with both sides equal. Renaming every child onto
        // itself is harmless but pointless, and the delete at the end would then
        // be trying to remove the live download root.
        if (from.absolutePath == to.absolutePath) return@runCatching 0
        if (!ensureWritable(to)) error("Can't write to the new folder")

        var moved = 0
        from.listFiles()?.forEach { child ->
            val target = File(to, child.name)
            if (child.renameTo(target)) {
                moved++
            } else {
                if (target.exists()) target.deleteRecursively()
                child.copyRecursively(target, overwrite = true)
                child.deleteRecursively()
                moved++
            }
        }
        runCatching { from.delete() }
        moved
    }

    /** The subfolders this app owns. Everything else in a base is someone else's. */
    private val OWNED = listOf(DOWNLOADS, CHAPTERS, BACKUPS)

    /** Whether a base has anything worth moving, for the move prompt. */
    fun hasStore(base: File): Boolean = runCatching {
        OWNED.any {
            val dir = File(base, it)
            dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
        }
    }.getOrDefault(false)

    /**
     * Moves only the folders this app owns from one base to another.
     *
     * Not the whole base: when the old one is `filesDir` it also holds the
     * chapter-list cache, the download queue and the path index, none of which
     * belong in the user's folder — and moving `download_queue.json` out from
     * under a running service is a good way to lose a queue.
     */
    fun moveStore(from: File, to: File): Result<Int> = runCatching {
        if (from.absolutePath == to.absolutePath) return@runCatching 0
        var moved = 0
        OWNED.forEach { name ->
            val source = File(from, name)
            if (source.isDirectory) moved += move(source, File(to, name)).getOrDefault(0)
        }
        moved
    }
}
