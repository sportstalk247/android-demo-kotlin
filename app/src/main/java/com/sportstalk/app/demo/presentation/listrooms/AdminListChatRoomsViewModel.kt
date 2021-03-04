package com.sportstalk.app.demo.presentation.listrooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.coroutine.api.ChatClient
import com.sportstalk.datamodels.SportsTalkException
import com.sportstalk.datamodels.chat.*
import com.sportstalk.datamodels.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
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

    private val chatRooms = ConflatedBroadcastChannel<List<ChatRoom>?>(null)
    private val progressFetchRooms = Channel<Boolean>(Channel.BUFFERED)
    private val progressDeleteChatRoom = Channel<Boolean>(Channel.BUFFERED)
    private val progressSendAnnouncement = Channel<Boolean>(Channel.BUFFERED)
    // Keep track of cursor
    private val cursor = ConflatedBroadcastChannel<String>("")
    val state = object: ViewState {
        override fun progressFetchChatRooms(): Flow<Boolean> =
            progressFetchRooms
                .consumeAsFlow()

        override fun chatRooms(): Flow<List<ChatRoom>> =
            chatRooms
                .asFlow()
                .filterNotNull()

        override fun progressDeleteChatRoom(): Flow<Boolean> =
            progressDeleteChatRoom
                .consumeAsFlow()

        override fun progressSendAnnouncement(): Flow<Boolean> =
            progressSendAnnouncement
                .consumeAsFlow()
    }

    private val _effect = Channel<ViewEffect>(Channel.BUFFERED)
    val effect: Flow<ViewEffect>
        get() = _effect
            .consumeAsFlow()

    fun fetchInitial(forceRefresh: Boolean) {
        // Skip if initial fetch has already been performed
        if(!forceRefresh && chatRooms.value != null) return

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
            chatRooms.send(updatedRoomList)
            // Emit new cursor(IF NOT BLANK) and if there is MORE
            listRoomsResponse.cursor?.let { nowCursor ->
                this@AdminListChatRoomsViewModel.cursor.sendBlocking(nowCursor)
            }

        } catch (err: SportsTalkException) {
            // Emit error if encountered
            _effect.send(ViewEffect.ErrorFetchListChatrooms(err = err))
        } finally {
            // Emit HIDE Progress indicator
            progressFetchRooms.send(false)
        }
    }

    fun update(which: ChatRoom) {
        val user = preferences.currentUser ?: return
        viewModelScope.launch {
            _effect.send(
                ViewEffect.NavigateToChatRoomDetails(user, which)
            )
        }
    }

    fun delete(which: ChatRoom) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressDeleteChatRoom.send(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.deleteRoom(
                        chatRoomId = which.id!!
                    )
                }

                // Emit Success
                _effect.send(
                    ViewEffect.SuccessDeleteRoom(response)
                )

            } catch (err: SportsTalkException) {
                _effect.send(
                    ViewEffect.ErrorDeleteRoom(err)
                )
            } finally {
                // HIDE Progress Indicator
                progressDeleteChatRoom.send(false)
            }

        }
    }

    fun sendAnnouncement(message: String, which: ChatRoom) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressSendAnnouncement.send(true)

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
                _effect.send(ViewEffect.SuccessSendAnnouncement(response))
            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(ViewEffect.ErrorSendAnnouncement(err))
            } finally {
                // DISPLAY Progress Indicator
                progressSendAnnouncement.send(false)
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