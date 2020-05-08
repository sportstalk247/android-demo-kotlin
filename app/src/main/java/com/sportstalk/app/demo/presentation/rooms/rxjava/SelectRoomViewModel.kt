package com.sportstalk.app.demo.presentation.rooms.rxjava

import androidx.lifecycle.ViewModel
import com.sportstalk.api.ChatApiService
import com.sportstalk.models.chat.ChatRoom
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
import java.util.concurrent.TimeUnit

class SelectRoomViewModel(
    /*
    * Typical use case to access API instance:
    *   SportsTalkManager.init(applicationContext) // invoked once under SportsTalkDemoApplication.onCreate()
    *   // Access singleton instance
    *   val chatApiService = SportsTalkManager.instance.chatApiService
    */
    private val chatApiService: ChatApiService
) : ViewModel() {

    private val rxDisposable = CompositeDisposable()

    private val performFetch = PublishSubject.create<String>()
    private val rooms = BehaviorSubject.create<List<ChatRoom>>()
    private val progressFetchRooms = PublishSubject.create<Boolean>()
    private val cursor = PublishSubject.create<String>()
    private val _state = BehaviorSubject.createDefault(ViewState())
    val state: Flowable<ViewState>
        get() = _state
            .startWith(ViewState(progressFetchRooms = true))
            .toFlowable(BackpressureStrategy.LATEST)

    private val _effect = PublishSubject.create<ViewEffect>()
    val effect: Flowable<ViewEffect>
        get() = _effect
            .throttleFirst(500, TimeUnit.MILLISECONDS)
            .toFlowable(BackpressureStrategy.LATEST)

    init {
        // Emit ViewState changes
        Observables.combineLatest(
            rooms,
            progressFetchRooms,
            cursor
        ) { _rooms, _progress, _cursor ->
            ViewState(
                _rooms,
                _progress,
                _cursor.takeIf { it.isNotEmpty() })
        }
            .debounce(50, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(_state::onNext)
            .addTo(rxDisposable)

        // Perform Fetch Room on attempt fetch
        performFetch
            // Emit Display Progress Indicator
            .doOnNext { progressFetchRooms.onNext(true) }
            .switchMap { _fetchWithCursor ->
////////////////////////////////////////////////////////
//////////////// CompletableFuture -> RxJava Single
////////////////////////////////////////////////////////
                Single.fromFuture(
                    chatApiService.listRooms(
                        cursor = _fetchWithCursor.takeIf { it.isNotEmpty() },
                        limit = LIMIT_FETCH_ROOMS
                    ),
                    // Provide IO RxScheduler(i.e. background thread) that will be used to await future execution
                    Schedulers.io()
                )
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .flatMap { response ->
                        // Map out `data` from response
                        if (response.code in 200..299) {
                            // "success"
                            Single.just(response.data!!)
                        } else {
                            // "error"
                            Single.error(Throwable(response.message))
                        }
                    }
                    .onErrorResumeNext { err ->
                        // Emit error if encountered
                        _effect.onNext(
                            ViewEffect.ErrorFetchRoom(
                                err = err
                            )
                        )
                        SingleSource { _state.value ?: ViewState() }
                    }
                    .toObservable()
            }
            .doOnNext { response ->
                // Emit HIDE Progress Indicator
                progressFetchRooms.onNext(false)
                // Emit Response List
                rooms.onNext(
                    ((response?.rooms ?: listOf()) + (rooms.value ?: listOf())).distinct()
                )
                // Emit new cursor
                cursor.onNext(response?.cursor ?: "")
            }
            .subscribe()
            .addTo(rxDisposable)
    }

    fun fetch(cursor: String? = null) {
        // Clear list if fetching without cursor(ex. swipe refresh)
        if (cursor == null || cursor.isEmpty()) rooms.onNext(listOf())
        // Attempt fetch
        performFetch.onNext(cursor ?: "")
    }


    fun selectRoom(which: ChatRoom) {
        _effect.onNext(
            ViewEffect.NavigateToChatRoom(
                which
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        rxDisposable.dispose()
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