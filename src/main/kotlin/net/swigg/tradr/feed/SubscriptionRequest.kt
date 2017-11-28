package net.swigg.tradr.feed

import com.fasterxml.jackson.annotation.JsonProperty
import net.swigg.tradr.feed.SubscriptionRequest.RequestType.SUBSCRIBE

class SubscriptionRequest(
  var productIds: List<String>? = null,
  var channels: List<Channel> = listOf(HeartbeatChannel(listOf("BTC-USD"))),
  val type: RequestType = SUBSCRIBE
) {
    enum class RequestType {
        @JsonProperty("subscribe")
        SUBSCRIBE,

        @JsonProperty("unsubscribe")
        UNSUBSCRIBE
    }
}