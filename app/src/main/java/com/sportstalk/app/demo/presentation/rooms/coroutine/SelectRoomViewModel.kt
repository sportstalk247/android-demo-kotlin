package com.sportstalk.app.demo.presentation.rooms.coroutine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.api.ChatClient
import com.sportstalk.models.SportsTalkException
import com.sportstalk.models.chat.ChatRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectRoomViewModel(
    /*
    * Typical use case to access API instance:
    *   SportsTalkManager.init(applicationContext) // invoked once under SportsTalkDemoApplication.onCreate()
    *   // Access singleton instance
    *   val chatApiService = SportsTalkManager.instance.chatApiService
    */
    private val chatClient: ChatClient
) : ViewModel() {

    private val rooms = ConflatedBroadcastChannel<List<ChatRoom>>()
    private val progressFetchRooms = BroadcastChannel<Boolean>(Channel.BUFFERED)
    private val cursor = BroadcastChannel<String>(Channel.BUFFERED)
    private val _state = ConflatedBroadcastChannel<ViewState>()
    val state: Flow<ViewState>
        get() = _state.asFlow()
            .onStart { emit(ViewState(progressFetchRooms = true)) }

    private val _effect = BroadcastChannel<ViewEffect>(Channel.BUFFERED)
    val effect: Flow<ViewEffect>
        get() = _effect
            .asFlow()
            .distinctUntilChanged()

    init {
        // Emit ViewState changes
        combine(
            rooms.asFlow(),
            progressFetchRooms.asFlow(),
            cursor.asFlow()
        ) { _rooms, _progress, _cursor ->
            ViewState(
                _rooms,
                _progress,
                _cursor.takeIf { it.isNotEmpty() })
        }
            .onEach { _state.send(it) }
            .launchIn(viewModelScope)
    }

    fun fetch(cursor: String? = null) {
        // Clear list if fetching without cursor(ex. swipe refresh)
        this.cursor.sendBlocking(cursor ?: "")

        // Attempt fetch
        viewModelScope.launch {
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
                            .await()
                    }

                // Emit update room list
                val updatedRooms = if (cursor == null || cursor.isEmpty()) listRoomsResponse.rooms
                else listRoomsResponse.rooms + rooms.value
                rooms.send(updatedRooms.distinct())
                // Emit new cursor
                this@SelectRoomViewModel.cursor.send(listRoomsResponse.cursor ?: "")

            } catch (err: SportsTalkException) {
                // Emit error if encountered
                _effect.send(ViewEffect.ErrorFetchRoom(err = err))
            } finally {
                // Emit HIDE Progress indicator
                progressFetchRooms.send(false)
            }

        }
    }


    fun selectRoom(which: ChatRoom) {
        _effect.sendBlocking(
            ViewEffect.NavigateToChatRoom(which)
        )
    }

    data class ViewState(
        val rooms: List<ChatRoom> = listOf(),
        val progressFetchRooms: Boolean = false,
        val cursor: String? = null
    )

    sealed class ViewEffect {
        data class NavigateToChatRoom(val which: ChatRoom) : ViewEffect()
        data class ErrorFetchRoom(val err: SportsTalkException) : ViewEffect()
    }

    companion object {
        const val LIMIT_FETCH_ROOMS = 100
    }

}