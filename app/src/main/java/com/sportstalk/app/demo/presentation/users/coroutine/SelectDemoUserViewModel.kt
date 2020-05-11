package com.sportstalk.app.demo.presentation.users.coroutine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.api.ChatApiService
import com.sportstalk.app.demo.extensions.throttleFirst
import com.sportstalk.models.chat.ChatRoomParticipant
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

class SelectDemoUserViewModel(
    /*
    * Typical use case to access API instance:
    *   SportsTalkManager.init(applicationContext) // invoked once under SportsTalkDemoApplication.onCreate()
    *   // Access singleton instance
    *   val chatApiService = SportsTalkManager.instance.chatApiService
    */
    private val chatApiService: ChatApiService
) : ViewModel() {

    private val participants = ConflatedBroadcastChannel<List<ChatRoomParticipant>>()
    private val progressFetchParticipants = BroadcastChannel<Boolean>(Channel.BUFFERED)
    private val cursor = BroadcastChannel<String>(Channel.BUFFERED)
    private val _state = ConflatedBroadcastChannel<ViewState>()
    val state: Flow<ViewState>
        get() = _state.asFlow()
            .onStart { emit(ViewState(progressFetchParticipants = true)) }

    private val _effect = BroadcastChannel<ViewEffect>(Channel.BUFFERED)
    val effect: Flow<ViewEffect>
        get() = _effect
            .asFlow()
            .distinctUntilChanged()

    init {
        // Emit ViewState changes
        combine(
            participants.asFlow(),
            progressFetchParticipants.asFlow(),
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

    fun fetch(roomId: String, cursor: String? = null) {
        // Clear list if fetching without cursor(ex. swipe refresh)
        this.cursor.sendBlocking(cursor ?: "")

        // Attempt fetch
        viewModelScope.launch {
            try {
                // Emit DISPLAY Progress indicator
                progressFetchParticipants.send(true)

////////////////////////////////////////////////////////
//////////////// CompletableFuture -> Coroutine
////////////////////////////////////////////////////////
                val response =
                    withContext(Dispatchers.IO) { /* Switch to IO Context(i.e. Background Thread) */
                        chatApiService.listRoomParticipants(
                            chatRoomId = roomId,
                            cursor = cursor,
                            limit = LIMIT_FETCH_USERS
                        )
                            .await()
                    }

                // Map out `data` from response
                val listParticipantsResponse = if (response.code in 200..299) {
                    response.data!!
                } else {
                    // "error"
                    throw Throwable(response.message)
                }
                // Emit update room list
                val updatedParticipants = if(cursor == null || cursor.isEmpty()) listParticipantsResponse.participants
                    else listParticipantsResponse.participants + participants.value
                participants.send(updatedParticipants.distinct())
                // Emit new cursor
                this@SelectDemoUserViewModel.cursor.send(listParticipantsResponse.cursor ?: "")
            } catch (err: Throwable) {
                // Emit error if encountered
                _effect.send(ViewEffect.ErrorFetchParticipants(err = err))
            } finally {
                // Emit HIDE Progress indicator
                progressFetchParticipants.send(false)
            }
        }
    }

    fun selectDemoUser(which: User) {
        _effect.sendBlocking(
            ViewEffect.NavigateToChatRoom(which)
        )
    }

    data class ViewState(
        val participants: List<ChatRoomParticipant> = listOf(),
        val progressFetchParticipants: Boolean = false,
        val cursor: String? = null
    )

    sealed class ViewEffect {
        data class NavigateToChatRoom(val which: User) : ViewEffect()
        data class ErrorFetchParticipants(val err: Throwable) : ViewEffect()
    }

    companion object {
        const val LIMIT_FETCH_USERS = 100
    }

}