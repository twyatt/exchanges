package com.traviswyatt.exchanges

import org.knowm.xchange.Exchange
import org.knowm.xchange.currency.Currency
import org.knowm.xchange.currency.CurrencyPair

const val DEBUG = true

private fun defaultFilter() = { _: Exchange?, _: Currency? -> true }

class Logger {
    companion object {
        var filter: (exchange: Exchange?, currency: Currency?) -> Boolean = defaultFilter()
    }
}

fun log(exchange: Exchange? = null, currency: Currency? = null, block: () -> String) {
    if (DEBUG && Logger.filter(exchange, currency)) {
        log(exchange, currency?.toString(), block())
    }
}

fun log(exchange: Exchange? = null, pair: CurrencyPair? = null, block: () -> String) {
    if (DEBUG && (Logger.filter(exchange, pair?.base) || Logger.filter(exchange, pair?.counter))) {
        log(exchange, pair?.toString(), block())
    }
}

fun log(exchange: Exchange? = null, currency: String? = null, message: String) {
    val prefix: String = listOf(exchange?.name ?: "", currency ?: "")
        .joinToString(" ")
        .let { if (it.isNotEmpty()) "$it: " else it }
    println(prefix + message)
}