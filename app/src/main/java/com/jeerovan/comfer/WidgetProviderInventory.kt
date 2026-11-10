package com.jeerovan.comfer

import android.appwidget.AppWidgetProviderInfo
import kotlinx.coroutines.CancellationException

/** A successful service inventory, including providers omitted from the picker by policy. */
data class WidgetProviderInventory(
    val providers: List<AppWidgetProviderInfo>,
    val groups: List<WidgetProviderGroup>,
)

/** null means unavailable, never evidence that an installed/saved widget was removed. */
internal fun readWidgetProviderInventory(
    queryProviders: () -> List<AppWidgetProviderInfo>,
    loadLabel: (String) -> String,
): WidgetProviderInventory? = try {
    val providers = queryProviders()
    var labelsOutOfMemory = false
    val groups = providers.filterNot {
        WidgetInflationGuard.isKnownUnsafe(it.provider.flattenToShortString())
    }.groupBy { it.provider.packageName }.map { (packageName, entries) ->
        val label = if (labelsOutOfMemory) packageName else try {
            loadLabel(packageName)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: OutOfMemoryError) {
            // Do not keep opening package resources during this low-memory refresh.
            labelsOutOfMemory = true
            packageName
        } catch (_: Exception) {
            // Metadata may be temporarily inaccessible; the service still lists the provider.
            packageName
        }
        WidgetProviderGroup(label, packageName, entries)
    }
    WidgetProviderInventory(providers, groups)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: OutOfMemoryError) {
    null
} catch (_: Exception) {
    null
}
