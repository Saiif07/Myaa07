package com.myraa.app

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Direct voice/text commands that run instantly on the phone: no model, no API key, no internet needed.
 * "open youtube", "flipkart kholo", "open flipkart on chrome", "torch on", "5 minute ka timer lagao" ...
 * Anything that does not match (or cannot be resolved) returns null and falls through to the AI agent.
 */
object LocalCommands {
    private class Rule(val name: String, pattern: String) {
        val re = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    // <phrases>
    private val devaPhrases = listOf(
        "खोल दो" to "kholo", "कर दो" to "kar do", "कम करो" to "kam karo", "यू ट्यूब" to "youtube",
        "फ्लिप कार्ट" to "flipkart", "के लिए" to "ke liye", "चालू करो" to "chalu karo", "बंद करो" to "band karo"
    )
    // </phrases>

    // <words>
    private val devaWords = mapOf(
        "खोलो" to "kholo", "खोलिए" to "kholo", "खोल" to "kholo", "ओपन" to "open", "यूट्यूब" to "youtube",
        "फ्लिपकार्ट" to "flipkart", "क्रोम" to "chrome", "गूगल" to "google", "व्हाट्सएप" to "whatsapp",
        "वॉट्सऐप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "इंस्टाग्राम" to "instagram", "अमेज़न" to "amazon",
        "अमेजन" to "amazon", "फेसबुक" to "facebook", "नेटफ्लिक्स" to "netflix", "जीमेल" to "gmail", "मैप्स" to "maps",
        "कैमरा" to "camera", "ब्राउज़र" to "browser", "ब्राउजर" to "browser", "कॉल" to "call", "फोन" to "phone",
        "टॉर्च" to "torch", "फ्लैशलाइट" to "flashlight", "चालू" to "chalu", "बंद" to "band", "सर्च" to "search",
        "ढूंढो" to "dhundo", "बजाओ" to "bajao", "चलाओ" to "chalao", "गाना" to "gaana", "वॉल्यूम" to "volume",
        "आवाज़" to "awaaz", "आवाज" to "awaaz", "बढ़ाओ" to "badhao", "टाइमर" to "timer", "अलार्म" to "alarm",
        "बैटरी" to "battery", "पर" to "par", "में" to "me", "करो" to "karo", "सेटिंग्स" to "settings",
        "वाईफाई" to "wifi", "ब्लूटूथ" to "bluetooth", "स्पॉटिफाई" to "spotify", "रास्ता" to "rasta", "का" to "ka",
        "मिनट" to "minute", "घंटे" to "ghante", "सेकंड" to "second", "बजे" to "baje", "सुबह" to "subah",
        "शाम" to "shaam", "रात" to "raat", "लगाओ" to "lagao", "बताओ" to "batao",
        "०" to "0", "१" to "1", "२" to "2", "३" to "3", "४" to "4", "५" to "5", "६" to "6", "७" to "7", "८" to "8", "९" to "9"
    )
    // </words>

    private val filler = Regex("\\b(?:please|hey myra+a*|myra+a*|zara|jara|bhai|yaar|ji|sir)\\b", RegexOption.IGNORE_CASE)

