package com.sportstalk.app.demo.presentation.chatroom.listparticipants

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.sportstalk.SportsTalk247
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentChatroomListParticipantsBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.app.demo.presentation.chatroom.listparticipants.adapters.ItemChatroomParticipantAdapter
import com.sportstalk.app.demo.presentation.utils.EndlessRecyclerViewScrollListener
import com.sportstalk.models.ClientConfig
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.users.User
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx2.asFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class ChatroomListParticipantsFragment : BaseFragment() {

    private lateinit var binding: FragmentChatroomListParticipantsBinding
    private val viewModel: ChatroomListParticipantsViewModel by viewModel {
        parametersOf(
            room,
            user,
            SportsTalk247.UserClient(config),
            SportsTalk247.ChatClient(config)
        )
    }
    private val config: ClientConfig by lazy {
        ClientConfig(
            appId = getString(R.string.sportstalk247_appid),
            apiToken = getString(R.string.sportstalk247_authToken),
            endpoint = getString(R.string.sportstalk247_urlEndpoint)
        )
    }

    private lateinit var user: User
    private lateinit var room: ChatRoom

    private lateinit var adapter: ItemChatroomParticipantAdapter
    private lateinit var scrollListener: RecyclerView.OnScrollListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        user = requireArguments().getParcelable(INPUT_ARG_USER)!!
        room = requireArguments().getParcelable(INPUT_ARG_ROOM)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatroomListParticipantsBinding.inflate(inflater)
        adapter = ItemChatroomParticipantAdapter(
            me = user,
            onTapChatParticipantItem = { participant: User ->
                Log.d(TAG, "onTapChatParticipantItem() -> participant = $participant")

                val options = mutableListOf<String>().apply {
                    if (participant.banned == true) add(getString(R.string.remove_ban_from_handle, user.handle ?: ""))
                    else add(getString(R.string.ban_handle, user.handle ?: ""))
                }.toTypedArray()

                MaterialAlertDialogBuilder(requireContext())
                    .setItems(options) { _, index ->
                        when (index) {
                            // Ban/Remove Ban
                            0 -> {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setMessage(R.string.are_you_sure)
                                    .setPositiveButton(android.R.string.ok) { dialog, which ->
                                        viewModel.setBanStatus(
                                            of = participant,
                                            isBanned = !(participant.banned ?: false)
                                        )

                                        dialog.dismiss()
                                    }
                                    .setNegativeButton(android.R.string.cancel) { dialog, which ->
                                        dialog.dismiss()
                                    }
                                    .show()
                            }
                        }
                    }
                    .show()
            }
        )

        binding.rvListParticipants.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)

        binding.rvListParticipants.adapter = adapter

        scrollListener = object : EndlessRecyclerViewScrollListener(binding.rvListParticipants.layoutManager!! as LinearLayoutManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                Log.d(
                    TAG,
                    "EndlessRecyclerViewScrollListener:: onLoadMore() -> page/totalItemsCount = ${page}/${totalItemsCount}"
                )
                // Attempt fetch more
                viewModel.fetchChatroomParticipants()
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.let { appActivity ->
            appActivity.setSupportActionBar(binding.toolbar)
            appActivity.supportActionBar?.setHomeButtonEnabled(true)
            appActivity.supportActionBar?.setDisplayShowHomeEnabled(true)
        }

        ///////////////////////////////
        // Bind ViewModel State
        ///////////////////////////////

        /**
         * Emits [true] upon start List Chatroom Participants SDK operation. Emits [false] when done.
         */
        viewModel.state.progressFetchChatroomParticipants()
            .onEach(::takeProgressFetchChatroomParticipants)
            .launchIn(lifecycleScope)

        /**
         * Emits response of List Chatroom Participants SDK operation.
         */
        viewModel.state.chatroomParticipants()
            .onEach(::takeChatroomParticipants)
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start Set Ban Status SDK operation. Emits [false] when done.
         */
        viewModel.state.progressUserSetBanStatus()
            .onEach(::takeProgressUserSetBanStatus)
            .launchIn(lifecycleScope)

        ///////////////////////////////
        // Bind View Effect
        ///////////////////////////////
        viewModel.effect
            .onEach(::takeViewEffect)
            .launchIn(lifecycleScope)

        ///////////////////////////////
        // Bind UI Input Actions
        ///////////////////////////////

        binding.swipeRefresh.refreshes()
            .asFlow()
            .onEach {
                // Perform refresh
                viewModel.refreshChatroomParticipants()
            }
            .launchIn(lifecycleScope)


        // Initial Fetch
        viewModel.fetchChatroomParticipants()
    }

    private suspend fun takeProgressFetchChatroomParticipants(inProgress: Boolean) {
        Log.d(TAG, "takeProgressFetchChatroomParticipants() -> inProgress = $inProgress")

        binding.swipeRefresh.isRefreshing = inProgress
        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            false -> View.GONE
        }
    }

    private suspend fun takeChatroomParticipants(participants: List<User>) {
        Log.d(TAG, "takeChatroomParticipants() -> participants = $participants")
        adapter.replace(participants)
    }

    private suspend fun takeProgressUserSetBanStatus(inProgress: Boolean) {
        Log.d(TAG, "takeProgressUserSetBanStatus() -> inProgress = $inProgress")

        when (inProgress) {
            true -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvListParticipants.isEnabled = false
            }
            false -> {
                binding.progressBar.visibility = View.GONE
                binding.rvListParticipants.isEnabled = true
            }
        }
    }

    private suspend fun takeViewEffect(effect: ChatroomListParticipantsViewModel.ViewEffect) {
        Log.d(TAG, "takeViewEffect() -> effect = ${effect::class.java.simpleName}")

        when (effect) {
            is ChatroomListParticipantsViewModel.ViewEffect.SuccessUserSetBanStatus -> {
                val updatedUser = effect.user
                adapter.update(updatedUser)
            }
            is ChatroomListParticipantsViewModel.ViewEffect.ErrorFetchChatroomParticipants -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatroomListParticipantsViewModel.ViewEffect.ErrorUserSetBanStatus -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> appNavController.popBackStack()
            else -> super.onOptionsItemSelected(item)
        }

    companion object {
        const val INPUT_ARG_ROOM = "input-arg-room"
        const val INPUT_ARG_USER = "input-arg-user"
    }
}