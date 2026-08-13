package com.wasl.app

import android.app.Application
import com.wasl.app.data.WaslRepository
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase

class WaslApplication : Application() {
    private val database: WaslDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WaslDatabase.create(this)
    }

    val repository: WaslRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomWaslRepository(database)
    }
}
