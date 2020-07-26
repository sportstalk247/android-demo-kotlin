package com.sportstalk.app.demo.presentation.chatroom.listparticipants

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import org.koin.android.ext.android.getKoin
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.androidx.viewmodel.koin.getViewModel
import org.koin.core.parameter.parametersOf

class ChatroomListParticipantsFragment : BaseFragment() {

    private lateinit var binding: FragmentChatroomListParticipantsBinding
    private val viewModel: ChatroomListParticipantsViewModel by lazy {
        getKoin().getViewModel<ChatroomListParticipantsViewModel>(
            owner = requireParentFragment(),
            parameters = {
                parametersOf(
                    room,
                    user
                )
            }
        )
    }

    private lateinit var user: User
    private lateinit var room: ChatRoom

    private lateinit var adapter: ItemChatroomParticipantAdapter
    private lateinit var scrollListener: RecyclerView.OnScrollListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

                val participantHandle = if(participant.handle?.isNotEmpty() == true && participant.handle!!.first() != '@') {
                    "@${participant.handle}"
                } else participant.handle

                val optionBan = getString(R.string.ban_handle, participantHandle ?: "")
                val optionRemoveBan = getString(R.string.remove_ban_from_handle, participantHandle ?: "")
                val optionPurgeMessages = getString(R.string.purge_messages_from_handle, participantHandle ?: "")

                val options = mutableListOf<String>().apply {
                    if (participant.banned == true) add(optionRemoveBan)
                    else add(optionBan)
                    add(optionPurgeMessages)
                }.toTypedArray()

                MaterialAlertDialogBuilder(requireContext())
                    .setItems(options) { _, index ->
                        when (options[index]) {
                            // Ban selected participant/Remove Ban from selected participant
                            optionBan, optionRemoveBan -> {
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
                            // Purge Message from selected participant
                            optionPurgeMessages -> {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setMessage(R.string.are_you_sure)
                                    .setPositiveButton(android.R.string.ok) { dialog, which ->
                                        // Attempt Perform Purge Operation
                                        viewModel.purgeMessages(from = participant)
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

        /**
         * Emits [true] upon start Purge User Messages SDK operation. Emits [false] when done.
         */
        viewModel.state.progressPurgeUserMessages()
            .onEach(::takeProgressPurgeUserMessages)
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

    private suspend fun takeProgressPurgeUserMessages(inProgress: Boolean) {
        Log.d(TAG, "takeProgressPurgeUserMessages() -> inProgress = $inProgress")

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
            is ChatroomListParticipantsViewModel.ViewEffect.SuccessPurgeUserMessages -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.chatevents_from_handle_successfully_purged, effect.who.handle),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatroomListParticipantsViewModel.ViewEffect.ErrorPurgeUserMessages -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        const val INPUT_ARG_ROOM = "input-arg-room"
        const val INPUT_ARG_USER = "input-arg-user"
    }
}