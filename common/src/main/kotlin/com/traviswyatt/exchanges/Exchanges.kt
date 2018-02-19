package com.traviswyatt.exchanges

import org.knowm.xchange.Exchange

val Exchange.name
    get() = exchangeSpecification.exchangeName

val Exchange.pairs
    get() = exchangeSymbols

fun Exchange.fetchFundingHistory() = with(accountService) {
    getFundingHistory(createFundingHistoryParams())
}

fun Exchange.fetchTradeHistory() = with(tradeService) {
    getTradeHistory(createTradeHistoryParams())
}
