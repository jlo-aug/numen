package it.palsoftware.pastiera.inputmethod.trackpad

data class TrackpadAxisRange(
    val min: Float,
    val max: Float
) {
    val span: Float
        get() = max - min

    val isValid: Boolean
        get() = min.isFinite() && max.isFinite() && span > 0f
}

object TrackpadCoordinateMapper {
    fun normalized(value: Float, range: TrackpadAxisRange): Float {
        if (!range.isValid || !value.isFinite()) return 0f
        return ((value - range.min) / range.span).coerceIn(0f, 1f)
    }

    fun third(value: Float, range: TrackpadAxisRange): Int {
        val normalized = normalized(value, range)
        return when {
            normalized < 1f / 3f -> 0
            normalized < 2f / 3f -> 1
            else -> 2
        }
    }
}
