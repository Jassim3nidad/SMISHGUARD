package ph.smishguard

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ph.smishguard.data.PreferencesRepository
import ph.smishguard.model.Detector
import ph.smishguard.model.LinearDetector
import ph.smishguard.model.MissingDetector
import ph.smishguard.privacy.DisabledReportingRepository
import ph.smishguard.sms.WarningNotifications
import java.io.FileNotFoundException

class SmishGuardApp : Application() {
    val preferences by lazy { PreferencesRepository(this) }
    val reporting = DisabledReportingRepository()
    // Only immutable model parameters are cached, never input messages or predictions.
    private val detector: Detector by lazy {
        try {
            val json = assets.open("model.json").use {
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = it.read(chunk)
                    if (count < 0) break
                    require(buffer.size() + count <= 8_000_000)
                    buffer.write(chunk, 0, count)
                }
                buffer.toString("UTF-8")
            }
            LinearDetector.parse(json)
        } catch (_: FileNotFoundException) { MissingDetector() }
        catch (_: Exception) { MissingDetector(invalid = true) }
    }
    suspend fun detector(): Detector = withContext(Dispatchers.IO) { detector }
    override fun onCreate() { super.onCreate(); WarningNotifications.createChannel(this) }
}
