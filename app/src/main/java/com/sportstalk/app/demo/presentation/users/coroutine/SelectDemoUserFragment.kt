package com.sportstalk.app.demo.presentation.users.coroutine

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentSelectDemoUserBinding
import com.sportstalk.app.demo.extensions.sharedGraphViewModel
import com.sportstalk.app.demo.extensions.throttleFirst
import com.sportstalk.app.demo.presentation.users.adapter.ItemSelectDemoUserRecycler
import com.sportstalk.models.chat.ChatRoomParticipant
import com.squareup.cycler.Recycler
import com.squareup.cycler.toDataSource
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.*

class SelectDemoUserFragment : Fragment() {

    private lateinit var appNavController: NavController

    private lateinit var binding: FragmentSelectDemoUserBinding
    private val viewModel: SelectDemoUserViewModel by viewModel()
    private lateinit var argRoomId: String

    private val cursor = ConflatedBroadcastChannel<Optional<String>>()
    private val onScrollFetchNext = ConflatedBroadcastChannel<String>()

    private lateinit var recycler: Recycler<ChatRoomParticipant>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        argRoomId = requireArguments().getString(INPUT_ARG_ROOM_ID, "")
        if (argRoomId.isEmpty()) {
            Toast.makeText(requireContext(), "Invalid Chat Room", Toast.LENGTH_SHORT).show()
            appNavController.popBackStack()
            return
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSelectDemoUserBinding.inflate(inflater)
        appNavController = Navigation.findNavController(requireActivity(), R.id.navHostFragmentApp)

        recycler = ItemSelectDemoUserRecycler.adopt(
            recyclerView = binding.recyclerView,
            onScrollFetchParticipantsNext = { cursor: String ->
                // Attemp scroll next
                onScrollFetchNext.sendBlocking(cursor)
            },
            onSelectDemoUser = { chatRoomParticipant: ChatRoomParticipant ->
                Log.d(TAG, "onSelectDemoUser() -> chatRoomParticipant = $chatRoomParticipant")
                // Select Participant
                viewModel.selectDemoUser(which = chatRoomParticipant.user!!)
            }
        )

        return binding.root
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when(item.itemId) {
            android.R.id.home -> appNavController.popBackStack()
            else -> super.onOptionsItemSelected(item)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.let { appActivity ->
            appActivity.setSupportActionBar(binding.toolbar)
            appActivity.actionBar?.setHomeButtonEnabled(true)
            appActivity.actionBar?.setDisplayShowHomeEnabled(true)
        }

        /**
         * Subscribe to and apply ViewState changes(ex. List of ChatRoom Participants Updates, Progress indicator, etc.).
         */
        viewModel.state
            .onEach { state ->
                takeProgressFetchParticipants(state.progressFetchParticipants)
                takeParticipants(state.participants)
                Log.d(TAG, "viewModel.state.cursor = ${state.cursor}")
                cursor.send(Optional.ofNullable(state.cursor))
            }
            .launchIn(lifecycleScope)

        /**
         * Subscribe to View Effects(ex. Prompt navigate to chat room, Fetch error, etc.)
         */
        viewModel.effect
            .onEach { effect ->
                Log.d(TAG, "viewModel.effect -> ${effect::class.java.simpleName}")
                when (effect) {
                    is SelectDemoUserViewModel.ViewEffect.NavigateToChatRoom -> {
                        // TODO::
                        Toast.makeText(
                            requireContext(),
                            "Click User: `${effect.which.handle}`",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is SelectDemoUserViewModel.ViewEffect.ErrorFetchParticipants -> {
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
            viewModel.fetch(roomId = argRoomId, cursor = null)
        }

        // Scroll Bottom Attempt Fetch More
        onScrollFetchNext
            .asFlow()
            .distinctUntilChanged()
            .throttleFirst(500)
            .onEach {
                val _cursor = cursor.value
                viewModel.fetch(
                    roomId = argRoomId,
                    cursor = if (_cursor.isPresent) _cursor.get()
                    else null
                )
            }
            .launchIn(lifecycleScope)

        // On click Create Chat Room action
        binding.fabAdd.setOnClickListener {
            Log.d(TAG, "binding.fabAdd.setOnClickListener")
        }

        // On view created, Perform fetch
        Log.d(TAG, "onViewCreated() -> viewModel.fetch()")
        viewModel.fetch(roomId = argRoomId, cursor = null)
    }

    private fun takeProgressFetchParticipants(inProgress: Boolean) {
        Log.d(TAG, "takeProgressFetchParticipants() -> inProgress = $inProgress")
        binding.swipeRefresh.isRefreshing = inProgress
    }

    private fun takeParticipants(participants: List<ChatRoomParticipant>) {
        Log.d(TAG, "takeParticipants() -> participants = $participants")

        recycler.update {
            data = participants.toDataSource()
        }

    }

    companion object {
        val TAG = SelectDemoUserFragment::class.java.simpleName

        const val INPUT_ARG_ROOM_ID = "input-arg-room-id"
    }

}