package ph.smishguard

import android.content.Intent
import android.provider.Telephony
import org.junit.Assert.assertEquals
import org.junit.Test
import ph.smishguard.sms.SmsParts
import java.io.ByteArrayOutputStream

class SmsDecodingTest {
    @Test fun platformDecodesMultipartUcs2InBroadcastOrder() {
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("format", "3gpp")
            putExtra("pdus", arrayOf(pdu("Kumusta ", 1), pdu("po ñ", 2)))
        }
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        assertEquals(2, messages.size)
        assertEquals("Kumusta po ñ", SmsParts.combine(messages.map { it.messageBody }))
    }
    /** Synthetic SMS-DELIVER with concatenation UDH and UCS-2 text, no real sender. */
    private fun pdu(body: String, sequence: Int): ByteArray {
        val encoded = body.toByteArray(Charsets.UTF_16BE)
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0x44, 4, 0x91.toByte(), 0, 0, 0, 8))
            write(byteArrayOf(0x62, 0x90.toByte(), 0x81.toByte(), 0, 0, 0, 0))
            write(encoded.size + 6)
            write(byteArrayOf(5, 0, 3, 42, 2, sequence.toByte()))
            write(encoded)
        }.toByteArray()
    }
}
