package com.sportstalk.app.demo.presentation.listrooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
/*import com.sportstalk.coroutine.api.ChatClient*/import com.sportstalk.sdk.core.api.ChatClient
import com.sportstalk.app.demo.SportsTalkDemoPreferences
/*import com.sportstalk.datamodels.SportsTalkException*/import com.sportstalk.sdk.model.SportsTalkException
/*import com.sportstalk.datamodels.chat.**/import com.sportstalk.sdk.model.chat.*
/*import com.sportstalk.datamodels.users.User*/import com.sportstalk.sdk.model.user.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminListChatRoomsViewModel(
    /*
    * Typical use case to access SDK client instance:
    *   val config = ClientConfig(appId = "...", apiToken = "...", endpoint = "...")
    *   // Instantiate via Factory
    *   val chatClient = SportsTalk247.ChatClient(config = config)
    */
    private val chatClient: ChatClient,
    private val preferences: SportsTalkDemoPreferences
): ViewModel() {

    private val chatRooms = MutableStateFlow<List<ChatRoom>?>(null)
    private val progressFetchRooms = MutableSharedFlow<Boolean>()
    private val progressDeleteChatRoom = MutableSharedFlow<Boolean>()
    private val progressSendAnnouncement = MutableSharedFlow<Boolean>()
    // Keep track of cursor
    private val cursor = ConflatedBroadcastChannel<String>("")
    val state = object: ViewState {
        override fun progressFetchChatRooms(): Flow<Boolean> =
            progressFetchRooms
                .apply { resetReplayCache() }
                .asSharedFlow()

        override fun chatRooms(): Flow<List<ChatRoom>> =
            chatRooms
                .asStateFlow()
                .filterNotNull()

        override fun progressDeleteChatRoom(): Flow<Boolean> =
            progressDeleteChatRoom
                .apply { resetReplayCache() }
                .asSharedFlow()

        override fun progressSendAnnouncement(): Flow<Boolean> =
            progressSendAnnouncement
                .apply { resetReplayCache() }
                .asSharedFlow()
    }

    private val _effect = MutableSharedFlow<ViewEffect>()
    val effect: Flow<ViewEffect>
        get() = _effect
            .apply { resetReplayCache() }
            .asSharedFlow()

    fun fetchInitial(forceRefresh: Boolean) {
        // Skip if initial fetch has already been performed
        if(!forceRefresh && chatRooms.value != null) return

        viewModelScope.launch {
            // Clear List Chatroom Items
            _effect.emit(ViewEffect.ClearListChatrooms())
            performFetch()
        }
    }

    fun fetchMore() {
        val _cursor = cursor.value
        viewModelScope.launch {
            performFetch(_cursor)
        }
    }

    private suspend fun performFetch(cursor: String? = null) {
        // Emit DISPLAY Progress indicator
        progressFetchRooms.emit(true)

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

            val updatedRoomList = ArrayList(chatRooms.value ?: listOf()).apply {
                listRoomsResponse.rooms.forEach { newRoom ->
                    val index = indexOfFirst { oldRoom -> oldRoom.id == newRoom.id }
                    if(index >= 0) {
                        set(index, newRoom)
                    } else {
                        add(newRoom)
                    }
                }
            }

            // Emit update room list
            chatRooms.value = updatedRoomList
            // Emit new cursor(IF NOT BLANK) and if there is MORE
            listRoomsResponse.cursor?.let { nowCursor ->
                this@AdminListChatRoomsViewModel.cursor.send(nowCursor)
            }

        } catch (err: SportsTalkException) {
            // Emit error if encountered
            _effect.emit(ViewEffect.ErrorFetchListChatrooms(err = err))
        } finally {
            // Emit HIDE Progress indicator
            progressFetchRooms.emit(false)
        }
    }

    fun update(which: ChatRoom) {
        val user = preferences.currentUser ?: return
        viewModelScope.launch {
            _effect.emit(
                ViewEffect.NavigateToChatRoomDetails(user, which)
            )
        }
    }

    fun delete(which: ChatRoom) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressDeleteChatRoom.emit(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.deleteRoom(
                        chatRoomId = which.id!!
                    )
                }

                // Emit Success
                _effect.emit(
                    ViewEffect.SuccessDeleteRoom(response)
                )

            } catch (err: SportsTalkException) {
                _effect.emit(
                    ViewEffect.ErrorDeleteRoom(err)
                )
            } finally {
                // HIDE Progress Indicator
                progressDeleteChatRoom.emit(false)
            }

        }
    }

    fun sendAnnouncement(message: String, which: ChatRoom) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressSendAnnouncement.emit(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.executeChatCommand(
                        chatRoomId = which.id!!,
                        request = ExecuteChatCommandRequest(
                            command = message,
                            // TODO:: Hard-coded ADMIN ID
                            userid = "admin",
                            eventtype = EventType.ANNOUNCEMENT
                        )
                    )
                }

                // EMIT Response
                _effect.emit(ViewEffect.SuccessSendAnnouncement(response))
            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.emit(ViewEffect.ErrorSendAnnouncement(err))
            } finally {
                // DISPLAY Progress Indicator
                progressSendAnnouncement.emit(false)
            }
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
         * Emits [true] upon start SDK Delete Chatroom Operation. Emits [false] when done.
         */
        fun progressDeleteChatRoom(): Flow<Boolean>

        /**
         * Emits [true] upon start SDK Execute Chat Command Operation(Announcement). Emits [false] when done.
         */
        fun progressSendAnnouncement(): Flow<Boolean>
    }

    sealed class ViewEffect {
        class ClearListChatrooms : ViewEffect()
        data class ErrorFetchListChatrooms(val err: SportsTalkException) : ViewEffect()

        data class NavigateToChatRoomDetails(val admin: User, val which: ChatRoom) : ViewEffect()
        data class SuccessDeleteRoom(val response: DeleteChatRoomResponse): ViewEffect()
        data class ErrorDeleteRoom(val err: SportsTalkException): ViewEffect()

        data class SuccessSendAnnouncement(val response: ExecuteChatCommandResponse): ViewEffect()
        data class ErrorSendAnnouncement(val err: SportsTalkException): ViewEffect()
    }

    companion object {
        const val LIMIT_FETCH_ROOMS = 15
    }

}