package com.mangareader.app

internal object EmbeddedPlayerScripts {
    val PLAY = """
        (function () {
          var v = document.querySelector('video');
          if (!v) return 'no <video> to play';
          var p = v.play();
          if (p && p.catch) {
            p.catch(function (e) {
              var el = document.getElementById('yomu-note');
              if (!el) {
                el = document.createElement('div');
                el.id = 'yomu-note';
                document.body.appendChild(el);
              }
              el.textContent = 'play() rejected: ' + e.name + ' ' + e.message;
            });
          }
          return 'play() called';
        })()
    """.trimIndent()

    val MEDIA_URLS = """
        (function () {
          var out = [];
          var v = document.querySelector('video');
          if (v && v.currentSrc) out.push('src|' + v.currentSrc);
          try {
            var res = performance.getEntriesByType('resource');
            for (var i = 0; i < res.length; i++) {
              var u = res[i].name;
              if (/\.(m3u8|mpd|mp4|webm|mkv|ts)(\?|$)/i.test(u)) out.push('net|' + u);
            }
          } catch (e) {}
          return out.filter(function (x, i) {
            return out.indexOf(x) === i;
          }).join('\n');
        })()
    """.trimIndent()

    val SILENT_PLAY = """
        (function () {
          var v = document.querySelector('video');
          if (!v) return 'no video yet';
          v.muted = true;
          v.volume = 0;
          var p = v.play();
          if (p && p.catch) p.catch(function () {});
          return 'started';
        })()
    """.trimIndent()

    fun parseMediaUrls(raw: String): List<String> =
        raw.removeSurrounding("\"")
            .replace("\\n", "\n")
            .replace("\\/", "/")
            .replace("\\u003C", "<", ignoreCase = true)
            .split("\n")
            .filter { it.isNotBlank() }
            .map { it.substringAfter('|') }
            .distinct()
}
