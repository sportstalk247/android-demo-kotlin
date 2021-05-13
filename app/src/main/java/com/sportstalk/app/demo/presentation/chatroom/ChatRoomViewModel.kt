package com.sportstalk.app.demo.presentation.chatroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.coroutine.api.ChatClient
import com.sportstalk.coroutine.api.polling.allEventUpdates
import com.sportstalk.reactive.rx2.api.ChatClient as RxChatClient
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.datamodels.SportsTalkException
import com.sportstalk.datamodels.chat.*
import com.sportstalk.datamodels.users.User
import com.sportstalk.reactive.rx2.api.polling.allEventUpdates
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

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
    private val rxChatClient: RxChatClient,
    private val preferences: SportsTalkDemoPreferences
) : ViewModel() {

    private val rxDisposeBag = CompositeDisposable()

    private val progressJoinRoom = Channel<Boolean>(Channel.BUFFERED)
    private val progressExitRoom = Channel<Boolean>(Channel.BUFFERED)
    private val progressListPreviousEvents = Channel<Boolean>(Channel.BUFFERED)
    private val progressSendChatMessage = Channel<Boolean>(Channel.BUFFERED)
    private val progressRemoveMessage = Channel<Boolean>(Channel.BUFFERED)
    private val progressReportMessage = Channel<Boolean>(Channel.BUFFERED)
    private val quotedReply = ConflatedBroadcastChannel<ChatEvent?>(null)

    private lateinit var previouseventscursor: String

    private val roomName = ConflatedBroadcastChannel<String?>(null)
    private val attendeesCount = ConflatedBroadcastChannel<Long?>(null)
    private val chatEvents = ConflatedBroadcastChannel<List<ChatEvent>?>(listOf())
    private val progressBounceUser = Channel<Boolean>()

    val state = object : ViewState {
        override fun roomName(): Flow<String> =
            roomName
                .asFlow()
                .filterNotNull()

        override fun attendeesCount(): Flow<Long> =
            attendeesCount
                .asFlow()
                .filterNotNull()

        override fun progressJoinRoom(): Flow<Boolean> =
            progressJoinRoom
                .consumeAsFlow()

        override fun progressExitRoom(): Flow<Boolean> =
            progressExitRoom
                .consumeAsFlow()
                .distinctUntilChanged()

        override fun progressListPreviousEvents(): Flow<Boolean> =
            progressListPreviousEvents
                .consumeAsFlow()

        override fun progressSendChatMessage(): Flow<Boolean> =
            progressSendChatMessage
                .consumeAsFlow()

        override fun progressRemoveMessage(): Flow<Boolean> =
            progressRemoveMessage
                .consumeAsFlow()

        override fun progressReportMessage(): Flow<Boolean> =
            progressReportMessage
                .consumeAsFlow()

        override fun quotedReply(): Flow<ChatEvent> =
            quotedReply
                .asFlow()
                .filterNotNull()

        override fun chatEvents(): Flow<List<ChatEvent>> =
            chatEvents
                .asFlow()
                .filterNotNull()

        override fun progressBounceUser(): Flow<Boolean> =
            progressBounceUser
                .consumeAsFlow()
    }

//    private val _effect = Channel<ViewEffect>()
//    val effect: Flow<ViewEffect>
//        get() =
//            _effect
//                .apply { resetReplayCache() }
//                .asSharedFlow()

    private val rxEffect = BehaviorSubject.create<ViewEffect>()
    val effect: Flowable<ViewEffect>
        get() =
            rxEffect.toFlowable(BackpressureStrategy.LATEST)

    init {
        // Emit Room Name
        roomName.sendBlocking(room.name ?: "")
        // Emit Room Attendees Count
        attendeesCount.sendBlocking(room.inroom ?: 0L)

        // Append to chat event list received event updates
        rxEffect
                .filter { it is ViewEffect.ReceiveChatEventUpdates }
                .map {
                    val eventUpdates = (it as ViewEffect.ReceiveChatEventUpdates).eventUpdates
                    return@map eventUpdates
                            .filter {
                                it.eventtype == EventType.SPEECH
                                        || it.eventtype == EventType.ACTION
                                        || it.eventtype == EventType.REACTION
                                        || it.eventtype == EventType.QUOTE
                                        || it.eventtype == EventType.REPLY
                            } +
                            // For responses triggered by DELETE chat event, must replace with "(deleted)" chat event body
                            eventUpdates.filter {
                                it.eventtype in listOf(EventType.REPLACE, EventType.REMOVE)
                            }
                                    .mapNotNull { rootEvent ->
                                        rootEvent.replyto
                                                ?.copy(
                                                        body = "(deleted)",
                                                        originalbody = rootEvent.replyto?.body
                                                )
                                    }
                }
                .filter { it.isNotEmpty() }
                .toFlowable(BackpressureStrategy.BUFFER)
                .subscribe { newEvents ->
                    val currentList = chatEvents.valueOrNull ?: listOf()
                    val updatedList = (newEvents + currentList)
                            .sortedByDescending { it.ts }
                            .distinctBy { it.id }

                    chatEvents.sendBlocking(updatedList)
                }
                .addTo(rxDisposeBag)
    }

    override fun onCleared() {
        rxDisposeBag.dispose()
        super.onCleared()
    }

    fun joinRoom() {
        if(::previouseventscursor.isInitialized) return

        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressJoinRoom.send(true)

                // Perform Join Room
                val response = withContext(Dispatchers.IO) {
//                    chatClient.joinRoom(
//                        chatRoomId = room.id!!,
//                        request = JoinChatRoomRequest(
//                            userid = user.userid!!,
//                            handle = user.handle
//                        )
//                    )
                    rxChatClient.joinRoom(
                        chatRoomId = room.id!!,
                        request = JoinChatRoomRequest(
                            userid = user.userid!!,
                            handle = user.handle
                        )
                    ).await()
                }

                // Emit join initial events list
                val joinInitialEvents = response.eventscursor?.events ?: listOf()
                chatEvents.send(joinInitialEvents)
                // Keep Previous Events Cursor
                previouseventscursor = response.previouseventscursor ?: ""

                // EMIT Success
//                _effect.send(ViewEffect.SuccessJoinRoom(response))
                rxEffect.onNext(ViewEffect.SuccessJoinRoom(response))

            } catch (err: SportsTalkException) {
                // EMIT Error
//                _effect.send(
//                    ViewEffect.ErrorJoinRoom(err)
//                )
                rxEffect.onNext(
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
    lateinit var disposableAllEventUpdates: Disposable
    fun startListeningToChatUpdates() {
        if(!::previouseventscursor.isInitialized) return
//        chatClient.startListeningToChatUpdates(forRoomId = room.id!!)
//
//        // Make sure that only 1 event updates job is running
//        if (::jobAllEventUpdates.isInitialized && jobAllEventUpdates.isActive) {
//            chatClient.stopListeningToChatUpdates(forRoomId = room.id!!)
//            jobAllEventUpdates.cancel()
//        }
//
//        // Subscribe to Chat Event Updates
//        jobAllEventUpdates = chatClient.allEventUpdates(
//            chatRoomId = room.id!!,
//            frequency = 500L
//        )
//            // Filter out empty message(s) generated when performing LIKE action
//            .map { it.filter { msg -> msg.body?.isNotEmpty() == true } }
//            .onEach { newEvents ->
//                // Emit New Chat Event(s) received
//                _effect.send(
//                    ViewEffect.ReceiveChatEventUpdates(newEvents)
//                )
//            }
//            .launchIn(viewModelScope)

        rxChatClient.startListeningToChatUpdates(forRoomId = room.id!!)

        // Make sure that only 1 event updates job is running
        if (::disposableAllEventUpdates.isInitialized && !disposableAllEventUpdates.isDisposed) {
            rxChatClient.stopListeningToChatUpdates(forRoomId = room.id!!)
            disposableAllEventUpdates.dispose()
            rxDisposeBag.remove(disposableAllEventUpdates)
        }

        disposableAllEventUpdates = rxChatClient.allEventUpdates(
            chatRoomId = room.id!!,
            frequency = 1500L,
            smoothEventUpdates = true,
            eventSpacingMs = 350L,
            maxEventBufferSize = 30
        )
            .onErrorResumeNext(Function { err ->
                // For some reasons, ChatRoom has been deleted and can NO longer be found.
                if(err is SportsTalkException) {
                    if(err.code == 404) {
                        // Emit Error Join Room
                        rxEffect.onNext(
                            ViewEffect.ErrorJoinRoom(err)
                        )
                    }
                }
                Flowable.just(listOf<ChatEvent>())
            })
            // Filter out empty message(s) generated when performing LIKE action
            /*.map { it.filter { msg -> msg.body?.isNotEmpty() == true } }*/
            .subscribe { newEvents ->
                // Emit New Chat Event(s) received
                rxEffect.onNext(
                    ViewEffect.ReceiveChatEventUpdates(newEvents)
                )
            }
            .addTo(rxDisposeBag)
    }

    /**
     * Invoked on exit room or can be invoked explicitly
     */
    fun stopListeningFromChatUpdates() {
//        chatClient.stopListeningToChatUpdates(forRoomId = room.id!!)
//        if (::jobAllEventUpdates.isInitialized && !jobAllEventUpdates.isCancelled) jobAllEventUpdates.cancel()
        rxChatClient.stopListeningToChatUpdates(forRoomId = room.id!!)
        if (::disposableAllEventUpdates.isInitialized && !disposableAllEventUpdates.isDisposed) {
            disposableAllEventUpdates.dispose()
            rxDisposeBag.remove(disposableAllEventUpdates)
        }
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
//                    chatClient.listPreviousEvents(
//                        chatRoomId = room.id!!,
//                        limit = LIST_LIMIT,
//                        cursor = previouseventscursor
//                    )
                    rxChatClient.listPreviousEvents(
                        chatRoomId = room.id!!,
                        limit = LIST_LIMIT,
                        cursor = previouseventscursor
                    ).await()
                }

                // Emit previous Chat Event List
                rxEffect.onNext(
                    ViewEffect.SuccessListPreviousEvents(response.events)
                )

                // KEEP cursor
                previouseventscursor = response.cursor ?: ""

            } catch (err: SportsTalkException) {
                // EMIT error
//                _effect.send(ViewEffect.ErrorListPreviousEvents(err))
                rxEffect.onNext(ViewEffect.ErrorListPreviousEvents(err))
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
                        progressSendChatMessage.send(true)

                        val response = withContext(Dispatchers.IO) {
//                            chatClient.executeChatCommand(
//                                chatRoomId = room.id!!,
//                                request = ExecuteChatCommandRequest(
//                                    command = message,
//                                    userid = user.userid!!
//                                )
//                            )
                            rxChatClient.executeChatCommand(
                                chatRoomId = room.id!!,
                                request = ExecuteChatCommandRequest(
                                    command = message,
                                    userid = user.userid!!
                                )
                            ).await()
                        }

                        // Emit SUCCESS Send Chat Message
//                        _effect.send(ViewEffect.ChatMessageSent(response))
                        rxEffect.onNext(ViewEffect.ChatMessageSent(response))

                    } catch (err: SportsTalkException) {
                        // EMIT Error
//                        _effect.send(ViewEffect.ErrorSendChatMessage(err))
                        rxEffect.onNext(ViewEffect.ErrorSendChatMessage(err))

                    } finally {
                        // HIDE Progress Indicator
                        progressSendChatMessage.send(false)
                    }
                }
            }
        }
    }

    fun prepareQuotedReply(replyTo: ChatEvent) {
        this@ChatRoomViewModel.quotedReply.sendBlocking(replyTo)
    }

    fun clearQuotedReply() {
        this@ChatRoomViewModel.quotedReply.sendBlocking(PLACEHOLDER_CLEAR_REPLY)
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
                progressSendChatMessage.send(true)

                val response = withContext(Dispatchers.IO) {
//                    chatClient.sendQuotedReply(
//                        chatRoomId = room.id!!,
//                        request = SendQuotedReplyRequest(
//                            userid = user.userid!!,
//                            body = message,
//                            customid = customid,
//                            custompayload = custompayload,
//                            customfield1 = customfield1,
//                            customfield2 = customfield2,
//                            customtags = customtags
//                        ),
//                        replyTo = replyTo.id!!
//                    )
                    rxChatClient.sendQuotedReply(
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
                    ).await()
                }

                // EMIT SUCCESS Quoted Reply
//                _effect.send(
//                    ViewEffect.QuotedReplySent(response)
//                )
                rxEffect.onNext(
                    ViewEffect.QuotedReplySent(response)
                )

            } catch (err: SportsTalkException) {
                // EMIT Error
//                _effect.send(
//                    ViewEffect.ErrorSendQuotedReply(err)
//                )
                rxEffect.onNext(
                    ViewEffect.ErrorSendQuotedReply(err)
                )
            } finally {
                // HIDE Progress Indicator
                progressSendChatMessage.send(false)

                // Clear Prepared Quoted Reply
                this@ChatRoomViewModel.quotedReply.sendBlocking(PLACEHOLDER_CLEAR_REPLY)
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
//                    chatClient.sendThreadedReply(
//                        chatRoomId = room.id!!,
//                        replyTo = replyTo.id!!,
//                        request = SendThreadedReplyRequest(
//                            body = message,
//                            userid = user.userid!!,
//                            customid = customid,
//                            custompayload = custompayload,
//                            customtype = customtype
//                        )
//                    )
                    rxChatClient.sendThreadedReply(
                        chatRoomId = room.id!!,
                        replyTo = replyTo.id!!,
                        request = SendThreadedReplyRequest(
                            body = message,
                            userid = user.userid!!,
                            customid = customid,
                            custompayload = custompayload,
                            customtype = customtype
                        )
                    ).await()
                }

                // EMIT SUCCESS Quoted Reply
