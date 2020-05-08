package com.sportstalk.app.demo.presentation.rooms.rxjava

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.sportstalk.app.demo.databinding.FragmentSelectRoomBinding
import com.sportstalk.app.demo.presentation.rooms.adapters.ItemSelectRoomRecycler
import com.sportstalk.models.chat.ChatRoom
import com.squareup.cycler.Recycler
import com.squareup.cycler.toDataSource
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.subjects.BehaviorSubject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.*
import java.util.concurrent.TimeUnit

class SelectRoomFragment : Fragment() {

    private lateinit var binding: FragmentSelectRoomBinding

    private val viewModel: SelectRoomViewModel by viewModel()

    private val rxDisposeBag = CompositeDisposable()

    private val cursor = BehaviorSubject.create<Optional<String>>()
    private val onScrollFetchNext = BehaviorSubject.create<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSelectRoomBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "binding.swipeRefresh")
            recycler.clear()
            viewModel.fetch(cursor = null)
        }

        // Scroll Bottom Attempt Fetch More
        cursor
            .filter { it.orElse("").isNotEmpty() }
            .compose { obsCursor ->
                onScrollFetchNext
                    .distinctUntilChanged()
                    .withLatestFrom(obsCursor) { _, _cursor -> _cursor }
            }
            .throttleFirst(500, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .subscribe { _cursor ->
                viewModel.fetch(
                    cursor = if (_cursor.isPresent) _cursor.get()
                    else null
                )
            }
            .addTo(rxDisposeBag)

        viewModel.state
            .subscribe { state ->
                takeProgressFetchRooms(state.progressFetchRooms)
                takeRooms(state.rooms)
                cursor.onNext(Optional.ofNullable(state.cursor))
            }
            .addTo(rxDisposeBag)

        viewModel.effect
            .subscribe { effect ->
                when (effect) {
                    is SelectRoomViewModel.ViewEffect.NavigateToChatRoom -> {
                        // TODO::
                        Toast.makeText(
                            requireContext(),
                            "Click Room: `${effect.which.slug}`",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is SelectRoomViewModel.ViewEffect.ErrorFetchRoom -> {
                        // TODO::
                        Toast.makeText(
                            requireContext(),
                            "Click Room: `${effect.err.message}`",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .addTo(rxDisposeBag)

        binding.fabAdd.setOnClickListener {
            Log.d(TAG, "binding.fabAdd.setOnClickListener")
        }

        // On view created, Perform fetch
        viewModel.fetch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rxDisposeBag.dispose()
    }

    private fun takeProgressFetchRooms(inProgress: Boolean) {
        Log.d(TAG, "takeProgressFetchRooms() -> inProgress = $inProgress")
        binding.swipeRefresh.isRefreshing = inProgress
    }

    private fun takeRooms(rooms: List<ChatRoom>) {
        Log.d(TAG, "takeRooms() -> rooms = $rooms")

        recycler.update {
            data = rooms.toDataSource()
        }
    }

    private val recycler: Recycler<ChatRoom> by lazy {
        ItemSelectRoomRecycler.adopt(
            recyclerView = binding.recyclerView,
            onScrollFetchChatRoomsNext = { cursor: String ->
                // Attempt scroll next
                onScrollFetchNext.onNext(cursor)
            },
            onSelectChatRoom = { chatRoom: ChatRoom ->
                // Select Chat Room
                viewModel.selectRoom(which = chatRoom)
            }
        )
    }

    companion object {
        val TAG = SelectRoomFragment::class.java.simpleName
    }

}