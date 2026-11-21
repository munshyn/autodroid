package com.husny.autodroid.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point Android Auto connects to when the car screen opens Autodroid.
 */
class AutodroidCarAppService : CarAppService() {

    // Personal-use app: accept any Android Auto host (real car or the Desktop Head Unit).
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onCreateSession(): Session = AutodroidSession()
}
