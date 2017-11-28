package net.swigg.tradr.feed

import java.time.Instant

data class Heartbeat(
  val lastTradeId: Long,
  val productId: String,
  val sequence: Long,
  val time: Instant
)