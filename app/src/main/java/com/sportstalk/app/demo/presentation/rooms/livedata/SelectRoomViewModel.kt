package com.sportstalk.app.demo.presentation.rooms.livedata

import androidx.lifecycle.*
import com.sportstalk.api.ChatApiService
import com.sportstalk.app.demo.extensions.SingleLiveEvent
import com.sportstalk.models.chat.ChatRoom
import kotlinx.coroutines.Dispatchers
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

    private val rooms = MutableLiveData<List<ChatRoom>>()
    private val progressFetchRooms = MutableLiveData<Boolean>()
    private val cursor = MutableLiveData<String>()
    private val _state = MutableLiveData(ViewState(progressFetchRooms = true))
    val state: LiveData<ViewState> get() = _state

    private val _effect = SingleLiveEvent<ViewEffect>()
    val effect: LiveData<ViewEffect> get() = _effect

    /*
    * Actively emit updated state based on combined livedata values
    */
    private val stateChangesLvDta = rooms.switchMap { _rooms ->
        progressFetchRooms.switchMap<Boolean, ViewState> { _progress ->
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

    override fun onCleared() {
        super.onCleared()
        stateChangesLvDta.removeObserver(stateObserver)
    }

    fun fetch(cursor: String? = null) {
        // Clear list if fetching without cursor(ex. swipe refresh)
        if (cursor == null || cursor.isEmpty()) rooms.postValue(listOf())
        this.cursor.postValue(cursor)

        // Attempt fetch
        viewModelScope.launch {
            // Emit DISPLAY Progress indicator
            progressFetchRooms.postValue(true)

            try {
                val response =
                    withContext(Dispatchers.IO) { /* Switch to IO Context(i.e. Background Thread) */
                        chatApiService.listRooms(
                            cursor = cursor,
                            limit = LIMIT_FETCH_ROOMS
                        )
                            .await()
                    }

                // Map out `data` from response
                val listRoomsResponse = if (response.code in 200..299) {
                    response.data!!
                } else {
                    // "error"
                    throw Throwable(response.message)
                }
                // Emit update room list
                rooms.postValue((listRoomsResponse.rooms + rooms.value!!).distinct())
                // Emit new cursor
                this@SelectRoomViewModel.cursor.postValue(listRoomsResponse.cursor ?: "")

            } catch (err: Throwable) {
                // Emit error if encountered
                _effect.postValue(ViewEffect.ErrorFetchRoom(err = err))
            } finally {
                // Emit HIDE Progress indicator
                progressFetchRooms.postValue(false)
            }

        }
    }


    fun selectRoom(which: ChatRoom) {
        _effect.postValue(
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