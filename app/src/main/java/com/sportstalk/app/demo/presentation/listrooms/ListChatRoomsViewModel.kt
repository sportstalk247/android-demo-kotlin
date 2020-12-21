package com.sportstalk.app.demo.presentation.listrooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.api.ChatClient
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.models.SportsTalkException
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListChatRoomsViewModel(
    /*
    * Typical use case to access SDK client instance:
    *   val config = ClientConfig(appId = "...", apiToken = "...", endpoint = "...")
    *   // Instantiate via Factory
    *   val chatClient = SportsTalk247.ChatClient(config = config)
    */
    private val chatClient: ChatClient,
    private val preferences: SportsTalkDemoPreferences
): ViewModel() {

    private val chatRooms = BroadcastChannel<List<ChatRoom>>(Channel.BUFFERED)
    private val progressFetchRooms = BroadcastChannel<Boolean>(Channel.BUFFERED)
    private val enableAccountSettings = MutableStateFlow<Boolean?>(null)
    // Keep track of cursor
    private val cursor = MutableStateFlow<String?>(null)
    val state = object: ViewState {
        override fun progressFetchChatRooms(): Flow<Boolean> =
            progressFetchRooms.asFlow()

        override fun chatRooms(): Flow<List<ChatRoom>> =
            chatRooms.asFlow()

        override fun enableAccountSettings(): Flow<Boolean> =
            enableAccountSettings
                .filterNotNull()
    }

    private val _effect = BroadcastChannel<ViewEffect>(Channel.BUFFERED)
    val effect: Flow<ViewEffect>
        get() = _effect.asFlow()

    fun fetchInitial() {
        viewModelScope.launch {
            // Clear List Chatroom Items
            _effect.send(ViewEffect.ClearListChatrooms())
            performFetch()
        }
    }

    fun fetchMore() {
        val _cursor = cursor.value
        viewModelScope.launch {
            performFetch(_cursor)
        }
    }

    fun join(which: ChatRoom) {
        val currentUser = preferences.currentUser
        if(currentUser != null) {
            _effect.sendBlocking(ViewEffect.NavigateToChatRoom(which, currentUser))
        } else {
            _effect.sendBlocking(ViewEffect.NavigateToCreateProfile(which))
        }

    }

    private suspend fun performFetch(cursor: String? = null) {
        // Emit DISPLAY Progress indicator
        progressFetchRooms.send(true)

        try {
            ////////////////////////////////////////////////////////
            //////////////// CompletableFuture -> Coroutine
            ////////////////////////////////////////////////////////
            val listRoomsResponse =
                withContext(Dispatchers.IO) { /* Switch to IO Context(i.e. Background Thread) */
                    chatClient.listRooms(
                        cursor = cursor,
                        limit = LIMIT_FETCH_ROOMS
                    )
                }

            // Emit update room list
            chatRooms.send(listRoomsResponse.rooms)
            // Emit new cursor(IF NOT BLANK) and if there is MORE
            listRoomsResponse.cursor?.let { nowCursor ->
                this@ListChatRoomsViewModel.cursor.value = nowCursor
            }

        } catch (err: SportsTalkException) {
            // Emit error if encountered
            _effect.send(ViewEffect.ErrorFetchListChatrooms(err = err))
        } finally {
            // Emit HIDE Progress indicator
            progressFetchRooms.send(false)

            // EMIT Enable/Disable Account Settings
            enableAccountSettings.value = preferences.currentUser != null
        }
    }

    interface ViewState {
        /**
         * Emits [true] upon start SDK List Chatrooms Operation. Emits [false] when done.
         */
        fun progressFetchChatRooms(): Flow<Boolean>

        /**
         * Emits a list of [ChatRoom] everytime we receive response from SDK List Chatrooms Operation
         */
        fun chatRooms(): Flow<List<ChatRoom>>

        /**
         * Emits [true] if account was already created. Emits [false] otherwise.
         */
        fun enableAccountSettings(): Flow<Boolean>
    }

    sealed class ViewEffect {
        class ClearListChatrooms : ViewEffect()
        data class NavigateToCreateProfile(val which: ChatRoom) : ViewEffect()
        data class NavigateToChatRoom(val which: ChatRoom, val who: User) : ViewEffect()
        data class ErrorFetchListChatrooms(val err: SportsTalkException) : ViewEffect()
    }

    companion object {
        const val LIMIT_FETCH_ROOMS = 15
    }

}