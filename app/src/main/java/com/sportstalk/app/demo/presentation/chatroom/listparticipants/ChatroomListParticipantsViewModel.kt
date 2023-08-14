package com.sportstalk.app.demo.presentation.chatroom.listparticipants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
/*import com.sportstalk.coroutine.api.ChatClient*/import com.sportstalk.sdk.core.api.ChatClient
/*import com.sportstalk.coroutine.api.UserClient*/import com.sportstalk.sdk.core.api.UserClient
/*import com.sportstalk.datamodels.SportsTalkException*/import com.sportstalk.sdk.model.SportsTalkException
/*import com.sportstalk.datamodels.chat.**/import com.sportstalk.sdk.model.chat.*
/*import com.sportstalk.datamodels.users.User*/import com.sportstalk.sdk.model.user.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatroomListParticipantsViewModel(
    private val room: ChatRoom,
    private val user: User,
    /*
    * Typical use case to access SDK client instance:
    *   val config = ClientConfig(appId = "...", apiToken = "...", endpoint = "...")
    *   // Instantiate via Factory
    *   val chatClient = SportsTalk247.ChatClient(config = config)
    */
    private val userClient: UserClient,
    private val chatClient: ChatClient
) : ViewModel() {

    private val progressFetchChatroomParticipants = Channel<Boolean>(Channel.BUFFERED)
    private val progressFetchBouncedUsers = Channel<Boolean>(Channel.BUFFERED)
    private val progressUserSetBanStatus = Channel<Boolean>(Channel.BUFFERED)
    private val progressBounceUser = Channel<Boolean>(Channel.BUFFERED)
    private val progressPurgeUserMessages = Channel<Boolean>(Channel.BUFFERED)

    private val chatroomParticipants = MutableStateFlow<List<User>?>(null)
    private val bouncedUsers = MutableStateFlow<List<User>?>(null)

    private var cursor: String? = null

    val state = object : ViewState {
        override fun progressFetchChatroomParticipants(): Flow<Boolean> =
            progressFetchChatroomParticipants
                .consumeAsFlow()

        override fun progressFetchBouncedUsers(): Flow<Boolean> =
            progressFetchBouncedUsers
                .consumeAsFlow()

        override fun chatroomParticipants(): Flow<List<User>> =
            chatroomParticipants.asStateFlow()
                .filterNotNull()

        override fun bouncedUsers(): Flow<List<User>> =
            bouncedUsers.asStateFlow()
                .filterNotNull()

        override fun progressUserSetBanStatus(): Flow<Boolean> =
            progressUserSetBanStatus
                .consumeAsFlow()

        override fun progressBounceUser(): Flow<Boolean> =
            progressBounceUser
                .consumeAsFlow()

        override fun progressPurgeUserMessages(): Flow<Boolean> =
            progressPurgeUserMessages
                .consumeAsFlow()
    }

    private val _effect = Channel<ViewEffect>(Channel.BUFFERED)
    val effect: Flow<ViewEffect> = _effect
        .consumeAsFlow()

    fun refreshChatroomParticipants() {
        cursor = null
        fetchChatroomParticipants()
    }

    fun fetchChatroomParticipants() {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressFetchChatroomParticipants.send(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.listRoomParticipants(
                        chatRoomId = room.id!!,
                        cursor = cursor,
                        limit = LIMIT_CHATROOM_PARTICIPANTS
                    )
                }

                // EMIT SUCCESS
                chatroomParticipants.emit(response.participants.mapNotNull { it.user })
                // Update cursor
                cursor = response.cursor

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(ViewEffect.ErrorFetchChatroomParticipants(err))
            } finally {
                // HIDE Progress Indicator
                progressFetchChatroomParticipants.send(false)
            }

        }
    }

    fun fetchBouncedUsers(which: ChatRoom) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressFetchBouncedUsers.send(true)

                val userIds = which.bouncedusers ?: return@launch
                val bouncedUsersWithDetails = ArrayList<User>()
                userIds.forEach { id ->
                    bouncedUsersWithDetails.add(
                        withContext(Dispatchers.IO) {
                            userClient.getUserDetails(id)
                        }
                    )
                }

                // EMIT Bounced Users
                bouncedUsers.emit(bouncedUsersWithDetails)
            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(ViewEffect.ErrorFetchBouncedUsers(err))
            } finally {
                // HIDE Progress Indicator
                progressFetchBouncedUsers.send(false)
            }
        }
    }

    fun setBanStatus(of: User, isBanned: Boolean) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressUserSetBanStatus.send(true)

                val response = withContext(Dispatchers.IO) {
                    userClient.setBanStatus(
                        userId = of.userid!!,
                        applyeffect = isBanned
                    )
                }

                // EMIT Success
                _effect.send(
                    ViewEffect.SuccessUserSetBanStatus(response)
                )

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(
                    ViewEffect.ErrorUserSetBanStatus(err)
                )
            } finally {
                // HIDE Progress Indicator
                progressUserSetBanStatus.send(false)
            }
        }
    }

    fun bounceUser(who: User, bounce: Boolean, announcement: String? = null) {
        viewModelScope.launch {
            try {
                // SHOW Progress Indicator
                progressBounceUser.send(true)

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
                if (bounce) {
                    _effect.send(ViewEffect.SuccessBounceUser(response))
                } else {
                    _effect.send(
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
                _effect.send(ViewEffect.ErrorBounceUser(err))
            } finally {
                // HIDE Progress Indicator
                progressBounceUser.send(false)
            }
        }
    }

    fun purgeMessages(from: User) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressPurgeUserMessages.send(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.executeChatCommand(
                        chatRoomId = room.id!!,
                        request = ExecuteChatCommandRequest(
                            command = "*purge zola ${from.handle!!}",
                            userid = user.userid!!
                        )
                    )
                }

                // EMIT Success
                _effect.send(ViewEffect.SuccessPurgeUserMessages(from, response))

            } catch (err: SportsTalkException) {
                // EMIT Error
                _effect.send(ViewEffect.ErrorPurgeUserMessages(err))
            } finally {
                // HIDE Progress Indicator
                progressPurgeUserMessages.send(false)
            }
        }
    }

    interface ViewState {
        /**
         * Emits [true] upon start List Chatroom Participants SDK operation. Emits [false] when done.
         */
        fun progressFetchChatroomParticipants(): Flow<Boolean>

        /**
         * Emits [true] upon start Batch call to Get User Details SDK operation. Emits [false] when done.
         */
        fun progressFetchBouncedUsers(): Flow<Boolean>

        /**
         * Emits response of List Chatroom Participants SDK operation.
         */
        fun chatroomParticipants(): Flow<List<User>>

        /**
         * Emits response from Batch of Get User Details SDK operation.
         */
        fun bouncedUsers(): Flow<List<User>>

        /**
         * Emits [true] upon start Set Ban Status SDK operation. Emits [false] when done.
         */
        fun progressUserSetBanStatus(): Flow<Boolean>

        /**
         * Emits [true] upon start Bounce User SDK operation. Emits [false] when done.
         */
        fun progressBounceUser(): Flow<Boolean>

        /**
         * Emits [true] upon start Purge User Messages SDK operation. Emits [false] when done.
         */
        fun progressPurgeUserMessages(): Flow<Boolean>

    }

    sealed class ViewEffect {
        data class ErrorFetchChatroomParticipants(val err: SportsTalkException) : ViewEffect()
        data class ErrorFetchBouncedUsers(val err: SportsTalkException) : ViewEffect()
        data class SuccessUserSetBanStatus(val user: User) : ViewEffect()
        data class ErrorUserSetBanStatus(val err: SportsTalkException) : ViewEffect()
        data class SuccessPurgeUserMessages(
            val who: User,
            val response: ExecuteChatCommandResponse
        ) : ViewEffect()

        data class ErrorPurgeUserMessages(val err: SportsTalkException) : ViewEffect()
        data class SuccessBounceUser(val response: BounceUserResponse) : ViewEffect()
        data class SuccessUnbounceUser(val response: BounceUserResponse) : ViewEffect()
        data class ErrorBounceUser(val err: SportsTalkException) : ViewEffect()
    }

    companion object {
        const val LIMIT_CHATROOM_PARTICIPANTS = 10
    }

}