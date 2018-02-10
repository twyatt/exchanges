# `Specifications.kt` Example

```
package com.traviswyatt.exchanges

import org.knowm.xchange.binance.BinanceExchange
import org.knowm.xchange.gemini.v1.GeminiExchange
import org.knowm.xchange.poloniex.PoloniexExchange

val specifications = listOf(
    BinanceExchange().defaultExchangeSpecification.apply {
        userName = "USERNAME"
        apiKey = "API_KEY"
        secretKey = "SECRET"
    },

    PoloniexExchange().defaultExchangeSpecification.apply {
        userName = "USERNAME"
        apiKey = "API_KEY"
        secretKey = "SECRET"
    },

    GeminiExchange().defaultExchangeSpecification.apply {
        userName = "USERNAME"
        apiKey = "API_KEY"
        secretKey = "SECRET"
    }
)
```
