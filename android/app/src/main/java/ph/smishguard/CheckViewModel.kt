package ph.smishguard

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ph.smishguard.model.Detection
import ph.smishguard.model.MAX_MESSAGE_LENGTH
import ph.smishguard.privacy.SafeDomain
import java.time.ZonedDateTime

enum class Screen { HOME, CHECK, RESULT, REPORT, SETTINGS }
data class CheckState(val screen: Screen = Screen.HOME, val text: String = "", val busy: Boolean = false,
    val error: String? = null, val result: Detection? = null, val domain: String? = null,
    val checkedAt: ZonedDateTime? = null, val modelStatus: String = "Loading model status…")

class CheckViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val app = application as SmishGuardApp
    private val mutable = MutableStateFlow(CheckState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    init { viewModelScope.launch { mutable.value = mutable.value.copy(modelStatus = app.detector().status) } }
    fun edit(text: String) {
        job?.cancel()
        mutable.value = mutable.value.copy(text = text.take(MAX_MESSAGE_LENGTH), busy = false,
            result = null, domain = null, checkedAt = null,
            error = if (text.length > MAX_MESSAGE_LENGTH) "Limit is 10,000 characters. Extra text was removed; review before checking." else null)
    }
    fun share(text: String) { endSession(Screen.CHECK); edit(text) }
    fun navigate(screen: Screen) {
        if (screen in setOf(Screen.HOME, Screen.CHECK, Screen.SETTINGS)) endSession(screen)
        else mutable.value = mutable.value.copy(screen = screen)
    }
    fun endSession(screen: Screen = Screen.HOME) {
        job?.cancel(); job = null
        mutable.value = CheckState(screen = screen, modelStatus = mutable.value.modelStatus)
    }
    fun scan() {
        if (mutable.value.text.isBlank()) { mutable.value = mutable.value.copy(error = "Enter a message to check."); return }
        if (mutable.value.busy) return
        job = viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, error = null)
            try {
                val resultAndDomain = withContext(Dispatchers.Default) {
                    withTimeout(7000) {
                        val text = mutable.value.text
                        app.detector().detect(text) to SafeDomain.fromMessage(text)
                    }
                }
                mutable.value = mutable.value.copy(screen = Screen.RESULT, text = "", busy = false,
                    result = resultAndDomain.first, domain = resultAndDomain.second, checkedAt = ZonedDateTime.now())
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                mutable.value = mutable.value.copy(screen = Screen.RESULT, text = "", busy = false, result = Detection.Failure)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(screen = Screen.RESULT, text = "", busy = false, result = Detection.Failure) }
        }
    }
}
