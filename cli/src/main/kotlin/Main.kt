import com.traviswyatt.exchanges.isNonZero
import com.traviswyatt.exchanges.name
import com.traviswyatt.exchanges.specifications
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.dto.account.AccountInfo

//    BinanceService().pairs.forEach { println(it) }

fun main(args: Array<String>) = runBlocking {
    specifications
        // List<ExchangeSpecification> → List<Exchange>
        .map { ExchangeFactory.INSTANCE.createExchange(it) }

        // List<Exchange> → List<Pair<Exchange, Deferred<AccountInfo>>>
        // asynchronously fetches account info associated with all exchanges
        .map { Pair(it, async { it.accountService.accountInfo }) }

        // List<Pair<Exchange, Deferred<AccountInfo>>> → List<Pair<Exchange, AccountInfo>>
        // joins the asynchronous jobs back together
        .map { (exchange, deferred) -> Pair(exchange, deferred.await()) }

        .forEach { (exchange, accountInfo) ->
            println("=== ${exchange.name} ===\n")
            printWallets(accountInfo)
            print("\n\n\n")
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
