package net.swigg.tradr.trade

import net.swigg.tradr.feed.Feed
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

@Controller("/trade")
class TradeController constructor(val feed: Feed){
    @Autowired
    lateinit var tradeService: TradeService

    @GetMapping
    fun index(): ModelAndView {
        return ModelAndView("trade/index", mapOf(
          "trade" to Trade()
        ))
    }

    @PostMapping
    fun post(@ModelAttribute trade: Trade): ModelAndView {
        tradeService.accounts()
        return ModelAndView("trade/index", mapOf(
          "trade" to trade
        ))
    }
}

