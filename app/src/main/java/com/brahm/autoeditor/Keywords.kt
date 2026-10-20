package com.brahm.autoeditor

/** Pick notable content words from the transcript to trigger b-roll / keyword pops. */
object Keywords {
    private val STOP = setOf(
        // English
        "the","a","an","is","are","was","were","be","been","to","of","in","on","at","and","or",
        "but","if","so","it","this","that","these","those","i","you","he","she","we","they","my",
        "your","our","for","with","as","by","from","not","no","yes","do","does","did","can","will",
        "would","should","has","have","had","what","which","who","how","when","where","why","there",
        "here","then","than","also","very","just","more","most","some","any","all","about","into",
        // Hindi (common)
        "है","हैं","का","की","के","को","में","से","पर","और","या","यह","वह","ये","वे","हम","तुम",
        "आप","मैं","नहीं","हो","था","थी","थे","कि","जो","तो","भी","ही","एक","बहुत","क्या","कैसे"
    )

    data class Hit(val keyword: String, val start: Double, val end: Double)

    fun extract(words: List<Word>, max: Int = 6, minGapSec: Double = 2.5): List<Hit> {
        val hits = ArrayList<Hit>()
        var lastT = -100.0
        val used = HashSet<String>()
        for (w in words) {
            val raw = w.w.trim().trim('.', ',', '!', '?', '।', ':', ';', '"', '\'', '(', ')')
            val key = raw.lowercase()
            if (raw.length < 4) continue
            if (key in STOP || key in used) continue
            if (w.s - lastT < minGapSec) continue
            // prefer "notable" words: longer or capitalised
            if (raw.length < 6 && raw[0].isLowerCase()) continue
            hits.add(Hit(raw, w.s, w.e + 1.8))
            used.add(key); lastT = w.s
            if (hits.size >= max) break
        }
        return hits
    }
}
