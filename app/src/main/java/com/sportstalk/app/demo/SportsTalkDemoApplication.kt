package com.sportstalk.app.demo

import android.app.Application
import com.sportstalk.api.ChatClient
import com.sportstalk.api.UserClient
import com.sportstalk.app.demo.presentation.listrooms.ListChatRoomsViewModel
import com.sportstalk.app.demo.presentation.rooms.CreateChatroomViewModel
import com.sportstalk.app.demo.presentation.users.AccountSettingsViewModel
import com.sportstalk.app.demo.presentation.users.CreateAccountViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
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
                     * Account Settings
                     */
                    viewModel {
                        (userClient: UserClient) -> AccountSettingsViewModel(userClient = userClient, preferences = get())
                    }

                    /*
                     * Create Chatroom
                     */
                    viewModel {
                        (chatClient: ChatClient) -> CreateChatroomViewModel(chatClient = chatClient, preferences = get())
                    }

                }
            )
        }
    }

}