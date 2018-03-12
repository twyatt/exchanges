package com.traviswyatt.exchanges

import org.knowm.xchange.currency.Currency
import org.knowm.xchange.dto.account.FundingRecord
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

data class GeminiCsv(val header: List<String>, val rows: List<List<String>>)

fun File.asGeminiCsv(): GeminiCsv = readCsv().let { GeminiCsv(it.first(), it.drop(1)) }

fun GeminiCsv.toFundingHistory(): List<FundingRecord> {
    return rows
        .map { row ->
            // List<header> + List<List<value>> → Map<header, value>
            row.mapIndexed { index, value ->
                Pair(header[index], value)
            }.toMap()
        }
        .filter { row -> row["Type"] == "Credit" || row["Type"] == "Debit" }
        .map(::rowToFundingRecord) // Map<header, value> → FundingRecord
}

private fun rowToFundingRecord(row: Map<String, String>): FundingRecord {
    /*
    CSV Column Headers:
    Date,
    Time (UTC),
    Type,
    Symbol,
    Specification,
    Liquidity Indicator,
    Trading Fee Rate (bps),
    USD Amount,
    Trading Fee (USD),
    USD Balance,
    BTC Amount,
    Trading Fee (BTC),
    BTC Balance,
    ETH Amount,
    ETH Balance,
    Trade ID,
    Order ID,
    Order Date,
    Order Time,
    Client Order ID,
    API Session,
    Tx Hash,
    Deposit Tx Output,
    Withdrawal Destination
    */

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.ENGLISH)

    // e.g. "(0.123 BTC)" → BigDecimal("-0.123")
    // e.g. "0.123 BTC" → BigDecimal("0.123")
    fun String.cryptoAmountAsBigDecimal(): BigDecimal {
        val negative = if (trimStart().substring(0, 1) == "(") "-" else ""
        return BigDecimal(negative + trim('(', ')', ' ').substringBefore(' ').trim())
    }

    // e.g. "0.123 BTC " → BigDecimal("0.123")
    fun String.cryptoBalanceAsBigDecimal() = BigDecimal(trim().substringBefore(' ').trim())

    // e.g. "$123.00 " → BigDecimal("123.00")
    fun String.usdAmountAsBigDecimal() = BigDecimal(trimStart('$').trim())

    // e.g. ""$1,234.56 "" → BigDecimal("1234.56")
    // e.g. ""($1,234.56) "" → BigDecimal("-1234.56")
    fun String.usdBalanceAsBigDecimal(): BigDecimal {
        val negative = if (trimStart('"', ' ').substring(0, 1) == "(") "-" else ""
        return BigDecimal(negative + trim('"', ' ').trimStart('$').replace(",", ""))
    }

    val address = row["Withdrawal Destination"]
    val date = dateFormat.parse("${row["Date"]} ${row["Time (UTC)"]} UTC")
    val currency = Currency.getInstance(row["Symbol"]?.substring(0..2))
    val (amount, balance) = when (currency) {
        Currency.BTC -> {
            val amount = row["BTC Amount"]?.cryptoAmountAsBigDecimal()
            val balance = row["BTC Balance"]?.cryptoBalanceAsBigDecimal()
            amount to balance
        }
        Currency.ETH -> {
            val amount = row["ETH Amount"]?.cryptoAmountAsBigDecimal()
            val balance = row["ETH Balance"]?.cryptoBalanceAsBigDecimal()
            amount to balance
        }
        Currency.USD -> {
            val amount = row["USD Amount"]?.usdAmountAsBigDecimal()
            val balance = row["USD Balance"]?.usdBalanceAsBigDecimal()
            amount to balance
        }
        else -> error("Unknown currency: $currency")
    }
    val internalId = null
    val externalId = null
    val type = when (row["Type"]) {
        "Credit" -> FundingRecord.Type.DEPOSIT
        "Debit" -> FundingRecord.Type.WITHDRAWAL
        else -> error("Unknown type: ${row["Type"]}")
    }
    val status = FundingRecord.Status.COMPLETE
    val fee = BigDecimal.ZERO
    val description = row["Specification"]

    return FundingRecord(
        address,
        date,
        currency,
        amount,
        internalId,
        externalId,
        type,
        status,
        balance,
        fee,
        description
    )
}