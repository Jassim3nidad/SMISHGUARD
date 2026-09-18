package ph.smishguard.privacy

import org.json.JSONObject
import ph.smishguard.model.Detection
import java.net.IDN
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Conservative registry suffix policy; unknown suffixes are omitted, never guessed. */
object SafeDomain {
    private val suffixes = setOf("com", "org", "net", "edu", "gov", "io", "ph", "com.ph", "org.ph", "net.ph", "gov.ph", "edu.ph", "co.uk")
    fun fromUrl(candidate: String): String? = runCatching {
        if (candidate.any { it.isWhitespace() || it.code < 32 } || '\\' in candidate) return null
        val u = URI(if (candidate.startsWith("www.", true)) "https://$candidate" else candidate)
        if (u.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || u.rawUserInfo != null) return null
        val host = u.host?.trimEnd('.') ?: return null
        if (host.contains(':') || host.all { it.isDigit() || it == '.' }) return null
        val ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        val suffix = suffixes.filter { ascii.endsWith(".$it") }.maxByOrNull { it.length } ?: return null
        val root = ascii.removeSuffix(".$suffix").substringAfterLast('.')
        // No tenant/user subdomains, numeric account-like roots, or internationalized labels.
        if (!Regex("[a-z][a-z-]{0,38}[a-z]|[a-z]").matches(root) || root.startsWith("xn--")) return null
        "$root.$suffix"
    }.getOrNull()
    fun fromMessage(text: String): String? = Regex("(?:https?://|www\\.)[^\\s<>]+", RegexOption.IGNORE_CASE)
        .findAll(text).mapNotNull { fromUrl(it.value.trimEnd('.', ',', ';', '!', ')', ']', '}')) }.firstOrNull()
}

enum class City(val label: String) { NOT_SELECTED("Not selected"), DASMARINAS("Dasmariñas"), BACOOR("Bacoor"), IMUS("Imus"), MANILA("Manila"), OTHER("Other city") }
enum class Brand(val label: String) { UNSPECIFIED("Not specified"), BDO("BDO"), BPI("BPI"), GCASH("GCash"), MAYA("Maya"), SSS("SSS"), OTHER("Other financial institution") }

class ReportPayload private constructor(
    val domain: String?, val score: Double, val predictedClass: String,
    val dateHour: String, val city: City, val brand: Brand
) {
    fun json(): String = JSONObject().apply {
        put("domain", domain ?: JSONObject.NULL)
        put("score", score)
        put("predicted_class", predictedClass)
        put("date_hour", dateHour)
        put("city", city.label)
        if (brand != Brand.UNSPECIFIED) put("brand", brand.label)
    }.toString(2)
    companion object {
        fun create(domain: String?, result: Detection.Success, time: ZonedDateTime, city: City, brand: Brand): ReportPayload? {
            if (result.synthetic || !result.score.isFinite() || city == City.NOT_SELECTED) return null
            val safe = domain?.let { SafeDomain.fromUrl("https://$it") }
            return ReportPayload(safe, result.score, if (result.suspicious) "suspicious" else "not_suspicious",
                time.withMinute(0).withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHXXX")), city, brand)
        }
    }
}

sealed interface Submission { data object Unavailable : Submission; data object Failed : Submission; data object Sent : Submission }
interface ReportingRepository { val available: Boolean; suspend fun submit(payload: ReportPayload): Submission }
class DisabledReportingRepository : ReportingRepository {
    override val available = false
    override suspend fun submit(payload: ReportPayload) = Submission.Unavailable
}
