package com.sportstalk.app.demo.presentation.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.coroutine.api.ChatClient
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.app.demo.presentation.listrooms.AdminListChatRoomsViewModel
import com.sportstalk.datamodels.SportsTalkException
import com.sportstalk.datamodels.chat.*
import com.sportstalk.datamodels.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateChatroomViewModel(
    private val room: ChatRoom,
    private val user: User,
    /*
    * Typical use case to access SDK client instance:
    *   val config = ClientConfig(appId = "...", apiToken = "...", endpoint = "...")
    *   // Instantiate via Factory
    *   val chatClient = SportsTalk247.ChatClient(config = config)
    */
    private val chatClient: ChatClient,
    val preferences: SportsTalkDemoPreferences
) : ViewModel() {

    private val chatroomDetails = MutableStateFlow<ChatRoom?>(null)
    private val roomName = MutableStateFlow<String?>(null)
    private val roomDescription = MutableStateFlow<String?>(null)
    private val roomCustomId = MutableStateFlow<String?>(null)
    private val roomAction = MutableStateFlow<Boolean>(true)
    private val roomEnterExit = MutableStateFlow<Boolean>(true)
    private val roomIsOpen = MutableStateFlow<Boolean>(true)
    private val roomProfanityEnabled = MutableStateFlow<Boolean>(true)

    val state = object : ViewState {
        override fun initialRoomName(): Flow<String?> =
            chatroomDetails.asStateFlow()
                .map { it?.name }

        override fun initialRoomDescription(): Flow<String?> =
            chatroomDetails.asStateFlow()
                .map { it?.description }

        override fun initialRoomCustomId(): Flow<String?> =
            chatroomDetails.asStateFlow()
                .map { it?.customid }

        override fun initialRoomAction(): Flow<Boolean?> =
            chatroomDetails.asStateFlow()
                .map { it?.enableactions }

        override fun initialRoomEnterExit(): Flow<Boolean?> =
            chatroomDetails.asStateFlow()
                .map { it?.enableenterandexit }

        override fun initialRoomIsOpen(): Flow<Boolean?> =
            chatroomDetails.asStateFlow()
                .map { it?.open }

        override fun initialRoomProfanityFilter(): Flow<Boolean?> =
            chatroomDetails.asStateFlow()
                .map { it?.enableprofanityfilter }

        override fun progressGetChatroomDetails(): Flow<Boolean> =
            progressGetChatroomDetails.consumeAsFlow()

        override fun validationRoomName(): Flow<Boolean> =
            validationRoomName.consumeAsFlow()

        override fun enableSave(): Flow<Boolean> =
            enableSave.consumeAsFlow()

        override fun progressUpdateChatroom(): Flow<Boolean> =
            progressUpdateChatroom.consumeAsFlow()

        override fun progressDeleteAllEventsInRoom(): Flow<Boolean> =
            progressDeleteAllEventsInRoom.consumeAsFlow()

        override fun progressDeleteRoom(): Flow<Boolean> =
            progressDeleteRoom.consumeAsFlow()

        override fun progressSendAnnouncement(): Flow<Boolean> =
            progressSendAnnouncement.consumeAsFlow()

        override fun roomAdded(): Flow<String?> =
            chatroomDetails.asStateFlow()
                .map { it?.added }

        override fun roomModified(): Flow<String?> =
            chatroomDetails.asStateFlow()
                .map { it?.whenmodified }

        override fun roomModeration(): Flow<String?> =
            chatroomDetails.asStateFlow()
                .map { it?.moderation }

        override fun roomMaxReports(): Flow<Long?> =
            chatroomDetails.asStateFlow()
                .map { it?.maxreports }

        override fun roomAttendeesCount(): Flow<Long?> =
            chatroomDetails.asStateFlow()
                .map { it?.inroom }
    }

    private val _effect = Channel<ViewEffect>(Channel.RENDEZVOUS)
    val effect: Flow<ViewEffect>
        get() = _effect.consumeAsFlow()

    private val validationRoomName = Channel<Boolean>(Channel.RENDEZVOUS)
    private val enableSave = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressGetChatroomDetails = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressUpdateChatroom = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressDeleteAllEventsInRoom = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressDeleteRoom = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressSendAnnouncement = Channel<Boolean>(Channel.RENDEZVOUS)

    init {
        roomName
            .asStateFlow()
            .map {
                Regex(REGEX_ROOMNAME).containsMatchIn(it ?: "")
            }
            .onEach { isValid -> validationRoomName.send(isValid) }
            .onEach { isValid -> enableSave.send(isValid) }
            .launchIn(viewModelScope)
    }

    fun roomName(roomName: String) =
        this.roomName.tryEmit(roomName)

    fun roomDescription(roomDescription: String) =
        this.roomDescription.tryEmit(roomDescription)

    fun roomCustomId(roomCustomId: String) =
        this.roomCustomId.tryEmit(roomCustomId)

    fun roomAction(roomAction: Boolean) =
        this.roomAction.tryEmit(roomAction)

    fun roomEnterExit(roomEnterExit: Boolean) =
        this.roomEnterExit.tryEmit(roomEnterExit)

    fun roomIsOpen(roomIsOpen: Boolean) =
        this.roomIsOpen.tryEmit(roomIsOpen)

    fun roomProfanityEnabled(roomProfanityEnabled: Boolean) =
        this.roomProfanityEnabled.tryEmit(roomProfanityEnabled)

    fun getChatroomDetails() {
        viewModelScope.launch {
            performGetChatroomDetails()
        }
    }

    private suspend fun performGetChatroomDetails() {
        val roomId = room.id ?: return
        try {
            // DISPLAY Progress Indicator
            progressGetChatroomDetails.send(true)

            val response = withContext(Dispatchers.IO) {
                // Get Room Details
                val getRoomDetails = chatClient.getRoomDetails(chatRoomId = roomId)
                // Then, Perform Join Room Operation
                chatClient.joinRoom(
                    chatRoomId = getRoomDetails.id!!,
                    request = JoinChatRoomRequest(
                        userid = user.userid!!,
                        handle = user.handle
                    )
                )
            }


            // EMIT Success
            chatroomDetails.emit(response.room!!)
        } catch (err: SportsTalkException) {
            // EMIT Error
            _effect.send(
                ViewEffect.ErrorGetChatroomDetails(err)
            )
        } finally {
            // HIDE Progress Indicator
            progressGetChatroomDetails.send(false)
        }
    }

    fun save() {
        viewModelScope.launch { performUpdateChatroom() }
    }

    private suspend fun performUpdateChatroom() {
        try {
            // DISPLAY Progress Indicator
            progressUpdateChatroom.send(true)

            val response = withContext(Dispatchers.IO) {
                chatClient.updateRoom(
                    chatRoomId = chatroomDetails.value?.id!!,
                    request = UpdateChatRoomRequest(
                        customid = roomCustomId.value,
                        name = roomName.value,
                        description = roomDescription.value,
                        enableactions = roomAction.value,
                        enableenterandexit = roomEnterExit.value,
                        roomisopen = roomIsOpen.value,
                        enableprofanityfilter = roomProfanityEnabled.value
                    )
                )
            }

            // EMIT response
            _effect.send(
                ViewEffect.SuccessUpdateChatroom(response)
            )

        } catch (err: SportsTalkException) {
            // EMIT Error
            _effect.send(
                ViewEffect.ErrorUpdateChatroom(err)
            )
        } finally {
            // HIDE Progress Indicator
            progressUpdateChatroom.send(false)
        }

    }

    fun deleteAllChatEvents() {
        viewModelScope.launch {
            performDeleteAllChatEvents()
        }
    }

    private suspend fun performDeleteAllChatEvents() {
        try {
            // DISPLAY Progress Indicator
            progressDeleteAllEventsInRoom.send(true)
            // Execute chat command with appropriate keyword
            val response = withContext(Dispatchers.IO) {
                chatClient.executeChatCommand(
                    chatRoomId = chatroomDetails.value?.id!!,
                    request = ExecuteChatCommandRequest(
                        command = "*deleteallevents zola",
                        userid = user.userid!!
                    )
                )
            }

            // Emit Success
            _effect.send(ViewEffect.SuccessDeleteAllEventsInRoom(response))

        } catch (err: SportsTalkException) {
            // Emit Error
            _effect.send(ViewEffect.ErrorDeleteAllEventsInRoom(err))
        } finally {
            // HIDe Progress Indicator
            progressDeleteAllEventsInRoom.send(false)
        }
    }

    fun deleteChatroom() {
        viewModelScope.launch {
            performDeleteChatroom()
        }
    }

    private suspend fun performDeleteChatroom() {
        try {
            // DISPLAY Progress Indicator
            progressDeleteRoom.send(true)

            val response = withContext(Dispatchers.IO) {
                chatClient.deleteRoom(
                    chatRoomId = chatroomDetails.value?.id!!
                )
            }

            // EMIT Success
            _effect.send(ViewEffect.SuccessDeleteRoom(response))

        } catch (err: SportsTalkException) {
            // EMIT Error
            _effect.send(ViewEffect.ErrorDeleteRoom(err))
        } finally {
            // HIDe Progress Indicator
            progressDeleteRoom.send(false)
        }
    }

    fun sendAnnouncement(message: String) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressSendAnnouncement.send(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.executeChatCommand(
                        chatRoomId = room.id!!,
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
         * Emits initial Room info
         */
        fun initialRoomName(): Flow<String?>
        fun initialRoomDescription(): Flow<String?>
        fun initialRoomCustomId(): Flow<String?>
        fun initialRoomAction(): Flow<Boolean?>
        fun initialRoomEnterExit(): Flow<Boolean?>
        fun initialRoomIsOpen(): Flow<Boolean?>
        fun initialRoomProfanityFilter(): Flow<Boolean?>

        /**
         * Emits [true] upon start Get Chatroom Details operation. Emits [false] when done.
         */
        fun progressGetChatroomDetails(): Flow<Boolean>

        /**
         * Emits [true] if Room name value is valid. Otherwise, emits [false].
         */
        fun validationRoomName(): Flow<Boolean>

        /**
         * Emits [true] if Room name is valid. Otherwise, emits [false].
         */
        fun enableSave(): Flow<Boolean>

        /**
         * Emits [true] upon start Create Chatroom operation. Emits [false] when done.
         */
        fun progressUpdateChatroom(): Flow<Boolean>

        /**
         * Emits [true] upon start Delete All Events in Room operation. Emits [false] when done.
         */
        fun progressDeleteAllEventsInRoom(): Flow<Boolean>

        /**
         * Emits [true] upon start Delete Room operation. Emits [false] when done.
         */
        fun progressDeleteRoom(): Flow<Boolean>

        /**
         * Emits [true] upon start SDK Execute Chat Command Operation(Announcement). Emits [false] when done.
         */
        fun progressSendAnnouncement(): Flow<Boolean>

        fun roomAdded(): Flow<String?>

        fun roomModified(): Flow<String?>

        fun roomModeration(): Flow<String?>

        fun roomMaxReports(): Flow<Long?>

        fun roomAttendeesCount(): Flow<Long?>
    }

    sealed class ViewEffect {
        data class ErrorGetChatroomDetails(val err: SportsTalkException): ViewEffect()
        data class SuccessUpdateChatroom(val room: ChatRoom) : ViewEffect()
        data class ErrorUpdateChatroom(val err: SportsTalkException) : ViewEffect()
        data class SuccessDeleteAllEventsInRoom(val response: ExecuteChatCommandResponse): ViewEffect()
        data class ErrorDeleteAllEventsInRoom(val err: SportsTalkException): ViewEffect()
        data class SuccessDeleteRoom(val response: DeleteChatRoomResponse): ViewEffect()
        data class ErrorDeleteRoom(val err: SportsTalkException): ViewEffect()
        data class SuccessSendAnnouncement(val response: ExecuteChatCommandResponse): ViewEffect()
        data class ErrorSendAnnouncement(val err: SportsTalkException): ViewEffect()
    }

    companion object {
        private val REGEX_ROOMNAME = "^(([a-zA-Z]+)(\\s)*([a-zA-Z0-9]+\$)?){4,}"
    }

}