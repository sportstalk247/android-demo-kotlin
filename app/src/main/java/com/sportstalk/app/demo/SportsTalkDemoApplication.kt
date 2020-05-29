package com.sportstalk.app.demo

import android.app.Application
import com.sportstalk.api.ChatClient
import com.sportstalk.app.demo.presentation.users.coroutine.SelectDemoUserViewModel as SelectDemoUserViewModelCoroutine
import com.sportstalk.app.demo.presentation.users.rxjava.SelectDemoUserViewModel as SelectDemoUserViewModelRx
import com.sportstalk.app.demo.presentation.users.livedata.SelectDemoUserViewModel as SelectDemoUserViewModelLiveData
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
                    /*
                    * Select Chat Room
                    */
                    viewModel {
                            (chatClient: ChatClient) -> SelectRoomViewModelRx(chatClient)
                    }
                    viewModel {
                            (chatClient: ChatClient) -> SelectRoomViewModelCoroutine(chatClient)
                    }
                    viewModel {
                            (chatClient: ChatClient) -> SelectRoomViewModelLiveData(chatClient)
                    }
                    /*
                    * Select Demo User
                    */
                    viewModel {
                            (chatClient: ChatClient) -> SelectDemoUserViewModelCoroutine(chatClient)
                    }
                    viewModel {
                            (chatClient: ChatClient) -> SelectDemoUserViewModelRx(chatClient)
                    }
                    viewModel {
                            (chatClient: ChatClient) -> SelectDemoUserViewModelLiveData(chatClient)
                    }
                }
            )
        }
    }

}