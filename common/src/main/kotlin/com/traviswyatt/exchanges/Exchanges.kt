package com.traviswyatt.exchanges

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import org.knowm.xchange.Exchange
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.account.FundingRecord
import org.knowm.xchange.dto.trade.UserTrade
import org.knowm.xchange.poloniex.PoloniexException
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair
import org.knowm.xchange.service.trade.params.TradeHistoryParams
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan
import java.util.*

val Exchange.name: String
    get() = exchangeSpecification.exchangeName

val Exchange.pairs: List<CurrencyPair>
    get() = exchangeSymbols

fun Exchange.fetchFundingHistory(
    params: TradeHistoryParams = accountService.createFundingHistoryParams()
): List<FundingRecord> = accountService.getFundingHistory(params)

fun Exchange.fetchFundingHistoryAsync(
    params: TradeHistoryParams = accountService.createFundingHistoryParams()
): Deferred<List<FundingRecord>> = async { fetchFundingHistory(params) }

/** Asynchronously fetches funding history for all [Exchange]s in [List], suspending until complete. */
suspend fun List<Exchange>.fetchFundingHistories(
    paramsBlock: (Exchange) -> TradeHistoryParams = { it.accountService.createFundingHistoryParams() }
): Map<Exchange, List<FundingRecord>> {
    return this
        .map { exchange -> Pair(exchange, exchange.fetchFundingHistoryAsync(paramsBlock(exchange))) }
        .associate { (exchange, deferred) -> Pair(exchange, deferred.await()) }
}

suspend fun Exchange.fetchTradeHistory(
    params: TradeHistoryParams = tradeService.createTradeHistoryParams()
): List<UserTrade> {
    if (params is TradeHistoryParamsTimeSpan) {
        params.startTime = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.time
        params.endTime = Date()
    }

    return when (params) {
        is TradeHistoryParamCurrencyPair -> fetchTradeHistory(params, pairs)
        else -> tradeService.getTradeHistory(params).userTrades
    }
}

suspend fun Exchange.fetchTradeHistory(
    params: TradeHistoryParamCurrencyPair,
    pairs: List<CurrencyPair>
): List<UserTrade> = pairs.flatMap { pair ->
    println("↓ $name → $pair")
    try {
        tradeService.getTradeHistory(params.apply { currencyPair = pair }).userTrades
    } catch (e: PoloniexException) {
        if (e.httpStatusCode == 422) { // Poloniex reports 422 for invalid currency pairs.
            println("! $name → ${e.message}")
            emptyList<UserTrade>()
        } else {
            throw IllegalStateException(e)
        }
    } finally {
        exchangeSpecification.exchangeSpecificParameters["delay"]?.let {
            delay(it as Long)
        }
    }
}

fun Exchange.fetchTradeHistoryAsync(
    params: TradeHistoryParamCurrencyPair,
    pairs: List<CurrencyPair>
): Deferred<List<UserTrade>> = async { fetchTradeHistory(params, pairs) }

fun Exchange.fetchTradeHistoryAsync(
    params: TradeHistoryParams = tradeService.createTradeHistoryParams()
): Deferred<List<UserTrade>> = async { fetchTradeHistory(params) }

/** Asynchronously fetches trade history for all [Exchange]s in [List], suspending until complete. */
suspend fun List<Exchange>.fetchTradeHistories(): Map<Exchange, List<UserTrade>> {
    return this
        .map { exchange -> Pair(exchange, exchange.fetchTradeHistoryAsync()) }
        .associate { (exchange, deferred) -> Pair(exchange, deferred.await()) }
}

fun List<Exchange>.fetchTradeHistoriesAsync(): Deferred<Map<Exchange, List<UserTrade>>> =
    async { fetchTradeHistories() }
