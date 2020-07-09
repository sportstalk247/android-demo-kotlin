package com.sportstalk.app.demo.presentation.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.api.ChatClient
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.app.demo.presentation.listrooms.AdminListChatRoomsViewModel
import com.sportstalk.models.SportsTalkException
import com.sportstalk.models.chat.*
import com.sportstalk.models.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
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

    private val chatroomDetails = ConflatedBroadcastChannel<ChatRoom>()
    private val roomName = ConflatedBroadcastChannel<String>()
    private val roomDescription = ConflatedBroadcastChannel<String>()
    private val roomCustomId = ConflatedBroadcastChannel<String>()
    private val roomAction = ConflatedBroadcastChannel<Boolean>(true)
    private val roomEnterExit = ConflatedBroadcastChannel<Boolean>(true)
    private val roomIsOpen = ConflatedBroadcastChannel<Boolean>(true)
    private val roomProfanityEnabled = ConflatedBroadcastChannel<Boolean>(true)

    val state = object : ViewState {
        override fun initialRoomName(): Flow<String?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.name }

        override fun initialRoomDescription(): Flow<String?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.description }

        override fun initialRoomCustomId(): Flow<String?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.customid }

        override fun initialRoomAction(): Flow<Boolean?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.enableactions }

        override fun initialRoomEnterExit(): Flow<Boolean?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.enableenterandexit }

        override fun initialRoomIsOpen(): Flow<Boolean?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.open }

        override fun initialRoomProfanityFilter(): Flow<Boolean?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.enableprofanityfilter }

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
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.added }

        override fun roomModified(): Flow<String?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.whenmodified }

        override fun roomModeration(): Flow<String?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.moderation }

        override fun roomMaxReports(): Flow<Long?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.maxreports }

        override fun roomAttendeesCount(): Flow<Long?> =
            chatroomDetails.openSubscription()
                .consumeAsFlow()
                .map { it.inroom }
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
            .asFlow()
            .map {
                Regex(REGEX_ROOMNAME).containsMatchIn(it)
            }
            .onEach { isValid -> validationRoomName.send(isValid) }
            .onEach { isValid -> enableSave.send(isValid) }
            .launchIn(viewModelScope)
    }

    fun roomName(roomName: String) =
        this.roomName.sendBlocking(roomName)

    fun roomDescription(roomDescription: String) =
        this.roomDescription.sendBlocking(roomDescription)

    fun roomCustomId(roomCustomId: String) =
        this.roomCustomId.sendBlocking(roomCustomId)

    fun roomAction(roomAction: Boolean) =
        this.roomAction.sendBlocking(roomAction)

    fun roomEnterExit(roomEnterExit: Boolean) =
        this.roomEnterExit.sendBlocking(roomEnterExit)

    fun roomIsOpen(roomIsOpen: Boolean) =
        this.roomIsOpen.sendBlocking(roomIsOpen)

    fun roomProfanityEnabled(roomProfanityEnabled: Boolean) =
        this.roomProfanityEnabled.sendBlocking(roomProfanityEnabled)

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
                val getRoomDetails = chatClient.getRoomDetails(chatRoomId = roomId).await()
                // Then, Perform Join Room Operation
                chatClient.joinRoom(
                    chatRoomId = getRoomDetails.id!!,
                    request = JoinChatRoomRequest(
                        userid = user.userid!!,
                        handle = user.handle
                    )
                )
                    .await()
            }


            // EMIT Success
            chatroomDetails.send(response.room!!)
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
                    chatRoomId = chatroomDetails.valueOrNull?.id!!,
                    request = UpdateChatRoomRequest(
                        customid = roomCustomId.valueOrNull,
                        name = roomName.valueOrNull,
                        description = roomDescription.valueOrNull,
                        enableactions = roomAction.valueOrNull,
                        enableenterandexit = roomEnterExit.valueOrNull,
                        roomisopen = roomIsOpen.valueOrNull,
                        enableprofanityfilter = roomProfanityEnabled.valueOrNull
                    )
                )
                    .await()
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
                    chatRoomId = chatroomDetails.valueOrNull?.id!!,
                    request = ExecuteChatCommandRequest(
                        command = "*deleteallevents zola",
                        userid = user.userid!!
                    )
                )
                    .await()
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
                    chatRoomId = chatroomDetails.valueOrNull?.id!!
                )
                    .await()
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
                            // TODO:: ADMIN ID For Send Announcement
                            userid = preferences.currentUser?.userid ?: "0M5fL2WAbQGiiMS4", // This is just a temporary ID
                            customtype = "announcement"
                        )
                    )
                        .await()
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