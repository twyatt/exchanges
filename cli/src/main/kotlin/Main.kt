import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.traviswyatt.exchanges.fetchTradeHistory
import com.traviswyatt.exchanges.isNonZero
import com.traviswyatt.exchanges.name
import com.traviswyatt.exchanges.specifications
import kotlinx.coroutines.experimental.runBlocking
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.account.AccountInfo
import org.knowm.xchange.dto.trade.UserTrades
import java.io.File

private val gson = GsonBuilder().setPrettyPrinting().create()

private fun log(message: String) = println("[${Thread.currentThread().name}] $message")

fun main(args: Array<String>) {
    val exchanges = specifications.map { ExchangeFactory.INSTANCE.createExchange(it) }

    val onlyGemini = exchanges.first { it.name == "Gemini" }
//    saveTradeHistories(onlyGemini)
    val histories = mapOf(onlyGemini.name to onlyGemini.loadTradeHistory())

    printTradeHistories(histories)
}

fun printTradeHistories(histories: Map<String, Map<CurrencyPair, UserTrades>>) {
    histories.forEach { (name, tradeHistory) ->
        log("=== $name ===")
        tradeHistory.forEach { pair, trades ->
            log("  \$\$\$ $pair \$\$\$")
            trades.trades.forEach { trade ->
                log("    $trade")
            }
        }
    }
}

fun fetchTradeHistories(exchanges: List<Exchange>): Map<String, Map<CurrencyPair, UserTrades>> {
    val jobs = exchanges.map { exchange ->
        log("Queueing ${exchange.name}")
        Pair(exchange.name, exchange.fetchTradeHistory())
    }

    return runBlocking {
        jobs.map { (name, deferred) ->
            log("Processing $name")
            val tradeHistory = deferred.await()
            val result = Pair(name, tradeHistory)
            log("Finished $name")
            result
        }
    }.toMap()
}

fun saveTradeHistories(tradeHistories: Map<CurrencyPair, UserTrades>) {
    tradeHistories.forEach { (name, history) ->
        val json = gson.toJson(history)
        val filename = "$name.json"
        log("Saving to $filename")
        File(filename).writeText(json)
    }
}

private fun printWallets(accountInfo: AccountInfo) {
    accountInfo.wallets
        .forEach { id, wallet ->
            log("Wallet [id=$id]")
            wallet.balances
                .filter { (_, balance) -> balance.available.isNonZero }
                .forEach { currency, balance -> log("  $currency: $balance") }
        }
}

fun Exchange.loadTradeHistory(): Map<CurrencyPair, UserTrades> {
    val file = File("$name.json")
    val json = file.readText()
    return Gson()
        .fromJson<Map<String, UserTrades>>(json)
        .map { (pair, trades) -> Pair(CurrencyPair(pair), trades) }
        .toMap()
}

inline fun <reified T> Gson.fromJson(json: String): T =
    this.fromJson<T>(json, object : TypeToken<T>() {}.type)
