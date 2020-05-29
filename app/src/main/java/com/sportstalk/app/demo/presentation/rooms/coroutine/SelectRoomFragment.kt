package com.sportstalk.app.demo.presentation.rooms.coroutine

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.sportstalk.SportsTalk247
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentSelectRoomBinding
import com.sportstalk.app.demo.extensions.throttleFirst
import com.sportstalk.app.demo.presentation.rooms.adapters.ItemSelectRoomRecycler
import com.sportstalk.app.demo.presentation.users.coroutine.SelectDemoUserFragment
import com.sportstalk.models.ClientConfig
import com.sportstalk.models.chat.ChatRoom
import com.squareup.cycler.Recycler
import com.squareup.cycler.toDataSource
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.*

class SelectRoomFragment : Fragment() {

    private lateinit var appNavController: NavController
    private lateinit var binding: FragmentSelectRoomBinding

    private val config: ClientConfig by lazy {
        ClientConfig(
            appId = "5ec0dc805617e00918446168",
            apiToken = "R-GcA7YsG0Gu3DjEVMWcJA60RkU9uyH0Wmn2pnEbzJzA",
            endpoint = "https://qa-talkapi.sportstalk247.com/api/v3/"
        )
    }

    private val viewModel: SelectRoomViewModel by viewModel {
        parametersOf(
            SportsTalk247.ChatClient(config = config)
        )
    }

    private val cursor = ConflatedBroadcastChannel<Optional<String>>()
    private val onScrollFetchNext = ConflatedBroadcastChannel<String>()

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
                onScrollFetchNext.sendBlocking(cursor)
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
            .debounce(250)
            .onEach { progress ->
                takeProgressFetchRooms(progress)
            }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.rooms }
            .debounce(250)
            .distinctUntilChanged()
            .onEach { rooms ->
                takeRooms(rooms)
            }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.cursor }
            .onEach { _cursor ->
                Log.d(TAG, "viewModel.state.cursor = ${_cursor}")
                cursor.send(Optional.ofNullable(_cursor))
            }
            .launchIn(lifecycleScope)

        /**
         * Subscribe to View Effects(ex. Prompt navigate to chat room, Fetch error, etc.)
         */
        viewModel.effect
            .onEach { effect ->
                Log.d(TAG, "viewModel.effect -> effect = ${effect::class.java.simpleName}")

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
            .launchIn(lifecycleScope)

        // Perform fetch on refresh
        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "binding.swipeRefresh")
            viewModel.fetch(cursor = null)
        }

        // Scroll Bottom Attempt Fetch More
        onScrollFetchNext
            .asFlow()
            .distinctUntilChanged()
            .throttleFirst(500)
            .onEach {
                val _cursor = cursor.value
                viewModel.fetch(
                    cursor = if (_cursor.isPresent) _cursor.get()
                    else null
                )
            }
            .launchIn(lifecycleScope)

        // On click Create Chat Room action
        binding.fabAdd.setOnClickListener {
            Log.d(TAG, "binding.fabAdd.setOnClickListener")
            viewModel.fetch(cursor = null)
        }

        // On view created, Perform fetch
        Log.d(TAG, "onViewCreated() -> viewModel.fetch()")
        viewModel.fetch(cursor = null)
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