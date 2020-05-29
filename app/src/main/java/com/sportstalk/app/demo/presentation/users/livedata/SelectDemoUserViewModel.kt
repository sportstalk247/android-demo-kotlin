package com.sportstalk.app.demo.presentation.users.livedata

import androidx.lifecycle.*
import com.sportstalk.api.ChatClient
import com.sportstalk.app.demo.extensions.SingleLiveEvent
import com.sportstalk.models.SportsTalkException
import com.sportstalk.models.chat.ChatRoomParticipant
import com.sportstalk.models.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectDemoUserViewModel(
    /*
    * Typical use case to access SDK client instance:
    *   val config = ClientConfig(appId = "...", apiToken = "...", endpoint = "...")
    *   // Instantiate via Factory
    *   val chatClient = SportsTalk247.ChatClient(config = config)
    */
    private val chatClient: ChatClient
): ViewModel() {

    private val participants = MutableLiveData<List<ChatRoomParticipant>>()
    private val progressFetchParticipants = SingleLiveEvent<Boolean>()
    private val cursor = SingleLiveEvent<String>()
    private val _state = MutableLiveData<ViewState>()
    val state: LiveData<ViewState>
        get() = _state

    private val _effect = SingleLiveEvent<ViewEffect>()
    val effect: LiveData<ViewEffect>
        get() = _effect.distinctUntilChanged()

    /*
   * Actively emit updated state based on combined livedata values
   */
    private val stateChangesLvDta = participants.switchMap { _rooms ->
        progressFetchParticipants.switchMap<Boolean, ViewState> { _progress ->
            cursor.map<String, ViewState> { _cursor ->
                ViewState(_rooms, _progress, _cursor)
            }
        }
    }

    private val stateObserver: Observer<ViewState> = Observer<ViewState> { _viewState ->
        _state.postValue(_viewState)
    }

    init {
        stateChangesLvDta.observeForever(stateObserver)
    }

    fun selectDemoUser(which: User) {
        _effect.postValue(
            ViewEffect.NavigateToChatRoom(which)
        )
    }

    fun fetch(roomId: String, cursor: String? = null) {
        // Clear list if fetching without cursor(ex. swipe refresh)
        this.cursor.postValue(cursor ?: "")

        // Attempt fetch
        viewModelScope.launch {
            try {
                // Emit DISPLAY Progress indicator
                progressFetchParticipants.postValue(true)

////////////////////////////////////////////////////////
//////////////// CompletableFuture -> Coroutine
////////////////////////////////////////////////////////
                val listParticipantsResponse =
                    withContext(Dispatchers.IO) { /* Switch to IO Context(i.e. Background Thread) */
                        chatClient.listRoomParticipants(
                            chatRoomId = roomId,
                            cursor = cursor,
                            limit = LIMIT_FETCH_USERS
                        )
                            .await()
                    }
                // Emit update room list
                val updatedParticipants =
                    if (cursor == null || cursor.isEmpty()) listParticipantsResponse.participants
                    else listParticipantsResponse.participants + participants.value!!
                participants.postValue(updatedParticipants.distinct())
                // Emit new cursor
                this@SelectDemoUserViewModel.cursor.postValue(listParticipantsResponse.cursor ?: "")
            } catch (err: SportsTalkException) {
                // Emit error if encountered
                _effect.postValue(ViewEffect.ErrorFetchParticipants(err = err))
            } finally {
                // Emit HIDE Progress indicator
                progressFetchParticipants.postValue(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stateChangesLvDta.removeObserver(stateObserver)
    }

    data class ViewState(
        val participants: List<ChatRoomParticipant> = listOf(),
        val progressFetchParticipants: Boolean = false,
        val cursor: String? = null
    )

    sealed class ViewEffect {
        data class NavigateToChatRoom(val which: User) : ViewEffect()
        data class ErrorFetchParticipants(val err: SportsTalkException) : ViewEffect()
    }

    companion object {
        const val LIMIT_FETCH_USERS = 100
    }

}