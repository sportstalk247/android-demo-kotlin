package com.sportstalk.app.demo

import android.app.Application
import com.sportstalk.SportsTalk247
import com.sportstalk.api.ChatClient
import com.sportstalk.api.UserClient
import com.sportstalk.models.ClientConfig
import com.sportstalk.app.demo.presentation.users.coroutine.SelectDemoUserViewModel as SelectDemoUserViewModelCoroutine
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
        startKoin {
            androidContext(applicationContext)
            modules(
                module {
                    single<ClientConfig> {
                        val metadata = applicationInfo.metaData

                        ClientConfig(
                            appId = metadata.getString("sportstalk.api.app_id")!!,
                            apiToken = metadata.getString("sportstalk.api.auth_token")!!,
                            endpoint = "https://qa-talkapi.sportstalk247.com/api/v3/"
                        )
                    }
                    single<UserClient> { SportsTalk247.UserClient(config = get()) }
                    single<ChatClient> { SportsTalk247.ChatClient(config = get()) }
                },
                module {
                    /*
                    * Select Chat Room
                    */
                    viewModel {
                        SelectRoomViewModelRx(
                            chatClient = get()
                        )
                    }
                    viewModel {
                        SelectRoomViewModelCoroutine(
                            chatClient = get()
                        )
                    }
                    viewModel {
                        SelectRoomViewModelLiveData(
                            chatClient = get()
                        )
                    }
                    /*
                    * Select Demo User
                    */
                    viewModel {
                        SelectDemoUserViewModelCoroutine(
                            chatClient = get()
                        )
                    }
                }
            )
        }
    }

}