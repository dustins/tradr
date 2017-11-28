package net.swigg.tradr

import net.swigg.tradr.feed.Feed
import net.swigg.tradr.feed.Snapshot
import net.swigg.tradr.feed.TickRepository
//import net.swigg.tradr.feed.Tick
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class FeedController {
    @Autowired
    lateinit var tickRepository: TickRepository

    @Autowired
    lateinit var feed: Feed

    @GetMapping("/snapshot/{product}")
    fun snapshot(@PathVariable product: String): Snapshot? = feed.snapshots.get(product)

//    @GetMapping("/tick")
//    fun tick(): Tick? = feed.tick
}