package com.traviswyatt.exchanges

import com.traviswyatt.exchanges.csv.PoloniexLendingEvent
import org.knowm.xchange.Exchange
import org.knowm.xchange.currency.Currency
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.Order
import org.knowm.xchange.dto.account.FundingRecord
import org.knowm.xchange.dto.account.FundingRecord.Type.DEPOSIT
import org.knowm.xchange.dto.account.FundingRecord.Type.WITHDRAWAL
import org.knowm.xchange.dto.trade.UserTrade
import java.math.BigDecimal
import java.util.*

typealias DateFilter = (date: Date) -> Boolean

data class Transaction(
    val currency: Currency,
    var amount: BigDecimal,
    val price: BigDecimal,
    val timestamp: Date,
    val pair: CurrencyPair
)

val Transaction.displayPrice: String get() = "$price $pair" + when {
    pair.base == Currency.USD -> {
        val p = BigDecimal.ONE / price
        " ($p ${pair.counter}/${pair.base}, \$${p * amount})"
    }
    pair.counter == Currency.USD -> " (\$${price * amount})"
    else -> ""
}

fun priceUsd(amount: BigDecimal, price: BigDecimal, pair: CurrencyPair): BigDecimal? = when {
    pair.base == Currency.USD -> BigDecimal.ONE / price * amount
    pair.counter == Currency.USD -> price * amount
    else -> null
}

data class Transfer(var amount: BigDecimal, val currency: Currency, val date: Date?)

class Funds(
    val exchange: Exchange,
    val currency: Currency,
    var funding: List<Transfer> = mutableListOf(),
    var error: BigDecimal = BigDecimal.ZERO
) {

    val balance: BigDecimal get() = funding.sumBy { it.amount }

    fun deposit(amount: BigDecimal, date: Date?) {
        funding += Transfer(amount, currency, date)
        log(exchange, currency) { "DEPOSITED $amount $currency" }
    }

    fun add(funds: Funds) {
        require(currency == funds.currency) { "Cannot add funds when currencies differ: $currency != ${funds.currency}" }
        funding += funds.funding
        error += funds.error
    }

    fun take(amount: BigDecimal): Funds {
        val taken = mutableListOf<Transfer>()

        var remainingWithdrawal = amount
        while (remainingWithdrawal.isNonZero) {
            val deposit = funding.firstOrNull() ?: break

            if (deposit.amount <= remainingWithdrawal) { // e.g. deposit $100, remainingWithdrawal $200
                log(exchange, currency) { "deposit.amount <= remainingWithdrawal → ${deposit.amount} <= $remainingWithdrawal" }
                remainingWithdrawal -= deposit.amount
                funding -= deposit
                taken += deposit
            } else { // e.g. deposit $200, remainingWithdrawal $100
                log(exchange, currency) { "deposit.amount > remainingWithdrawal → ${deposit.amount} > $remainingWithdrawal" }
                deposit.amount -= remainingWithdrawal
                taken += deposit.copy(amount = remainingWithdrawal)
                break
            }
        }

        return Funds(exchange, currency, taken)
    }
}

