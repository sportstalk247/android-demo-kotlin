package com.sportstalk.app.demo.presentation.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.coroutine.api.ChatClient
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.datamodels.SportsTalkException
import com.sportstalk.datamodels.chat.ChatRoom
import com.sportstalk.datamodels.chat.CreateChatRoomRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateChatroomViewModel(
    /*
    * Typical use case to access SDK client instance:
    *   val config = ClientConfig(appId = "...", apiToken = "...", endpoint = "...")
    *   // Instantiate via Factory
    *   val chatClient = SportsTalk247.ChatClient(config = config)
    */
    private val chatClient: ChatClient,
    val preferences: SportsTalkDemoPreferences
) : ViewModel() {

    private val roomName = ConflatedBroadcastChannel<String>()
    private val roomDescription = ConflatedBroadcastChannel<String>()
    private val roomCustomId = ConflatedBroadcastChannel<String>()
    private val roomAction = ConflatedBroadcastChannel<Boolean>(true)
    private val roomEnterExit = ConflatedBroadcastChannel<Boolean>(true)
    private val roomIsOpen = ConflatedBroadcastChannel<Boolean>(true)
    private val roomProfanityEnabled = ConflatedBroadcastChannel<Boolean>(true)

    val state = object : ViewState {
        override fun validationRoomName(): Flow<Boolean> =
            validationRoomName.consumeAsFlow()

        override fun enableSubmit(): Flow<Boolean> =
            enableSubmit.consumeAsFlow()

        override fun progressCreateChatroom(): Flow<Boolean> =
            progressCreateChatroom.consumeAsFlow()
    }

    private val _effect = Channel<ViewEffect>(Channel.RENDEZVOUS)
    val effect: Flow<ViewEffect>
        get() = _effect.consumeAsFlow()

    private val validationRoomName = Channel<Boolean>(Channel.RENDEZVOUS)
    private val enableSubmit = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressCreateChatroom = Channel<Boolean>(Channel.RENDEZVOUS)

    init {
        roomName
            .asFlow()
            .map {
                Regex(REGEX_ROOMNAME).containsMatchIn(it)
            }
            .onEach { isValid -> validationRoomName.send(isValid) }
            .onEach { isValid -> enableSubmit.send(isValid) }
            .launchIn(viewModelScope)
    }

    fun roomName(roomName: String) =
        this.roomName.trySendBlocking(roomName)

    fun roomDescription(roomDescription: String) =
        this.roomDescription.trySendBlocking(roomDescription)

    fun roomCustomId(roomCustomId: String) =
        this.roomCustomId.trySendBlocking(roomCustomId)

    fun roomAction(roomAction: Boolean) =
        this.roomAction.trySendBlocking(roomAction)

    fun roomEnterExit(roomEnterExit: Boolean) =
        this.roomEnterExit.trySendBlocking(roomEnterExit)

    fun roomIsOpen(roomIsOpen: Boolean) =
        this.roomIsOpen.trySendBlocking(roomIsOpen)

    fun roomProfanityEnabled(roomProfanityEnabled: Boolean) =
        this.roomProfanityEnabled.trySendBlocking(roomProfanityEnabled)

    fun submit() {
        viewModelScope.launch { performCreateChatroom() }
    }

    private suspend fun performCreateChatroom() {
        try {
            // DISPLAY Progress Indicator
            progressCreateChatroom.send(true)

            val response = withContext(Dispatchers.IO) {
                chatClient.createRoom(
                    request = CreateChatRoomRequest(
                        name = roomName.valueOrNull,
                        description = roomDescription.valueOrNull,
                        customid = roomCustomId.valueOrNull,
                        userid = preferences.currentUser?.userid,
                        /*moderation = "pre",*/
                        enableactions = roomAction.valueOrNull,
                        enableenterandexit = roomEnterExit.valueOrNull,
                        roomisopen = roomIsOpen.valueOrNull,
                        enableprofanityfilter = roomProfanityEnabled.valueOrNull
                    )
                )
            }

            // EMIT response
            _effect.send(
                ViewEffect.SuccessCreateChatroom(response)
            )

        } catch (err: SportsTalkException) {
            // EMIT Error
            _effect.send(
                ViewEffect.ErrorCreateChatroom(err)
            )
        } finally {
            // HIDE Progress Indicator
            progressCreateChatroom.send(false)
        }

    }

    interface ViewState {
        /**
         * Emits [true] if Room name value is valid. Otherwise, emits [false].
         */
        fun validationRoomName(): Flow<Boolean>

        /**
         * Emits [true] if Room name is valid. Otherwise, emits [false].
         */
        fun enableSubmit(): Flow<Boolean>

        /**
         * Emits [true] upon start Create Chatroom operation. Emits [false] when done.
         */
        fun progressCreateChatroom(): Flow<Boolean>
    }

    sealed class ViewEffect {
        data class SuccessCreateChatroom(val room: ChatRoom) : ViewEffect()
        data class ErrorCreateChatroom(val err: SportsTalkException) : ViewEffect()
    }

    companion object {
        private val REGEX_ROOMNAME = "^(([a-zA-Z]+)(\\s)*([a-zA-Z0-9]+\$)?){4,}"
    }

}