//                _effect.send(ViewEffect.ThreadedReplySent(response))
                rxEffect.onNext(ViewEffect.ThreadedReplySent(response))
            } catch (err: SportsTalkException) {
                // EMIT Error
//                _effect.send(
//                    ViewEffect.ErrorSendThreadedReply(err)
//                )
                rxEffect.onNext(
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
//                    chatClient.reportMessage(
//                        chatRoomId = room.id!!,
//                        eventId = which.id!!,
//                        request = ReportMessageRequest(
//                            reporttype = reporttype,
//                            userid = user.userid!!
//                        )
//                    )
                    rxChatClient.reportMessage(
                        chatRoomId = room.id!!,
                        eventId = which.id!!,
                        request = ReportMessageRequest(
                            reporttype = reporttype,
                            userid = user.userid!!
                        )
                    ).await()
                }

                // EMIT Success
//                _effect.send(ViewEffect.SuccessReportMessage(response))
                rxEffect.onNext(ViewEffect.SuccessReportMessage(response))

            } catch (err: SportsTalkException) {
                // EMIT Error
//                _effect.send(ViewEffect.ErrorReportMessage(err))
                rxEffect.onNext(ViewEffect.ErrorReportMessage(err))
            } finally {
                // HIDE Progress Indicator
                progressReportMessage.send(false)
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
//                    chatClient.reactToEvent(
//                        chatRoomId = room.id!!,
//                        eventId = event.id!!,
//                        request = ReactToAMessageRequest(
//                            userid = user.userid!!,
//                            reaction = EventReaction.LIKE,
//                            reacted = !reacted
//                        )
//                    )
                    rxChatClient.reactToEvent(
                        chatRoomId = room.id!!,
                        eventId = event.id!!,
                        request = ReactToAMessageRequest(
                            userid = user.userid!!,
                            reaction = EventReaction.LIKE,
                            reacted = !reacted
                        )
                    ).await()
                }

                // Emit Success
                var replyTo: ChatEvent? = response.replyto
                while (replyTo != null) {
                    if(replyTo.id == event.id) {
//                        _effect.send(
//                            ViewEffect.SuccessReactToAMessage(replyTo)
//                        )
                        rxEffect.onNext(
                            ViewEffect.SuccessReactToAMessage(replyTo)
                        )
                        break
                    }

                    replyTo = replyTo?.replyto
                }

            } catch (err: SportsTalkException) {
                // Emit Error
//                _effect.send(ViewEffect.ErrorReactToAMessage(err))
                rxEffect.onNext(ViewEffect.ErrorReactToAMessage(err))
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
//                            chatClient.permanentlyDeleteEvent(
//                                chatRoomId = room.id!!,
//                                userid = user.userid!!,
//                                eventId = which.id!!
//                            )
                            rxChatClient.permanentlyDeleteEvent(
                                chatRoomId = room.id!!,
                                userid = user.userid!!,
                                eventId = which.id!!
                            ).await()
                        }
                        // Perform Flag Event as Deleted
                        false -> {
//                            chatClient.flagEventLogicallyDeleted(
//                                chatRoomId = room.id!!,
//                                userid = user.userid!!,
//                                eventId = which.id!!,
//                                deleted = true,
//                                permanentifnoreplies = permanentifnoreplies
//                            )
                            rxChatClient.flagEventLogicallyDeleted(
                                chatRoomId = room.id!!,
                                userid = user.userid!!,
                                eventId = which.id!!,
                                deleted = true,
                                permanentifnoreplies = permanentifnoreplies
                            ).await()
                        }
                    }
                }

