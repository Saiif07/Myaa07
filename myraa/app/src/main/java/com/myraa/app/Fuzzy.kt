package com.myraa.app

/**
 * Sound-alike matching so ANY app name works however it is spoken or spelt:
 * "फ्लिपकार्ट" ~ "Flipkart", "स्विगी" ~ "Swiggy", "whatsap" ~ "WhatsApp".
 * Both sides are reduced to a consonant skeleton (vowels, h, y dropped) and compared.
 */
object Fuzzy {
    // <deva-skel>
    private val devaSkel = mapOf(
        'क' to "k", 'ख' to "k", 'ग' to "g", 'घ' to "g", 'ङ' to "n", 'च' to "X", 'छ' to "X", 'ज' to "j", 'झ' to "j", 'ञ' to "n",
        'ट' to "t", 'ठ' to "t", 'ड' to "d", 'ढ' to "d", 'ण' to "n", 'त' to "t", 'थ' to "t", 'द' to "d", 'ध' to "d", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "b", 'म' to "m", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "s", 'ष' to "s", 'स' to "s"
    )
    // </deva-skel>

    private val latinPairs = listOf(
        "chr" to "kr", "sh" to "s", "ch" to "X", "kh" to "k", "gh" to "g", "th" to "t", "dh" to "d",
        "ph" to "f", "bh" to "b", "jh" to "j", "ck" to "k", "qu" to "k", "x" to "ks"
    )

    fun skeleton(raw: String): String {
        var s = raw.lowercase()
        for (p in latinPairs) s = s.replace(p.first, p.second)
        val sb = StringBuilder()
        for (ch in s) {
            val m: String? = when {
                devaSkel.containsKey(ch) -> devaSkel[ch]
                ch == 'c' || ch == 'q' -> "k"
                ch == 'w' -> "v"
                ch == 'z' -> "j"
                ch in "bdfgjklmnprstvX" -> ch.toString()
                ch.isDigit() -> ch.toString()
                else -> null
            }
            if (m != null) {
                for (c in m) {
                    if (sb.isEmpty() || sb[sb.length - 1] != c) sb.append(c)
                }
            }
        }
        return sb.toString()
    }

    fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + (if (a[i - 1] == b[j - 1]) 0 else 1))
                prev = tmp
            }
        }
        return dp[b.length]
    }

    /** 0..100: how well [query] sounds like [label]. 85+ is a confident match. */
    fun score(query: String, label: String): Int {
        val q = skeleton(query)
        val l = skeleton(label)
        if (q.length < 2 || l.length < 2) return 0
        if (q == l) return 100
        if (q.length < 3 || l.length < 3) return 0
        if (l.contains(q) || q.contains(l)) return 90
        if (q.length >= 4 && lev(q, l) <= 1) return 85
        return 0
    }
}
