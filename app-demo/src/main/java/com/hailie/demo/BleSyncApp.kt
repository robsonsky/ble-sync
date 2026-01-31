package com.hailie.demo

import android.app.Application
import com.hailie.demo.di.fakesModule
import com.hailie.demo.di.portsModule
import com.hailie.demo.di.runtimeModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BleSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BleSyncApp)

            modules(
                portsModule,
                fakesModule,
                runtimeModule,
            )

//            Use the code below when code is production ready
//            if (BuildConfig.DEMO) {
//                modules(
//                    portsModule,
//                    fakesModule,
//                    runtimeModule
//                )
//            } else {
//                modules(
//                    portsModule,
//                    androidAdaptersModule,
//                    runtimeModule
//                )
//            }
        }
    }
}
