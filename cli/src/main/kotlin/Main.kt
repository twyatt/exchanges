import com.traviswyatt.exchanges.*
import kotlinx.coroutines.experimental.runBlocking
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.dto.account.FundingRecord
import org.knowm.xchange.dto.trade.UserTrade
import java.io.File

private const val OFFLINE = false

fun main(args: Array<String>): Unit = runBlocking {
    if (OFFLINE) log("Offline mode.")

//    val csv = File("gemini_transaction_history.csv").asGeminiCsv()
//    csv.toFundingHistory().forEach(::println)

    val exchanges =
        specifications
            .map { it.apply { isShouldLoadRemoteMetaData = !OFFLINE } }
            .map { ExchangeFactory.INSTANCE.createExchange(it) }
    createLedger(exchanges).let(::println)

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

    val history = (tradeHistories + fundingHistories).sortedBy { (_, item) ->
        when (item) {
            is FundingRecord -> item.date
            is UserTrade -> item.timestamp
            else -> error("Unknown item type: ${item.javaClass.simpleName}")
        }
    }

    val ledger = Ledger()

    for ((_, item) in history) {
        when (item) {
            is FundingRecord -> ledger.add(item)
            is UserTrade -> ledger.add(item)
        }
    }

    return ledger
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
