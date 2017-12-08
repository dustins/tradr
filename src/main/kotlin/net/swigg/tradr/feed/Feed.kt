package net.swigg.tradr.feed

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.searchbox.client.JestClient
import io.searchbox.core.Index
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.ta4j.core.BaseTick
import org.ta4j.core.Tick
import org.ta4j.core.TimeSeries
import java.time.Duration
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.websocket.*

private val logger = KotlinLogging.logger {}

@ClientEndpoint
@Component
class Feed @Autowired constructor(val timeSeries: TimeSeries, val jestClient: JestClient, val objectMapper: ObjectMapper) {
    var session: Session? = null

    val gson = Gson()

    val snapshots: MutableMap<String, Snapshot> = mutableMapOf()

    @OnOpen
    fun onOpen(session: Session) {
        this.session = session
        logger.debug { "Session Opened" }

        snapshots.clear()
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
        if (timeSeries.tickCount == 0) {
            timeSeries.addTick(createTick(match))
        }

        var tick = timeSeries.lastTick
        if (match.time.isAfter(tick.endTime.toInstant())) {
            if (timeSeries.tickCount > 1) {
                saveTick(tick)
            }
            logger.debug { "${tick.beginTime} (${tick.trades}) - (o:${tick.openPrice} l:${tick.minPrice} h:${tick.maxPrice} c:${tick.closePrice} v:${tick.volume})" }
            tick = createTick(match)
            timeSeries.addTick(tick)
        }

        tick.addTrade(match.size, match.price)
    }

    private fun saveTick(tick: Tick) {
        val source = JsonObject().apply {
            addProperty("time", tick.beginTime.toInstant().toEpochMilli())
            addProperty("open", tick.openPrice.toDouble())
            addProperty("low", tick.minPrice.toDouble())
            addProperty("high", tick.maxPrice.toDouble())
            addProperty("close", tick.closePrice.toDouble())
            addProperty("volume", tick.volume.toDouble())
            addProperty("trades", tick.trades)
            addProperty("amount", tick.amount.toDouble())
        }

        val result = jestClient.execute(
          Index.Builder(source)
            .index("tradr")
            .type("tick")
            .id(tick.beginTime.toInstant().toEpochMilli().toString())
            .build()
        )
    }

    private fun createTick(match: Match): Tick {
        val endTime = match.time.atZone(ZoneId.systemDefault())

        return BaseTick(
          Duration.ofMinutes(1),
          endTime.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)
        )
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

    private fun parseMessage(message: String) = gson.fromJson(message, JsonObject::class.java)

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