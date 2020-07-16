package com.sportstalk.app.demo

import androidx.multidex.MultiDexApplication
import com.sportstalk.api.ChatClient
import com.sportstalk.api.UserClient
import com.sportstalk.app.demo.presentation.chatroom.ChatRoomViewModel
import com.sportstalk.app.demo.presentation.chatroom.listparticipants.ChatroomListParticipantsViewModel
import com.sportstalk.app.demo.presentation.inappsettings.InAppSettingsViewModel
import com.sportstalk.app.demo.presentation.listrooms.AdminListChatRoomsViewModel
import com.sportstalk.app.demo.presentation.listrooms.ListChatRoomsViewModel
import com.sportstalk.app.demo.presentation.rooms.CreateChatroomViewModel
import com.sportstalk.app.demo.presentation.rooms.UpdateChatroomViewModel
import com.sportstalk.app.demo.presentation.users.AccountSettingsViewModel
import com.sportstalk.app.demo.presentation.users.CreateAccountViewModel
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.users.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

class SportsTalkDemoApplication: MultiDexApplication() {

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
                    * In-app Settings
                    */
                    viewModel { InAppSettingsViewModel(preferences = get()) }

                    /*
                    * List Chatrooms
                    */
                    viewModel { (chatClient: ChatClient) -> ListChatRoomsViewModel(chatClient = chatClient, preferences = get()) }

                    /*
                    * Admin List Chatrooms
                    */
                    viewModel { (chatClient: ChatClient) -> AdminListChatRoomsViewModel(chatClient = chatClient, preferences = get()) }

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

                    /*
                    * Chatroom
                    */
                    viewModel {
                        (room: ChatRoom, user: User, chatClient: ChatClient) ->
                        ChatRoomViewModel(
                            room = room,
                            user = user,
                            chatClient = chatClient,
                            preferences = get()
                        )
                    }

                    /*
                    * Update Chatroom
                    */
                    viewModel {
                        (room: ChatRoom, user: User, chatClient: ChatClient) ->
                        UpdateChatroomViewModel(
                            room = room,
                            user = user,
                            chatClient = chatClient,
                            preferences = get()
                        )
                    }

                    /*
                    * Chatroom List Participants
                    */
                    viewModel {
                        (room: ChatRoom, user: User, userClient: UserClient, chatClient: ChatClient) ->
                        ChatroomListParticipantsViewModel(
                            room = room,
                            user = user,
                            userClient = userClient,
                            chatClient = chatClient
                        )
                    }

                }
            )
        }
    }

}