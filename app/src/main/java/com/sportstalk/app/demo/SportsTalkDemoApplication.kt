package com.sportstalk.app.demo

import android.app.Application
import com.sportstalk.SportsTalkManager
import com.sportstalk.api.ChatApiService
import com.sportstalk.api.UsersApiService
import com.sportstalk.app.demo.presentation.rooms.rxjava.SelectRoomViewModel as SelectRoomViewModelRx
import com.sportstalk.app.demo.presentation.rooms.coroutine.SelectRoomViewModel as SelectRoomViewModelCoroutine
import com.sportstalk.app.demo.presentation.rooms.livedata.SelectRoomViewModel as SelectRoomViewModelLiveData
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

class SportsTalkDemoApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        // Instantiate
        SportsTalkManager.init(applicationContext)

        startKoin {
            androidContext(applicationContext)
            modules(
                module {
                    single { SportsTalkManager.instance }
                    single<UsersApiService> { get<SportsTalkManager>().usersApiService }
                    single<ChatApiService> { get<SportsTalkManager>().chatApiService }
                },
                module {
                    viewModel {
                        SelectRoomViewModelRx(
                            chatApiService = get()
                        )
                    }
                    viewModel {
                        SelectRoomViewModelCoroutine(
                            chatApiService = get()
                        )
                    }
                    viewModel {
                        SelectRoomViewModelLiveData(
                            chatApiService = get()
                        )
                    }
                }
            )
        }
    }

}