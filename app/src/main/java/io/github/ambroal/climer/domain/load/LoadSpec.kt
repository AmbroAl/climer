package io.github.ambroal.climer.domain.load

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LoadSpec {
    @Serializable
    @SerialName("none")
    data object None : LoadSpec

    @Serializable
    @SerialName("fixed")
    data class Fixed(val kg: Double) : LoadSpec

    @Serializable
    @SerialName("fixedPerSide")
    data class FixedPerSide(val rightKg: Double?, val leftKg: Double?) : LoadSpec

    @Serializable
    @SerialName("percent")
    data class PercentOfMax(val measureTypeId: Long, val percent: Double) : LoadSpec
}

data class MaxValue(val single: Double?, val right: Double?, val left: Double?)
