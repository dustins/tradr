package net.swigg.tradr.feed

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.*

data class Match(
  val tradeId: Long,
  val sequence: Long,
  val makerOrderId: UUID,
  val takerOrderId: UUID,
  val time: Instant,
  val productId: String,
  val size: Double,
  val price: Double,
  val side: Side
) {
    fun getTimestamp(): Long = time.let { time.toEpochMilli() }?: 0

    enum class Side {
        @JsonProperty("buy")
        BUY,

        @JsonProperty("sell")
        SELL
    }
}