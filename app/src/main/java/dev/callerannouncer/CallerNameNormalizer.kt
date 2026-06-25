package dev.callerannouncer

import java.text.Normalizer
import java.util.Locale

data class SpokenCallerName(
    val text: String,
    val locale: Locale
)

object CallerNameNormalizer {
    private val arabicLocale: Locale = Locale("ar", "EG")
    private val arabicLetters = Regex("[\\u0600-\\u06FF]")
    private val tokenRegex = Regex("[A-Za-z0-9']+|[^A-Za-z0-9']+")
    private val arabiziMarker = Regex("[2356789]|3'|gh|kh|sh", RegexOption.IGNORE_CASE)

    private val exactNames: Map<String, String> = linkedMapOf(
        "mohamed" to "محمد", "mohammad" to "محمد", "mohammed" to "محمد", "muhammad" to "محمد", "mo7amed" to "محمد", "m7md" to "محمد",
        "ahmed" to "أحمد", "ahmad" to "أحمد", "a7med" to "أحمد", "mahmoud" to "محمود", "ma7moud" to "محمود",
        "mostafa" to "مصطفى", "moustafa" to "مصطفى", "mustafa" to "مصطفى",
        "ali" to "علي", "aly" to "علي", "3ali" to "علي", "3ly" to "علي",
        "alaa" to "علاء", "3laa" to "علاء", "3alaa" to "علاء", "ala2" to "علاء",
        "omar" to "عمر", "3omar" to "عمر", "amr" to "عمرو", "3amr" to "عمرو", "amro" to "عمرو",
        "hassan" to "حسن", "hasan" to "حسن", "7assan" to "حسن", "hussein" to "حسين", "hussien" to "حسين", "7ussein" to "حسين",
        "hossam" to "حسام", "7ossam" to "حسام", "ibrahim" to "إبراهيم", "ebrahim" to "إبراهيم",
        "youssef" to "يوسف", "yousef" to "يوسف", "yusuf" to "يوسف", "yassin" to "ياسين", "yassen" to "ياسين",
        "kareem" to "كريم", "karim" to "كريم", "ramy" to "رامي", "rami" to "رامي", "hany" to "هاني", "hani" to "هاني",
        "tarek" to "طارق", "tariq" to "طارق", "khaled" to "خالد", "khalid" to "خالد", "5aled" to "خالد",
        "waleed" to "وليد", "walid" to "وليد", "sayed" to "سيد", "said" to "سعيد", "saeed" to "سعيد",
        "salah" to "صلاح", "saleh" to "صالح", "sherif" to "شريف", "shereef" to "شريف", "ehab" to "إيهاب", "ihab" to "إيهاب",
        "islam" to "إسلام", "eslam" to "إسلام", "adel" to "عادل", "3adel" to "عادل", "ashraf" to "أشرف",
        "samy" to "سامي", "sami" to "سامي", "ziad" to "زياد", "zeyad" to "زياد", "seif" to "سيف", "saif" to "سيف",
        "moamen" to "مؤمن", "momen" to "مؤمن", "mo2men" to "مؤمن", "mo3men" to "مؤمن", "mo3taz" to "معتز", "motaz" to "معتز",
        "abdallah" to "عبد الله", "abdullah" to "عبد الله", "abdelrahman" to "عبد الرحمن", "abd" to "عبد", "abdel" to "عبد",
        "sara" to "سارة", "sarah" to "سارة", "mariam" to "مريم", "maryam" to "مريم", "aya" to "آية", "ayah" to "آية",
        "nour" to "نور", "noor" to "نور", "nada" to "ندى", "rana" to "رنا", "reem" to "ريم", "mai" to "مي", "may" to "مي",
        "menna" to "منة", "mona" to "منى", "dina" to "دينا", "salma" to "سلمى", "shimaa" to "شيماء", "shymaa" to "شيماء",
        "esraa" to "إسراء", "israa" to "إسراء", "doaa" to "دعاء", "do3aa" to "دعاء", "hager" to "هاجر", "hagar" to "هاجر",
        "hana" to "هنا", "hanaa" to "هناء", "jana" to "جنى", "janna" to "جنة", "farah" to "فرح", "basma" to "بسمة",
        "yasmin" to "ياسمين", "yasmine" to "ياسمين", "eman" to "إيمان", "iman" to "إيمان", "habiba" to "حبيبة", "7abiba" to "حبيبة",
        "hoda" to "هدى", "huda" to "هدى", "fatma" to "فاطمة", "fatima" to "فاطمة", "ganna" to "جنة", "malak" to "ملك",
        "marwa" to "مروة", "asmaa" to "أسماء", "asma" to "أسماء", "heba" to "هبة", "hiba" to "هبة", "rawan" to "روان", "rowan" to "روان",
        "mama" to "ماما", "mamy" to "مامي", "baba" to "بابا", "papa" to "بابا", "teta" to "تيتا", "gedo" to "جدو", "gido" to "جدو", "tante" to "طنط"
    )

