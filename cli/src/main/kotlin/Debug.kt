import com.traviswyatt.exchanges.GeminiCsv
import com.traviswyatt.exchanges.isNonZero
import com.traviswyatt.exchanges.name
import org.knowm.xchange.Exchange
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.account.AccountInfo
import org.knowm.xchange.dto.account.FundingRecord
import org.knowm.xchange.dto.trade.UserTrades

internal fun log(message: String) = println("[${Thread.currentThread().name}] $message")

internal fun printGeminiCsv(csv: GeminiCsv) {
    val (header, rows) = csv
    println("=== Header ===")
    println(header)
    println()
    println("=== Rows ===")
    rows.forEach(::println)
}

internal fun printFundingHistory(fundingHistory: Map.Entry<Exchange, List<FundingRecord>>) {
    val (exchange, history) = fundingHistory
    println("=== ${exchange.name} ===")
    history.forEach(::println)
    println()
}

internal fun printTradeHistories(histories: Map<String, Map<CurrencyPair, UserTrades>>) {
    histories
        .forEach { (name, tradeHistory) ->
            log("=== $name Trade History ===")
            tradeHistory.forEach { pair, trades ->
                log("  \$\$\$ $pair \$\$\$")
                trades.trades.forEach { trade ->
                    log("    $trade")
                }
            }
        }
}

internal fun printWallets(accountInfo: AccountInfo) {
    accountInfo.wallets
        .forEach { id, wallet ->
            log("Wallet [id=$id]")
            wallet.balances
                .filter { (_, balance) -> balance.available.isNonZero }
                .forEach { currency, balance -> log("  $currency: $balance") }
        }
}
