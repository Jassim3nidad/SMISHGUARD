package ph.smishguard

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ph.smishguard.model.*
import ph.smishguard.privacy.*
import ph.smishguard.sms.PermissionState
import ph.smishguard.sms.SmsParts
import java.time.ZonedDateTime

class CoreTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()
    @Test fun pythonKotlinParity() = runBlocking {
        val model = LinearDetector.parse(resource("synthetic-model.json"), allowSynthetic = true)
        val cases = JSONArray(resource("parity-cases.json"))
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            assertEquals(case.getString("normalized"), Preprocessing.normalize(case.getString("text")))
            val result = model.detect(case.getString("text")) as Detection.Success
            assertEquals(case.getDouble("score"), result.score, 1e-10)
            assertEquals(case.getBoolean("suspicious"), result.suspicious)
            assertTrue(result.synthetic)
            assertFalse(result.probability)
        }
    }
    @Test fun modelsFailClosed() = runBlocking {
        assertEquals(Detection.Unavailable, MissingDetector().detect("example"))
        assertEquals(Detection.InvalidModel, MissingDetector(true).detect("example"))
        assertThrows(IllegalArgumentException::class.java) { LinearDetector.parse(resource("synthetic-model.json")) }
        val bad = JSONObject(resource("synthetic-model.json")).put("preprocessing_version", "unknown").toString()
        assertThrows(IllegalArgumentException::class.java) { LinearDetector.parse(bad, true) }
        assertEquals(Detection.Failure, LinearDetector.parse(resource("synthetic-model.json"), true).detect(" "))
    }
    @Test fun domainSanitization() {
        assertEquals("example.com", SafeDomain.fromUrl("https://customer-alice.example.com/private?otp=123#secret"))
        assertEquals("example.com.ph", SafeDomain.fromUrl("https://name.example.com.ph/a"))
        assertEquals("github.io", SafeDomain.fromUrl("https://private-user.github.io/secret"))
        assertEquals("example.com", SafeDomain.fromUrl("https://EXAMPLE.COM.:443/a"))
        for (url in listOf("https://alice:pw@example.com", "https://127.0.0.1/a", "https://[::1]/", "https://bad%zz.com/a",
            "https://bank.com\\@evil.com", "https://user123.com", "https://example.unknown", "javascript:alert(1)", "https://a b.com", "https://xn--e1afmkfd.com")) {
            assertNull(url, SafeDomain.fromUrl(url))
        }
        assertNull(SafeDomain.fromMessage("Walang link dito."))
        assertEquals("example.com", SafeDomain.fromMessage("Visit https://abc.example.com/path)."))
    }
    @Test fun payloadAllowlist() {
        val result = Detection.Success(true, .73)
        val payload = ReportPayload.create("private.example.com", result, ZonedDateTime.parse("2026-09-17T21:59:59+08:00"), City.DASMARINAS, Brand.UNSPECIFIED)!!
        val json = JSONObject(payload.json())
        assertEquals(setOf("domain","score","predicted_class","date_hour","city"), json.keys().asSequence().toSet())
        assertEquals("example.com", json.getString("domain"))
        assertEquals("2026-09-17T21+08:00", json.getString("date_hour"))
        assertNull(ReportPayload.create(null,result.copy(synthetic=true),ZonedDateTime.now(),City.DASMARINAS,Brand.GCASH))
        assertNull(ReportPayload.create(null,result,ZonedDateTime.now(),City.NOT_SELECTED,Brand.GCASH))
    }
    @Test fun multipartAndPermissionStates() {
        assertEquals("Bayad na po", SmsParts.combine(listOf("Bayad ","na ","po")))
        assertNull(SmsParts.combine(emptyList()))
        assertNull(SmsParts.combine(listOf("one",null)))
        assertNull(SmsParts.combine(listOf("x".repeat(10001))))
        for (consent in listOf(false,true)) for (telephony in listOf(false,true)) for (sms in listOf(false,true)) {
            assertEquals(consent && telephony && sms, PermissionState(telephony,sms,false).active(consent))
        }
    }
    @Test fun reportingUnavailable() = runBlocking {
        val repository = DisabledReportingRepository()
        assertFalse(repository.available)
        val payload = ReportPayload.create(null,Detection.Success(true,.7),ZonedDateTime.now(),City.DASMARINAS,Brand.UNSPECIFIED)!!
        assertEquals(Submission.Unavailable, repository.submit(payload))
    }
}
