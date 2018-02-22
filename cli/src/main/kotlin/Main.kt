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

private fun log(message: String) = println("[${Thread.currentThread().name}] $message")

private val gson = GsonBuilder().setPrettyPrinting().create()

typealias ExchangeName = String

fun main(args: Array<String>) {
    specifications

        // List<ExchangeSpecification> → List<Exchange>
        .map { ExchangeFactory.INSTANCE.createExchange(it) }

        .filter { it.name == "Gemini" }

        // List<Exchange> → Map<ExchangeName, Map<CurrencyPair, UserTrades>>
        .let(::fetchTradeHistories).also(::saveTradeHistories)
//        .let(::loadTradeHistories)

        .also(::printTradeHistories)
}

fun saveTradeHistories(tradeHistories: Map<ExchangeName, Map<CurrencyPair, UserTrades>>) {
    tradeHistories
        .forEach { exchangeName, tradeHistory ->
            val json = gson.toJson(tradeHistory)
            val filename = "$exchangeName.json"
            log("Saving to $filename")
            File(filename).writeText(json)
        }
}

fun printTradeHistories(histories: Map<ExchangeName, Map<CurrencyPair, UserTrades>>) {
    histories
        .forEach { (name, tradeHistory) ->
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
    val jobs = exchanges
        .map { exchange ->
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

private fun printWallets(accountInfo: AccountInfo) {
    accountInfo.wallets
        .forEach { id, wallet ->
            log("Wallet [id=$id]")
            wallet.balances
                .filter { (_, balance) -> balance.available.isNonZero }
                .forEach { currency, balance -> log("  $currency: $balance") }
        }
}

fun loadTradeHistories(exchanges: List<Exchange>): Map<ExchangeName, Map<CurrencyPair, UserTrades>> {
    return exchanges
        // List<Exchange> → List<Pair<ExchangeName, Map<CurrencyPair, UserTrades>>>
        .map { exchange -> exchange.name to loadTradeHistory(exchange) }

        // List<Pair<ExchangeName, Map<CurrencyPair, UserTrades>>> → Map<ExchangeName, Map<CurrencyPair, UserTrades>>
        .toMap()
}

private fun loadTradeHistory(exchange: Exchange): Map<CurrencyPair, UserTrades> {
    return gson
        // Map<String, UserTrades>
        .fromJson<Map<String, UserTrades>>(File("${exchange.name}.json").readText())

        // Map<String, UserTrades> → List<Pair<CurrencyPair, UserTrades>>
        .map { (pair, trades) -> Pair(CurrencyPair(pair), trades) }

        // List<Pair<CurrencyPair, UserTrades>> → Map<CurrencyPair, UserTrades>
        .toMap()
}

inline fun <reified T> Gson.fromJson(json: String): T =
    this.fromJson<T>(json, object : TypeToken<T>() {}.type)
