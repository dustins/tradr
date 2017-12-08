package net.swigg.tradr.feed

import java.time.Instant

data class Tick(
    val time: Instant,
    var open: Double? = null,
    var low: Double? = null,
    var high: Double? = null,
    var close: Double? = null,
    var volume: Double = 0.0,
    var trades: Int = 0,
    var amount: Double = 0.0
)