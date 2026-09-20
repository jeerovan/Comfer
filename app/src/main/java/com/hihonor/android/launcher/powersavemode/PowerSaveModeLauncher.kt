package com.hihonor.android.launcher.powersavemode

import androidx.annotation.Keep
import com.jeerovan.comfer.HonorPowerSaveCompatibilityActivity

/**
 * Some Honor firmware bypasses the application's component factory and asks the
 * platform factory to load this exact name through Comfer's class loader.
 * Keep the no-argument class available in DEX; the inherited activity only exits
 * the malformed task. It implements no vendor behavior and exposes no component.
 */
@Keep
class PowerSaveModeLauncher : HonorPowerSaveCompatibilityActivity()
