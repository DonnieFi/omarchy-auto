package com.omarchy.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session

class OmarchySession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = OmarchyScreen(carContext)
}
