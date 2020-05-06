package com.sportstalk.app.demo

import android.app.Application
import com.sportstalk.SportsTalkManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class SportsTalkDemoApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(applicationContext)
            modules(
                module {
                    single { SportsTalkManager.init(applicationContext) }
                },
                module {

                }
            )
        }
    }

}