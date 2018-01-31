package com.traviswyatt.exchanges

import java.math.BigDecimal

val BigDecimal.isZero: Boolean
    get() = this.compareTo(BigDecimal.ZERO) == 0

val BigDecimal.isNonZero: Boolean
    get() = !isZero