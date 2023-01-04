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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentChatroomListParticipantsBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.app.demo.presentation.chatroom.listparticipants.adapters.ItemChatroomParticipantAdapter
import com.sportstalk.app.demo.presentation.utils.EndlessRecyclerViewScrollListener
import com.sportstalk.datamodels.chat.ChatRoom
import com.sportstalk.datamodels.users.User
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.rx2.asFlow
import org.koin.android.ext.android.getKoin
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

        val bouncedUsers = when {
            ::room.isInitialized && room.bouncedusers != null -> room.bouncedusers!!
            else -> listOf()
        }.map { userid -> User(userid = userid) }

        adapter = ItemChatroomParticipantAdapter(
            me = user,
            bouncedItems = bouncedUsers,
            onTapChatParticipantItem = { participant: User ->
                Log.d(TAG, "onTapChatParticipantItem() -> participant = $participant")

                val participantHandle = if(participant.handle?.isNotEmpty() == true && participant.handle!!.first() != '@') {
                    "@${participant.handle}"
                } else participant.handle

                val optionBan = getString(R.string.ban_handle, participantHandle ?: "")
                val optionRemoveBan = getString(R.string.remove_ban_from_handle, participantHandle ?: "")
                val optionBounce = getString(R.string.bounce_handle, participantHandle ?: "")
                val optionRemoveBounce = getString(R.string.remove_bounce_from_handle, participantHandle ?: "")
                val optionPurgeMessages = getString(R.string.purge_messages_from_handle, participantHandle ?: "")

                val options = mutableListOf<String>().apply {
                    if (participant.banned == true) add(optionRemoveBan)
                    else add(optionBan)

                    if(room.bouncedusers?.any { id -> participant.userid == id } == true) add(optionRemoveBounce)
                    else add(optionBounce)

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
                            // Bounce/Remove Bounce
                            optionBounce -> {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setMessage(R.string.are_you_sure)
                                    .setPositiveButton(android.R.string.ok) { dialog, which ->
                                        val textInputLayout = LayoutInflater.from(requireContext())
                                            .inflate(
                                                R.layout.layout_inapp_settings_input_text,
                                                binding.root,
                                                false
                                            ) as TextInputLayout
                                        val tietInputText = textInputLayout.findViewById<TextInputEditText>(R.id.tietInputText).apply {
                                            participant.handle?.let { handle -> setText(getString(R.string.the_bouncer_shows_handle_the_way_out, handle)) }
                                        }

                                        // Display Alert Prompt With Input Text
                                        MaterialAlertDialogBuilder(requireContext())
                                            .setTitle(optionBounce)
                                            .setView(textInputLayout)
                                            .setPositiveButton(R.string.apply) { _, which ->
                                                Log.d(TAG, "selected: optionBounce = $optionBounce")
                                                viewModel.bounceUser(
                                                    who = participant,
                                                    bounce = true,
                                                    announcement = tietInputText.text?.toString()
                                                )
                                            }
                                            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                                                dialog.dismiss()
                                            }
                                            .show()

                                        dialog.dismiss()
                                    }
                                    .setNegativeButton(android.R.string.cancel) { dialog, which ->
                                        dialog.dismiss()
                                    }
                                    .show()
                            }
                            /* Bounced users are listed on separate sections
                            optionRemoveBounce -> {
                                Log.d(TAG, "selected: optionRemoveBounce = $optionRemoveBounce")
                                viewModel.bounceUser(
                                    who = participant,
                                    bounce = false,
                                    announcement = getString(R.string.the_bouncer_has_allowed_handle_to_enter_the_room, participantHandle)
                                )
                            }*/
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
            },
            onTapBouncedUser = { item ->
                Log.d(TAG, "onTapBouncedUser:: item = $item")

                val optionUnbounce = getString(R.string.chat_message_tap_option_unbounce)
                val options = arrayOf(optionUnbounce)

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("@${item.handle}")
                    .setItems(options) { _, index ->
                        when(options[index]) {
                            optionUnbounce -> {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setMessage(R.string.are_you_sure)
                                    .setPositiveButton(android.R.string.ok) { dialog, which ->
                                        // Perform Un-bounce user
                                        viewModel.bounceUser(
                                            who = item,
                                            bounce = false,
                                            announcement = getString(R.string.the_bouncer_has_allowed_handle_to_enter_the_room, "@${item.handle}")
                                        )
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
         * Emits [true] upon start Batch call to Get User Details SDK operation. Emits [false] when done.
         */
        viewModel.state.progressFetchBouncedUsers()
            .onEach(::takeProgressFetchBouncedUsers)
            .launchIn(lifecycleScope)

        /**
         * Emits response of List Chatroom Participants SDK operation.
         */
        viewModel.state.chatroomParticipants()
            .zip(
                /**
                 * Emits response from Batch of Get User Details SDK operation.
                 */
                viewModel.state.bouncedUsers()
            ) { _participants, _bouncedUsers ->
                Pair(_participants, _bouncedUsers)
            }
            .onEach { (_participants, _bouncedUsers) ->
                takeChatroomParticipantsBouncedUsers(_participants, _bouncedUsers)
            }
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
                viewModel.fetchBouncedUsers(which = this.room)
            }
            .launchIn(lifecycleScope)


        // Initial Fetch Chatroom Participants
        viewModel.fetchChatroomParticipants()
        // Initial fetch bounced users
        viewModel.fetchBouncedUsers(which = this.room)
    }

    private suspend fun takeProgressFetchChatroomParticipants(inProgress: Boolean) {
        Log.d(TAG, "takeProgressFetchChatroomParticipants() -> inProgress = $inProgress")

        binding.swipeRefresh.isRefreshing = inProgress
        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            false -> View.GONE
        }
    }

    private suspend fun takeProgressFetchBouncedUsers(inProgress: Boolean) {
        Log.d(TAG, "takeProgressFetchBouncedUsers() -> inProgress = $inProgress")

        when (inProgress) {
            true -> {
                binding.progressBar.visibility = View.VISIBLE
            }
            false -> {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun takeChatroomParticipantsBouncedUsers(participants: List<User>, bouncedUsers: List<User>) {
        Log.d(TAG, "takeBouncedUsers() -> bouncedUsers = $bouncedUsers")

        adapter.replaceParticipants(participants)
        adapter.replaceBouncedUsers(bouncedUsers)
        adapter.notifyDataSetChanged()
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
                viewModel.refreshChatroomParticipants()
            }
            is ChatroomListParticipantsViewModel.ViewEffect.ErrorFetchChatroomParticipants -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatroomListParticipantsViewModel.ViewEffect.ErrorFetchBouncedUsers -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatroomListParticipantsViewModel.ViewEffect.ErrorUserSetBanStatus -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatroomListParticipantsViewModel.ViewEffect.SuccessPurgeUserMessages -> {
                Toast.makeText(
                    requireContext(),
                    effect.response.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.chatevents_from_handle_successfully_purged, effect.who.handle),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatroomListParticipantsViewModel.ViewEffect.ErrorPurgeUserMessages -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatroomListParticipantsViewModel.ViewEffect.SuccessBounceUser -> {
                Toast.makeText(
                    requireContext(),
                    effect.response.event?.body ?: "",
                    Toast.LENGTH_SHORT
                ).show()

                this.room = effect.response.room!!

                // Re-fetch chatroom participants
                viewModel.fetchChatroomParticipants()
                // Re-fetch bounced users
                viewModel.fetchBouncedUsers(which = this.room)
            }
            is ChatroomListParticipantsViewModel.ViewEffect.SuccessUnbounceUser -> {
                Toast.makeText(
                    requireContext(),
                    effect.response.event?.body ?: "",
                    Toast.LENGTH_SHORT
                ).show()

                this.room = effect.response.room!!

                // Re-fetch chatroom participants
                viewModel.fetchChatroomParticipants()
                // Re-fetch bounced users
                viewModel.fetchBouncedUsers(which = this.room)
            }
            is ChatroomListParticipantsViewModel.ViewEffect.ErrorBounceUser -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() } ?: getString(R.string.something_went_wrong_please_try_again),
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