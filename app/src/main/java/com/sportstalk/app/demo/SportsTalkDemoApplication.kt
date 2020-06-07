package com.sportstalk.app.demo

import android.app.Application
import com.sportstalk.api.ChatClient
import com.sportstalk.api.UserClient
import com.sportstalk.app.demo.presentation.listrooms.ListChatRoomsViewModel
import com.sportstalk.app.demo.presentation.users.CreateAccountViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.json.JsonConfiguration
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
                    single {
                        Json(
                            JsonBuilder()
                                .apply {
                                    prettyPrint = true
                                    isLenient = true
                                    ignoreUnknownKeys = true
                                }
                                .buildConfiguration()
                        )
                    }

                    // Preferences
                    single {
                        SportsTalkDemoPreferences(
                            context = get(),
                            json = get()
                        )
                    }

                    /*
                    * List Chatrooms
                    */
                    viewModel { (chatClient: ChatClient) -> ListChatRoomsViewModel(chatClient = chatClient, preferences = get()) }

                    /*
                     * Create Account
                     */
                    viewModel {
                        (userClient: UserClient) -> CreateAccountViewModel(userClient = userClient, preferences = get())
                    }

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