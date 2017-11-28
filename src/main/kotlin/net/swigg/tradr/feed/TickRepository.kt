package net.swigg.tradr.feed

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface TickRepository : ElasticsearchRepository<Tick, Long> {
}