//                val response = withContext(Dispatchers.IO) {
//                    when(isPermanentDelete) {
//                        // Perform Permanent Delete
//                        true -> {
//                            rxChatClient.permanentlyDeleteEvent(
//                                chatRoomId = room.id!!,
//                                userid = user.userid!!,
//                                eventId = which.id!!
//                            ).await()
//                        }
//                        // Perform Flag Event as Deleted
//                        false -> {
//                            rxChatClient.flagEventLogicallyDeleted(
//                                chatRoomId = room.id!!,
//                                userid = user.userid!!,
//                                eventId = which.id!!,
//                                deleted = true,
//                                permanentifnoreplies = permanentifnoreplies
//                            ).await()
//                        }
//                    }
//                }

                // EMIT Success
//                _effect.send(
//                    ViewEffect.SuccessRemoveMessage(response)
//                )
                rxEffect.onNext(
                    ViewEffect.SuccessRemoveMessage(response)
                )

            } catch (err: SportsTalkException) {
                // EMIT Error
//                _effect.send(ViewEffect.ErrorReactToAMessage(err))
                rxEffect.onNext(ViewEffect.ErrorReactToAMessage(err))
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
//                    chatClient.exitRoom(
//                        chatRoomId = room.id!!,
//                        userId = user.userid!!
//                    )
                    rxChatClient.exitRoom(
                        chatRoomId = room.id!!,
                        userId = user.userid!!
                    ).await()
                }

                // EMIT Success
