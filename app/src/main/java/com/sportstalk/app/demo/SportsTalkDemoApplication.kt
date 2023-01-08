package com.sportstalk.app.demo

import androidx.multidex.MultiDexApplication
import com.sportstalk.app.demo.presentation.chatroom.ChatRoomViewModel
import com.sportstalk.app.demo.presentation.chatroom.listparticipants.ChatroomListParticipantsViewModel
import com.sportstalk.app.demo.presentation.inappsettings.InAppSettingsViewModel
import com.sportstalk.app.demo.presentation.listrooms.AdminListChatRoomsViewModel
import com.sportstalk.app.demo.presentation.listrooms.ListChatRoomsViewModel
import com.sportstalk.app.demo.presentation.rooms.CreateChatroomViewModel
import com.sportstalk.app.demo.presentation.rooms.UpdateChatroomViewModel
import com.sportstalk.app.demo.presentation.users.AccountSettingsViewModel
import com.sportstalk.app.demo.presentation.users.CreateAccountViewModel
import com.sportstalk.coroutine.SportsTalk247
import com.sportstalk.datamodels.ClientConfig
import com.sportstalk.datamodels.chat.ChatRoom
import com.sportstalk.datamodels.users.User
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

class SportsTalkDemoApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()

        // Instantiate
        startKoin {
            androidContext(applicationContext)
            modules(
                module {
                    single {
                        Json {
                            prettyPrint = true
                            isLenient = true
                            ignoreUnknownKeys = true
                        }
                    }

                    // Preferences
                    single {
                        SportsTalkDemoPreferences(
                            context = get(),
                            json = get()
                        )
                    }

                    // Client Config
                    factory {
                        val preferences: SportsTalkDemoPreferences = get()
                        // Config field values might change based on latest preference value(s)
                        ClientConfig(
                            appId = preferences.appId ?: "",
                            apiToken = preferences.authToken ?: "",
                            endpoint = preferences.urlEndpoint ?: ""
                        )
                    }

                    // User Client
                    factory {
                        val config: ClientConfig = get()
                        SportsTalk247.UserClient(config)
                    }

                    // Chat Client
                    factory {
                        val config: ClientConfig = get()
                        SportsTalk247.ChatClient(config)
                    }

                    /*
                    * In-app Settings
                    */
                    viewModel { InAppSettingsViewModel(preferences = get()) }

                    /*
                    * List Chatrooms
                    */
                    viewModel { ListChatRoomsViewModel(chatClient = get(), preferences = get()) }

                    /*
                    * Admin List Chatrooms
                    */
                    viewModel {
                        AdminListChatRoomsViewModel(
                            chatClient = get(),
                            preferences = get()
                        )
                    }

                    /*
                     * Create Account
                     */
                    viewModel { CreateAccountViewModel(userClient = get(), preferences = get()) }

                    /*
                     * Account Settings
                     */
                    viewModel { AccountSettingsViewModel(userClient = get(), preferences = get()) }

                    /*
                     * Create Chatroom
                     */
                    viewModel { CreateChatroomViewModel(chatClient = get(), preferences = get()) }

                    /*
                    * Chatroom
                    */
                    viewModel { (room: ChatRoom, user: User) ->
                        ChatRoomViewModel(
                            room = room,
                            user = user,
                            chatClient = get(),
                            preferences = get()
                        )
                    }

                    /*
                    * Update Chatroom
                    */
                    viewModel { (room: ChatRoom, user: User) ->
                        UpdateChatroomViewModel(
                            room = room,
                            user = user,
                            chatClient = get(),
                            preferences = get()
                        )
                    }

                    /*
                    * Chatroom List Participants
                    */
                    viewModel { (room: ChatRoom, user: User) ->
                        ChatroomListParticipantsViewModel(
                            room = room,
                            user = user,
                            userClient = get(),
                            chatClient = get()
                        )
                    }

                }
            )
        }
    }

}