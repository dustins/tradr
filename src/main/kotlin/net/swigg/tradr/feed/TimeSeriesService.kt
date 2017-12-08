package net.swigg.tradr.feed

import com.google.gson.JsonElement
import io.searchbox.client.JestClient
import io.searchbox.core.Search
import io.searchbox.core.search.sort.Sort
import io.searchbox.params.Parameters
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.script.Script
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.ta4j.core.BaseTick
import org.ta4j.core.BaseTimeSeries
import org.ta4j.core.Tick
import org.ta4j.core.TimeSeries
import java.time.Instant
import java.time.Period
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.ArrayList

@Service
class TimeSeriesService @Autowired constructor(val jestClient: JestClient) {
    fun getTimeSeries(start: Instant, period: Period, tickSize: ChronoUnit): TimeSeries {
        val timeSeries = BaseTimeSeries()
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.size(0).query(
          QueryBuilders.rangeQuery("time")
            .from(start.toEpochMilli())
            .to(start.plus(period).toEpochMilli())
        ).aggregation(
          AggregationBuilders.dateHistogram("time")
            .extendedBounds(start.toEpochMilli(), start.plus(period).toEpochMilli())
            .interval(when (tickSize) {
                ChronoUnit.SECONDS -> DateHistogramInterval.SECOND
                ChronoUnit.MINUTES -> DateHistogramInterval.MINUTE
                ChronoUnit.HOURS -> DateHistogramInterval.HOUR
                ChronoUnit.HALF_DAYS -> DateHistogramInterval("12h")
                ChronoUnit.DAYS -> DateHistogramInterval.DAY
                ChronoUnit.WEEKS -> DateHistogramInterval.WEEK
                ChronoUnit.MONTHS -> DateHistogramInterval.MONTH
                ChronoUnit.YEARS -> DateHistogramInterval.YEAR
                ChronoUnit.DECADES -> DateHistogramInterval("10y")
                ChronoUnit.CENTURIES -> DateHistogramInterval("100y")
                ChronoUnit.MILLENNIA -> DateHistogramInterval("1000")
                ChronoUnit.ERAS -> DateHistogramInterval("1000000y")
                ChronoUnit.FOREVER -> throw RuntimeException("Unable to aggregate to infinite unit of time.")
                ChronoUnit.NANOS, ChronoUnit.MICROS, ChronoUnit.MILLIS -> throw RuntimeException("Minimum tickSize is ")
            }).subAggregation(
            AggregationBuilders.sum("volume").field("volume")
          ).subAggregation(
            AggregationBuilders.max("high").field("high")
          ).subAggregation(
            AggregationBuilders.min("low").field("low")
          ).subAggregation(
            AggregationBuilders.scriptedMetric("open")
              .initScript(Script("params._agg.ticks = []"))
              .mapScript(Script("params._agg.ticks.add(['time': doc.time.value.getMillis(),'open':doc.open.value])"))
              .combineScript(Script("params._agg.ticks.sort((a,b) -> a?.time.compareTo(b?.time)); return params._agg.ticks[0]"))
              .reduceScript(Script("params._aggs.sort((a,b) -> a?.time.compareTo(b?.time)); return params._aggs[0]?.open"))
          ).subAggregation(
            AggregationBuilders.scriptedMetric("close")
              .initScript(Script("params._agg.ticks = []"))
              .mapScript(Script("params._agg.ticks.add(['time': doc.time.value.getMillis(),'close':doc.close.value])"))
              .combineScript(Script("params._agg.ticks.sort((a,b) -> b?.time.compareTo(a?.time)); return params._agg.ticks[0]"))
              .reduceScript(Script("params._aggs.sort((a,b) -> b?.time.compareTo(a?.time)); return params._aggs[0]?.close"))
          )
        )

        var search = Search.Builder(searchSourceBuilder.toString())
          .addIndex("tradr")
          .addType("tick")
          .addSort(Sort("time", Sort.Sorting.ASC))
          .setParameter(Parameters.SCROLL, "5m")
          .build()

        var result = jestClient.execute(search)
        result.jsonObject.getAsJsonObject("hits").getAsJsonArray("hits").forEach { element ->
            timeSeries.addTick(createTick(tickSize, element))
        }

//        do {
//
//        } while ()
        return timeSeries
    }

    private fun createTick(tickSize: ChronoUnit, element: JsonElement): Tick {
        return BaseTick(
          tickSize.duration,
          Instant.ofEpochMilli(element.asLong).atZone(ZoneOffset.UTC)
        )
    }
}