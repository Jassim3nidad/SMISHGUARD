package ph.smishguard

import ph.smishguard.model.Detection

/** Explicit debug-only UI fixtures. Never wired into production/background detection. */
object SyntheticFixtures {
    val missing = Detection.Unavailable
    val unsupported = Detection.InvalidModel
    val failure = Detection.Failure
    val suspicious = Detection.Success(true, .8, probability = false, synthetic = true)
    val unflagged = Detection.Success(false, .2, probability = false, synthetic = true)
}
