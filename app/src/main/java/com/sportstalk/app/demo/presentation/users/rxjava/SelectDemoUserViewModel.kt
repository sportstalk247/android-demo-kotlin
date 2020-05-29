package com.sportstalk.app.demo.presentation.users.rxjava

import androidx.lifecycle.ViewModel
import com.sportstalk.api.ChatClient
import com.sportstalk.models.SportsTalkException
import com.sportstalk.models.chat.ChatRoomParticipant
import com.sportstalk.models.chat.ListChatRoomParticipantsResponse
import com.sportstalk.models.users.User
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.SingleSource
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

class SelectDemoUserViewModel(
    /*
    * Typical use case to access SDK client instance:
    *   val config = ClientConfig(appId = "...", apiToken = "...", endpoint = "...")
    *   // Instantiate via Factory
    *   val chatClient = SportsTalk247.ChatClient(config = config)
    */
    private val chatClient: ChatClient
) : ViewModel() {

    private val rxDisposable = CompositeDisposable()

    private val participants = BehaviorSubject.create<List<ChatRoomParticipant>>()
    private val progressFetchParticipants = PublishSubject.create<Boolean>()
    private val cursor = PublishSubject.create<String>()
    private val _state = BehaviorSubject.create<ViewState>()
    val state: Flowable<ViewState>
        get() = _state
            .toFlowable(BackpressureStrategy.LATEST)
            .distinctUntilChanged()

    private val _effect = PublishSubject.create<ViewEffect>()
    val effect: Flowable<ViewEffect> = _effect
        .toFlowable(BackpressureStrategy.LATEST)
        .distinctUntilChanged()

    init {
        Observables.combineLatest(
            participants,
            progressFetchParticipants,
            cursor
        ) { _rooms, _progress, _cursor ->
            ViewState(
                _rooms,
                _progress,
                _cursor.takeIf { it.isNotEmpty() })
        }
            .subscribe(_state::onNext)
            .addTo(rxDisposable)
    }

    fun fetch(roomId: String, cursor: String? = null) {
        // Clear list if fetching without cursor(ex. swipe refresh)
        this.cursor.onNext(cursor ?: "")

        // Emit DISPLAY Progress indicator
        progressFetchParticipants.onNext(true)

////////////////////////////////////////////////////////
//////////////// CompletableFuture -> Single
////////////////////////////////////////////////////////
        Single.fromFuture(
            chatClient.listRoomParticipants(
                chatRoomId = roomId,
                cursor = cursor,
                limit = LIMIT_FETCH_USERS
            ),
            Schedulers.io()
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorResumeNext { err ->
                // Emit error if encountered
                _effect.onNext(
                    ViewEffect.ErrorFetchParticipants(
                        err = err as SportsTalkException
                    )
                )
                SingleSource { e -> e.onSuccess(ListChatRoomParticipantsResponse()) }
            }
            .doOnSuccess { listParticipantsResponse ->
                // Emit update room list
                val updatedParticipants = when (cursor == null || cursor.isEmpty()) {
                    true -> listParticipantsResponse.participants
                    else -> listParticipantsResponse.participants + participants.value!!
                }
                participants.onNext(updatedParticipants.distinct())

                // Emit new cursor
                this@SelectDemoUserViewModel.cursor.onNext(listParticipantsResponse.cursor ?: "")
            }
            .subscribe()
            .addTo(rxDisposable)
    }

    fun selectDemoUser(which: User) {
        _effect.onNext(
            ViewEffect.NavigateToChatRoom(which)
        )
    }

    override fun onCleared() {
        super.onCleared()
        rxDisposable.dispose()
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