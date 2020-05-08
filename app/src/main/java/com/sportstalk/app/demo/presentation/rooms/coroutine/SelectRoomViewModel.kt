package com.sportstalk.app.demo.presentation.rooms.coroutine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.api.ChatApiService
import com.sportstalk.app.demo.extensions.throttleFirst
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.chat.ListRoomsResponse
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
    private val chatApiService: ChatApiService
) : ViewModel() {

    private val rooms = ConflatedBroadcastChannel<List<ChatRoom>>()
    private val progressFetchRooms = BroadcastChannel<Boolean>(Channel.BUFFERED)
    private val cursor = BroadcastChannel<String>(Channel.BUFFERED)
    private val _state = ConflatedBroadcastChannel(ViewState())
    val state: Flow<ViewState>
        get() = _state.asFlow()
            .onStart { emit(ViewState(progressFetchRooms = true)) }

    private val _effect = BroadcastChannel<ViewEffect>(Channel.BUFFERED)
    val effect: Flow<ViewEffect>
        get() = _effect
            .asFlow()
            .throttleFirst(250)

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
        if (cursor == null || cursor.isEmpty()) rooms.sendBlocking(listOf())
        this.cursor.sendBlocking(cursor ?: "")

        // Attempt fetch
        flow {
            emit(
                chatApiService.listRooms(
                    cursor = cursor,
                    limit = LIMIT_FETCH_ROOMS
                )
                    .await()
            )
        }
            // Emit DISPLAY Progress indicator
            .onStart { progressFetchRooms.send(true) }
            .map { response ->
                // Map out `data` from response
                if (response.code in 200..299) {
                    response.data!!
                } else {
                    // "error"
                    throw Throwable(response.message)
                }
            }
            .catch { err ->
                // Emit error if encountered
                _effect.send(ViewEffect.ErrorFetchRoom(err = err))
                emit(ListRoomsResponse())
            }
            .onEach { listResponse ->
                rooms.send((listResponse.rooms + rooms.value).distinct())
                this@SelectRoomViewModel.cursor.send(listResponse.cursor ?: "")
            }
            // Emit HIDE Progress indicator
            .onCompletion { progressFetchRooms.send(false) }
            .launchIn(viewModelScope)
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
        class NavigateToChatRoom(val which: ChatRoom) : ViewEffect()
        class ErrorFetchRoom(val err: Throwable) : ViewEffect()
    }

    companion object {
        const val LIMIT_FETCH_ROOMS = 100
    }

}