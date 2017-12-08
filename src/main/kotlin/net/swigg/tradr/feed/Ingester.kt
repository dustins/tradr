package net.swigg.tradr.feed

import com.google.gson.JsonObject
import io.searchbox.client.JestClient
import io.searchbox.core.Bulk
import io.searchbox.core.Index
import io.searchbox.core.Search
import io.searchbox.params.Parameters
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.elasticsearch.action.bulk.BulkRequestBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.springframework.stereotype.Service
import org.ta4j.core.BaseTick
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

@Service
class Ingester(val jestClient: JestClient) {
    fun ingest() {
//        val latest = lastTick()
        ingestStream()
    }

    private fun ingestStream() {
        val urlStream = URL("http://api.bitcoincharts.com/v1/csv/coinbaseUSD.csv.gz").openStream()
        val gzipStream = GZIPInputStream(urlStream)
        val reader = BufferedReader(InputStreamReader(gzipStream))
        val parser = CSVFormat.DEFAULT.parse(reader)

        var currentMinute: Instant? = null
        var price = 0.0
        var volume = 0.0
        var bulk = bulkBuilder()
        var bulkCount = 0
        var tick: BaseTick? = null
        for (record: CSVRecord in parser) {
            // set the current minute if it isn't set
            currentMinute = currentMinute ?: Instant.ofEpochSecond(record.get(0).toLong()).truncatedTo(ChronoUnit.MINUTES)
            tick = tick ?: BaseTick(Duration.ofMinutes(1), currentMinute?.atZone(ZoneId.systemDefault()))

            val recordTime = Instant.ofEpochSecond(record.get(0).toLong())
            if (recordTime.isAfter(currentMinute?.plus(1, ChronoUnit.MINUTES))) {
                // add index action
                bulkCount++
                bulk.addAction(Index.Builder(JsonObject().apply {
                    addProperty("time", tick?.beginTime?.toInstant()?.toEpochMilli())
                    addProperty("open", tick?.openPrice?.toDouble())
                    addProperty("low", tick?.minPrice?.toDouble())
                    addProperty("high", tick?.maxPrice?.toDouble())
                    addProperty("close", tick?.closePrice?.toDouble())
                    addProperty("volume", tick?.volume?.toDouble())
                    addProperty("trades", 0)
                    addProperty("amount", 0)
                }).id(currentMinute?.toEpochMilli()?.toString()).setParameter(Parameters.OP_TYPE, "create") .build())

                if (bulkCount >= TimeUnit.DAYS.toMinutes(60)) {

                    val result = jestClient.execute(bulk.build())
                    bulk = bulkBuilder()
                    bulkCount = 0
                }

                currentMinute = recordTime.truncatedTo(ChronoUnit.MINUTES)
                tick = BaseTick(Duration.ofMinutes(1), currentMinute.atZone(ZoneId.systemDefault()))
            }

            tick.addTrade(record.get(2), record.get(1))
        }

        val result = jestClient.execute(bulk.build())
    }

    private fun bulkBuilder(): Bulk.Builder {
        return Bulk.Builder()
          .defaultIndex("tradr")
          .defaultType("tick")
    }

    private fun lastTick(): Instant {
        val current = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.query(
          QueryBuilders.rangeQuery("time").lte(current.toEpochMilli())
        ).noFields().size(1)

        val result = jestClient.execute(Search.Builder(searchSourceBuilder.toString())
          .addIndex("tradr")
          .addType("tick")
          .build()
        )

        if (!result.isSucceeded || result.total == 0L) {
            throw RuntimeException("Unable to find latest Tick")
        }

        val hits = result.jsonObject.getAsJsonObject("hits")
        val hit = hits.getAsJsonArray("hits").get(0)
        val time = hit.asJsonObject.getAsJsonPrimitive("_id").asLong

        return Instant.ofEpochMilli(time)
    }
}