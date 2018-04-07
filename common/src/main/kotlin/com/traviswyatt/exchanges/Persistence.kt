package com.traviswyatt.exchanges

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import org.knowm.xchange.Exchange
import org.knowm.xchange.dto.account.FundingRecord
import org.knowm.xchange.dto.trade.UserTrade
import java.io.File
import java.util.*

const val FUNDING_HISTORY_POSTFIX = "_funding_history.json"
const val TRADE_HISTORY_POSTFIX = "_trade_history.json"

private val defaultGson: Gson = GsonBuilder()
    .registerTypeAdapter(Date::class.java, JsonDeserializer<Date> { json, _, _ -> Date(json.asJsonPrimitive.asLong) } )
    .registerTypeAdapter(Date::class.java, JsonSerializer<Date> { date, _, _ -> JsonPrimitive(date.time) } )
    .setPrettyPrinting()
    .create()

private inline fun <reified T> Gson.fromJson(json: String): T =
    this.fromJson<T>(json, object : TypeToken<T>() {}.type)

fun List<*>.toJson(gson: Gson = defaultGson) = gson.toJson(this)

fun List<*>.saveAs(file: File, gson: Gson = defaultGson) = file.writeText(toJson(gson))

fun List<*>.saveAs(filename: String, gson: Gson = defaultGson) = saveAs(File(filename), gson)

fun saveFundingHistory(exchange: Exchange, fundingHistory: List<FundingRecord>) =
    fundingHistory.saveAs(exchange.name.toLowerCase() + FUNDING_HISTORY_POSTFIX)

fun saveFundingHistory(entry: Map.Entry<Exchange, List<FundingRecord>>) =
    saveFundingHistory(entry.key, entry.value)

fun saveFundingHistories(histories: Map<Exchange, List<FundingRecord>>) =
    histories.forEach(::saveFundingHistory)

fun saveTradeHistory(exchange: Exchange, fundingHistory: List<UserTrade>) =
    fundingHistory.saveAs(exchange.name.toLowerCase() + TRADE_HISTORY_POSTFIX)

fun saveTradeHistory(entry: Map.Entry<Exchange, List<UserTrade>>) =
    saveTradeHistory(entry.key, entry.value)

fun saveTradeHistories(histories: Map<Exchange, List<UserTrade>>) =
    histories.forEach(::saveTradeHistory)

fun File.asFundingHistory(gson: Gson = defaultGson): List<FundingRecord> = gson.fromJson(readText())

fun Exchange.loadFundingHistory(gson: Gson = defaultGson): List<FundingRecord> =
    File(name.toLowerCase() + FUNDING_HISTORY_POSTFIX).asFundingHistory(gson)

fun List<Exchange>.loadFundingHistories(gson: Gson = defaultGson): Map<Exchange, List<FundingRecord>> =
    associate { exchange -> Pair(exchange, exchange.loadFundingHistory(gson)) }

fun File.asTradeHistory(gson: Gson = defaultGson): List<UserTrade> = gson.fromJson(readText())

fun Exchange.loadTradeHistory(gson: Gson = defaultGson): List<UserTrade> =
    File(name.toLowerCase() + TRADE_HISTORY_POSTFIX).asTradeHistory(gson)

fun List<Exchange>.loadTradeHistories(gson: Gson = defaultGson): Map<Exchange, List<UserTrade>> =
    associate { exchange -> Pair(exchange, exchange.loadTradeHistory(gson)) }
