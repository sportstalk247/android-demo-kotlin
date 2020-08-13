package com.sportstalk.app.demo.presentation.chatroom

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding3.view.clicks
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentChatroomBinding
import com.sportstalk.app.demo.extensions.throttleFirst
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.users.User
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx2.asFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

class ChatRoomFragment : BaseFragment() {

    private lateinit var binding: FragmentChatroomBinding
    private val viewModel: ChatRoomViewModel by viewModel {
        parametersOf(
            room,
            user
        )
    }

    private val popBackChannel = Channel<Any>(Channel.RENDEZVOUS)

    override fun enableBackPressedCallback(): Boolean = true
    override fun onBackPressedCallback(): OnBackPressedCallback.() -> Unit = {
        popBackChannel.sendBlocking(Any())
    }

    private lateinit var user: User
    private lateinit var room: ChatRoom

    private lateinit var errorJoinSnackBar: Snackbar

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
        binding = FragmentChatroomBinding.inflate(inflater)
        errorJoinSnackBar = Snackbar.make(
            binding.root,
            R.string.unable_to_join_room_please_try_again,
            Snackbar.LENGTH_INDEFINITE
        )
            .setAction(R.string.join_room) {
                // TODO:: Retry Join
                viewModel.joinRoom()
            }

        childFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,
                LiveChatFragment().apply {
                    arguments = bundleOf(
                        LiveChatFragment.INPUT_ARG_ROOM to room,
                        LiveChatFragment.INPUT_ARG_USER to user
                    )
                }
            )
            .commit()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.let { appActivity ->
            appActivity.setSupportActionBar(binding.toolbar)
            appActivity.supportActionBar?.title = room.name
            appActivity.supportActionBar?.setHomeButtonEnabled(true)
        }

        popBackChannel.consumeAsFlow()
            .throttleFirst(1000L)
            .onEach {
                Log.d(TAG, "popBackChannel.consumeAsFlow()")
                // Attempt execute Exit Room
                viewModel.exitRoom()
            }
            .launchIn(lifecycleScope)

        ///////////////////////////////
        // Bind ViewModel State
        ///////////////////////////////

        /**
         * Emits [ChatRoom.name].
         */
        viewModel.state.roomName()
            .onEach(::takeRoomName)
            .launchIn(lifecycleScope)

        /**
         * Emits [ChatRoom.inroom].
         */
        viewModel.state.attendeesCount()
            .onEach(::takeAttendeesCount)
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start Join Room SDK operation. Emits [false] when done.
         */
        viewModel.state.progressJoinRoom()
            .onEach(::takeProgressJoinRoom)
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start Exit Room SDK operation. Emits [false] when done.
         */
        viewModel.state.progressExitRoom()
            .onEach(::takeProgressExitRoom)
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start Execute Chat Command SDK operation. Emits [false] when done.
         */
        viewModel.state.progressSendChatMessage()
            .onEach(::takeProgressSendChatMessage)
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start `Delete Event` or `Flag Message Event as Deleted` SDK operation. Emits [false] when done.
         */
        viewModel.state.progressRemoveMessage()
            .onEach(::takeProgressRemoveMessage)
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start `Report Message` SDK operation. Emits [false] when done.
         */
        viewModel.state.progressReportMessage()
            .onEach(::takeProgressReportMessage)
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
        binding.btnSend.clicks()
            .throttleFirst(500, TimeUnit.MILLISECONDS)
            .asFlow()
            .onEach {
                // Perform send
                viewModel.sendChatMessage(
                    message = binding.tietChatMessage.text.toString().trim() ?: ""
                )
                // Clear text
                binding.tietChatMessage.setText("")
            }
            .launchIn(lifecycleScope)


        // Perform Join Chatroom
        viewModel.joinRoom()

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.chatroom, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                requireActivity().onBackPressed()
                true
            }
            R.id.action_account_settings -> {
                // Navigate to Account Settings
                if(appNavController.currentDestination?.id == R.id.fragmentChatroom) {
                    appNavController.navigate(
                        R.id.action_fragmentChatroom_to_fragmentAccountSettings
                    )
                }

                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private suspend fun takeRoomName(name: String) {
        Log.d(TAG, "takeRoomName() -> name = $name")
        (requireActivity() as? AppCompatActivity)?.let { act ->
            act.supportActionBar?.title = name
        }
    }

    private val attendanceFormatter = DecimalFormat("###,###,###,###,###,###")
    private suspend fun takeAttendeesCount(count: Long) {
        Log.d(TAG, "takeAttendeesCount() -> count = $count")
        // TODO:: takeAttendeesCount()
    }

    private suspend fun takeProgressJoinRoom(inProgress: Boolean) {
        Log.d(TAG, "takeProgressJoinRoom() -> inProgress")

        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            false -> View.GONE
        }
    }

    private suspend fun takeProgressExitRoom(inProgress: Boolean) {
        Log.d(TAG, "takeProgressExitRoom() -> inProgress = $inProgress")

        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            false -> View.GONE
        }
    }

    private suspend fun takeProgressSendChatMessage(inProgress: Boolean) {
        Log.d(TAG, "takeProgressSendChatMessage() -> inProgress = $inProgress")

        when (inProgress) {
            true -> {
                binding.tilChatMessage.isEnabled = false
                binding.btnSend.isEnabled = false
                binding.progressBarSendChat.visibility = View.VISIBLE
            }
            false -> {
                binding.tilChatMessage.isEnabled = true
                binding.btnSend.isEnabled = true
                binding.progressBarSendChat.visibility = View.GONE
            }
        }
    }

    private suspend fun takeProgressRemoveMessage(inProgress: Boolean) {
        Log.d(TAG, "takeProgressRemoveMessage() -> inProgress = $inProgress")

        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            false -> View.GONE
        }
    }

    private suspend fun takeProgressReportMessage(inProgress: Boolean) {
        Log.d(TAG, "takeProgressReportMessage() -> inProgress = $inProgress")

        binding.progressBar.visibility = when (inProgress) {
            true -> View.VISIBLE
            false -> View.GONE
        }
    }

    private suspend fun takeViewEffect(effect: ChatRoomViewModel.ViewEffect) {
        Log.d(TAG, "takeViewEffect() -> effect = ${effect::class.java.simpleName}")

        when (effect) {
            is ChatRoomViewModel.ViewEffect.SuccessJoinRoom -> {
                // Attempt Start Listening to Chat Events
                viewModel.startListeningToChatUpdates(lifecycleOwner = this@ChatRoomFragment)
            }
            is ChatRoomViewModel.ViewEffect.ErrorJoinRoom -> {
                errorJoinSnackBar.show()
            }
            is ChatRoomViewModel.ViewEffect.SuccessExitRoom -> {
                Toast.makeText(
                    requireContext(),
                    "You've left the room.",
                    Toast.LENGTH_SHORT
                ).show()

                // Navigate popback
                appNavController.popBackStack()
            }
            is ChatRoomViewModel.ViewEffect.ErrorExitRoom -> {
                Toast.makeText(
                    requireContext(),
                    R.string.something_went_wrong_please_try_again,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.ChatMessageSent -> {
                // Clear text
                binding.tietChatMessage.setText("")
            }
            is ChatRoomViewModel.ViewEffect.ErrorSendChatMessage -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.ErrorRemoveMessage -> {
                Toast.makeText(
                    requireContext(),
                    R.string.something_went_wrong_please_try_again,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.SuccessReportMessage -> {
                Toast.makeText(
                    requireContext(),
                    R.string.message_successfully_reported,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ChatRoomViewModel.ViewEffect.ErrorReportMessage -> {
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