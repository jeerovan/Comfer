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

    private val ZhCN = listOf(
        listOf(
            // High-frequency radicals as direct-entry aids
            "一","丨","丶","丿","亅","冂","冖","口","囗","土","士","夂","夕",
            "大","女","子","宀","寸","小","山","川",
            "工","己","巾","干","幺","心","戈",
            "手","日","月","木","水","火","犬",
            "王","田","目","石","禾","竹","米",
            "糸","耳","衣","言","貝","走","足",
            "金","門","隹","雨","食","馬"
        ),
        listOf(
            // Pinyin tone marks
            "ā","á","ǎ","à",
            "ē","é","ě","è",
            "ī","í","ǐ","ì",
            "ō","ó","ǒ","ò",
            "ū","ú","ǔ","ù",
            "ǖ","ǘ","ǚ","ǜ",
            "ü"
        )
    )

    private val Japanese = listOf(
        listOf(
            "あ","い","う","え","お",
            "か","き","く","け","こ",
            "さ","し","す","せ","そ",
            "た","ち","つ","て","と",
            "な","に","ぬ","ね","の",
            "は","ひ","ふ","へ","ほ",
            "ま","み","む","め","も",
            "や","ゆ","よ"
        ),
        listOf(
            "ら","り","る","れ","ろ",
            "わ","を","ん",
            "ゃ","ゅ","ょ","っ",
            "゛", // dakuten
            "゜"  // handakuten
        )
    )

    private val Korean = listOf(
        listOf(
            // Initial consonants
            "ㄱ","ㄲ","ㄴ","ㄷ","ㄸ","ㄹ","ㅁ","ㅂ","ㅃ","ㅅ","ㅆ","ㅇ","ㅈ","ㅉ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ",
            // Medial vowels
            "ㅏ","ㅐ","ㅑ","ㅒ","ㅓ","ㅔ","ㅕ","ㅖ","ㅗ","ㅘ","ㅙ","ㅚ","ㅜ","ㅝ","ㅞ","ㅟ","ㅡ","ㅢ","ㅣ"
        ),
        listOf(
            // Final consonant forms (if you want separate handling)
            "ㄳ","ㄵ","ㄶ","ㄺ","ㄻ","ㄼ","ㄽ","ㄾ","ㄿ","ㅀ","ㅄ"
        )
    )

    private val Turkish = listOf(
        listOf(
            "a","b","c","ç","d","e","f","g","ğ","h","ı","i","j","k","l","m",
            "n","o","ö","p","r","s","ş","t","u","ü","v","y","z"
        )
    )

    private val Persian = listOf(
        listOf(
            "ا","ب","پ","ت","ث","ج","چ","ح","خ","د","ذ",
            "ر","ز","ژ","س","ش","ص","ض","ط","ظ",
            "ع","غ","ف","ق","ک","گ","ل","م","ن","ه","و","ی",
            "َ","ُ","ِ","ّ","ْ"
        )
    )

    private val Vietnamese = listOf(
        listOf(
            "a","ă","â","b","c","d","đ","e","ê","g","h",
            "i","k","l","m","n","o","ô","ơ","p","q","r",
            "s","t","u","ư","v","x","y"
        ),
        listOf(
            "́","̀","̉","̃","̣", // tone marks
            "̆","̂","̛"         // vowel modifiers
        )
    )

    private val Thai = listOf(
        listOf(
            "ก","ข","ค","ฆ","ง",
            "จ","ฉ","ช","ซ","ญ",
            "ฎ","ฏ","ฐ","ฑ","ฒ","ณ",
            "ด","ต","ถ","ท","ธ","น",
            "บ","ป","ผ","พ","ฟ","ภ","ม"
        ),
        listOf(
            "ย","ร","ล","ว",
            "ศ","ษ","ส","ห","ฬ","อ","ฮ",
            "ะ","า","ิ","ี","ึ","ื","ุ","ู",
            "เ","แ","โ","ใ","ไ",
            "ั","็","่","้","๊","๋","์","ฺ"
        )
    )

    private val Romanian = listOf(
        listOf(
            "a","ă","â","b","c","d","e","f","g","h",
            "i","î","j","k","l","m","n","o","p","q",
            "r","s","ș","t","ț","u","v","w","x","y","z"
        )
    )

    private val Myanmar = listOf(
        listOf(
            "က","ခ","ဂ","ဃ","င",
            "စ","ဆ","ဇ","ဈ","ည",
            "ဋ","ဌ","ဍ","ဎ","ဏ",
            "တ","ထ","ဒ","ဓ","န",
            "ပ","ဖ","ဗ","ဘ","မ",
            "ယ","ရ","လ","ဝ","သ",
            "ဟ","ဠ","အ"
        ),
        listOf(
            "ါ","ာ","ိ","ီ","ု","ူ","ေ","ဲ","့","း","်","္"
        )
    )

    private val Azerbaijani = listOf(
        listOf(
            "a","ə","b","c","ç","d","e","f","g","ğ","h",
            "ı","i","j","k","l","m","n","o","ö","p",
            "q","r","s","ş","t","u","ü","v","y","z"
        )
    )

    private val Khmer = listOf(
        listOf(
            "ក","ខ","គ","ឃ","ង",
            "ច","ឆ","ជ","ឈ","ញ",
            "ដ","ឋ","ឌ","ឍ","ណ",
            "ត","ថ","ទ","ធ","ន",
            "ប","ផ","ព","ភ","ម",
            "យ","រ","ល","វ","ស",
            "ហ","ឡ","អ"
        ),
        listOf(
            "ា","ិ","ី","ឹ","ឺ","ុ","ូ","ើ","ឿ","ៀ","េ","ែ","ៃ","ោ","ៅ",
            "ៈ","់","ំ","ះ","៉","៊","់","៍","⁎"
        )
    )

    private val Lao = listOf(
        listOf(
            "ກ","ຂ","ຄ","ງ","ຈ","ສ","ຊ","ຍ","ດ","ຕ",
            "ນ","ບ","ປ","ຜ","ຝ","ພ","ຟ","ມ","ຢ","ຣ",
            "ລ","ວ","ຫ","ອ","ຮ"
        ),
        listOf(
            "ະ","າ","ິ","ີ","ຶ","ື","ຸ","ູ","ເ","ແ","ໂ","ໃ","ໄ",
            "່","້","໊","໋","໌","ໍ"
        )
    )

    private val Mongolian = listOf(
        listOf(
            "а","б","в","г","д","е","ё","ж","з","и","й",
            "к","л","м","н","о","ө","п","р","с","т",
            "у","ү","ф","х","ц","ч","ш","щ","ъ","ы","ь","э","ю","я"
        )
    )

    private val Tajik = listOf(
        listOf(
            "а","б","в","г","ғ","д","е","ё","ж","з","и","ӣ",
            "й","к","қ","л","м","н","о","п","р","с",
            "т","у","ӯ","ф","х","ҳ","ч","ҷ","ш","ъ","э","ю","я"
        )
    )

    private val Turkmen = listOf(
        listOf(
            "a","ä","b","ç","d","e","f","g","h","i","j","k","l","m",
            "n","ň","o","ö","p","r","s","ş","t","u","ü","w","y","z"
        )
    )

    private val Uzbek = listOf(
        listOf(
            "a","b","d","e","f","g","ğ","h","i","j","k","l","m",
            "n","o","ö","p","q","r","s","ş","t","u","ü","v","x","y","z"
        )
    )


    fun getSupportedLocales(): List<Locale> {
        return listOf(
            "ar",
            "az",
            "de",
            "el",
            "en",
            "es",
            "fa",
            "fr",
            "he",
            "hi",
            "it",
            "ja",
            "km",
            "ko",
            "lo",
            "mn",
            "my",
            "pt",
            "ro",
            "ru",
            "tg",
            "th",
            "tk",
            "tr",
            "uk",
            "uz",
            "vi",
            "zh-CN"
        ).map{Locale.forLanguageTag(it)}
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
            "zh-CN" -> ZhCN
            "ja" -> Japanese
            "ko" -> Korean
            "tr" -> Turkish
            "fa" -> Persian
            "vi" -> Vietnamese
            "th" -> Thai
            "ro" -> Romanian
            "my" -> Myanmar
            "az" -> Azerbaijani
            "km" -> Khmer
            "lo" -> Lao
            "mn" -> Mongolian
            "tg" -> Tajik
            "tk" -> Turkmen
            "uz" -> Uzbek
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