class Monies(
    val exchange: Exchange,
    val funds: HashMap<Currency, Funds> = hashMapOf(),
    val transactions: HashMap<Currency, Transactions> = hashMapOf()
) {

    override fun toString(): String =
        "funds = $fundsBalance, transactions = $transactionsBalance, error = $errorFundsBalance"

    val errorFunds: HashMap<Currency, Funds> = hashMapOf()
    val errorFundsBalance
        get() = errorFunds.mapValues { entry -> entry.value.balance }

    val fundsBalance: Map<Currency, BigDecimal>
        get() = funds.mapValues { entry -> entry.value.balance }

    val transactionsBalance: Map<Currency, BigDecimal>
        get() = transactions.mapValues { entry -> entry.value.balance }

    /** Current combined balance of [funds] and [transactions]. */
    val balance: Map<Currency, BigDecimal>
        get() = funds.mapValues { it.value.balance }
            .mergeReduce(transactions.mapValues { it.value.balance }) { funds, transactions ->
                funds + transactions
            }

    infix fun BigDecimal.diff(other: BigDecimal): BigDecimal = this.positive - other.positive

    /**
     * Takes specified [amount] of [currency] from funds and then remaining amount from
     * transactions.
     *
     * @return a new [Monies] of the funds and transactions removed.
     */
    fun take(currency: Currency, amount: BigDecimal): Monies {
        log(exchange, currency) { "DEBUG BALANCE funds = $fundsBalance, transactions = $transactionsBalance" }

        val fundsTaken = fundsFor(currency).take(amount)
        val remainingAmount = amount - fundsTaken.balance

        val transactionsTaken: HashMap<Currency, Transactions> = if (remainingAmount.isNonZero) {
            val transactionsTaken = transactions[currency]?.take(remainingAmount)
            log(exchange, currency) { "transactionsTaken = ${transactionsTaken?.balance}" }
            if (transactionsTaken == null || (transactionsTaken.balance diff remainingAmount) > BigDecimal("0.000001")) {
                errorFundsFor(currency).deposit(remainingAmount, null)
                fundsTaken.deposit(remainingAmount, null)
                log(exchange, currency) { "!!! DEBUG NEGATIVE BALANCE funds = $errorFundsBalance" }
            }
            if (transactionsTaken == null) {
                hashMapOf(currency to Transactions(exchange, currency))
            } else {
                hashMapOf(currency to transactionsTaken)
            }
        } else {
            hashMapOf()
        }

        return Monies(
            exchange,
            hashMapOf(currency to fundsTaken),
            transactionsTaken
        )
    }

    fun fundsFor(currency: Currency) =
        funds[currency] ?: Funds(exchange, currency).also { funds[currency] = it }

    fun errorFundsFor(currency: Currency) =
        errorFunds[currency] ?: Funds(exchange, currency).also { errorFunds[currency] = it }

    fun transactionsFor(currency: Currency): Transactions =
        transactions[currency] ?: Transactions(exchange, currency).also { transactions[currency] = it }

    fun add(fundingRecord: FundingRecord) {
        with(fundingRecord) {
            fundsFor(currency).deposit(amount - (fee?.positive ?: BigDecimal.ZERO), fundingRecord.date)
        }
    }

    fun add(event: PoloniexLendingEvent) {
        with(event) {
            fundsFor(currency).deposit(earned - fee.positive, null)
        }
    }

    fun add(transaction: Transaction) {
        transactionsFor(transaction.currency).add(transaction)
    }

    fun add(transactions: List<Transaction>) {
        transactions.forEach { transactionsFor(it.currency).add(it) }
    }
}

class Transactions(
    private val exchange: Exchange,
    private val currency: Currency,
    val transactions: MutableList<Transaction> = mutableListOf()
) {

    val balance: BigDecimal get() = transactions.sumBy { it.amount }

    /**
     * Removes the specified amount of currency from this collection of [Transactions]. Currency is
     * removed oldest to newest.
     *
     * # Example
     *
     * ```
     * val transactions = Transactions(Transaction(amount = 100), Transaction(amount = 200))
     * val newTransactions = transactions.take(amount = 150)
     * ```
     *
     * They will now contain:
     *
     * `transactions`
     * - `Transaction(amount = 100)`
     * - `Transaction(amount = 150)`
     *
     * `newTransactions`
     * - `Transaction(amount = 100)`
     * - `Transaction(amount = 50)`
     *
     * @return a new [Transactions] that contains the amount of currency removed from this [Transactions].
     */
    fun take(amount: BigDecimal): Transactions {
        val taken = mutableListOf<Transaction>()

        transactions.sortBy { it.timestamp }
        var remainingAmount = amount
        while (remainingAmount.isNonZero) {
            val transaction = transactions.firstOrNull() ?: break // error("No transactions found.")

            if (transaction.amount <= remainingAmount) { // e.g. transaction $100, remaining $200
                log(exchange, currency) { "transaction.amount <= remainingAmount → ${transaction.amount} <= $remainingAmount" }
                remainingAmount -= transaction.amount
                transactions -= transaction
                taken += transaction
            } else { // e.g. transaction $200, remaining $100
                log(exchange, currency) { "transaction.amount > remainingAmount → ${transaction.amount} > $remainingAmount" }
                transaction.amount -= remainingAmount
                taken += transaction.copy(amount = remainingAmount)
                break
            }
        }

        return Transactions(exchange, currency, taken)
    }

    fun add(t: Transaction) {
        transactions += t
    }

    fun add(other: Transactions) {
        transactions += other.transactions
    }
}

