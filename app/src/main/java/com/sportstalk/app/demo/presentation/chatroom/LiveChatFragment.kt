package com.sportstalk.app.demo.presentation.chatroom

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentChatroomLiveChatBinding
import com.sportstalk.app.demo.extensions.throttleFirst
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.app.demo.presentation.chatroom.adapters.ItemChatEventAdapter
import com.sportstalk.app.demo.presentation.utils.EndlessRecyclerViewScrollListener
import com.sportstalk.datamodels.chat.ChatEvent
import com.sportstalk.datamodels.chat.ChatRoom
import com.sportstalk.datamodels.chat.EventType
import com.sportstalk.datamodels.chat.ReportType
import com.sportstalk.datamodels.users.User
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.getKoin
import org.koin.androidx.viewmodel.koin.getViewModel
import reactivecircus.flowbinding.android.view.clicks

class LiveChatFragment : BaseFragment() {

    private lateinit var binding: FragmentChatroomLiveChatBinding
    private val viewModel: ChatRoomViewModel by lazy {
        getKoin().getViewModel<ChatRoomViewModel>(owner = requireParentFragment())
    }

    private lateinit var scrollListener: RecyclerView.OnScrollListener

    private lateinit var user: User
    private lateinit var room: ChatRoom

    private lateinit var adapter: ItemChatEventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        user = requireArguments().getParcelable(ChatRoomFragment.INPUT_ARG_USER)!!
        room = requireArguments().getParcelable(ChatRoomFragment.INPUT_ARG_ROOM)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatroomLiveChatBinding.inflate(inflater)