    // <sites>
    private val sites = mapOf(
        "youtube" to "https://www.youtube.com", "google" to "https://www.google.com", "gmail" to "https://mail.google.com",
        "drive" to "https://drive.google.com", "docs" to "https://docs.google.com", "sheets" to "https://sheets.google.com",
        "slides" to "https://slides.google.com", "meet" to "https://meet.google.com", "calendar" to "https://calendar.google.com",
        "photos" to "https://photos.google.com", "maps" to "https://maps.google.com", "translate" to "https://translate.google.com",
        "news" to "https://news.google.com", "playstore" to "https://play.google.com", "gemini" to "https://gemini.google.com",
        "bing" to "https://www.bing.com", "duckduckgo" to "https://duckduckgo.com", "yahoo" to "https://www.yahoo.com",
        "facebook" to "https://www.facebook.com", "instagram" to "https://www.instagram.com", "twitter" to "https://x.com",
        "threads" to "https://www.threads.net", "linkedin" to "https://www.linkedin.com", "pinterest" to "https://www.pinterest.com",
        "reddit" to "https://www.reddit.com", "snapchat" to "https://www.snapchat.com", "tiktok" to "https://www.tiktok.com",
        "whatsapp" to "https://web.whatsapp.com", "telegram" to "https://web.telegram.org", "discord" to "https://discord.com",
        "quora" to "https://www.quora.com", "medium" to "https://medium.com", "github" to "https://github.com",
        "gitlab" to "https://gitlab.com", "stackoverflow" to "https://stackoverflow.com", "wikipedia" to "https://www.wikipedia.org",
        "imdb" to "https://www.imdb.com", "netflix" to "https://www.netflix.com", "primevideo" to "https://www.primevideo.com",
        "hotstar" to "https://www.hotstar.com", "jiocinema" to "https://www.jiocinema.com", "sonyliv" to "https://www.sonyliv.com",
        "zee5" to "https://www.zee5.com", "spotify" to "https://open.spotify.com", "jiosaavn" to "https://www.jiosaavn.com",
        "gaana" to "https://gaana.com", "wynk" to "https://wynk.in", "soundcloud" to "https://soundcloud.com",
        "twitch" to "https://www.twitch.tv", "youtubemusic" to "https://music.youtube.com", "amazon" to "https://www.amazon.in",
        "flipkart" to "https://www.flipkart.com", "myntra" to "https://www.myntra.com", "ajio" to "https://www.ajio.com",
        "meesho" to "https://www.meesho.com", "nykaa" to "https://www.nykaa.com", "snapdeal" to "https://www.snapdeal.com",
        "tatacliq" to "https://www.tatacliq.com", "croma" to "https://www.croma.com", "reliancedigital" to "https://www.reliancedigital.in",
        "bigbasket" to "https://www.bigbasket.com", "blinkit" to "https://blinkit.com", "zepto" to "https://www.zeptonow.com",
        "swiggy" to "https://www.swiggy.com", "zomato" to "https://www.zomato.com", "dominos" to "https://www.dominos.co.in",
        "mcdonalds" to "https://www.mcdonaldsindia.com", "kfc" to "https://online.kfc.co.in", "pizzahut" to "https://www.pizzahut.co.in",
        "uber" to "https://www.uber.com", "ola" to "https://www.olacabs.com", "rapido" to "https://www.rapido.bike",
        "makemytrip" to "https://www.makemytrip.com", "goibibo" to "https://www.goibibo.com", "cleartrip" to "https://www.cleartrip.com",
        "irctc" to "https://www.irctc.co.in", "redbus" to "https://www.redbus.in", "ixigo" to "https://www.ixigo.com",
        "booking" to "https://www.booking.com", "airbnb" to "https://www.airbnb.com", "paytm" to "https://paytm.com",
        "phonepe" to "https://www.phonepe.com", "gpay" to "https://pay.google.com", "sbi" to "https://www.onlinesbi.sbi",
        "hdfcbank" to "https://www.hdfcbank.com", "icicibank" to "https://www.icicibank.com", "axisbank" to "https://www.axisbank.com",
        "kotak" to "https://www.kotak.com", "zerodha" to "https://zerodha.com", "groww" to "https://groww.in",
        "upstox" to "https://upstox.com", "paypal" to "https://www.paypal.com", "ebay" to "https://www.ebay.com",
        "aliexpress" to "https://www.aliexpress.com", "olx" to "https://www.olx.in", "naukri" to "https://www.naukri.com",
        "indeed" to "https://www.indeed.com", "glassdoor" to "https://www.glassdoor.com", "coursera" to "https://www.coursera.org",
        "udemy" to "https://www.udemy.com", "khanacademy" to "https://www.khanacademy.org", "byjus" to "https://byjus.com",
        "unacademy" to "https://unacademy.com", "duolingo" to "https://www.duolingo.com", "chatgpt" to "https://chatgpt.com",
        "claude" to "https://claude.ai", "perplexity" to "https://www.perplexity.ai", "outlook" to "https://outlook.live.com",
        "office" to "https://www.office.com", "onedrive" to "https://onedrive.live.com", "dropbox" to "https://www.dropbox.com",
        "notion" to "https://www.notion.so", "canva" to "https://www.canva.com", "figma" to "https://www.figma.com",
        "zoom" to "https://zoom.us", "teams" to "https://teams.microsoft.com", "slack" to "https://slack.com",
        "weather" to "https://weather.com", "cricbuzz" to "https://www.cricbuzz.com", "espncricinfo" to "https://www.espncricinfo.com",
        "hindustantimes" to "https://www.hindustantimes.com", "ndtv" to "https://www.ndtv.com", "timesofindia" to "https://timesofindia.indiatimes.com",
        "bbc" to "https://www.bbc.com", "cnn" to "https://www.cnn.com", "nytimes" to "https://www.nytimes.com",
        "bookmyshow" to "https://in.bookmyshow.com", "justdial" to "https://www.justdial.com", "urbancompany" to "https://www.urbancompany.com",
        "practo" to "https://www.practo.com", "1mg" to "https://www.1mg.com", "pharmeasy" to "https://pharmeasy.in",
        "digilocker" to "https://www.digilocker.gov.in", "uidai" to "https://uidai.gov.in", "incometax" to "https://www.incometax.gov.in",
        "whatsappweb" to "https://web.whatsapp.com", "browser" to "https://www.google.com", "internet" to "https://www.google.com"
    )
    // </sites>

