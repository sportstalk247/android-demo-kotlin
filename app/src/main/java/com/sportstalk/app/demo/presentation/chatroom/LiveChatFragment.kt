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
import com.jakewharton.rxbinding3.view.clicks
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentChatroomLiveChatBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.app.demo.presentation.chatroom.adapters.ItemChatEventAdapter
import com.sportstalk.models.chat.ChatEvent
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.users.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx2.asFlow
import org.koin.androidx.viewmodel.ext.android.getViewModel
import java.util.concurrent.TimeUnit

class LiveChatFragment : BaseFragment() {

    private lateinit var binding: FragmentChatroomLiveChatBinding
    private val viewModel: ChatRoomViewModel by lazy {
        (parentFragment ?: this@LiveChatFragment).getViewModel<ChatRoomViewModel>()
    }

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
            .throttleFirst(1000, TimeUnit.MILLISECONDS)
            .asFlow()
            .onEach {
                viewModel.clearQuotedReply()
            }
            .launchIn(lifecycleScope)

    }

    private suspend fun takeProgressListPreviousEvents(inProgress: Boolean) {
        Log.d(TAG, "takeProgressListPreviousEvents() -> inProgress = $inProgress")
        // TODO
    }

    private suspend fun takeChatEvents(initialChatEvents: List<ChatEvent>) {
        Log.d(TAG, "takeChatEvents() -> initialChatEvents = ${initialChatEvents}")

        adapter = ItemChatEventAdapter(
            me = user,
            initialItems = initialChatEvents,
            onTapChatEventItem = { chatEvent: ChatEvent ->
                Log.d(TAG, "takeChatEvents() -> onTapChatEventItem() -> chatEvent = $chatEvent")

                val options = ArrayList(
                    resources.getStringArray(R.array.chat_message_tap_options).toList()
                ).run {
                    // User's sent chat message(Prompt "Reply", "Flag as Deleted", or "Delete Permanently" options)
                    if(chatEvent.userid == user.userid)
                        slice(0 until size)
                    // Other's chat message(Prompt "Reply" option ONLY)
                    else
                        slice(0 until 1)
                }.toTypedArray()

                MaterialAlertDialogBuilder(requireContext())
                    .setItems(options) { dialog, which ->
                        when(which) {
                            // Reply
                            0 -> {
                                // Prepare Quoted Reply
                                viewModel.prepareQuotedReply(replyTo = chatEvent)
                            }
                            else -> {
                                /*
                                * [Y/N] Do you want this message to get permanently deleted if no replies were received?
                                */
                                var permanentifnoreplies: Boolean? = null
                                MaterialAlertDialogBuilder(requireContext())
                                    .setMessage(R.string.permanent_if_no_replies)
                                    .setPositiveButton(android.R.string.yes) { dialog, _ ->
                                        permanentifnoreplies = true
                                        dialog.dismiss()
                                    }
                                    .setNegativeButton(android.R.string.no) { dialog, _ ->
                                        permanentifnoreplies = false
                                        dialog.dismiss()
                                    }
                                    .setOnDismissListener {
                                        when(which) {
                                            // Flag as Deleted
                                            1 -> {
                                                viewModel.removeMessage(
                                                    which = chatEvent,
                                                    isPermanentDelete = false,
                                                    permanentifnoreplies = permanentifnoreplies
                                                )
                                            }
                                            // Delete Permanently
                                            2 -> {
                                                viewModel.removeMessage(
                                                    which = chatEvent,
                                                    isPermanentDelete = true,
                                                    permanentifnoreplies = permanentifnoreplies
                                                )
                                            }
                                        }
                                    }
                                    .show()
                            }
                        }
                        dialog.dismiss()
                    }
                    .show()
            },
            onTapReactChatEventItem = { chatEvent: ChatEvent, hasAlreadyReacted: Boolean ->
                // Perform React Operation
                viewModel.reactToAMessage(
                    event = chatEvent,
                    hasAlreadyReacted = hasAlreadyReacted
                )
            }
        )

        binding.recyclerView.adapter = adapter
        // Explicit force scroll to latest chat event item
        delay(1000)
        binding.recyclerView.smoothScrollToPosition(0)
    }

    private suspend fun takeReplyTo(replyTo: ChatEvent) {
        Log.d(TAG, "takeReplyTo() - replyTo = $replyTo")

        if(replyTo.id != null) {
            binding.actvReplyTo.text = getString(R.string.reply_to, replyTo.user?.handle ?: "")
            binding.actvRepliedMessage.text = replyTo.body

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
                    adapter.update(effect.eventUpdates)
                }
            }
            is ChatRoomViewModel.ViewEffect.ChatMessageSent -> {
                // Scroll to bottom of chat event list
                if (::adapter.isInitialized) {
                    delay(1000)
                    binding.recyclerView.smoothScrollToPosition(0)
                }
            }
            is ChatRoomViewModel.ViewEffect.ErrorSendChatMessage -> {
                Toast.makeText(
                    requireContext(),
                    R.string.something_went_wrong_please_try_again,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.QuotedReplySent -> {

            }
            is ChatRoomViewModel.ViewEffect.ErrorSendQuotedReply -> {
                Toast.makeText(
                    requireContext(),
                    R.string.something_went_wrong_please_try_again,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.ThreadedReplySent -> {

            }
            is ChatRoomViewModel.ViewEffect.ErrorSendThreadedReply -> {
                Toast.makeText(
                    requireContext(),
                    R.string.something_went_wrong_please_try_again,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.SuccessListPreviousEvents -> {

            }
            is ChatRoomViewModel.ViewEffect.ErrorListPreviousEvents -> {
                Toast.makeText(
                    requireContext(),
                    R.string.something_went_wrong_please_try_again,
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
                    R.string.something_went_wrong_please_try_again,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.SuccessRemoveMessage -> {
                // Pre-emptively Remove ChatEvent
                if (::adapter.isInitialized) {
                    effect.response.event?.let { removedEvent ->
                        adapter.remove(removedEvent)
                    }
                }

                Toast.makeText(
                    requireContext(),
                    R.string.message_successfully_removed,
                    Toast.LENGTH_SHORT
                ).show()
            }
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