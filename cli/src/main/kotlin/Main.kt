import com.traviswyatt.exchanges.*
import com.traviswyatt.exchanges.csv.*
import kotlinx.coroutines.experimental.runBlocking
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.currency.Currency
import org.knowm.xchange.dto.account.FundingRecord
import org.knowm.xchange.dto.trade.UserTrade
import java.io.File
import java.util.*

private const val OFFLINE = true

fun main(args: Array<String>) = runBlocking<Unit> {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    if (OFFLINE) log("Offline mode.")

//    val csv = File("gemini_transaction_history.csv").asGeminiCsv()
//    csv.toFundingHistory().forEach(::println)

    val exchanges =
        specifications
            .map { it.apply { isShouldLoadRemoteMetaData = !OFFLINE } }
            .map { ExchangeFactory.INSTANCE.createExchange(it) }

//    Logger.filter = { _, currency: Currency? -> currency == null || currency == Currency.XVG }
//    Logger.filter = { e: Exchange?, c: Currency? -> c == Currency.ETH && e == exchanges.first { it.name == "Gemini" } }

    createLedger(exchanges).apply {
        val startDate = Calendar.getInstance().apply { set(2017, 0, 1) }.time
        val endDate = Calendar.getInstance().apply { set(2019, 0, 1) }.time
        print { date: Date -> date.after(startDate) && date.before(endDate) }
    }

//    fetchAndSaveTradeHistories(exchanges)
//    val tradeHistories = exchanges.loadTradeHistories()
//    printTradeHistories(tradeHistories)
//
//    repeat(3) { println() }
//
//    fetchAndSaveFundingHistories(exchanges.filter { it.name != "Gemini" })
//    val fundingHistories = loadFundingHistories(exchanges)
//    printFundingHistories(fundingHistories)
}

fun createLedger(exchanges: List<Exchange>): Ledger {
    val tradeHistories = exchanges.loadTradeHistories()
        .flatMap { (exchange, userTrades) ->
            userTrades.map { exchange to it }
        }

    val fundingHistories = loadFundingHistories(exchanges)
        .flatMap { (exchange, fundingRecords) ->
            fundingRecords.map { exchange to it }
        }

    val poloniex = exchanges.first { it.name == "Poloniex" }
    val lendingHistory = File("poloniex_lending_history.csv")
        .asPoloniexLendingCsv()
        .toLendingEvents()
        .map { poloniex to it }

    val history = (tradeHistories + fundingHistories + lendingHistory).sortedBy { (_, item) ->
        when (item) {
            is FundingRecord -> item.date
            is UserTrade -> item.timestamp
            is PoloniexLendingEvent -> item.closed
            else -> error("Unknown item type: ${item.javaClass.simpleName}")
        }
    }

    printHistory(history).also { println() } // for debugging

    val external = listOf(
    )

    val ledger = Ledger(external)
    for ((exchange, item) in history) {
        ledger.process(exchange, item)
    }
    return ledger
}

fun printHistory(history: List<Pair<Exchange, Any>>) {
    history.forEach { (exchange, item) ->
        when (item) {
            is FundingRecord -> log(exchange, item.currency) { "${item.type} ${item.amount} ${item.currency} at ${item.date}" }
            is UserTrade -> log(exchange, item.currencyPair) { "${item.type} ${item.originalAmount} ${item.currencyPair} for ${item.price} fee ${item.feeAmount} ${item.feeCurrency} at ${item.timestamp}" }
            is PoloniexLendingEvent -> log(exchange, item.currency) { "LENDING ${item.amount} ${item.currency} earned ${item.earned} fee ${item.fee} at ${item.closed}" }
        }
    }
}

private suspend fun fetchAndSaveTradeHistories(exchanges: List<Exchange>) {
    exchanges
        .fetchTradeHistories()
        .also(::saveTradeHistories)
        .forEach { exchange, trades ->
            println()
            println("=== $exchange ===")
            trades.forEach(::println)
            println()
        }
}

private fun printTradeHistories(histories: Map<Exchange, List<UserTrade>>) {
    histories
        .forEach { exchange, history ->
            println()
            println("== $exchange ==")
            history.forEach(::println)
        }
}

private suspend fun fetchAndSaveFundingHistories(exchanges: List<Exchange>) {
    exchanges
        .filter { it.name != "Gemini" } // Gemini API does not support funding history :_(
        .fetchFundingHistories()
        .also(::saveFundingHistories)
        .forEach { exchange, records ->
            println()
            println("=== $exchange ===")
            records.forEach(::println)
            println()
        }
}

private fun loadFundingHistories(exchanges: List<Exchange>): Map<Exchange, List<FundingRecord>> {
    val gemini = exchanges.first { it.name == "Gemini" }
    return exchanges
        .filter { it.name != "Gemini" }
        .loadFundingHistories()
        .plus(gemini to File("gemini_transaction_history.csv").asGeminiCsv().toFundingHistory())
}

private fun printFundingHistories(histories: Map<Exchange, List<FundingRecord>>) {
    histories.forEach { exchange, records ->
        println()
        println("== $exchange ==")
        records.forEach(::println)
    }
}