    private val rules = listOf(
        Rule("settings", "^(?:open\\s+)?(wifi|bluetooth|display|sound|battery saver|location|airplane mode|apps)\\s+settings$"),
        Rule("timer_a", "^(?:set\\s+)?(?:a\\s+)?timer\\s+(?:for\\s+)?(\\d+)\\s*(sec|second|seconds|min|minute|minutes|hour|hours|ghante|ghanta)$"),
        Rule("timer_b", "^(\\d+)\\s*(sec|second|seconds|min|minute|minutes|hour|hours|ghante|ghanta)\\s*(?:ka|ke liye)?\\s*timer(?:\\s+(?:set\\s+)?(?:karo|kar do|lagao))?$"),
        Rule("alarm_a", "^(?:set\\s+)?(?:an?\\s+)?alarm\\s+(?:for\\s+|at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?$"),
        Rule("alarm_b", "^(?:(subah|shaam|raat|dopahar)\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?\\s*(?:ka|ke liye)?\\s*alarm(?:\\s+(?:set\\s+)?(?:karo|kar do|lagao))?$"),
        Rule("torch_a", "^(?:torch|flashlight|flash light|flash|tarch)(?:\\s+(on|off|chalu|band|jalao|bujhao))?(?:\\s+(?:karo|kar do|kijiye))?$"),
        Rule("torch_b", "^(?:turn|switch)\\s+(on|off)\\s+(?:the\\s+)?(?:torch|flashlight|flash light|flash)$"),
        Rule("volume_a", "^(?:volume|awaaz|sound)\\s+(up|down|max|full|mute|badhao|kam karo|kam|band|zyada)(?:\\s+(?:karo|kar do))?$"),
        Rule("volume_b", "^(increase|decrease|raise|lower|turn up|turn down)\\s+(?:the\\s+)?volume$"),
        Rule("mute", "^(?:mute|silent)$"),
        Rule("battery", "^(?:what(?:'s| is)(?: the)? )?(?:my )?battery(?: level| status| percentage| kitni hai| kitna hai| kitni| %)?\\??$|^kitni battery(?: hai| bachi hai)?$"),
        Rule("time", "^(?:what(?:'s| is)?(?: the)? )?(?:current )?time(?: is it| now| kya hai| kya hua hai)?\\??$|^(?:abhi )?(?:kitne baje(?: hain?)?|samay kya hai)$"),
        Rule("browser_a", "^(?!(?:search|dhundo|dhoondo|find|look up)\\b)(?:open|kholo|khol|launch|start|go to|visit)?\\s*(.+?)\\s+(?:on|in|me|mein|par|pe)\\s+(chrome|browser|firefox|edge|brave|opera|samsung internet)(?:\\s+(?:me|mein|par|pe))?(?:\\s+(?:open|kholo|khol do|khol|karo|kar do|chalao))?$"),
        Rule("browser_b", "^(.+?)\\s+(chrome|browser|firefox|edge|brave|opera)\\s+(?:me|mein|par|pe|on|in)(?:\\s+(?:open|kholo|khol do|khol|karo|kar do|chalao))?$"),
        Rule("browser_c", "^(chrome|browser|firefox|edge|brave|opera)\\s+(?:me|mein|par|pe|on|in)\\s+(.+?)(?:\\s+(?:open|kholo|khol do|khol|karo|kar do|chalao))?$"),
        Rule("search_a", "^(?:search|dhundo|dhoondo|find|look up)\\s+(?:for\\s+)?(.+?)(?:\\s+(on|in|par|pe|me|mein)\\s+([a-z0-9.]+(?: [a-z0-9.]+)?))?$"),
        Rule("search_b", "^(.+?)\\s+(?:search karo|search kar do|search kar|dhundo|dhoondo|search)(?:\\s+(?:on|par|pe)\\s+(google|youtube|amazon|flipkart|maps|wikipedia|chrome|browser))?$"),
        Rule("play_a", "^(?:play|bajao|lagao|laga do)\\s+(.+?)(?:\\s+(?:on|par|pe)\\s+(youtube|spotify))?$"),
        Rule("open_prefix", "^(?:open|kholo|khol|launch|start|go to|visit)\\s+(?:the\\s+)?(.+?)(?:\\s+(?:app|application|website|site))?$"),
        Rule("open_suffix", "^(.+?)\\s+(?:kholo|khol do|khol|open karo|open kar do|open|chalao|chalu karo|chalu kar do|start karo|launch karo)$"),
        Rule("call_a", "^(?:call|dial|phone)\\s+(.+)$"),
        Rule("call_b", "^(.+?)\\s+(?:ko\\s+)?(?:call karo|call kar do|call lagao|phone karo|phone lagao|call)$"),
        Rule("navigate_a", "^(?:navigate|directions|route|take me)\\s+(?:to\\s+)?(.+)$"),
        Rule("navigate_b", "^(.+?)\\s+(?:ka rasta|ke liye rasta|tak ka rasta|navigate karo|ka route)(?:\\s+(?:batao|dikhao))?$"),
        Rule("play_b", "^(.+?)\\s+(?:bajao|chalao|laga do|lagao|play karo|play kar do)$")
    )

