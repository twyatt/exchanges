package com.traviswyatt.exchanges.csv

import org.knowm.xchange.currency.Currency
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

data class PoloniexLendingCsv(val header: List<String>, val rows: List<List<String>>)

data class PoloniexLendingEvent(
    val currency: Currency,
    val rate: BigDecimal,
    val amount: BigDecimal,
    val duration: BigDecimal, // days
    val interest: BigDecimal,
    val fee: BigDecimal,
    val earned: BigDecimal,
    val open: Date,
    val closed: Date
)

fun File.asPoloniexLendingCsv(): PoloniexLendingCsv = readCsv().let {
    PoloniexLendingCsv(
        it.first(),
        it.drop(1)
    )
}

fun PoloniexLendingCsv.toLendingEvents(): List<PoloniexLendingEvent> {
    return rows
        .map { row ->
            // List<header> + List<List<value>> → Map<header, value>
            row.mapIndexed { index, value ->
                Pair(header[index], value)
            }.toMap()
        }
        .map(::rowToLendingEvent) // Map<header, value> → PoloniexLendingEvent
}

private fun rowToLendingEvent(row: Map<String, String>): PoloniexLendingEvent {
    /*
    Currency,
    Rate,
    Amount,
    Duration,
    Interest,
    Fee,
    Earned,
    Open,
    Close
     */

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.ENGLISH)

    val currency = Currency.getInstance(row["Currency"])
    val rate = BigDecimal(row["Rate"])
    val amount = BigDecimal(row["Amount"])
    val duration = BigDecimal(row["Duration"])
    val interest = BigDecimal(row["Interest"])
    val fee = BigDecimal(row["Fee"])
    val earned = BigDecimal(row["Earned"])
    val open = dateFormat.parse("${row["Open"]} UTC")
    val close = dateFormat.parse("${row["Close"]} UTC")

    return PoloniexLendingEvent(currency, rate, amount, duration, interest, fee, earned, open, close)
}