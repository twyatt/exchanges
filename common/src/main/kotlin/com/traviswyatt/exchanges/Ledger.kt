package com.traviswyatt.exchanges

import org.knowm.xchange.currency.Currency
import org.knowm.xchange.dto.Order.OrderType.ASK
import org.knowm.xchange.dto.Order.OrderType.BID
import org.knowm.xchange.dto.account.FundingRecord
import org.knowm.xchange.dto.account.FundingRecord.Type.DEPOSIT
import org.knowm.xchange.dto.account.FundingRecord.Type.WITHDRAWAL
import org.knowm.xchange.dto.trade.UserTrade
import java.math.BigDecimal

class Ledger {

    private val ledger = hashMapOf<Currency, BigDecimal>()

    fun add(currency: Currency, amount: BigDecimal) {
        val previousAmount = ledger[currency] ?: BigDecimal.ZERO
        ledger[currency] = previousAmount + amount
    }

    fun sub(currency: Currency, amount: BigDecimal) = add(currency, amount.negate())

    fun add(record: FundingRecord) {
        val currency = record.currency
        val amount = record.amount

        when (record.type) {
            DEPOSIT -> add(currency, amount)
            WITHDRAWAL -> sub(currency, amount)
            else -> error("Unknown record type: ${record.type}")
        }
    }

    fun add(trade: UserTrade) {
        val counter = trade.currencyPair.counter // e.g. ___/XRP
        val base = trade.currencyPair.base // e.g. XRP/___
        val baseAmount = trade.originalAmount
        val counterAmount = baseAmount.multiply(trade.price)
        val feeAmount = trade.feeAmount.positive
        val feeCurrency = trade.feeCurrency

        when (trade.type) {
            // e.g. ASK XRP/BTC = Sell XRP for BTC
            ASK -> {
                sub(base, baseAmount)
                add(counter, counterAmount)
                sub(feeCurrency, feeAmount)
                println("Sell $baseAmount $base for $counterAmount $counter fee $feeAmount $feeCurrency")
            }

            // e.g. BID XRP/BTC = Buy XRP using BTC
            BID -> {
                add(base, baseAmount)
                sub(counter, counterAmount)
                sub(feeCurrency, feeAmount)
                println("Buy $baseAmount $base using $counterAmount $counter fee $feeAmount $feeCurrency")
            }

            else -> error("Unknown order type: ${trade.type}")
        }
    }

    override fun toString(): String = ledger.toString()
}

/** Force [BigDecimal] to be a positive value. */
private val BigDecimal.positive: BigDecimal get() = if (this < BigDecimal.ZERO) negate() else this
