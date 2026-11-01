/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.data

import java.text.Normalizer

/**
 * Temas de las pestañas (como la agrupación automática de Safari en iOS 27), sin IA:
 * primero por dominio conocido, luego por palabras del título y, si nada encaja, por sitio.
 * Es puro y rápido: unas decenas de pestañas se clasifican en microsegundos.
 */
object Topics {
    data class Topic(val name: String, val emoji: String)

    val SHOPPING = Topic("Compras", "🛍️")
    val VIDEO = Topic("Video", "▶️")
    val MUSIC = Topic("Música", "🎵")
    val MAIL = Topic("Correo", "✉️")
    val WORK = Topic("Trabajo y docs", "📄")
    val SOCIAL = Topic("Redes", "💬")
    val NEWS = Topic("Noticias", "📰")
    val DEV = Topic("Desarrollo", "🧑‍💻")
    val AI = Topic("IA", "✨")
    val REFERENCE = Topic("Consulta", "📚")
    val TRAVEL = Topic("Viajes", "✈️")

    /** Dominio (sin www) → tema. Se compara contra el host y sus dominios padre. */
    private val domains: Map<String, Topic> = buildMap {
        fun add(t: Topic, vararg d: String) = d.forEach { put(it, t) }
        add(SHOPPING, "amazon.com", "amazon.es", "amazon.com.mx", "mercadolibre.com", "mercadolibre.com.co", "mercadolibre.com.mx",
            "ebay.com", "aliexpress.com", "temu.com", "shein.com", "walmart.com", "etsy.com", "falabella.com", "exito.com",
            "alkosto.com", "bestbuy.com", "target.com", "ikea.com", "rappi.com", "zara.com", "apple.com/shop")
        add(VIDEO, "youtube.com", "youtu.be", "netflix.com", "twitch.tv", "vimeo.com", "primevideo.com", "disneyplus.com",
            "max.com", "hbomax.com", "crunchyroll.com", "dailymotion.com", "tv.apple.com", "plex.tv")
        add(MUSIC, "spotify.com", "music.apple.com", "music.youtube.com", "soundcloud.com", "deezer.com", "bandcamp.com")
        add(MAIL, "mail.google.com", "outlook.live.com", "outlook.office.com", "mail.proton.me", "mail.yahoo.com", "icloud.com/mail", "fastmail.com")
        add(WORK, "docs.google.com", "drive.google.com", "sheets.google.com", "slides.google.com", "calendar.google.com",
            "meet.google.com", "notion.so", "notion.site", "office.com", "sharepoint.com", "onedrive.live.com", "slack.com",
            "trello.com", "asana.com", "figma.com", "miro.com", "dropbox.com", "zoom.us", "canva.com", "airtable.com",
            "linear.app", "atlassian.net", "teams.microsoft.com", "mobbin.com")
        add(SOCIAL, "facebook.com", "instagram.com", "x.com", "twitter.com", "tiktok.com", "reddit.com", "linkedin.com",
            "threads.net", "threads.com", "bsky.app", "mastodon.social", "pinterest.com", "web.whatsapp.com", "web.telegram.org",
            "discord.com", "tumblr.com")
        add(NEWS, "nytimes.com", "bbc.com", "bbc.co.uk", "cnn.com", "elpais.com", "eltiempo.com", "elespectador.com",
            "theguardian.com", "reuters.com", "apnews.com", "macrumors.com", "theverge.com", "xataka.com", "xataka.com.co",
            "9to5mac.com", "9to5google.com", "cultofmac.com", "idownloadblog.com", "news.google.com", "infobae.com",
            "semana.com", "clarin.com", "elmundo.es", "lanacion.com.ar", "techcrunch.com", "arstechnica.com", "wired.com",
            "engadget.com", "applesfera.com", "genbeta.com", "elcolombiano.com", "bloomberg.com", "news.ycombinator.com")
        add(DEV, "github.com", "gitlab.com", "bitbucket.org", "stackoverflow.com", "stackexchange.com", "developer.android.com",
            "developer.mozilla.org", "developer.apple.com", "npmjs.com", "pypi.org", "kotlinlang.org", "docs.rs", "crates.io",
            "vercel.com", "netlify.com", "codepen.io", "jsfiddle.net", "searchfox.org", "bugzilla.mozilla.org", "localhost")
        add(AI, "chatgpt.com", "chat.openai.com", "openai.com", "claude.ai", "anthropic.com", "gemini.google.com",
            "perplexity.ai", "copilot.microsoft.com", "huggingface.co", "poe.com", "mistral.ai", "chat.mistral.ai",
            "deepseek.com", "chat.deepseek.com", "grok.com", "notebooklm.google.com")
        add(REFERENCE, "wikipedia.org", "wikimedia.org", "wiktionary.org", "rae.es", "wordreference.com", "britannica.com",
            "translate.google.com", "deepl.com", "maps.google.com", "khanacademy.org", "coursera.org", "duolingo.com")
        add(TRAVEL, "booking.com", "airbnb.com", "expedia.com", "tripadvisor.com", "kayak.com", "skyscanner.net",
            "avianca.com", "latamairlines.com", "despegar.com", "uber.com")
    }

