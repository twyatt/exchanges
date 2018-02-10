package com.traviswyatt.exchanges

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import org.knowm.xchange.Exchange
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.trade.UserTrades
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair

val Exchange.name: String
    get() = exchangeSpecification.exchangeName

val Exchange.pairs: MutableList<CurrencyPair>
    get() = exchangeSymbols

fun Exchange.fetchFundingHistory() = with(accountService) {
    getFundingHistory(createFundingHistoryParams())
}

fun Exchange.fetchTradeHistory() = async {
    val params = tradeService.createTradeHistoryParams()
    when (params) {
        is TradeHistoryParamCurrencyPair -> fetchTradeHistory(this@fetchTradeHistory, params)
        else -> throw UnsupportedOperationException()
    }
}

private suspend fun fetchTradeHistory(
    exchange: Exchange,
    params: TradeHistoryParamCurrencyPair
): Map<CurrencyPair, UserTrades> {
    return exchange.pairs.map { pair ->
        println("Fetching ${exchange.name} → $pair")
        params.currencyPair = pair
        delay(5_000L)
        pair to exchange.tradeService.getTradeHistory(params)
    }.toMap()
}