        // Setup RecyclerView
        binding.recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, true)

        adapter = ItemChatEventAdapter(
            me = user,
            initialItems = listOf(),
            onTapChatEventItem = { chatEvent: ChatEvent ->
                Log.d(TAG, "onTapChatEventItem() -> chatEvent = $chatEvent")

                val options = ArrayList(
                    resources.getStringArray(R.array.chat_message_tap_options).toList()
                ).run {
                    // User's sent chat message(Prompt "Like", "Reply", "Report", "Flag as Deleted", or "Delete Permanently" options)
                    if(chatEvent.userid == user.userid)
                        slice(0 until size)
                    // Other's chat message(Prompt "Like", "Reply" and "Report" options ONLY)
                    else
                        slice(0 until 3)

                    // Bounce/Unbounce option
                    if(::room.isInitialized && room.bouncedusers?.contains(chatEvent.userid) == true) {
                        add(getString(R.string.chat_message_tap_option_unbounce))
                    } else {
                        add(getString(R.string.chat_message_tap_option_bounce))
                    }

                    return@run this
                }.toTypedArray()

                MaterialAlertDialogBuilder(requireContext())
                    .setItems(options) { dialog, which ->
                        when(options[which]) {
                            // Like
                            getString(R.string.chat_message_tap_option_like) -> {
                                // Perform React Operation
                                viewModel.reactToAMessage(
                                    event = chatEvent
                                )
                            }
                            // Reply
                            getString(R.string.chat_message_tap_option_reply) -> {
                                // Prepare Quoted Reply
                                viewModel.prepareQuotedReply(replyTo = chatEvent)
                            }
                            // Report
                            getString(R.string.chat_message_tap_option_report) -> {
                                // Perform Report Message
                                viewModel.reportMessage(which = chatEvent, reporttype = ReportType.ABUSE)
                            }
                            // Flag as Deleted
                            getString(R.string.chat_message_tap_option_flag_as_deleted) -> {
                                viewModel.removeMessage(
                                    which = chatEvent,
                                    isPermanentDelete = true,
                                    permanentifnoreplies = false/*true*/
                                )
                            }
                            // Delete Permanently
                            getString(R.string.chat_message_tap_option_delete_permanently) -> {
                                // Delete Permanently
                                viewModel.removeMessage(
                                    which = chatEvent,
                                    isPermanentDelete = true,
                                    permanentifnoreplies = true
                                )
                            }
                            // Bounce User
                            getString(R.string.chat_message_tap_option_bounce) -> {
                                val textInputLayout = LayoutInflater.from(requireContext())
                                    .inflate(
                                        R.layout.layout_inapp_settings_input_text,
                                        binding.root,
                                        false
                                    ) as TextInputLayout
                                val tietInputText = textInputLayout.findViewById<TextInputEditText>(R.id.tietInputText).apply {
                                    chatEvent.user?.handle?.let { handle -> setText(getString(R.string.the_bouncer_shows_handle_the_way_out, handle)) }
                                }

                                // Display Alert Prompt With Input Text
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.chat_message_tap_option_bounce)
                                    .setView(textInputLayout)
                                    .setPositiveButton(R.string.apply) { _, which ->
                                        viewModel.bounceUser(
                                            who = chatEvent.user!!,
                                            bounce = true,
                                            announcement = tietInputText.text?.toString()
                                        )
                                    }
                                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                                        dialog.dismiss()
                                    }
                                    .show()
                            }
                            // Un-bounce User
                            getString(R.string.chat_message_tap_option_unbounce) -> {
                                viewModel.bounceUser(
                                    who = chatEvent.user!!,
                                    bounce = false,
                                    announcement = chatEvent.user?.handle?.let { handle -> getString(R.string.the_bouncer_has_allowed_handle_to_enter_the_room, handle) }
                                )
                            }
                        }
                        dialog.dismiss()
                    }
                    .show()
            }
        )

        adapter.registerAdapterDataObserver(object: RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val item = adapter.getItem(positionStart)
                if(item.userid == user.userid) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }
        })

        binding.recyclerView.adapter = adapter

        scrollListener = object : EndlessRecyclerViewScrollListener(binding.recyclerView.layoutManager!! as LinearLayoutManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                Log.d(
                    TAG,
                    "EndlessRecyclerViewScrollListener:: onLoadMore() -> page/totalItemsCount = ${page}/${totalItemsCount}"
                )
                // Attempt fetch more
                viewModel.listPreviousEvents()
            }
        }

        binding.recyclerView.addOnScrollListener(scrollListener)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setNestedScrollingEnabled(binding.recyclerView, true)

        ///////////////////////////////
        // Bind ViewModel State
        ///////////////////////////////

        /**
         * Emits [true] upon start List Previous Events SDK operation. Emits [false] when done.
         */
        viewModel.state.progressListPreviousEvents()
            .onEach(::takeProgressListPreviousEvents)
            .launchIn(lifecycleScope)

        /**
         * Emits the overall list of events(includes results from `previouseventscursor` and `nexteventscursor`)
         */
        viewModel.state.chatEvents()
            .onEach(::takeChatEvents)
            .launchIn(lifecycleScope)


        /**
         * Emits an instance of [ChatEvent] the user wants to reply to(quoted).
         */
        viewModel.state.quotedReply()
            .onEach(::takeReplyTo)
            .launchIn(lifecycleScope)

        ///////////////////////////////
        // Bind View Effect
        ///////////////////////////////
        viewModel.effect
            .onEach(::takeViewEffect)
            .launchIn(lifecycleScope)

        /**
         * On click Clear Quoted Reply
         */
        binding.btnClear.clicks()
            .throttleFirst(1000)
            .onEach {
                viewModel.clearQuotedReply()
            }
            .launchIn(lifecycleScope)

    }

    override fun onDestroyView() {
        if(::scrollListener.isInitialized) {
            binding.recyclerView.removeOnScrollListener(scrollListener)
        }

        super.onDestroyView()
    }

    private suspend fun takeProgressListPreviousEvents(inProgress: Boolean) {
        Log.d(TAG, "takeProgressListPreviousEvents() -> inProgress = $inProgress")
        // TODO
    }

    private suspend fun takeChatEvents(chatEvents: List<ChatEvent>) {
        Log.d(TAG, "takeChatEvents() -> chatEvents = $chatEvents")

        if(::adapter.isInitialized) {
            adapter.replace(chatEvents)
        }
    }

    private suspend fun takeReplyTo(replyTo: ChatEvent) {
        Log.d(TAG, "takeReplyTo() - replyTo = $replyTo")

        if(replyTo.id != null) {
            binding.actvReplyTo.text = getString(R.string.reply_to, replyTo.user?.handle ?: "")
            binding.actvRepliedMessage.text = replyTo.body?.trim()

            binding.containerReply.visibility = View.VISIBLE
        } else {
            binding.containerReply.visibility = View.GONE
        }
    }

    private suspend fun takeViewEffect(effect: ChatRoomViewModel.ViewEffect) {
        Log.d(TAG, "takeViewEffect() -> effect = ${effect::class.java.simpleName}")

        when (effect) {
            is ChatRoomViewModel.ViewEffect.ReceiveChatEventUpdates -> {
                // Dispatch update received new events
                if (::adapter.isInitialized) {
                    val chatEvents = effect.eventUpdates.filter {
                        it.eventtype == EventType.SPEECH
                                || it.eventtype == EventType.ACTION
                                || it.eventtype == EventType.REACTION
                                || it.eventtype == EventType.QUOTE
                                || it.eventtype == EventType.REPLY
                    }
                    // Append to Chat list
                    adapter.update(chatEvents)

                    // Other events

                    val purgeEvents = effect.eventUpdates.filter {
                        it.eventtype == EventType.PURGE
                    }
                    // TODO:: Handle Purge Events

                    val reactionEvents = effect.eventUpdates.filter {
                        it.eventtype == EventType.REACTION
                    }
                    // TODO:: Handle Reaction Events

                    val roomOpenEvents = effect.eventUpdates.filter {
                        it.eventtype == EventType.ROOM_OPENED
                    }
                    // TODO:: Handle Room Open Events

                    val roomClosedEvents = effect.eventUpdates.filter {
                        it.eventtype == EventType.ROOM_CLOSED
                    }
                    // TODO:: Handle Room Closed Events

                    val roomGoalEvents = effect.eventUpdates.filter {
                        it.eventtype == EventType.GOAL
                    }
                    // TODO:: Handle Goal Events

                    val roomAdvertisementEvents = effect.eventUpdates.filter {
                        it.eventtype == EventType.ADVERTISEMENT
                    }
                    // TODO:: Handle Advertisement Events

                }
            }
            is ChatRoomViewModel.ViewEffect.QuotedReplySent -> {
                // Do nothing...
            }
            is ChatRoomViewModel.ViewEffect.ErrorSendQuotedReply -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.ThreadedReplySent -> {
                // TODO:: Success Threaded Reply
            }
            is ChatRoomViewModel.ViewEffect.ErrorSendThreadedReply -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.SuccessListPreviousEvents -> {

            }
            is ChatRoomViewModel.ViewEffect.ErrorListPreviousEvents -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.SuccessReactToAMessage -> {
                // Pre-emptively Update React State of Reacted ChatEvent
                if (::adapter.isInitialized) {
                    adapter.update(effect.response)
                }
            }
            is ChatRoomViewModel.ViewEffect.ErrorReactToAMessage -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.SuccessRemoveMessage -> {
                Log.d(TAG, "ChatRoomViewModel.ViewEffect.SuccessRemoveMessage:: permanentdelete = ${effect.response.permanentdelete}")
                Log.d(TAG, "ChatRoomViewModel.ViewEffect.SuccessRemoveMessage:: removedEvent = ${effect.response.event}")

                // Pre-emptively Remove ChatEvent
                if (::adapter.isInitialized) {
                    effect.response.event?.let { removedEvent ->
                        if(effect.response.permanentdelete == true) {
                            adapter.remove(removedEvent)
                        } else {
                            adapter.update(removedEvent)
                        }
                    }
                }

                Toast.makeText(
                    requireContext(),
                    R.string.message_successfully_removed,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.SuccessBounceUser -> {
                Log.d(TAG, "ChatRoomViewModel.ViewEffect.SuccessBounceUser -> this.room = effect.response.room!!")
                this.room = effect.response.room!!
            }
            is ChatRoomViewModel.ViewEffect.SuccessUnbounceUser -> {
                Log.d(TAG, "ChatRoomViewModel.ViewEffect.SuccessUnbounceUser -> this.room = effect.response.room!!")
                this.room = effect.response.room!!
            }
            else -> {}
        }
    }

    companion object {

        const val INPUT_ARG_ROOM = "input-arg-room"
        const val INPUT_ARG_USER = "input-arg-user"

        fun newInstance(room: ChatRoom, user: User): LiveChatFragment =
            LiveChatFragment().apply {
                arguments = bundleOf(
                    INPUT_ARG_ROOM to room,
                    INPUT_ARG_USER to user
                )
            }

    }
}