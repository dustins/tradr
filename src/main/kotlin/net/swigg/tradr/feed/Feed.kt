package net.swigg.tradr.feed

import com.espertech.esper.client.EPServiceProvider
import com.espertech.esper.client.time.CurrentTimeEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.google.gson.JsonObject
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.ta4j.core.BaseTick
import org.ta4j.core.Decimal
import org.ta4j.core.TimeSeries
import org.ta4j.core.indicators.EMAIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.annotation.PostConstruct
import javax.websocket.*

private val logger = KotlinLogging.logger {}

@ClientEndpoint
@Component
class Feed @Autowired constructor(val timeSeries: TimeSeries, val tickRepository: TickRepository, val objectMapper: ObjectMapper, val engine: EPServiceProvider) {
    var session: Session? = null

    val gson = Gson()

    val snapshots: MutableMap<String, Snapshot> = mutableMapOf()

    @PostConstruct
    fun postConstruct() {
        engine.epAdministrator.createEPL("select current_timestamp().roundFloor('min') as endtime, first(price) as open, max(price) as high, min(price) as low, last(price) as close, sum(m.size) as volume FROM Match#time_batch(1 min, 0L) m full outer join Tick#time_batch(1 min, 0L) t").addListener { newEvents, oldEvents ->
            timeSeries.addTick(BaseTick(
              Duration.ofSeconds(1),
              Instant.ofEpochMilli(newEvents.first().get("endtime") as Long).atZone(ZoneId.systemDefault()),
              Decimal.valueOf(newEvents.firstOrNull()?.get("open") as Double),
              Decimal.valueOf(newEvents.firstOrNull()?.get("high") as Double),
              Decimal.valueOf(newEvents.firstOrNull()?.get("low") as Double),
              Decimal.valueOf(newEvents.firstOrNull()?.get("close") as Double),
              Decimal.valueOf(newEvents.firstOrNull()?.get("volume") as Double)
            ))

            val smaIndicator = SMAIndicator(ClosePriceIndicator(timeSeries), 12).getValue(timeSeries.endIndex)
            val emaIndicator = EMAIndicator(ClosePriceIndicator(timeSeries), 12).getValue(timeSeries.endIndex)
            logger.debug { "sma: ${smaIndicator}, ema: ${emaIndicator}" }

            logger.debug { "o: ${newEvents.firstOrNull()?.get("open")}, h: ${newEvents.firstOrNull()?.get("high")}, l: ${newEvents.firstOrNull()?.get("low")}, c: ${newEvents.firstOrNull()?.get("close")}, v: ${newEvents.firstOrNull()?.get("volume")}" }
        }
    }

    @OnOpen
    fun onOpen(session: Session) {
        this.session = session
        logger.debug { "Session Opened" }

        session.basicRemote.sendText(objectMapper.writeValueAsString(SubscriptionRequest(channels = listOf(
          HeartbeatChannel(listOf("BTC-USD")),
          TickerChannel(listOf("BTC-USD")),
          Level2Channel(listOf("BTC-USD")),
          MatchesChannel(listOf("BTC-USD"))
        ))))
    }

    @OnMessage
    fun onMessage(message: String) {
        logger.trace { "Message: $message" }

        parseMessage(message)?.let { messageObject ->
            when (messageObject.getAsJsonPrimitive("type").asString) {
                "heartbeat" -> {
                    /*
                    {
                        "type": "heartbeat",
                        "sequence": 90,
                        "last_trade_id": 20,
                        "product_id": "BTC-USD",
                        "time": "2014-11-07T08:19:28.464459Z"
                    }
                    */
                    updateHeartbeat(objectMapper.readValue(message, Heartbeat::class.java))
                }
                "ticker" -> {
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
                    updateTicker(objectMapper.readValue(message, Tick::class.java))
                }
                "match" -> {
                    /*
                    {
                        "type": "match",
                        "trade_id": 10,
                        "sequence": 50,
                        "maker_order_id": "ac928c66-ca53-498f-9c13-a110027a60e8",
                        "taker_order_id": "132fb6ae-456b-4654-b4e0-d681ac05cea1",
                        "time": "2014-11-07T08:19:27.028459Z",
                        "product_id": "BTC-USD",
                        "size": "5.23512",
                        "price": "400.23",
                        "side": "sell"
                    }
                    */
                    updateMatch(objectMapper.readValue(message, Match::class.java))
                }
                "snapshot" -> createSnapshot(messageObject)
                "l2update" -> updateSnapshot(messageObject)
                else -> null
            }
        }
    }

    private fun updateMatch(match: Match) {
        engine.epRuntime.sendEvent(match)
    }

    private fun updateSnapshot(message: JsonObject) {
        /*
        {
            "type": "l2update",
            "product_id": "BTC-EUR",
            "changes": [
                ["buy", "1", "3"],
                ["sell", "3", "1"],
                ["sell", "2", "2"],
                ["sell", "4", "0"]
            ]
        }
        */
        val product = message.getAsJsonPrimitive("product_id").asString
        message.getAsJsonArray("changes").forEach {
            val change = it.asJsonArray
            val limit = change.get(1).asDouble
            val price = change.get(2).asDouble

            when (change.get(0).asString) {
                "buy" -> {
                    if (price > 0) {
                        this.snapshots.get(product)?.bids?.put(limit, price)
                    } else {
                        this.snapshots.get(product)?.bids?.remove(limit)
                    }
                }
                "sell" -> {
                    if (price > 0) {
                        this.snapshots.get(product)?.asks?.put(limit, price)
                    } else {
                        this.snapshots.get(product)?.asks?.remove(limit)
                    }
                }
            }
        }
    }

    private fun createSnapshot(message: JsonObject) {
        /*
        {
            "type": "snapshot",
            "product_id": "BTC-EUR",
            "bids": [["1", "2"]],
            "asks": [["2", "3"]],
        }
        */
        val product = message.getAsJsonPrimitive("product_id").asString
        val snapshot = Snapshot(product)

        snapshot.bids.apply {
            message.getAsJsonArray("bids").forEach {
                val bid = it.asJsonArray
                val limit = bid.get(0).asDouble
                val price = bid.get(1).asDouble
                if (price > 0) {
                    this.put(limit, price)
                } else {
                    this.remove(limit)
                }
            }
        }
        snapshot.asks.apply {
            message.getAsJsonArray("asks").forEach {
                val ask = it.asJsonArray
                val limit = ask.get(0).asDouble
                val price = ask.get(1).asDouble
                if (price > 0) {
                    this.put(limit, price)
                } else {
                    this.remove(limit)
                }
            }
        }

        this.snapshots.put(product, snapshot)
    }

    private fun updateTicker(tick: Tick) {
        engine.epRuntime.sendEvent(tick)
    }

    private fun parseMessage(message: String) = gson.fromJson(message, JsonObject::class.java)

    private fun updateHeartbeat(heartbeat: Heartbeat) {
        engine.epRuntime.sendEvent(heartbeat)
        engine.epRuntime.sendEvent(CurrentTimeEvent(heartbeat.time.toEpochMilli()))
    }

    @OnError
    fun onError(session: Session, t: Throwable) {
        throw t
    }

    @OnClose
    fun onClose(session: Session) {
        this.session = null
        logger.debug { "Session Closed" }
    }
}

data class Snapshot(
  val product: String,
  val bids: MutableMap<Double, Double> = mutableMapOf(),
  val asks: MutableMap<Double, Double> = mutableMapOf()
)