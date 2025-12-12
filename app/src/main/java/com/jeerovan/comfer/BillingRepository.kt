package com.jeerovan.comfer

import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Singleton: Created once in Application or via DI (Hilt/Koin)
object BillingRepository {

    private val _customerInfo = MutableStateFlow<CustomerInfo?>(null)
    val customerInfo: StateFlow<CustomerInfo?> = _customerInfo.asStateFlow()

    init {
        // 1. Set the global listener ONCE here
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { info ->
                _customerInfo.value = info
            }

        // 2. Fetch initial state immediately
        refreshSubscription()
    }

    fun refreshSubscription() {
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { /* Log error, but don't clear state blindly */ },
            onSuccess = { info -> _customerInfo.value = info }
        )
    }
}
