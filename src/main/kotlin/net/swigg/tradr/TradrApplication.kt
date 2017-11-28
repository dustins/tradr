package net.swigg.tradr

import com.espertech.esper.client.Configuration
import com.espertech.esper.client.EPServiceProvider
import com.espertech.esper.client.EPServiceProviderManager
import mu.KotlinLogging
import net.swigg.tradr.feed.Feed
import net.swigg.tradr.feed.Heartbeat
import net.swigg.tradr.feed.Match
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.socket.client.standard.AnnotatedEndpointConnectionManager
import org.ta4j.core.BaseTimeSeries
import org.ta4j.core.Tick
import org.ta4j.core.TimeSeries
import javax.websocket.ContainerProvider

private val logger = KotlinLogging.logger {}

@SpringBootApplication
class TradrApplication {
    @Bean
    fun websocketClient(feed: Feed, @Value("\${tradr.coinbase.websocketUrl}") websocketUrl: String): AnnotatedEndpointConnectionManager {
        val manager = AnnotatedEndpointConnectionManager(feed, websocketUrl)
        manager.webSocketContainer = ContainerProvider.getWebSocketContainer()
        manager.isAutoStartup = true
        manager.webSocketContainer.defaultMaxBinaryMessageBufferSize = 1024 * 1024
        manager.webSocketContainer.defaultMaxTextMessageBufferSize = 1024 * 1024

        return manager
    }

    @Bean
    fun esper(timeSeries: TimeSeries): EPServiceProvider {
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
        val configuration = Configuration()
        configuration.engineDefaults.threading.isInternalTimerEnabled = false

        val engine = EPServiceProviderManager.getDefaultProvider()

        engine.epAdministrator.configuration.addEventType(Heartbeat::class.java)
        engine.epAdministrator.configuration.addEventType(Tick::class.java)
        engine.epAdministrator.configuration.addEventType(Match::class.java)

//        engine.epAdministrator.createEPL("select sum(size) as volume FROM Match#ext_timed_batch(timestamp, 1 min, 0L)").addListener { newEvents, oldEvents ->
//            logger.debug { "ext_timed_batch: ${newEvents.firstOrNull()?.get("volume")}" }
//        }

//        engine.epAdministrator.createEPL("select first(price) as open, max(price) as high, min(price) as low, last(price) as close, sum(m.size) as volume FROM Match#time_batch(1 min, 0L) m full outer join Tick#time_batch(1 min, 0L) t").addListener { newEvents, oldEvents ->
//            logger.debug { "o: ${newEvents.firstOrNull()?.get("open")}, h: ${newEvents.firstOrNull()?.get("high")}, l: ${newEvents.firstOrNull()?.get("low")}, c: ${newEvents.firstOrNull()?.get("close")}, v: ${newEvents.firstOrNull()?.get("volume")}" }
//        }

//        engine.epAdministrator.createEPL("select sum(size) as volume FROM Match#time_batch(1 min, 0L)").addListener { newEvents, oldEvents ->
//            logger.debug { "time_batch: ${newEvents.firstOrNull()?.get("volume")}" }
//        }

//        engine.epAdministrator.createEPL("select * from Ticker").addListener({newEvents,oldEvents,statement,engine ->
//            println("ticked")
//        })

//        engine.epAdministrator.createEPL("select * FROM Match#time(60) m OUTPUT LAST AT (*, *, *, *, *) ORDER BY time DESC").addListener { newEvents, oldEvents ->
//            newEvents.forEachIndexed { index, eventBean -> logger.debug { "volume(#${index}): ${eventBean.get("time")} @ ${eventBean.get("time")}" } }
//        }
//        engine.epAdministrator.createEPL("select sum(size) as volume FROM Match#time(90) WHERE time.roundFloor('min') = cast(current_timestamp(), localdatetime).minus(1 SECOND).roundFloor('min') OUTPUT LAST EVERY 10 SECONDS").addListener { newEvents, oldEvents ->
//            logger.debug { newEvents[0].get("volume") }
//        }

//        engine.epAdministrator.createEPL("select first(t.price), max(t.price), min(t.price), last(t.price), sum(m.size) from Ticker#lastevent as t, Match#time(60) m output last every 60 seconds").addListener({newEvents,oldEvents,statement,engine ->
//            val stats = SummaryStatistics()
////            newEvents.firstOrNull()?.let { tick ->
////                timeSeries.addTick(BaseTick(
////                  Duration.ofSeconds(60),
////                  ZonedDateTime.now(),
////                  Decimal.valueOf(tick.get("first") as Double),
////                  Decimal.valueOf(tick.get("max") as Double),
////                  Decimal.valueOf(tick.get("min") as Double),
////                  Decimal.valueOf(tick.get("last") as Double),
////                  Decimal.valueOf(tick.get("sum") as Double)
////                ))
////            }
////            val prices = newEvents.map { it.get("price") as Double }
////            prices.forEach { stats.addValue(it) }
////
////            timeSeries.addTick(BaseTick(
////              Duration.of(statement.timeLastStateChange, ChronoUnit.MILLIS),
////              ZonedDateTime.now(),
////              Decimal.valueOf(prices.first()),
////              Decimal.valueOf(stats.max),
////              Decimal.valueOf(stats.min),
////              Decimal.valueOf(prices.last()),
////              Decimal.ZERO
////            ))
////
////            println(SMAIndicator(ClosePriceIndicator(timeSeries), 12).getValue(timeSeries.endIndex))
//        })

        return engine
    }

    @Bean
    fun timeseries(): TimeSeries {
        val timeSeries = BaseTimeSeries()

        return timeSeries
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(TradrApplication::class.java, *args)
}
