package com.sequmio.givlse.runner

/** A missing Intent token is not proof that Android denied the launch. */
internal class ResultReturnObservation {
    private var token = 0L
    private var pending = false
    private var automaticConfirmed = false
    private var manualConfirmed = false
    private var visibleBeforeDeadline = false

    fun reset() {
        token = 0L
        pending = false
        automaticConfirmed = false
        manualConfirmed = false
        visibleBeforeDeadline = false
    }

    fun begin(newToken: Long) {
        reset()
        token = newToken
        pending = true
    }

    /** null preserves the current recommendation; false clears it. */
    fun visible(automaticToken: Long, manualToken: Long): Boolean? {
        if (token == 0L) return null
        if (automaticToken == token) {
            automaticConfirmed = true
            return false
        }
        if (automaticConfirmed) return null
        if (manualToken == token) {
            manualConfirmed = true
            return true
        }
        // Unknown origin: never infer a denial from an absent token.
        if (pending) visibleBeforeDeadline = true
        return null
    }

    /** Active application window evidence; never override a known manual notification return. */
    fun foregroundObserved(): Boolean? {
        if (!pending || token == 0L || manualConfirmed) return null
        visibleBeforeDeadline = true
        return false
    }

    fun deadline(screenAvailable: Boolean, ownForeground: Boolean?): Boolean? {
        if (!pending) return null
        pending = false
        if (automaticConfirmed) return false
        if (manualConfirmed) return true
        // Prefer not showing an incorrect warning over classifying an
        // ambiguous manual launcher return as a failure.
        if (visibleBeforeDeadline || (screenAvailable && ownForeground == true)) {
            automaticConfirmed = true
            return false
        }
        if (!screenAvailable || ownForeground == null) return null
        return true
    }
}