    /** Palabras del título (sin tildes, en minúsculas) → tema. Se buscan como palabras completas. */
    private val keywords: List<Pair<Topic, List<String>>> = listOf(
        AI to listOf("ia", "ai", "chatgpt", "gpt", "llm", "chatbot", "inteligencia artificial"),
        MAIL to listOf("bandeja de entrada", "inbox", "correo", "email", "gmail"),
        SHOPPING to listOf("carrito", "comprar", "compra", "oferta", "ofertas", "precio", "tienda", "envio gratis", "shop", "cart", "checkout", "descuento"),
        VIDEO to listOf("video", "videos", "trailer", "episodio", "temporada", "pelicula", "serie", "watch"),
        MUSIC to listOf("cancion", "album", "playlist", "musica", "lyrics", "letra"),
        WORK to listOf("documento", "hoja de calculo", "presentacion", "reunion", "spreadsheet", "calendario", "informe"),
        DEV to listOf("api", "kotlin", "android studio", "documentation", "documentacion", "sdk", "commit", "pull request", "issue", "repositorio", "javascript", "python", "gradle"),
        NEWS to listOf("noticias", "news", "ultima hora", "breaking", "periodico"),
        SOCIAL to listOf("publicacion", "seguidores", "tweet"),
        TRAVEL to listOf("vuelo", "vuelos", "hotel", "hoteles", "reserva", "viaje", "flight"),
    )

    private val wordSplit = Regex("[^a-z0-9]+")

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Tema de una pestaña, o null si no se reconoce (entonces se agrupa por sitio). */
    fun of(url: String, title: String): Topic? {
        val host = Urls.host(url)?.lowercase()?.removePrefix("www.") ?: return null
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
        // Con ruta primero (icloud.com/mail, apple.com/shop), luego el host y sus padres.
        domains["$host/${path.trim('/').substringBefore('/')}"]?.let { return it }
        var h = host
        while (true) {
            domains[h]?.let { return it }
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) break
            h = h.substring(dot + 1)
        }
        val words = " " + normalize(title).split(wordSplit).filter { it.isNotEmpty() }.joinToString(" ") + " "
        if (words.isBlank()) return null
        return keywords.firstOrNull { (_, list) -> list.any { words.contains(" $it ") } }?.first
    }

    /** Nombre del sitio ("Wikipedia", "Bbc") para las pestañas sin tema. */
    fun siteName(url: String): String {
        val host = Urls.host(url)?.removePrefix("www.") ?: return ""
        val parts = host.split('.')
        val core = if (parts.size >= 3 && parts[parts.size - 2].length <= 3) parts[parts.size - 3] else parts.getOrElse(parts.size - 2) { parts[0] }
        return core.replaceFirstChar { it.uppercase() }
    }

    /** Un filtro de la fila de píldoras: tema, sitio o grupo existente, con las pestañas que incluye. */
    data class Filter(val key: String, val label: String, val emoji: String, val tabIds: Set<String>)

    /**
     * Filtros para la vista de pestañas: primero los grupos (carpetas), luego los temas
     * detectados y, para lo que no tiene tema, los sitios con 2 o más pestañas.
     */
    fun filters(tabs: List<Tab>): List<Filter> {
        val list = tabs.filter { !it.isStart }
        val out = mutableListOf<Filter>()
        list.filter { it.folder.isNotEmpty() }.groupBy { it.folder }.forEach { (name, ts) ->
            out += Filter("g:$name", name, "📁", ts.map { it.id }.toSet())
        }
        val byTopic = LinkedHashMap<Topic, MutableList<Tab>>()
        val bySite = LinkedHashMap<String, MutableList<Tab>>()
        list.forEach { t ->
            val topic = of(t.url, t.title)
            if (topic != null) byTopic.getOrPut(topic) { mutableListOf() } += t
            else siteName(t.url).takeIf { it.isNotBlank() }?.let { bySite.getOrPut(it) { mutableListOf() } += t }
        }
        val used = out.map { it.label.lowercase() }.toMutableSet()
        byTopic.entries.sortedByDescending { it.value.size }.forEach { (topic, ts) ->
            if (used.add(topic.name.lowercase())) out += Filter("t:${topic.name}", topic.name, topic.emoji, ts.map { it.id }.toSet())
        }
        bySite.entries.filter { it.value.size >= 2 }.sortedByDescending { it.value.size }.forEach { (site, ts) ->
            if (used.add(site.lowercase())) out += Filter("s:$site", site, "🌐", ts.map { it.id }.toSet())
        }
        return out
    }

    /**
     * Grupos que crearía "Crear grupos por tema": las pestañas sueltas con el mismo tema
     * (o del mismo sitio si no tienen tema), solo si son 2 o más.
     */
    fun groups(tabs: List<Tab>): Map<String, List<Tab>> =
        tabs.filter { it.folder.isEmpty() && !it.isStart && !it.isPrivate }
            .groupBy { of(it.url, it.title)?.name ?: siteName(it.url) }
            .filter { it.key.isNotBlank() && it.value.size >= 2 }
}
