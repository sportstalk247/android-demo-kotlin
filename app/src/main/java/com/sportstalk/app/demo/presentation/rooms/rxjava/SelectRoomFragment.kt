package com.sportstalk.app.demo.presentation.rooms.rxjava

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.sportstalk.SportsTalk247
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentSelectRoomBinding
import com.sportstalk.app.demo.presentation.rooms.adapters.ItemSelectRoomRecycler
import com.sportstalk.app.demo.presentation.users.rxjava.SelectDemoUserFragment
import com.sportstalk.models.ClientConfig
import com.sportstalk.models.chat.ChatRoom
import com.squareup.cycler.Recycler
import com.squareup.cycler.toDataSource
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.*
import java.util.concurrent.TimeUnit

class SelectRoomFragment : Fragment() {

    private lateinit var appNavController: NavController
    private lateinit var binding: FragmentSelectRoomBinding

    private val config: ClientConfig by lazy {
        ClientConfig(
            appId = getString(R.string.sportstalk247_appid),
            apiToken = getString(R.string.sportstalk247_authToken),
            endpoint = getString(R.string.sportstalk247_urlEndpoint)
        )
    }

    private val viewModel: SelectRoomViewModel by viewModel {
        parametersOf(
            SportsTalk247.ChatClient(config = config)
        )
    }

    private val rxDisposeBag = CompositeDisposable()

    private val cursor = BehaviorSubject.create<Optional<String>>()
    private val onScrollFetchNext = BehaviorSubject.create<String>()

    private lateinit var recycler: Recycler<ChatRoom>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSelectRoomBinding.inflate(inflater)
        appNavController = Navigation.findNavController(requireActivity(), R.id.navHostFragmentApp)

        recycler = ItemSelectRoomRecycler.adopt(
            recyclerView = binding.recyclerView,
            onScrollFetchChatRoomsNext = { cursor: String ->
                // Attempt scroll next
                onScrollFetchNext.onNext(cursor)
            },
            onSelectChatRoom = { chatRoom: ChatRoom ->
                Log.d(TAG, "onSelectChatRoom() -> chatRoom = $chatRoom")
                // Select Chat Room
                viewModel.selectRoom(which = chatRoom)
            }
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /**
         * Subscribe to and apply ViewState changes(ex. List of Room Updates, Progress indicator, etc.).
         */
        viewModel.state
            .map { it.progressFetchRooms }
            .subscribe(::takeProgressFetchRooms)
            .addTo(rxDisposeBag)

        viewModel.state
            .map { it.rooms }
            .subscribe(::takeRooms)
            .addTo(rxDisposeBag)

        viewModel.state
            .map { Optional.ofNullable(it.cursor) }
            .doOnNext { Log.d(TAG, "viewModel.state.cursor = $it") }
            .subscribe(cursor::onNext)
            .addTo(rxDisposeBag)

        /**
         * Subscribe to View Effects(ex. Prompt navigate to chat room, Fetch error, etc.)
         */
        viewModel.effect
            .subscribe { effect ->
                when (effect) {
                    is SelectRoomViewModel.ViewEffect.NavigateToChatRoom -> {
                        // Navigate to Select Demo User screen
                        if (appNavController.currentDestination?.id == R.id.fragmentSelectRoom) {
                            appNavController.navigate(
                                R.id.action_fragmentSelectRoom_to_fragmentSelectDemoUser,
                                bundleOf(
                                    SelectDemoUserFragment.INPUT_ARG_ROOM to effect.which
                                )
                            )
                        }
                    }
                    is SelectRoomViewModel.ViewEffect.ErrorFetchRoom -> {
                        // TODO::
                        Toast.makeText(
                            requireContext(),
                            "Fetch error: `${effect.err.message}`",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .addTo(rxDisposeBag)

        // Perform fetch on refresh
        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "binding.swipeRefresh")
            viewModel.fetch(cursor = null)
        }

        // Scroll Bottom Attempt Fetch More
        onScrollFetchNext
            .distinctUntilChanged()
            .throttleFirst(500, TimeUnit.MILLISECONDS)
            .subscribe { _cursor ->
                val _cursor = cursor.value ?: Optional.empty()
                viewModel.fetch(
                    cursor = if (_cursor.isPresent) _cursor.get()
                    else null
                )
            }
            .addTo(rxDisposeBag)

        // On click Create Chat Room action
        binding.fabAdd.setOnClickListener {
            Log.d(TAG, "binding.fabAdd.setOnClickListener")
        }

        // On view created, Perform fetch
        viewModel.fetch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Dispose subscribed observables
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

    companion object {
        val TAG = SelectRoomFragment::class.java.simpleName
    }

}