class Ledger(externalAddresses: List<String> = listOf()) {

    private val _externalAddresses: List<String> = externalAddresses.map { it.toLowerCase() }

    data class Event(
        val exchange: Exchange,
        val sources: List<Transaction>,
        val transaction: Transaction
    )

    data class WithdrawEvent(
        val exchange: Exchange,
        val sources: Pair<List<Transfer>?, List<Transaction>?>,
        val withdraw: Transfer,
        val destination: String?
    )

    val events = mutableListOf<Event>()
    val withdrawEvents = mutableListOf<WithdrawEvent>()

    fun isExternal(address: String) = _externalAddresses.contains(address.toLowerCase())

    fun print(dateFilter: DateFilter = defaultDateFilter) {
        monies.forEach { (exchange, monies) ->
            println()
            println("=== ${exchange.name} ===")
            println()
            printBalance(monies)
            println()
            printFunds(monies)
            println()
            printTransactions(monies)
            println()
            printError(monies)
        }

        println()
        printEvents(dateFilter)

        println()
        printWithdrawalEvents(dateFilter)

        println()
        printWithdrawalEventsCsv(dateFilter)
    }

    private val defaultDateFilter = { _: Date -> true }

    private fun printEvents(dateFilter: ((date: Date) -> Boolean) = defaultDateFilter) {
        events
            .filter { dateFilter(it.transaction.timestamp) }
            .forEach { event ->
                println("${event.exchange} EVENT ${event.transaction.amount} ${event.transaction.currency} @ ${event.transaction.displayPrice} at ${event.transaction.timestamp}")
                event.sources.forEach { sourceTransaction ->
                    println("  using ${sourceTransaction.amount} ${sourceTransaction.currency} @ ${sourceTransaction.displayPrice} at ${sourceTransaction.timestamp}")
                    if (sourceTransaction.pair == event.transaction.pair) {
                        val percentChange =
                            (event.transaction.price / sourceTransaction.price) * BigDecimal("100")
                        println("    $percentChange %")
                    }
                }
            }
    }

    private fun printWithdrawalEvents(dateFilter: DateFilter = defaultDateFilter) {
        withdrawEvents
            .filter { it.withdraw.date != null && dateFilter(it.withdraw.date) }
            .forEach { event ->
                println("${event.exchange} WITHDRAWAL EVENT ${event.withdraw.amount} ${event.withdraw.currency} at ${event.withdraw.date}")
                val (deposits, transactions) = event.sources

                deposits
                    ?.filter { it.date != null }
                    ?.forEach { deposit ->
                        println("  using deposit of ${deposit.amount} ${deposit.currency} at ${deposit.date}")
                    }
                transactions
                    ?.filter { it.amount > BigDecimal("0.0000001") }
                    ?.forEach { transaction ->
                        println("  using transaction of ${transaction.amount} ${transaction.currency} @ ${transaction.displayPrice} at ${transaction.timestamp}")
                    }
            }
    }

    private val CurrencyPair.inv get() = toString().split("/").reversed().joinToString("/")