    private fun norm(raw: String): String {
        var t = raw.lowercase().trim()
        for (p in devaPhrases) t = t.replace(p.first, p.second)
        t = t.split(Regex("\\s+")).joinToString(" ") { w -> devaWords[w] ?: w }
        t = t.replace(Regex("\\s+dot\\s+(com|in|org|net|co|io|ai)\\b"), ".$1")
        t = t.replace(filler, " ")
        t = t.replace(Regex("^[\\s.!?,;।]+"), "").replace(Regex("[\\s.!?,;।]+$"), "")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    /** Returns the spoken reply if a rule ran, or null when this is not a direct command. */
    fun handle(ctx: Context, raw: String): String? {
        val t = norm(raw)
        if (t.isEmpty() || t.length > 120) return null
        for (r in rules) {
            val m = r.re.find(t) ?: continue
            val reply = run(ctx, r.name, m.groupValues) ?: continue
            return reply
        }
        return null
    }

    private fun tool(ctx: Context, n: String, vararg kv: Pair<String, Any>): String {
        val o = JSONObject()
        for (p in kv) o.put(p.first, p.second)
        return PhoneAgent.callTool(ctx, n, o)
    }

    private fun say(r: String, ok: String): String =
        if (r.startsWith("error:") || r.startsWith("failed:")) "Nahi ho paya: " + r.removePrefix("error: ")
        else if (r == "tap-needed") "Notification par tap karke kholo. Seedha khulne ke liye SETUP mein OVERLAY permission do."
        else ok

    private fun unitSecs(u: String): Int = if (u.lowercase().startsWith("sec")) 1 else if (u.lowercase().startsWith("min")) 60 else 3600

    private fun human(secs: Int): String =
        if (secs >= 3600 && secs % 3600 == 0) (secs / 3600).toString() + " ghante" else if (secs >= 60 && secs % 60 == 0) (secs / 60).toString() + " minute" else secs.toString() + " second"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun searchUrl(engine: String, q: String): String {
        val e = enc(q)
        return when (engine.lowercase()) {
            "youtube" -> "https://www.youtube.com/results?search_query=" + e
            "amazon" -> "https://www.amazon.in/s?k=" + e
            "flipkart" -> "https://www.flipkart.com/search?q=" + e
            "maps" -> "https://www.google.com/maps/search/?api=1&query=" + e
            "wikipedia" -> "https://en.wikipedia.org/w/index.php?search=" + e
            else -> "https://www.google.com/search?q=" + e
        }
    }

    private fun siteUrl(t: String): String? {
        val key = t.lowercase().replace(" ", "")
        val exact = sites[key]
        if (exact != null) return exact
        var bestKey = ""
        for (e in sites.entries) if (e.key.length >= 4 && key.contains(e.key) && e.key.length > bestKey.length) bestKey = e.key
        if (bestKey.isNotEmpty()) return sites[bestKey]
        if (Regex("^[a-z0-9-]+(\\.[a-z]{2,})+(/\\S*)?$").matches(t)) return "https://" + t
        var best = 0
        var bestUrl: String? = null
        for (e in sites.entries) {  // sound-alike: "फ्लिपकार्ट", "flipcart", "swigy" ...
            val sc = Fuzzy.score(t, e.key)
            if (sc > best) { best = sc; bestUrl = e.value }
        }
        return if (best >= 85) bestUrl else null
    }

    private val chatty = Regex("\\b(is|are|was|what|how|why|who|kya|hai|kaise|for|and|with|of|the)\\b", RegexOption.IGNORE_CASE)

    /** Open ANY app (matched by sound-alike on installed apps) or ANY website (known list, domain, or auto top-result search). */
    private fun open(ctx: Context, target0: String, browser: String): String? {
        var t = target0.trim().removePrefix("the ").trim()
        t = t.replace(Regex("^(?:open|kholo|khol|launch|start)\\s+"), "").trim()
        val wantsApp = Regex("\\s+(?:app|application)$").containsMatchIn(t)
        t = t.replace(Regex("\\s+(?:app|application|website|site)$"), "").trim()
        if (t.isEmpty()) return null
        if (browser.isEmpty()) {
            val r = tool(ctx, "open_app", "name" to t)
            if (!r.startsWith("error:")) return say(r, "Theek hai, " + t + " khol raha hoon.")
        }
        val known = siteUrl(t)
        val short = t.split(" ").size <= 3 && !chatty.containsMatchIn(t)
        val url: String = known
            ?: (if (wantsApp && browser.isEmpty()) "https://play.google.com/store/search?c=apps&q=" + enc(t) else null)
            ?: (if (short) "https://www.google.com/search?btnI=1&q=" + enc(t) else null)
            ?: (if (browser.isNotEmpty()) searchUrl("google", t) else null)
            ?: return null
        val b = if (browser == "browser") "" else browser
        val r = tool(ctx, "open_url", "url" to url, "browser" to b)
        val where = if (b.isEmpty()) "browser mein" else b + " mein"
        val what = if (known == null && url.contains("play.google.com")) t + " ko Play Store mein dhoondh raha hoon (app install nahi mila)." else "Theek hai, " + t + " " + where + " khol raha hoon."
        return say(r, what)
    }

    private fun search(ctx: Context, q: String, prep: String, engine: String): String? {
        val e = engine.lowercase()
        val known = setOf("google", "youtube", "amazon", "flipkart", "maps", "wikipedia")
        var url: String
        var browser = ""
        var query = q
        if (e.isEmpty() || e == "google") url = searchUrl("google", q)
        else if (e == "chrome" || e == "browser") { url = searchUrl("google", q); browser = if (e == "chrome") "chrome" else "" }
        else if (known.contains(e)) url = searchUrl(e, q)
        else {
            val site = siteUrl(e)
            if (site != null) {  // any website: search inside it
                val host = java.net.URL(site).host.removePrefix("www.")
                url = "https://www.google.com/search?q=" + enc("site:" + host + " " + q)
            } else {             // not a site name (e.g. "best phones in india"): it was part of the query
                query = q + " " + prep + " " + engine
                url = searchUrl("google", query)
            }
        }
        val r = tool(ctx, "open_url", "url" to url, "browser" to browser)
        return say(r, "Theek hai, '" + query + "' search kar raha hoon.")
    }

    private fun alarm(ctx: Context, period: String, h0: String, m0: String, ap: String): String? {
        var h = h0.toIntOrNull() ?: return null
        val m = if (m0.isEmpty()) 0 else (m0.toIntOrNull() ?: return null)
        if (h > 23 || m > 59) return null
        val a = ap.lowercase()
        if (a == "pm" && h < 12) h += 12
        if (a == "am" && h == 12) h = 0
        if (a.isEmpty() && h < 12 && (period == "shaam" || period == "raat")) h += 12
        val r = tool(ctx, "set_alarm", "hour" to h, "minute" to m, "label" to "MYRAA")
        return say(r, "Theek hai, " + h.toString() + ":" + (if (m < 10) "0" else "") + m + " ka alarm laga diya.")
    }

    private fun run(ctx: Context, name: String, g: List<String>): String? {
        return when (name) {
            "settings" -> {
                val page = when (g[1].lowercase()) { "battery saver" -> "battery"; "airplane mode" -> "airplane"; else -> g[1].lowercase() }
                say(tool(ctx, "settings", "page" to page), "Theek hai, " + g[1] + " settings khol raha hoon.")
            }
            "browser_a", "browser_b" -> open(ctx, g[1], g[2].lowercase())
            "browser_c" -> open(ctx, g[2], g[1].lowercase())
            "search_a" -> search(ctx, g[1], g[2], g[3])
            "search_b" -> search(ctx, g[1], "", g[2])
            "play_a" -> say(tool(ctx, "play_music", "query" to g[1]), "Theek hai, " + g[1] + " chala raha hoon.")
            "open_prefix", "open_suffix" -> open(ctx, g[1], "")
            "play_b" -> say(tool(ctx, "play_music", "query" to g[1]), "Theek hai, " + g[1] + " chala raha hoon.")
            "call_a", "call_b" -> say(tool(ctx, "call", "to" to g[1]), "Theek hai, " + g[1] + " ko call ke liye dialer khol raha hoon.")
            "torch_a", "torch_b" -> {
                val off = g[1].lowercase() == "off" || g[1].lowercase() == "band" || g[1].lowercase() == "bujhao"
                val r = tool(ctx, "flashlight", "on" to !off)
                if (r.startsWith("error:")) "Nahi ho paya: " + r.removePrefix("error: ") else (if (off) "Torch band kar di." else "Torch chalu kar di.")
            }
            "volume_a", "volume_b" -> {
                val x = g[1].lowercase()
                val act = if (x == "up" || x == "badhao" || x == "zyada" || x == "increase" || x == "raise" || x == "turn up") "up"
                    else if (x == "down" || x == "kam karo" || x == "kam" || x == "decrease" || x == "lower" || x == "turn down") "down"
                    else if (x == "max" || x == "full") "max" else "mute"
                val r = tool(ctx, "volume", "action" to act)
                if (r.startsWith("error:")) "Nahi ho paya: " + r.removePrefix("error: ") else "Ho gaya, " + r
            }
            "mute" -> say(tool(ctx, "volume", "action" to "mute"), "Mute kar diya.")
            "timer_a", "timer_b" -> {
                val secs = (g[1].toIntOrNull() ?: return null) * unitSecs(g[2])
                say(tool(ctx, "set_timer", "seconds" to secs, "label" to "MYRAA"), "Theek hai, " + human(secs) + " ka timer laga diya.")
            }
            "alarm_a" -> alarm(ctx, "", g[1], g[2], g[3])
            "alarm_b" -> alarm(ctx, g[1].lowercase(), g[2], g[3], g[4])
            "battery" -> tool(ctx, "battery")
            "time" -> "Abhi " + tool(ctx, "time")
            "navigate_a", "navigate_b" -> say(tool(ctx, "navigate", "place" to g[1]), "Theek hai, " + g[1] + " ka rasta khol raha hoon.")
            else -> null
        }
    }
}
