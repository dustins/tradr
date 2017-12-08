package net.swigg.tradr

import net.swigg.tradr.feed.Ingester
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class IngestController {
    @Autowired
    lateinit var ingester: Ingester

    @GetMapping("/ingest")
    fun ingest() {
        ingester.ingest()
    }
}