import com.traviswyatt.exchanges.fetchTradeHistories
import com.traviswyatt.exchanges.saveTradeHistories
import com.traviswyatt.exchanges.specifications
import kotlinx.coroutines.experimental.runBlocking
import org.knowm.xchange.ExchangeFactory

private const val OFFLINE = false

fun main(args: Array<String>) = runBlocking {
    if (OFFLINE) log("Offline mode.")

//    val csv = File("gemini_transaction_history.csv").asGeminiCsv()
//    csv.toFundingHistory().forEach(::println)

    val exchanges =
        specifications
            .map { it.apply { isShouldLoadRemoteMetaData = !OFFLINE } }
            .map { ExchangeFactory.INSTANCE.createExchange(it) }
//            .filter { it.name == "Poloniex" }

//    val fundingHistories = exchanges.fetchFundingHistories()
//    fundingHistories.forEach(::printFundingHistory)

    exchanges.fetchTradeHistories().also(::saveTradeHistories).forEach { exchange, trades ->
        println("=== $exchange ===")
        trades.forEach(::println)
        println()
    }

//    val tradeHistories = if (!OFFLINE) {
//        fetchTradeHistories(exchanges).also(::saveTradeHistories)
//    } else {
//        loadTradeHistories(exchanges)
//    }
//    printTradeHistories(tradeHistories)
}

