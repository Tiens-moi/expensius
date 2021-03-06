/*
 * Copyright (C) 2016 Mantas Varnagiris.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.mvcoding.expensius.firebase.model

import com.mvcoding.expensius.firebase.defaultCurrency
import com.mvcoding.expensius.model.Currency
import com.mvcoding.expensius.model.ReportGroup
import com.mvcoding.expensius.model.ReportPeriod
import com.mvcoding.expensius.model.Settings
import com.mvcoding.expensius.model.SubscriptionType
import com.mvcoding.expensius.model.SubscriptionType.FREE

data class FirebaseSettings(val mainCurrency: String? = null, val subscriptionType: String? = null) {

    fun toSettings() = Settings(
            mainCurrency?.let { Currency(it) } ?: defaultCurrency(),
            ReportPeriod.MONTH,
            ReportGroup.DAY,
            subscriptionType?.let { SubscriptionType.valueOf(it) } ?: FREE)
}