package com.omarchy.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import com.omarchy.auto.core.SetupActivity
import com.omarchy.auto.core.diag.OaLog

/**
 * Best-effort bridge to the phone-only setup activity. Car hosts block or
 * silently ignore phone activities while driving, so this is only a
 * convenience: every screen that needs permissions also explains the phone
 * path in its own row.
 */
internal object CarPhoneSetup {
    private const val TAG = "CarPhoneSetup"

    fun open(carContext: CarContext) {
        OaLog.i(TAG, "Phone setup requested from the car")
        toast(carContext, "Opening Omarchy Auto setup on your phone")
        val intent = Intent(carContext, SetupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            carContext.startActivity(intent)
            return
        } catch (error: Exception) {
            OaLog.w(TAG, "Could not start phone setup from the car service", error)
        }
        try {
            carContext.startCarApp(intent)
        } catch (error: Exception) {
            OaLog.w(TAG, "Car host refused the phone setup activity", error)
            toast(carContext, "Open Omarchy Auto on your phone")
        }
    }

    private fun toast(carContext: CarContext, message: String) {
        try {
            CarToast.makeText(carContext, message, CarToast.LENGTH_LONG).show()
        } catch (error: Exception) {
            OaLog.w(TAG, "CarToast was unavailable", error)
        }
    }
}
