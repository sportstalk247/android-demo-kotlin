package com.sportstalk.app.demo.presentation.chatroom.listparticipants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.api.ChatClient
import com.sportstalk.api.UserClient
import com.sportstalk.models.SportsTalkException
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.chat.ExecuteChatCommandRequest
import com.sportstalk.models.chat.ExecuteChatCommandResponse
import com.sportstalk.models.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.future.await
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

    private val progressFetchChatroomParticipants = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressUserSetBanStatus = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressPurgeUserMessages = Channel<Boolean>(Channel.RENDEZVOUS)

    private val chatroomParticipants = ConflatedBroadcastChannel<List<User>>()

    private var cursor: String? = null

    val state = object: ViewState {
        override fun progressFetchChatroomParticipants(): Flow<Boolean> =
            progressFetchChatroomParticipants.receiveAsFlow()

        override fun chatroomParticipants(): Flow<List<User>> =
            chatroomParticipants
                .openSubscription()
                .receiveAsFlow()

        override fun progressUserSetBanStatus(): Flow<Boolean> =
            progressUserSetBanStatus.receiveAsFlow()

        override fun progressPurgeUserMessages(): Flow<Boolean> =
            progressPurgeUserMessages.receiveAsFlow()
    }

    private val _effect = Channel<ViewEffect>(Channel.RENDEZVOUS)
    val effect: Flow<ViewEffect> = _effect.receiveAsFlow()

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
                        .await()
                }

                // EMIT SUCCESS
                chatroomParticipants.send(response.participants.mapNotNull { it.user })
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

    fun setBanStatus(of: User, isBanned: Boolean) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressUserSetBanStatus.send(true)

                val response = withContext(Dispatchers.IO) {
                    userClient.setBanStatus(
                        userId = of.userid!!,
                        banned = isBanned
                    )
                        .await()
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

    fun purgeMessages(from: User) {
        viewModelScope.launch {
            try {
                // DISPLAY Progress Indicator
                progressPurgeUserMessages.send(true)

                val response = withContext(Dispatchers.IO) {
                    chatClient.executeChatCommand(
                        chatRoomId = room.id!!,
                        request = ExecuteChatCommandRequest(
                            command = "*purge zola ${from.userid!!}",
                            userid = user.userid!!
                        )
                    )
                        .await()
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
         * Emits response of List Chatroom Participants SDK operation.
         */
        fun chatroomParticipants(): Flow<List<User>>

        /**
         * Emits [true] upon start Set Ban Status SDK operation. Emits [false] when done.
         */
        fun progressUserSetBanStatus(): Flow<Boolean>

        /**
         * Emits [true] upon start Purge User Messages SDK operation. Emits [false] when done.
         */
        fun progressPurgeUserMessages(): Flow<Boolean>

    }

    sealed class ViewEffect {
        data class ErrorFetchChatroomParticipants(val err: SportsTalkException) : ViewEffect()
        data class SuccessUserSetBanStatus(val user: User) : ViewEffect()
        data class ErrorUserSetBanStatus(val err: SportsTalkException) : ViewEffect()
        data class SuccessPurgeUserMessages(val who: User, val response: ExecuteChatCommandResponse): ViewEffect()
        data class ErrorPurgeUserMessages(val err: SportsTalkException): ViewEffect()
    }

    companion object {
        const val LIMIT_CHATROOM_PARTICIPANTS = 10
    }

}