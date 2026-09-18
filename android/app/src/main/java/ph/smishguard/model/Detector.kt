package ph.smishguard.model

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sqrt

const val MAX_MESSAGE_LENGTH = 10000

/** v1 is deliberately ASCII-folded for byte-for-byte Python/JVM portability. */
object Preprocessing {
    const val VERSION = "sg-ascii-v1"
    private val url = Regex("(?:https?://|www\\.)[^\\s<>]+")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    fun normalize(text: String): String {
        var s = text.map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")
        s = url.replace(s, " zzurlzz ")
        s = email.replace(s, " zzemailzz ")
        s = Regex("[0-9]+").replace(s, " zznumberzz ")
        return Regex("[^a-z]+").replace(s, " ").trim()
    }
    fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }
}

sealed interface Detection {
    data object Unavailable : Detection
    data object InvalidModel : Detection
    data object Failure : Detection
    data class Success(val suspicious: Boolean, val score: Double, val probability: Boolean = false,
                       val synthetic: Boolean = false) : Detection
}

interface Detector {
    val status: String
    suspend fun detect(text: String): Detection
}

class MissingDetector(private val invalid: Boolean = false) : Detector {
    override val status = if (invalid) "Unsupported or invalid model" else "Model not installed"
    override suspend fun detect(text: String): Detection =
        if (invalid) Detection.InvalidModel else Detection.Unavailable
}

/** Portable unigram raw-count, IDF, L2 normalization and binary linear decision. */
class LinearDetector private constructor(
    private val vocabulary: Map<String, Int>, private val idf: DoubleArray,
    private val weights: DoubleArray, private val bias: Double, private val threshold: Double,
    private val calibration: Pair<Double, Double>?, private val evaluated: Boolean,
    private val synthetic: Boolean
) : Detector {
    override val status = if (synthetic) "Synthetic test fixture" else "Model ready · offline"
    override suspend fun detect(text: String): Detection {
        if (text.isBlank() || text.length > MAX_MESSAGE_LENGTH) return Detection.Failure
        val counts = HashMap<Int, Double>()
        for (word in Preprocessing.tokens(text)) {
            currentCoroutineContext().ensureActive()
            vocabulary[word]?.let { counts[it] = (counts[it] ?: 0.0) + 1.0 }
        }
        var norm = 0.0
        counts.replaceAll { index, count -> (count * idf[index]).also { norm += it * it } }
        norm = sqrt(norm)
        var margin = bias
        if (norm > 0) counts.forEach { (i, v) -> margin += weights[i] * v / norm }
        val score = calibration?.let { (a, b) -> 1.0 / (1.0 + exp(-(a * margin + b))) } ?: margin
        if (!score.isFinite()) return Detection.Failure
        return Detection.Success(score >= threshold, score, calibration != null && evaluated, synthetic)
    }
    companion object {
        fun parse(json: String, allowSynthetic: Boolean = false): LinearDetector {
            val o = JSONObject(json)
            require(o.getInt("schema_version") == 1)
            require(o.getString("preprocessing_version") == Preprocessing.VERSION)
            require(o.getString("feature_type") == "word-unigram-tfidf-l2")
            require(o.getJSONArray("classes").toString() == "[\"not_suspicious\",\"suspicious\"]")
            val synthetic = o.getBoolean("synthetic")
            require(!synthetic || allowSynthetic)
            val words = o.getJSONArray("vocabulary")
            require(words.length() in 1..50000)
            val vocabulary = (0 until words.length()).associate { words.getString(it) to it }
            require(vocabulary.size == words.length() && vocabulary.keys.all { Regex("[a-z]+").matches(it) })
            fun array(name: String): DoubleArray {
                val a = o.getJSONArray(name)
                require(a.length() == words.length())
                return DoubleArray(a.length()) { a.getDouble(it).also { n -> require(n.isFinite()) } }
            }
            val idf = array("idf").also { require(it.all { n -> n > 0.0 }) }
            val weights = array("weights")
            val bias = o.getDouble("bias").also { require(it.isFinite()) }
            val threshold = o.getDouble("threshold").also { require(it.isFinite()) }
            val cal = o.optJSONObject("calibration")?.let {
                require(it.getString("method") == "sigmoid")
                Pair(it.getDouble("a"), it.getDouble("b")).also { p ->
                    require(p.first.isFinite() && p.second.isFinite())
                    require(threshold in 0.0..1.0)
                }
            }
            return LinearDetector(vocabulary, idf, weights, bias, threshold, cal,
                o.getBoolean("calibration_evaluated"), synthetic)
        }
    }
}
