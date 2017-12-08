package net.swigg.tradr.trade

public class Trade {
    var side: Side = Side.BUY

    var price: Double = 0.0

    var amount: Double = 0.0

    public enum class Side {
        BUY,
        SELL
    }
}