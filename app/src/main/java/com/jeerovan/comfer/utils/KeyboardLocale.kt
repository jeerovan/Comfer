package com.jeerovan.comfer.utils

import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.floor

object KeyboardLocale {
    private fun charRange(start: Char, end: Char) = (start..end).map { it.toString() }
    private val English = listOf(
        listOf(
        "a","b","c","d","e","f","g","h","i","j","k","l","m",
        "n","o","p","q","r","s","t","u","v","w","x","y","z"
        )
    )

    private val Spanish = listOf(
        listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "ñ", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "á", "é", "í", "ó", "ú", "ü"
        )
    )

    private val French = listOf(
        listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w"
        ),
        listOf("x", "y", "z",
        "à", "â", "ç", "é", "è", "ê", "ë", "î", "ï", "ô", "ù", "û", "ü", "ÿ", "œ", "æ"
        )
    )

    private val German = listOf(
        listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "ä", "ö", "ü", "ß"
        )
    )

    private val Portuguese = listOf(
        listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "à", "á", "â", "ã", "ç", "é", "ê", "í", "ó", "ô", "õ", "ú", "ü"
        )
    )

    private val Italian = listOf(
        listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "à", "è", "é", "ì", "ò", "ó", "ù"
        )
    )

    // --- Cyrillic Scripts ---
    private val Russian = listOf(
        listOf(
        "а","б","в","г","д","е","ё","ж","з","и","й",
        "к","л","м","н","о","п","р","с","т","у","ф",
        "х","ц","ч","ш","щ","ъ","ы","ь","э","ю","я"
        )
    )

    private val Ukrainian = listOf(
        listOf(
        "а", "б", "в", "г", "ґ", "д", "е", "є", "ж", "з", "и", "і", "ї", "й",
        "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц",
        "ч", "ш", "щ", "ь", "ю", "я"
        )
    )

    // --- Other Scripts ---
    private val Greek = listOf(
        listOf(
        "α","β","γ","δ","ε","ζ","η","θ","ι","κ","λ","μ",
        "ν","ξ","ο","π","ρ","ς","σ","τ","υ","φ","χ","ψ","ω"
        )
    )

    private val Arabic = listOf(
        listOf(
        // base letters
        "ا","ب","ت","ث","ج","ح","خ","د","ذ",
        "ر","ز","س","ش","ص","ض","ط","ظ",
        "ع","غ","ف","ق","ك","ل","م","ن","ه","و","ي",
        ),
        listOf(
        // hamza-related forms and ta marbuta
        "ء","آ","أ","ؤ","إ","ئ","ة",
        // dependent vowels / diacritics (ḥarakāt)
        "َ", // fatha
        "ً", // tanween fatha
        "ُ", // damma
        "ٌ", // tanween damma
        "ِ", // kasra
        "ٍ", // tanween kasra
        "ْ", // sukun
        "ّ", // shadda
        "ٰ"  // superscript alif / maddah-like mark
        )
    )


    private val Hebrew = listOf(
        listOf(
        // letters (incl. final forms)
        "א","ב","ג","ד","ה","ו","ז","ח","ט","י",
        "ך","כ","ל","ם","מ","ן","נ","ס","ע","ף",
        "פ","ץ","צ","ק","ר","ש","ת",
        ),
        listOf(
        // niqqud (vowel points & related marks)
        "ַ", // pataḥ
        "ָ", // qamats
        "ֶ", // segol
        "ֵ", // tsere
        "ִ", // ḥiriq
        "ֹ", // ḥolam
        "ֻ", // qubuts
        "ֲ", // ḥataf pataḥ
        "ֱ", // ḥataf segol
        "ֳ", // ḥataf qamats
        "ְ", // sheva
        "ּ", // dagesh / mappiq
        "ֽ", // meteg / siluq (optional, you can drop if overkill)
        "ׁ", // shin dot
        "ׂ"  // sin dot
        )
    )

    // --- Indic Scripts (Vowels & Consonants merged) ---
    private val Hindi = listOf(
        listOf(
        // Consonants
        "क", "ख", "ग", "घ", "ङ",
        "च", "छ", "ज", "झ", "ञ",
        "ट", "ठ", "ड", "ढ", "ण",
        "त", "थ", "द", "ध", "न",
        "प", "फ", "ब", "भ", "म",
        "य", "र", "ल", "व",
        "श", "ष", "स", "ह",
        "क्ष", "त्र", "ज्ञ",
        ),
        listOf(
        // Vowels
        "अ", "आ", "इ", "ई", "उ", "ऊ", "ऋ", "ए", "ऐ", "ओ", "औ", "अं", "अः",
        // Dependent vowels (matras) & signs
        "ा","ि","ी","ु","ू","ृ","े","ै","ो","ौ",
        "ं","ः","ँ", // anusvara, visarga, chandrabindu
        "ॅ","ॉ",    // short e/o (less common but used)
        "्",        // virama / halant
        )
    )

    fun getSupportedLocales(): List<Locale> {
        return listOf("en","es","fr","de","pt","it","ru","uk","el","ar","he","hi").map{Locale.forLanguageTag(it)}
    }
    fun getCharsForLocale(locale: Locale): List<List<String>> {
        return when (locale.language) {
            "en" -> English
            "es" -> Spanish
            "fr" -> French
            "de" -> German
            "pt" -> Portuguese
            "it" -> Italian
            "ru" -> Russian
            "uk" -> Ukrainian
            "el" -> Greek
            "ar" -> Arabic
            "he", "iw" -> Hebrew // "iw" is legacy code for Hebrew
            "hi" -> Hindi
            else -> English
        }
    }
}