    private fun printWithdrawalEventsCsv(dateFilter: DateFilter = defaultDateFilter) {
        println("Exchange,ID,Amount,Currency,EntryPrice,ExitPrice,EntryPriceUsd,ExitPriceUsd,EntryDate,ExitDate,Source,DestinationAddress")
        withdrawEvents
            .filter { it.withdraw.date != null && dateFilter(it.withdraw.date) }
            .forEachIndexed { index, event ->
                val (deposits, transactions) = event.sources

                deposits
                    ?.filter { it.date != null }
                    ?.forEach { deposit ->
                        require(event.withdraw.currency == deposit.currency)

                        val line = listOf(
                            event.exchange.name, // Exchange
                            "${index+1}", // ID
                            "${deposit.amount}", // Amount
                            "${deposit.currency}", // Currency
                            "", // EntryPrice
                            "", // ExitPrice
                            "", // EntryPriceUsd
                            "", // ExitPriceUsd
                            "${deposit.date}", // EntryDate
                            "${event.withdraw.date}", // ExitDate
                            "Deposit", // Source
                            event.destination ?: "" // DestinationAddress
                        ).joinToString(",")

                        println(line)
                    }
                transactions
                    ?.filter { it.amount > BigDecimal("0.0000001") }
                    ?.forEach { transaction ->
                        require(event.withdraw.currency == transaction.currency)

                        val line = listOf(
                            event.exchange.name, // Exchange
                            "${index+1}", // ID
                            "${transaction.amount}", // Amount
                            "${transaction.currency}", // Currency
                            "${transaction.price} ${transaction.pair.inv}", // EntryPrice
                            "", // ExitPrice
                            priceUsd(transaction.amount, transaction.price, transaction.pair) ?: "", // EntryPriceUsd
                            "", // ExitPriceUsd
                            "${transaction.timestamp}", // EntryDate
                            "${event.withdraw.date}", // ExitDate
                            "Transaction", // Source
                            event.destination ?: "" // DestinationAddress
                        ).joinToString(",")

                        println(line)
                    }
            }
    }

    private fun printError(monies: Monies) {
        println("  : ERROR :")
        monies.errorFundsBalance.forEach { (currency, balance) ->
            println("  $balance $currency")
        }
    }

    private fun printTransactions(monies: Monies) {
        println("  : TRANSACTIONS :")
        monies.transactionsBalance.forEach { (currency, balance) ->
            println("  $balance $currency")
        }
    }

    private fun printFunds(monies: Monies) {
        println("  : FUNDS :")
        monies.fundsBalance.forEach { (currency, balance) ->
            println("  $balance $currency")
        }
    }

    private fun printBalance(monies: Monies) {
        println("  : BALANCE :")
        monies.balance
            .filter { (_, balance) -> balance.isNonZero }
            .forEach { (currency, balance) ->
                println("  $balance $currency")
            }
    }

    override fun toString(): String =
        monies.map { (exchange, monies) -> "${exchange.name} = $monies" }.joinToString(", ")

    private val monies = hashMapOf<Exchange, Monies>()

    fun process(exchange: Exchange, item: Any) {
        val m = monies[exchange] ?: Monies(exchange).also { monies[exchange] = it }
        when (item) {
            is FundingRecord -> process(exchange, item, m)
            is UserTrade -> process(exchange, item, m)
            is PoloniexLendingEvent -> process(exchange, item, m)
            else -> error("Unknown item type: ${item.javaClass.simpleName}")
        }
    }

    private fun process(exchange: Exchange, event: PoloniexLendingEvent, monies: Monies) {
        log(exchange, event.currency) { "LENDING PROFIT ${event.earned} ${event.currency} fee ${event.fee.positive} at ${event.closed}" }
        monies.add(event)
        log(exchange, event.currency) { "NEW TOTAL BALANCE ${monies.balance} (funds=${monies.fundsBalance}, transactions=${monies.transactionsBalance})" }
    }

    private fun process(exchange: Exchange, fundingRecord: FundingRecord, monies: Monies) {
        val address = if (fundingRecord.address.isNullOrBlank()) "?" else fundingRecord.address
        log(exchange, fundingRecord.currency) { "${fundingRecord.type} ${fundingRecord.amount} ${fundingRecord.currency} destination $address at ${fundingRecord.date}" }

        if (fundingRecord.amount != null) {
            when (fundingRecord.type) {
                DEPOSIT -> {
                    monies.add(fundingRecord)
                    log(exchange, fundingRecord.currency) { "NEW TOTAL BALANCE ${monies.balance} (funds=${monies.fundsBalance}, transactions=${monies.transactionsBalance})" }
                }
                WITHDRAWAL -> {
                    val withdrawal = monies.take(fundingRecord.currency, fundingRecord.amount)
                    if (isExternal(address)) {
                        val sources = withdrawal.funds[fundingRecord.currency]?.funding to withdrawal.transactions[fundingRecord.currency]?.transactions
                        withdrawEvents += WithdrawEvent(exchange, sources, Transfer(fundingRecord.amount, fundingRecord.currency, fundingRecord.date), fundingRecord.address)
                        println("!!! DEBUG EXTERNAL WITHDRAWAL ${fundingRecord.currency} ${fundingRecord.amount} to ${fundingRecord.address}")
                    }
                    // FIXME What do we do w/ withdrawal? (right now we're just "taking" and discarding)
                    log(exchange, fundingRecord.currency) { "WITHDRAWAL ${withdrawal.balance} at ${fundingRecord.date}" }
                }
                else -> error("Unknown record type: ${fundingRecord.type}")
            }
        } else {
            log(exchange, fundingRecord.currency) { "!!! DEBUG Invalid record (null amount), skipping $fundingRecord" }
        }
    }

