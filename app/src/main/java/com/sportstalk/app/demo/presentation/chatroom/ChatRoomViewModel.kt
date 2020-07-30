package com.sportstalk.app.demo.presentation.chatroom

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.api.ChatClient
import com.sportstalk.api.polling.coroutines.allEventUpdates
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.models.SportsTalkException
import com.sportstalk.models.chat.*
import com.sportstalk.models.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
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

    private val progressJoinRoom = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressExitRoom = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressListPreviousEvents = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressSendChatMessage = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressRemoveMessage = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressReportMessage = Channel<Boolean>(Channel.RENDEZVOUS)
    private val quotedReply = ConflatedBroadcastChannel<ChatEvent>()

    private lateinit var previouseventscursor: String

    private val roomName = ConflatedBroadcastChannel<String>()
    private val attendeesCount = ConflatedBroadcastChannel<Long>()
    private val chatEvents = ConflatedBroadcastChannel<List<ChatEvent>>()

    val state = object : ViewState {
        override fun roomName(): Flow<String> =
            roomName
                .asFlow()
                .take(1)

        override fun attendeesCount(): Flow<Long> =
            attendeesCount
                .asFlow()
                .take(1)

        override fun progressJoinRoom(): Flow<Boolean> =
            progressJoinRoom.receiveAsFlow()

        override fun progressExitRoom(): Flow<Boolean> =
            progressExitRoom.receiveAsFlow()

        override fun progressListPreviousEvents(): Flow<Boolean> =
            progressListPreviousEvents.receiveAsFlow()

        override fun progressSendChatMessage(): Flow<Boolean> =
            progressSendChatMessage.receiveAsFlow()

        override fun progressRemoveMessage(): Flow<Boolean> =
            progressRemoveMessage.receiveAsFlow()

        override fun progressReportMessage(): Flow<Boolean> =
            progressReportMessage.receiveAsFlow()

        override fun quotedReply(): Flow<ChatEvent> =
            quotedReply.asFlow()

        override fun chatEvents(): Flow<List<ChatEvent>> =
            chatEvents
                .asFlow()
                .take(1)
    }

    private val _effect = BroadcastChannel<ViewEffect>(Channel.BUFFERED)/*Channel<ViewEffect>(Channel.RENDEZVOUS)*/
    val effect: Flow<ViewEffect>
        get() = _effect
            /*.receiveAsFlow()*/
            .asFlow()

    init {
        // Emit Room Name
        roomName.sendBlocking(room.name ?: "")
        // Emit Room Attendees Count
        attendeesCount.sendBlocking(room.inroom ?: 0L)
    }

    fun joinRoom() {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressJoinRoom.send(true)

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
                chatEvents.send(joinInitialEvents)
                // Keep Previous Events Cursor
                previouseventscursor = response.previouseventscursor ?: ""

                // EMIT Success
                _effect.send(ViewEffect.SuccessJoinRoom(response))

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(
                    ViewEffect.ErrorJoinRoom(err)
                )
            } finally {
                // HIDE Progress Indicator
                progressJoinRoom.send(false)
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
            frequency = 500L,
            lifecycleOwner = lifecycleOwner
        )
                // Filter out empty message(s) generated when performing LIKE action
            .map { it.filter { msg -> msg.body?.isNotEmpty() == true } }
            .onEach { newEvents ->
                val updatedChatEventList = ArrayList(chatEvents.valueOrNull ?: listOf()).apply {
                    newEvents.forEach { newEvent ->
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

                // Emit New Chat Event(s) received
                _effect.send(
                    ViewEffect.ReceiveChatEventUpdates(newEvents)
                )

                // Update overall chat event list
                chatEvents.send(updatedChatEventList)
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
                progressListPreviousEvents.send(true)

                // Perform List Previous Events SDK Operation
                val response = withContext(Dispatchers.IO) {
                    chatClient.listPreviousEvents(
                        chatRoomId = room.id!!,
                        limit = LIST_LIMIT,
                        cursor = previouseventscursor
                    )
                }

                // EMIT Success
                _effect.send(
                    ViewEffect.SuccessListPreviousEvents(response.events)
                )

                // KEEP cursor
                previouseventscursor = response.cursor ?: ""

            } catch (err: SportsTalkException) {
                // EMIT error
                _effect.send(ViewEffect.ErrorListPreviousEvents(err))
            } finally {
                // HIDE Progress Indicator
                progressListPreviousEvents.send(false)
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
            quotedReply.valueOrNull != null && quotedReply.value != PLACEHOLDER_CLEAR_REPLY -> {
                sendQuotedReply(
                    message = message,
                    customid = customid,
                    custompayload = custompayload,
                    replyTo = quotedReply.value
                )
            }
            else -> {
                viewModelScope.launch {
                    try {
                        // DISPLAY Progress Indicator
                        progressSendChatMessage.send(true)

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
                        _effect.send(ViewEffect.ChatMessageSent(response))

                    } catch (err: SportsTalkException) {
                        // EMIT Error
                        _effect.send(ViewEffect.ErrorSendChatMessage(err))

                    } finally {
                        // HIDE Progress Indicator
                        progressSendChatMessage.send(false)
                    }
                }
            }
        }
    }

    fun prepareQuotedReply(replyTo: ChatEvent) =
        this@ChatRoomViewModel.quotedReply.sendBlocking(replyTo)

    fun clearQuotedReply() =
        this@ChatRoomViewModel.quotedReply.sendBlocking(PLACEHOLDER_CLEAR_REPLY)

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
                progressSendChatMessage.send(true)

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
                _effect.send(
                    ViewEffect.QuotedReplySent(response)
                )

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(
                    ViewEffect.ErrorSendQuotedReply(err)
                )
            } finally {
                // HIDE Progress Indicator
                progressSendChatMessage.send(false)

                // Clear Prepared Quoted Reply
                this@ChatRoomViewModel.quotedReply.send(PLACEHOLDER_CLEAR_REPLY)
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
                progressSendChatMessage.send(true)

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
                _effect.send(ViewEffect.ThreadedReplySent(response))
            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(
                    ViewEffect.ErrorSendThreadedReply(err)
                )
            } finally {
                // HIDE Progress Indicator
                progressSendChatMessage.send(false)
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
                progressReportMessage.send(true)

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
                _effect.send(ViewEffect.SuccessReportMessage(response))

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(ViewEffect.ErrorReportMessage(err))
            } finally {
                // HIDE Progress Indicator
                progressReportMessage.send(false)
            }

        }
    }

    fun reactToAMessage(event: ChatEvent, hasAlreadyReacted: Boolean) {
        viewModelScope.launch {

            try {
                // Perform React To a Message SDK Operation
                val response = withContext(Dispatchers.IO) {
                    chatClient.reactToEvent(
                        chatRoomId = room.id!!,
                        eventId = event.id!!,
                        request = ReactToAMessageRequest(
                            userid = user.userid!!,
                            reaction = EventReaction.LIKE,
                            reacted = !hasAlreadyReacted
                        )
                    )
                }

                // Emit Success
                var replyTo: ChatEvent? = response.replyto
                while (replyTo != null) {
                    if(replyTo.id == event.id) {
                        _effect.send(
                            ViewEffect.SuccessReactToAMessage(replyTo)
                        )
                        break
                    }

                    replyTo = replyTo?.replyto
                }

            } catch (err: SportsTalkException) {
                // Emit Error
                _effect.send(ViewEffect.ErrorReactToAMessage(err))
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
                progressRemoveMessage.send(true)

                val response = withContext(Dispatchers.IO) {
                    when(isPermanentDelete) {
                        // Perform Permanent Delete
                        true -> {
                            chatClient.permanentlyDeleteEvent(
                                chatRoomId = room.id!!,
                                userid = user.userid!!,
                                eventId = which.id!!,
                                permanentifnoreplies = permanentifnoreplies
                            )
                        }
                        // Perform Flag Event as Deleted
                        false -> {
                            chatClient.flagEventLogicallyDeleted(
                                chatRoomId = room.id!!,
                                userid = user.userid!!,
                                eventId = which.id!!,
                                permanentifnoreplies = permanentifnoreplies
                            )
                        }
                    }
                }

                // EMIT Success
                _effect.send(
                    ViewEffect.SuccessRemoveMessage(response)
                )

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(ViewEffect.ErrorReactToAMessage(err))
            } finally {
                // HIDE Progress Indicator
                progressRemoveMessage.send(false)
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
                progressExitRoom.send(true)
                // Perform `Exit Room` SDK Operation
                val response = withContext(Dispatchers.IO) {
                    chatClient.exitRoom(
                        chatRoomId = room.id!!,
                        userId = user.userid!!
                    )
                }

                // EMIT Success
                _effect.send(ViewEffect.SuccessExitRoom())

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(ViewEffect.ErrorExitRoom(err))
            } finally {
                // HIDE Progress Indicator
                progressExitRoom.send(false)
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
        data class ThreadedReplySent(val response: ExecuteChatCommandResponse) : ViewEffect()
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
    }

    companion object {
        private const val LIST_LIMIT = 10

        val PLACEHOLDER_CLEAR_REPLY = ChatEvent()
    }

}