    fun prepareForSpeech(rawName: String): SpokenCallerName? {
        val cleaned = rawName.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return null

        if (arabicLetters.containsMatchIn(cleaned)) {
            return SpokenCallerName(cleaned, arabicLocale)
        }

        exactNames[keyFor(cleaned)]?.let { return SpokenCallerName(it, arabicLocale) }

        var changed = false
        val converted = tokenRegex.findAll(cleaned).joinToString(separator = "") { match ->
            val token = match.value
            val newToken = convertToken(token)
            if (newToken != token) changed = true
            newToken
        }

        return if (changed) {
            SpokenCallerName(converted, arabicLocale)
        } else {
            SpokenCallerName(cleaned, Locale.getDefault())
        }
    }

    fun prepareDigits(rawValue: String): SpokenCallerName? {
        val parts = mutableListOf<String>()
        rawValue.trim().forEachIndexed { index, c ->
            when {
                c == '+' && index == 0 -> parts += "plus"
                c.isDigit() -> parts += c.toString()
            }
        }
        val spoken = parts.joinToString(" ").trim()
        return if (spoken.isBlank()) null else SpokenCallerName(spoken, Locale.getDefault())
    }

    private fun convertToken(token: String): String {
        if (!token.any { it.isLetterOrDigit() }) return token
        if (token.all { it.isDigit() }) return token
        exactNames[keyFor(token)]?.let { return it }
        if (!arabiziMarker.containsMatchIn(token)) return token
        return arabiziFallback(token)
    }

    private fun keyFor(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "")
    }

    private fun arabiziFallback(token: String): String {
        var s = token.lowercase(Locale.US)
        s = s.replace("3'", "غ")
            .replace("gh", "غ")
            .replace("kh", "خ")
            .replace("sh", "ش")
            .replace("ch", "ش")

        val out = StringBuilder()
        var index = 0
        while (index < s.length) {
            val c = s[index]
            val next = s.getOrNull(index + 1)
            when (c) {
                '2' -> out.append('ء')
                '3' -> out.append('ع')
                '5' -> out.append('خ')
                '6' -> out.append('ط')
                '7' -> out.append('ح')
                '8' -> out.append('غ')
                '9' -> out.append('ص')
                'a' -> {
                    out.append(if (out.isEmpty()) 'أ' else 'ا')
                    if (next == 'a') index++
                }
                'e', 'i', 'y' -> {
                    out.append('ي')
                    if (next == c) index++
                }
                'o', 'u', 'w' -> {
                    out.append('و')
                    if (next == c) index++
                }
                'b', 'p' -> out.append('ب')
                't' -> out.append('ت')
                'g', 'j' -> out.append('ج')
                'd' -> out.append('د')
                'r' -> out.append('ر')
                'z' -> out.append('ز')
                's' -> out.append('س')
                'f', 'v' -> out.append('ف')
                'q' -> out.append('ق')
                'k', 'c' -> out.append('ك')
                'l' -> out.append('ل')
                'm' -> out.append('م')
                'n' -> out.append('ن')
                'h' -> out.append('ه')
                else -> out.append(c)
            }
            index++
        }
        return out.toString().ifBlank { token }
    }
}
