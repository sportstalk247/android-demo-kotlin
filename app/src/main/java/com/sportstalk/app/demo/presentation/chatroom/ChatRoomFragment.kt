package com.sportstalk.app.demo.presentation.chatroom

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.jakewharton.rxbinding3.view.clicks
import com.sportstalk.SportsTalk247
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentChatroomBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.app.demo.presentation.chatroom.listparticipants.ChatroomListParticipantsFragment
import com.sportstalk.app.demo.presentation.utils.AppBarStateChangedListener
import com.sportstalk.models.ClientConfig
import com.sportstalk.models.chat.ChatEvent
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.users.User
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
            user,
            SportsTalk247.ChatClient(config)
        )
    }

    private lateinit var user: User
    private lateinit var room: ChatRoom

    private lateinit var errorJoinSnackBar: Snackbar

    private val config: ClientConfig by lazy {
        ClientConfig(
            appId = getString(R.string.sportstalk247_appid),
            apiToken = getString(R.string.sportstalk247_authToken),
            endpoint = getString(R.string.sportstalk247_urlEndpoint)
        )
    }

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
                    message = binding.tietChatMessage.text?.toString() ?: ""
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
                // Call Exit Chatroom Operation
                appNavController.popBackStack()
                true
            }
            R.id.action_leave_room -> {
                // Attempt execute Exit Room
                viewModel.exitRoom()
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
        Log.d(TAG, "takeProgressExitRoom() -> inProgress")

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