package com.sportstalk.app.demo.presentation.chatroom

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.coroutine.api.ChatClient
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.coroutine.api.polling.allEventUpdates
import com.sportstalk.datamodels.SportsTalkException
import com.sportstalk.datamodels.chat.*
import com.sportstalk.datamodels.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatRoomViewModel(
    private val room: ChatRoom,
    private val user: User,
    /*
    * Typical use case to access SDK client instance:
    *   val config = ClientConfig(appId = "...", apiToken = "...", endpoint = "...")
    *   // Instantiate via Factory
    *   val chatClient = SportsTalk247.ChatClient(config = config)
    */
    private val chatClient: ChatClient,
    private val preferences: SportsTalkDemoPreferences
) : ViewModel() {

    private val progressJoinRoom = MutableSharedFlow<Boolean>()
    private val progressExitRoom = MutableSharedFlow<Boolean>()
    private val progressListPreviousEvents = MutableSharedFlow<Boolean>()
    private val progressSendChatMessage = MutableSharedFlow<Boolean>()
    private val progressRemoveMessage = MutableSharedFlow<Boolean>()
    private val progressReportMessage = MutableSharedFlow<Boolean>()
    private val quotedReply = MutableStateFlow<ChatEvent?>(null)

    private lateinit var previouseventscursor: String

    private val roomName = MutableStateFlow<String?>(null)
    private val attendeesCount = MutableStateFlow<Long?>(null)
    private val chatEvents = MutableStateFlow<List<ChatEvent>?>(listOf())
    private val progressBounceUser = MutableSharedFlow<Boolean>()

    val state = object : ViewState {
        override fun roomName(): Flow<String> =
            roomName
                .asStateFlow()
                .filterNotNull()

        override fun attendeesCount(): Flow<Long> =
            attendeesCount
                .asStateFlow()
                .filterNotNull()

        override fun progressJoinRoom(): Flow<Boolean> =
            progressJoinRoom
                .apply { resetReplayCache() }
                .asSharedFlow()

        override fun progressExitRoom(): Flow<Boolean> =
            progressExitRoom
                .apply { resetReplayCache() }
                .asSharedFlow()
                .distinctUntilChanged()

        override fun progressListPreviousEvents(): Flow<Boolean> =
            progressListPreviousEvents
                .apply { resetReplayCache() }
                .asSharedFlow()

        override fun progressSendChatMessage(): Flow<Boolean> =
            progressSendChatMessage
                .apply { resetReplayCache() }
                .asSharedFlow()

        override fun progressRemoveMessage(): Flow<Boolean> =
            progressRemoveMessage
                .apply { resetReplayCache() }
                .asSharedFlow()

        override fun progressReportMessage(): Flow<Boolean> =
            progressReportMessage
                .apply { resetReplayCache() }
                .asSharedFlow()

        override fun quotedReply(): Flow<ChatEvent> =
            quotedReply
                .asStateFlow()
                .filterNotNull()

        override fun chatEvents(): Flow<List<ChatEvent>> =
            chatEvents
                .asStateFlow()
                .filterNotNull()

        override fun progressBounceUser(): Flow<Boolean> =
            progressBounceUser
                .apply { resetReplayCache() }
                .asSharedFlow()
    }

    private val _effect = MutableSharedFlow<ViewEffect>()
    val effect: Flow<ViewEffect>
        get() =
            _effect
                .apply { resetReplayCache() }
                .asSharedFlow()

    init {
        // Emit Room Name
        roomName.value = room.name ?: ""
        // Emit Room Attendees Count
        attendeesCount.value = room.inroom ?: 0L
    }

    fun joinRoom() {
        if(::previouseventscursor.isInitialized) return

        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressJoinRoom.emit(true)

                // Perform Join Room
                val response = withContext(Dispatchers.IO) {
                    chatClient.joinRoom(
                        chatRoomId = room.id!!,
                        request = JoinChatRoomRequest(
                            userid = user.userid!!,
                            handle = user.handle
                        )
                    )
                }

                // Emit join initial events list
                val joinInitialEvents = response.eventscursor?.events ?: listOf()
                chatEvents.emit(joinInitialEvents)
                // Keep Previous Events Cursor
                previouseventscursor = response.previouseventscursor ?: ""

                // EMIT Success
                _effect.emit(ViewEffect.SuccessJoinRoom(response))

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.emit(
                    ViewEffect.ErrorJoinRoom(err)
                )
            } finally {
                // HIDE Progress Indicator
                progressJoinRoom.emit(false)
            }
        }
    }

    /**
     * Internally invoked after join room or it can be explicitly called on-demand.
     */
    lateinit var jobAllEventUpdates: Job
    fun startListeningToChatUpdates(lifecycleOwner: LifecycleOwner) {
        chatClient.startListeningToChatUpdates(forRoomId = room.id!!)

        // Make sure that only 1 event updates job is running
        if (::jobAllEventUpdates.isInitialized && jobAllEventUpdates.isActive) {
            chatClient.stopListeningToChatUpdates(forRoomId = room.id!!)
            jobAllEventUpdates.cancel()
        }

        // Subscribe to Chat Event Updates
        jobAllEventUpdates = chatClient.allEventUpdates(
            chatRoomId = room.id!!,
            frequency = 1000L
        )
            // Filter out empty message(s) generated when performing LIKE action
            .map { it.filter { msg -> msg.body?.isNotEmpty() == true } }
            .onEach { newEvents ->
                // Emit New Chat Event(s) received
                _effect.emit(
                    ViewEffect.ReceiveChatEventUpdates(newEvents)
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Invoked on exit room or can be invoked explicitly
     */
    fun stopListeningFromChatUpdates() {
        chatClient.stopListeningToChatUpdates(forRoomId = room.id!!)
        if (::jobAllEventUpdates.isInitialized && !jobAllEventUpdates.isCancelled) jobAllEventUpdates.cancel()
    }

    /**
     * Invoked on scroll to previous chat event(s)
     * - Perform `List Previous Events` SDK Operation
     */
    fun listPreviousEvents() {
        // Do not perform if previous cursor is empty(this means no more previous events available)
        if (!::previouseventscursor.isInitialized || previouseventscursor.isEmpty()) {
            return
        }

        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressListPreviousEvents.emit(true)

                // Perform List Previous Events SDK Operation
                val response = withContext(Dispatchers.IO) {
                    chatClient.listPreviousEvents(
                        chatRoomId = room.id!!,
                        limit = LIST_LIMIT,
                        cursor = previouseventscursor
                    )
                }

                val updatedChatEventList = ArrayList(chatEvents.value ?: listOf()).apply {
                    response.events.forEach { newEvent ->
                        val index = indexOfFirst { oldEvent -> oldEvent.id == newEvent.id }
                        if (index >= 0) {
                            set(index, newEvent)
                        } else {
                            add(0, newEvent)
                        }
                    }
                }
                    // Filter out "reaction" event type
                    .filter { it.eventtype != EventType.REACTION }
                    .sortedByDescending { it.ts }
                    .distinctBy { it.id }

                // EMIT Success
                _effect.emit(
                    ViewEffect.SuccessListPreviousEvents(response.events)
                )
                // Emit updated Chat Event List
                chatEvents.emit(updatedChatEventList)

                // KEEP cursor
                previouseventscursor = response.cursor ?: ""

            } catch (err: SportsTalkException) {
                // EMIT error
                _effect.emit(ViewEffect.ErrorListPreviousEvents(err))
            } finally {
                // HIDE Progress Indicator
                progressListPreviousEvents.emit(false)
            }
        }
    }

    /**
     * Perform `Execute Chat Command` SDK Operation
     */
    fun sendChatMessage(
        message: String,
        customid: String? = null,
        custompayload: String? = null,
        customtype: String? = null
    ) {

        when {
            quotedReply.value != null && quotedReply.value != PLACEHOLDER_CLEAR_REPLY -> {
                sendQuotedReply(
                    message = message,
                    customid = customid,
                    custompayload = custompayload,
                    replyTo = quotedReply.value!!
                )
            }
            else -> {
                viewModelScope.launch {
                    try {
                        // DISPLAY Progress Indicator
                        progressSendChatMessage.emit(true)

                        val response = withContext(Dispatchers.IO) {
                            chatClient.executeChatCommand(
                                chatRoomId = room.id!!,
                                request = ExecuteChatCommandRequest(
                                    command = message,
                                    userid = user.userid!!
                                )
                            )
                        }

                        // Emit SUCCESS Send Chat Message
                        _effect.emit(ViewEffect.ChatMessageSent(response))

                    } catch (err: SportsTalkException) {
                        // EMIT Error
                        _effect.emit(ViewEffect.ErrorSendChatMessage(err))

                    } finally {
                        // HIDE Progress Indicator
                        progressSendChatMessage.emit(false)
                    }
                }
            }
        }
    }

    fun prepareQuotedReply(replyTo: ChatEvent) {
        this@ChatRoomViewModel.quotedReply.value = replyTo
    }

    fun clearQuotedReply() {
        this@ChatRoomViewModel.quotedReply.value = PLACEHOLDER_CLEAR_REPLY
    }

    /**
     * Perform `Reply to a Message (Quoted)` SDK Operation
     */
    fun sendQuotedReply(
        message: String,
        customid: String? = null, // OPTIONAL
        custompayload: String? = null, // OPTIONAL
        customfield1: String? = null, // OPTIONAL
        customfield2: String? = null, // OPTIONAL
        customtags: List<String>? = null, // OPTIONAL
        replyTo: ChatEvent
    ) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressSendChatMessage.emit(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.sendQuotedReply(
                        chatRoomId = room.id!!,
                        request = SendQuotedReplyRequest(
                            userid = user.userid!!,
                            body = message,
                            customid = customid,
                            custompayload = custompayload,
                            customfield1 = customfield1,
                            customfield2 = customfield2,
                            customtags = customtags
                        ),
                        replyTo = replyTo.id!!
                    )
                }

                // EMIT SUCCESS Quoted Reply
                _effect.emit(
                    ViewEffect.QuotedReplySent(response)
                )

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.emit(
                    ViewEffect.ErrorSendQuotedReply(err)
                )
            } finally {
                // HIDE Progress Indicator
                progressSendChatMessage.emit(false)

                // Clear Prepared Quoted Reply
                this@ChatRoomViewModel.quotedReply.value = PLACEHOLDER_CLEAR_REPLY
            }
        }
    }

    /**
     * Perform `Reply to a Message (Threaded)` SDK Operation
     */
    fun sendThreadedReply(
        message: String,
        customid: String? = null, // OPTIONAL
        custompayload: String? = null, // OPTIONAL
        customtype: String? = null, // OPTIONAL
        replyTo: ChatEvent
    ) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressSendChatMessage.emit(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.sendThreadedReply(
                        chatRoomId = room.id!!,
                        replyTo = replyTo.id!!,
                        request = SendThreadedReplyRequest(
                            body = message,
                            userid = user.userid!!,
                            customid = customid,
                            custompayload = custompayload,
                            customtype = customtype
                        )
                    )
                }

                // EMIT SUCCESS Quoted Reply
                _effect.emit(ViewEffect.ThreadedReplySent(response))
            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.emit(
                    ViewEffect.ErrorSendThreadedReply(err)
                )
            } finally {
                // HIDE Progress Indicator
                progressSendChatMessage.emit(false)
            }
        }
    }

    /**
     * Perform `Report Message` SDK Operation
     */
    fun reportMessage(which: ChatEvent, reporttype: String) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressReportMessage.emit(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.reportMessage(
                        chatRoomId = room.id!!,
                        eventId = which.id!!,
                        request = ReportMessageRequest(
                            reporttype = reporttype,
                            userid = user.userid!!
                        )
                    )
                }

                // EMIT Success
                _effect.emit(ViewEffect.SuccessReportMessage(response))

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.emit(ViewEffect.ErrorReportMessage(err))
            } finally {
                // HIDE Progress Indicator
                progressReportMessage.emit(false)
            }

        }
    }

    fun reactToAMessage(event: ChatEvent/*, hasAlreadyReacted: Boolean*/) {
        viewModelScope.launch {

            try {
                val reacted = event.reactions.firstOrNull { r -> r.type == EventReaction.LIKE }
                    ?.users?.any { u -> u.userid == user.userid } ?: false

                // Perform React To a Message SDK Operation
                val response = withContext(Dispatchers.IO) {
                    chatClient.reactToEvent(
                        chatRoomId = room.id!!,
                        eventId = event.id!!,
                        request = ReactToAMessageRequest(
                            userid = user.userid!!,
                            reaction = EventReaction.LIKE,
                            reacted = !reacted
                        )
                    )
                }

                // Emit Success
                var replyTo: ChatEvent? = response.replyto
                while (replyTo != null) {
                    if(replyTo.id == event.id) {
                        _effect.emit(
                            ViewEffect.SuccessReactToAMessage(replyTo)
                        )
                        break
                    }

                    replyTo = replyTo?.replyto
                }

            } catch (err: SportsTalkException) {
                // Emit Error
                _effect.emit(ViewEffect.ErrorReactToAMessage(err))
            }

        }
    }

    fun removeMessage(
        which: ChatEvent,
        isPermanentDelete: Boolean,
        permanentifnoreplies: Boolean? = null
    ) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressRemoveMessage.emit(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.flagEventLogicallyDeleted(
                        chatRoomId = room.id!!,
                        userid = user.userid!!,
                        eventId = which.id!!,
                        deleted = isPermanentDelete,
                        permanentifnoreplies = permanentifnoreplies,
                    )
                }

                // EMIT Success
                _effect.emit(
                    ViewEffect.SuccessRemoveMessage(response)
                )

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.emit(ViewEffect.ErrorReactToAMessage(err))
            } finally {
                // HIDE Progress Indicator
                progressRemoveMessage.emit(false)
            }
        }
    }

    /**
     * Invoked before Activity/Fragment View gets destroyed.
     * - Perform `Exit Room` SDK Operation
     */
    fun exitRoom() {
        // Unsubscribe to Chat Event Updates
        stopListeningFromChatUpdates()
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressExitRoom.emit(true)
                // Perform `Exit Room` SDK Operation
                val response = withContext(Dispatchers.IO) {
                    chatClient.exitRoom(
                        chatRoomId = room.id!!,
                        userId = user.userid!!
                    )
                }

                // EMIT Success
                _effect.emit(ViewEffect.SuccessExitRoom())

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.emit(ViewEffect.ErrorExitRoom(err))
            } finally {
                // HIDE Progress Indicator
                progressExitRoom.emit(false)
            }
        }
    }

    fun bounceUser(who: User, bounce: Boolean, announcement: String? = null) {
        viewModelScope.launch {
            try {
                // SHOW Progress Indicator
                progressBounceUser.emit(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.bounceUser(
                        chatRoomId = room.id!!,
                        request = BounceUserRequest(
                            userid = who.userid!!,
                            bounce = bounce,
                            announcement = announcement
                        )
                    )
                }

                // EMIT Success
                if(bounce) {
                    _effect.emit(ViewEffect.SuccessBounceUser(response))
                } else {
                    _effect.emit(
                        ViewEffect.SuccessUnbounceUser(
                            response.copy(
                                event = ChatEvent(
                                    body = announcement,
                                    userid = who.userid,
                                    user = who
                                )
                            )
                        )
                    )
                }

            } catch (err: SportsTalkException) {
                // EMIT ERROR
                _effect.emit(ViewEffect.ErrorBounceUser(err))
            } finally {
                // HIDE Progress Indicator
                progressBounceUser.emit(false)
            }
        }
    }

    interface ViewState {

        /**
         * Emits [ChatRoom.name].
         */
        fun roomName(): Flow<String>

        /**
         * Emits [ChatRoom.inroom].
         */
        fun attendeesCount(): Flow<Long>

        /**
         * Emits [true] upon start Join Room SDK operation. Emits [false] when done.
         */
        fun progressJoinRoom(): Flow<Boolean>

        /**
         * Emits [true] upon start Exit Room SDK operation. Emits [false] when done.
         */
        fun progressExitRoom(): Flow<Boolean>

        /**
         * Emits [true] upon start List Previous Events SDK operation. Emits [false] when done.
         */
        fun progressListPreviousEvents(): Flow<Boolean>

        /**
         * Emits [true] upon start Execute Chat Command SDK operation. Emits [false] when done.
         */
        fun progressSendChatMessage(): Flow<Boolean>

        /**
         * Emits [true] upon start `Delete Event` or `Flag Message Event as Deleted` SDK operation. Emits [false] when done.
         */
        fun progressRemoveMessage(): Flow<Boolean>

        /**
         * Emits [true] upon start `Report Message` SDK operation. Emits [false] when done.
         */
        fun progressReportMessage(): Flow<Boolean>

        /**
         * Emits an instance of [ChatEvent] the user wants to reply to(quoted).
         * - Displays a reply UI component on top of chat input field.
         * - If emitted instance is [PLACEHOLDER_CLEAR_REPLY], clears the UI component
         */
        fun quotedReply(): Flow<ChatEvent>

        /**
         * Emits the overall list of events(includes results from `previouseventscursor` and `nexteventscursor`)
         */
        fun chatEvents(): Flow<List<ChatEvent>>

        /**
         * Emits [true] upon start `Bounce user`/`Unbounce user` SDK operation. Emits [false] when done.
         */
        fun progressBounceUser(): Flow<Boolean>
    }

    sealed class ViewEffect {
        data class SuccessJoinRoom(val response: JoinChatRoomResponse) : ViewEffect()
        class ErrorJoinRoom(err: SportsTalkException) : ViewEffect()

        data class ReceiveChatEventUpdates(val eventUpdates: List<ChatEvent>) : ViewEffect()
        data class ErrorGetUpdates(val err: SportsTalkException) : ViewEffect()
        data class ChatMessageSent(val response: ExecuteChatCommandResponse) : ViewEffect()
        data class ErrorSendChatMessage(val err: SportsTalkException) : ViewEffect()
        data class QuotedReplySent(val response: ChatEvent) : ViewEffect()
        data class ErrorSendQuotedReply(val err: SportsTalkException) : ViewEffect()
        data class ThreadedReplySent(val response: ChatEvent) : ViewEffect()
        data class ErrorSendThreadedReply(val err: SportsTalkException) : ViewEffect()

        data class SuccessReactToAMessage(val response: ChatEvent) : ViewEffect()
        data class ErrorReactToAMessage(val err: SportsTalkException) : ViewEffect()

        data class SuccessRemoveMessage(val response: DeleteEventResponse) : ViewEffect()
        data class ErrorRemoveMessage(val err: SportsTalkException) : ViewEffect()

        data class SuccessReportMessage(val response: ChatEvent) : ViewEffect()
        data class ErrorReportMessage(val err: SportsTalkException) : ViewEffect()

        class SuccessExitRoom() : ViewEffect()
        data class ErrorExitRoom(val err: SportsTalkException) : ViewEffect()

        data class SuccessListPreviousEvents(val previousEvents: List<ChatEvent>) : ViewEffect()
        data class ErrorListPreviousEvents(val err: SportsTalkException) : ViewEffect()

        data class SuccessBounceUser(val response: BounceUserResponse): ViewEffect()
        data class SuccessUnbounceUser(val response: BounceUserResponse): ViewEffect()
        data class ErrorBounceUser(val err: SportsTalkException): ViewEffect()
    }

    companion object {
        private const val LIST_LIMIT = 10

        val PLACEHOLDER_CLEAR_REPLY = ChatEvent()
    }

}