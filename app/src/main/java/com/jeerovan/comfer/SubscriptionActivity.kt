package com.jeerovan.comfer

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.getValue
import androidx.core.net.toUri
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class SubscriptionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Only set colors for Android 14 and below to avoid deprecation warnings
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        // Handle display cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContent {
            ComferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    SubscriptionScreen({})
                }
            }
        }
    }
}
@Composable
fun SubscriptionScreen(
    onDismiss: () -> Unit
) {
    var offerings by remember { mutableStateOf<Offerings?>(null) }
    var customerInfo by remember { mutableStateOf<CustomerInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var yearlyPackage by remember { mutableStateOf<Package?>(null) }
    var isSubscribed by remember { mutableStateOf(false) }

    // Check subscription status on launch
    LaunchedEffect(Unit) {
        try {
            // Wait up to 10 seconds for SDK initialization
            withTimeout(10_000L) {
                while (!Purchases.isConfigured) {
                    delay(50)
                }
            }

            Purchases.sharedInstance.getCustomerInfoWith(
                onError = { error ->
                    isLoading = false
                    errorMessage = error.message
                },
                onSuccess = { info ->
                    customerInfo = info
                    isSubscribed = info.entitlements.active.isNotEmpty()

                    if (!isSubscribed) {
                        Purchases.sharedInstance.getOfferingsWith(
                            onError = { error ->
                                isLoading = false
                                errorMessage = error.message
                            },
                            onSuccess = { fetchedOfferings ->
                                offerings = fetchedOfferings
                                yearlyPackage = fetchedOfferings.current?.availablePackages?.find {
                                    it.packageType == PackageType.ANNUAL
                                }
                                isLoading = false
                            }
                        )
                    } else {
                        isLoading = false
                    }
                }
            )
        } catch (e: TimeoutCancellationException) {
            isLoading = false
            errorMessage = "Unable to load subscription information. Please check your connection and try again."
            // Optionally log this error to your crash reporting service
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Failed to load subscription",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            isSubscribed -> {
                SubscribedContent(
                    customerInfo = customerInfo!!,
                    onDismiss = onDismiss
                )
            }

            yearlyPackage != null -> {
                SubscriptionContent(
                    yearlyPackage = yearlyPackage!!,
                    onDismiss = onDismiss,
                    onSubscriptionSuccess = { updatedCustomerInfo ->
                        customerInfo = updatedCustomerInfo
                        isSubscribed = updatedCustomerInfo.entitlements.active.isNotEmpty()
                    },
                    onRestoreSuccess = { updatedCustomerInfo ->
                        customerInfo = updatedCustomerInfo
                        isSubscribed = updatedCustomerInfo.entitlements.active.isNotEmpty()
                    }
                )
            }

            else -> {
                Text(
                    text = "No subscription available",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun SubscribedContent(
    customerInfo: CustomerInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activeEntitlement = customerInfo.entitlements.active.values.firstOrNull()
    val expirationDate = activeEntitlement?.expirationDate

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success Icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Active Badge
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = "ACTIVE SUBSCRIPTION",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Subscription info
        expirationDate?.let { date ->
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            Text(
                text = if (activeEntitlement.willRenew) {
                    "Renews on ${dateFormat.format(date)}"
                } else {
                    "Expires on ${dateFormat.format(date)}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Benefits Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp,color=MaterialTheme.colorScheme.outline,RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                BenefitItem("Set your own dynamic wallpapers")
                BenefitItem("Set your favorite widgets on home screen")
                BenefitItem("Customize default widgets on home screen")
                BenefitItem("Magic gestures to open apps quickly")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Manage Subscription Button
        Button(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = "https://play.google.com/store/account/subscriptions".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        "Unable to open subscription settings",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(width = 1.dp,color=MaterialTheme.colorScheme.primary,RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Manage Subscription",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SubscriptionContent(
    yearlyPackage: Package,
    onDismiss: () -> Unit,
    onSubscriptionSuccess: (CustomerInfo) -> Unit,
    onRestoreSuccess: (CustomerInfo) -> Unit
) {
    val context = LocalContext.current
    var isPurchasing by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    val product = yearlyPackage.product

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = "Pro Access",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Price
        Text(
            text = product.price.formatted,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "/year",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Subscription Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                BenefitItem("Set your own dynamic wallpapers")
                BenefitItem("Set your favorite widgets on home screen")
                BenefitItem("Customize default widgets on home screen")
                BenefitItem("Magic gestures to open apps quickly")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Purchase button
        Button(
            onClick = {
                isPurchasing = true
                Purchases.sharedInstance.purchaseWith(
                    PurchaseParams.Builder(context as Activity, yearlyPackage).build(),
                    onError = { error, userCancelled ->
                        isPurchasing = false
                        if (!userCancelled) {
                            Toast.makeText(
                                context,
                                "Purchase failed: ${error.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onSuccess = { _, customerInfo ->
                        isPurchasing = false
                        Toast.makeText(
                            context,
                            "Successfully subscribed!",
                            Toast.LENGTH_SHORT
                        ).show()
                        onSubscriptionSuccess(customerInfo)
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(width = 1.dp,color = MaterialTheme.colorScheme.primary,RoundedCornerShape(16.dp)),
            enabled = !isPurchasing && !isRestoring,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isPurchasing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = "Subscribe Now",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Restore purchases button
        TextButton(
            onClick = {
                isRestoring = true
                Purchases.sharedInstance.restorePurchasesWith(
                    onError = { error ->
                        isRestoring = false
                        Toast.makeText(
                            context,
                            "Restore failed: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onSuccess = { customerInfo ->
                        isRestoring = false
                        if (customerInfo.entitlements.active.isNotEmpty()) {
                            Toast.makeText(
                                context,
                                "Purchases restored successfully!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onRestoreSuccess(customerInfo)
                        } else {
                            Toast.makeText(
                                context,
                                "No purchases found to restore",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            },
            enabled = !isPurchasing && !isRestoring
        ) {
            if (isRestoring) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = "Restore Purchase",
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BenefitItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
