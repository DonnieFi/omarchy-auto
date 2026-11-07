package com.omarchy.auto

import android.content.Context
import com.omarchy.auto.core.DistractionLock
import com.omarchy.auto.core.diag.OaLog
import java.lang.reflect.Proxy

/**
 * Android Automotive OS reports parked versus driving through
 * android.car.drivingstate.CarUxRestrictionsManager. That class is not in the
 * Car App Library 1.7 artifact and is absent on a phone running Android Auto.
 * If it cannot be registered, the distraction lock stays at UNKNOWN, which
 * keeps distracting features locked. Speed is never consulted.
 *
 * The platform method is unregisterListener() with no argument (one listener
 * per manager). Some builds take the listener instead. Both are attempted.
 * A generation token drops callbacks that arrive after stop().
 */
internal object AaosHostUx {
    private const val TAG = "AaosHostUx"
    private var car: Any? = null
    private var manager: Any? = null
    private var listener: Any? = null
    private var listenerType: Class<*>? = null
    private var generation: Int = 0

    fun start(context: Context, onUx: (DistractionLock.HostUx) -> Unit): Boolean {
        stop()
        val token = generation
        return try {
            val carClass = Class.forName("android.car.Car")
            val created = carClass.getMethod("createCar", Context::class.java)
                .invoke(null, context.applicationContext) ?: return false
            car = created
            val serviceName = carClass.getField("CAR_UX_RESTRICTION_SERVICE").get(null) as String
            val mgr = carClass.getMethod("getCarManager", String::class.java)
                .invoke(created, serviceName)
            if (mgr == null) {
                if (token == generation) stop()
                return false
            }
            manager = mgr
            val type = Class.forName(
                "android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener",
            )
            listenerType = type
            val requires = Class.forName("android.car.drivingstate.CarUxRestrictions")
                .getMethod("isRequiresDistractionOptimization")
            val proxy = Proxy.newProxyInstance(
                type.classLoader,
                arrayOf(type),
            ) { _, method, args ->
                if (method.name == "onUxRestrictionsChanged" &&
                    token == generation &&
                    !args.isNullOrEmpty()
                ) {
                    onUx(uxOf(requires.invoke(args[0]) as Boolean))
                }
                null
            }
            listener = proxy
            mgr.javaClass.getMethod("registerListener", type).invoke(mgr, proxy)
            val current = mgr.javaClass.getMethod("getCurrentCarUxRestrictions").invoke(mgr)
            if (current != null && token == generation) {
                onUx(uxOf(requires.invoke(current) as Boolean))
            }
            token == generation
        } catch (error: Throwable) {
            OaLog.w(TAG, "Host UX restrictions are unavailable; distractions stay locked", error)
            if (token == generation) stop()
            false
        }
    }

    fun stop() {
        generation++
        val mgr = manager
        val proxy = listener
        val type = listenerType
        listener = null
        listenerType = null
        manager = null
        if (mgr != null) {
            var unregistered = false
            if (proxy != null && type != null) {
                try {
                    mgr.javaClass.getMethod("unregisterListener", type).invoke(mgr, proxy)
                    unregistered = true
                } catch (_: Throwable) {
                    // This platform uses the no-argument form.
                }
            }
            if (!unregistered) {
                try {
                    mgr.javaClass.getMethod("unregisterListener").invoke(mgr)
                } catch (_: Throwable) {
                    // Already unregistered, or the class was never loaded.
                }
            }
        }
        val created = car
        car = null
        if (created != null) {
            try {
                created.javaClass.getMethod("disconnect").invoke(created)
            } catch (_: Throwable) {
                // Already disconnected.
            }
        }
    }

    private fun uxOf(requiresDistractionOptimization: Boolean): DistractionLock.HostUx =
        if (requiresDistractionOptimization) {
            DistractionLock.HostUx.DRIVING
        } else {
            DistractionLock.HostUx.PARKED
        }
}
