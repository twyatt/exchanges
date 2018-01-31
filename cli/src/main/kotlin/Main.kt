import com.traviswyatt.exchanges.isNonZero
import com.traviswyatt.exchanges.specifications
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.dto.account.AccountInfo

fun main(args: Array<String>) {
//    BinanceService().pairs.forEach { println(it) }

    val exchanges = specifications.map { ExchangeFactory.INSTANCE.createExchange(it) }

    val jobs = exchanges.map {
        val deferred = async { it.accountService.accountInfo }
        Pair(it, deferred)
    }
    runBlocking {
        jobs
            .map { (exchange, deferred) -> Pair(exchange, deferred.await()) }
            .forEach { (exchange, accountInfo) ->
                println("=== ${exchange.name} ===\n")
                printWallets(accountInfo)
                repeat(3) { println() }
            }
    }
}

private fun printWallets(accountInfo: AccountInfo) {
    accountInfo.wallets
        .forEach { id, wallet ->
            println("Wallet [id=$id]")
            wallet.balances
                .filter { (_, balance) -> balance.available.isNonZero }
                .forEach { currency, balance -> println("  $currency: $balance") }
        }
}

private val Exchange.name
    get() = exchangeSpecification.exchangeName