//                _effect.send(ViewEffect.SuccessExitRoom())
                rxEffect.onNext(ViewEffect.SuccessExitRoom())

            } catch (err: SportsTalkException) {
                // EMIT Error
//                _effect.send(ViewEffect.ErrorExitRoom(err))
                rxEffect.onNext(ViewEffect.ErrorExitRoom(err))
            } finally {
                // HIDE Progress Indicator
                progressExitRoom.send(false)
            }
        }
    }

    fun bounceUser(who: User, bounce: Boolean, announcement: String? = null) {
        viewModelScope.launch {
            try {
                // SHOW Progress Indicator
                progressBounceUser.send(true)

                val response = withContext(Dispatchers.IO) {
//                    chatClient.bounceUser(
//                        chatRoomId = room.id!!,
//                        request = BounceUserRequest(
//                            userid = who.userid!!,
//                            bounce = bounce,
//                            announcement = announcement
//                        )
//                    )
                    rxChatClient.bounceUser(
                        chatRoomId = room.id!!,
                        request = BounceUserRequest(
                            userid = who.userid!!,
                            bounce = bounce,
                            announcement = announcement
                        )
                    ).await()
                }

                // EMIT Success
                if(bounce) {
//                    _effect.send(ViewEffect.SuccessBounceUser(response))
                    rxEffect.onNext(ViewEffect.SuccessBounceUser(response))
                } else {
//                    _effect.send(
//                        ViewEffect.SuccessUnbounceUser(
//                            response.copy(
//                                event = ChatEvent(
//                                    body = announcement,
//                                    userid = who.userid,
//                                    user = who
//                                )
//                            )
//                        )
//                    )
                    rxEffect.onNext(
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
//                _effect.send(ViewEffect.ErrorBounceUser(err))
                rxEffect.onNext(ViewEffect.ErrorBounceUser(err))
            } finally {
                // HIDE Progress Indicator
                progressBounceUser.send(false)
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
        class ErrorJoinRoom(val err: SportsTalkException) : ViewEffect()

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