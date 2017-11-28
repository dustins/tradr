package net.swigg.tradr.feed

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Mapping

/*
{
    "type":"ticker",
    "sequence":4415702206,
    "product_id":"BTC-USD",
    "price":"8222.49000000",
    "open_24h":"8080.01000000",
    "volume_24h":"14479.48570789",
    "low_24h":"8222.49000000",
    "high_24h":"8324.00000000",
    "volume_30d":"636853.61832642",
    "best_bid":"8222.48",
    "best_ask":"8222.49"
 }
 */
@Document(indexName = "tradr", type = "tick")
@Mapping(mappingPath = "elasticsearch/mapping/tick.json")
data class Tick(
  @Id
  val sequence: Long,

  val productId: String,

  val price: Double,

  val open24h: Double,

  val volume24h: Double,

  val low24h: Double,

  val high24h: Double,

  val volume30d: Double,

  val bestBid: Double,

  val bestAsk: Double
)