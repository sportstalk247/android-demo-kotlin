package com.sportstalk.app.demo.presentation.chatroom

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.sportstalk.app.demo.presentation.utils.AppBarStateChangedListener
import com.sportstalk.models.ClientConfig
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

    private lateinit var viewPager2PageChangeCallback: ViewPager2.OnPageChangeCallback

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
            }

        // Setup ViewPager2 and TabLayout
        binding.viewPager2.adapter = ViewPager2Adapter(childFragmentManager, lifecycle)
        viewPager2PageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Also sync tab layout selected tab
                binding.tabLayoutChatroom.selectTab(
                    binding.tabLayoutChatroom.getTabAt(position)
                )
            }
        }
        // Register page change callback
        binding.viewPager2.registerOnPageChangeCallback(viewPager2PageChangeCallback)

        binding.tabLayoutChatroom.addOnTabSelectedListener(object :
            TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: return
                binding.viewPager2.currentItem = position

                binding.layoutInputMessage.visibility = when (position) {
                    TAB_LIVE_CHAT -> View.VISIBLE
                    else -> View.GONE
                }
            }
        })

        // Initially set selected tab to `Live Chat`
        binding.tabLayoutChatroom.selectTab(
            binding.tabLayoutChatroom.getTabAt(2)
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.let { appActivity ->
            appActivity.setSupportActionBar(binding.toolbar)
            appActivity.supportActionBar?.setHomeButtonEnabled(true)
            appActivity.supportActionBar?.setDisplayShowHomeEnabled(true)

            // Setup AppBar Expand/Collapse Behavior
            binding.appBarLayout.addOnOffsetChangedListener(
                object : AppBarStateChangedListener() {
                    override fun onStateChanged(appBarLayout: AppBarLayout, state: State) {
                        when (state) {
                            State.COLLAPSED -> appActivity.supportActionBar?.title = room.name
                            State.EXPANDED -> appActivity.supportActionBar?.title = ""
                            else -> {
                            }
                        }
                    }
                }
            )
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
            }
            .launchIn(lifecycleScope)


        // Perform Join Chatroom
        viewModel.joinRoom()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister page change callback
        binding.viewPager2.unregisterOnPageChangeCallback(viewPager2PageChangeCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.chatroom, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                // TODO:: Call Exit Chatroom Operation
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
        binding.actvRoomName.text = name
    }

    private val attendanceFormatter = DecimalFormat("###,###,###,###,###,###")
    private suspend fun takeAttendeesCount(count: Long) {
        Log.d(TAG, "takeAttendeesCount() -> count = $count")
        binding.actvAttendance.text = attendanceFormatter.format(count)
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
        // TODO

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
        }
    }

    inner class ViewPager2Adapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun getItemCount(): Int = TAB_COUNT

        override fun createFragment(position: Int): Fragment =
            when (position) {
                TAB_TEAM -> Fragment()
                TAB_STATISTICS -> Fragment()
                TAB_LIVE_CHAT -> LiveChatFragment.newInstance(room, user)
                TAB_HIGHLIGHTS -> Fragment()
                TAB_VIDEOS -> Fragment()
                else -> Fragment()
            }
    }

    companion object {
        val TAG = ChatRoomFragment::class.java.simpleName

        private const val TAB_COUNT = 5
        private const val TAB_TEAM = 0
        private const val TAB_STATISTICS = 1
        private const val TAB_LIVE_CHAT = 2
        private const val TAB_HIGHLIGHTS = 3
        private const val TAB_VIDEOS = 4

        const val INPUT_ARG_ROOM = "input-arg-room"
        const val INPUT_ARG_USER = "input-arg-user"
    }

}