    private data class ProcessOrder(
        val takeCurrency: Currency,
        val takeAmount: BigDecimal,
        val addCurrency: Currency,
        val addAmount: BigDecimal
    )

    private fun process(exchange: Exchange, userTrade: UserTrade, monies: Monies) {
        val baseCurrency = userTrade.currencyPair.base // e.g. XRP/___
        val baseAmount = userTrade.originalAmount

        val counterCurrency = userTrade.currencyPair.counter // e.g. ___/XRP
        val counterAmount = baseAmount.multiply(userTrade.price)

        val feeCurrency = userTrade.feeCurrency
        val feeAmount = userTrade.feeAmount.positive

        val order: ProcessOrder = when (userTrade.type) {
            Order.OrderType.ASK -> { // e.g. ASK XRP/BTC = Sell XRP (base) for BTC (counter)
                log(exchange, userTrade.currencyPair) { "SELL $baseAmount $baseCurrency for $counterAmount $counterCurrency priced at ${userTrade.price} ${userTrade.currencyPair} with fee of $feeAmount $feeCurrency" }

                when (feeCurrency) {
                    counterCurrency -> ProcessOrder(baseCurrency, baseAmount, counterCurrency, counterAmount - feeAmount.positive)
                    baseCurrency -> ProcessOrder(baseCurrency, baseAmount + feeAmount.positive, counterCurrency, counterAmount)
                    else -> error("Fee currency $feeCurrency does not match base currency $baseCurrency or counter currency $counterCurrency")
                }
            }

            Order.OrderType.BID -> { // e.g. BID XRP/BTC = Buy XRP (base) using BTC (counter)
                log(exchange, userTrade.currencyPair) { "BUY $baseAmount $baseCurrency using $counterAmount $counterCurrency priced at ${userTrade.price} ${userTrade.currencyPair} with fee of $feeAmount $feeCurrency" }

                when (feeCurrency) {
                    counterCurrency -> ProcessOrder(counterCurrency, counterAmount + feeAmount.positive, baseCurrency, baseAmount)
                    baseCurrency -> ProcessOrder(counterCurrency, counterAmount, baseCurrency, baseAmount - feeAmount.positive)
                    else -> error("Fee currency $feeCurrency does not match base currency $baseCurrency or counter currency $counterCurrency")
                }
            }

            else -> error("Unknown order type: ${userTrade.type}")
        }


        val newTransaction = Transaction(
            order.addCurrency,
            order.addAmount,
            userTrade.price,
            userTrade.timestamp,
            userTrade.currencyPair
        )
        val sourceMonies = monies.take(order.takeCurrency, order.takeAmount)
        monies.add(newTransaction)

        val eventTransactions = sourceMonies.transactions.flatMap { it.value.transactions }
        if (eventTransactions.isNotEmpty()) {
            events += Event(exchange, eventTransactions, newTransaction)
        }

        log(exchange, userTrade.currencyPair) { "TRANSACTIONS BALANCE ${monies.transactionsBalance}" }
    }
}

private inline fun <T> Iterable<T>.sumBy(selector: (T) -> BigDecimal): BigDecimal {
    var sum: BigDecimal = BigDecimal.ZERO
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

/** Force [BigDecimal] to be a positive value. */
private val BigDecimal.positive: BigDecimal get() = if (this < BigDecimal.ZERO) negate() else this
