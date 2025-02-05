package com.moshy.drugcalc.calc
internal fun DecodedCycleBuilder(
    active: String,
    dose: @Positive Double = 0.0,
    halfLife: @Positive @TimeTickScaled Double = 0.0,
    start: @Positive @TimeTickScaled Int,
    duration: @Positive @TimeTickScaled Int,
    freqs: List<@Positive @TimeTickScaled Int>, // each element is number of tickDuration's to increment by
    transformer: String? = null): DecodedCycle =
    DecodedCycle.build(DecodedCycle.withRawValues(active, dose, halfLife, start, duration, freqs, transformer))