object KeyboardLayoutEngine {

    // Configuration for "Optimal Usability"
    val CenterButtonSize = 45.dp
    val KeyButtonSize = 35.dp
    val Spacing = 2.dp

    /**
     * Distributes a flat list of characters into concentric layers
     * based on how many fit in each ring geometrically.
     */
    fun distributeCharsToLayers(allChars: List<String>): List<List<String>> {
        val layers = mutableListOf<List<String>>()
        val remainingChars = allChars.toMutableList()

        var currentLayerIndex = 0

        while (remainingChars.isNotEmpty()) {
            // 1. Calculate the radius of the current ring (center-to-center)
            // Layer 0 distance = CenterRadius/2 + Gap + ButtonRadius/2
            // Layer N distance = PrevLayerRadius + ButtonSize + Gap
            val radiusDp = if (currentLayerIndex == 0) {
                (CenterButtonSize / 2) + Spacing + (KeyButtonSize / 2)
            } else {
                val prevRadius = (CenterButtonSize / 2) + Spacing + (KeyButtonSize / 2) +
                        ((KeyButtonSize + Spacing) * currentLayerIndex)
                prevRadius
            }

            // 2. Calculate Circumference: C = 2 * PI * r
            // We use a raw float approximation for the math
            val radiusVal = radiusDp.value
            val buttonDiameterVal = KeyButtonSize.value + Spacing.value

            // 3. Calculate Capacity
            // Maximum number of buttons that fit on this circle without overlapping
            // Formula: N = PI / asin(r_button / R_layer)
            // (Using chord length logic ensures they don"t touch)
            val oneButtonAngle = 2 * asin((buttonDiameterVal / 2) / radiusVal)
            val maxButtonsInLayer = floor((2 * PI) / oneButtonAngle).toInt()

            // 4. Slice the list
            val takeCount = minOf(remainingChars.size, maxButtonsInLayer)
            val layerChars = remainingChars.take(takeCount)

            layers.add(layerChars)
            remainingChars.subList(0, takeCount).clear()

            currentLayerIndex++
        }

        return layers
    }
}
