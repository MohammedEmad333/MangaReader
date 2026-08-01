# Rules handed to whatever app consumes this module. They do nothing until
# that app minifies — which, until 0.121, it never did, so the narrower version
# of this file that stood here had never once been applied.
#
# THE WHOLE MODULE IS AN API SURFACE FOR FOREIGN CODE.
#
# :source-api is not a library this app calls. It is the set of classes that
# extension APKs — loaded with PathClassLoader, parented to the app
# classloader — resolve by name at runtime. R8 cannot see an extension, so
# every symbol here that :app does not itself call looks dead to it.
#
# So the rule is blanket rather than surgical. The previous version kept
# `.model.**`, `.online.**` and subclasses of Source, and missed:
#
#   - eu.kanade.tachiyomi.network.**  — NetworkHelper, the interceptors,
#     GET/POST in Requests.kt, awaitSuccess / parseAs / asObservableSuccess in
#     OkHttpExtensions.kt. That is the surface every extension touches on every
#     request, and NetworkHelper doubles as an Injekt binding key.
#   - The top-level source interfaces — CatalogueSource, ConfigurableSource,
#     SourceFactory, UnmeteredSource. The `extends Source` rule does not reach
#     the interfaces themselves, and ExtensionLoader type-checks instances
#     against them to decide whether a class is a source or a factory.
#   - eu.kanade.tachiyomi.util.RxExtension — awaitSingle.
#
# The module is about thirty-five files. Keeping all of it is worth far less
# than one source that loads and then fails at first use.
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }

# SMangaImpl.url/.title and SChapterImpl.url/.name are lateinit, and the
# extension writes them. Keeping members covers the accessors; this keeps the
# backing fields from being merged away by the optimiser as well.
-keepclassmembers class eu.kanade.tachiyomi.source.model.** {
    <fields>;
}
