package com.sportstalk.app.demo.presentation.chatroom

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentChatroomBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.app.demo.presentation.utils.AppBarStateChangedListener
import com.sportstalk.models.ClientConfig
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.users.User

class ChatRoomFragment : BaseFragment() {

    private lateinit var binding: FragmentChatroomBinding

    private lateinit var user: User
    private lateinit var room: ChatRoom

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

        // Setup ViewPager2 and TabLayout
        binding.viewPager2.adapter = ViewPager2Adapter(childFragmentManager, lifecycle)
        viewPager2PageChangeCallback = object: ViewPager2.OnPageChangeCallback() {
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

        binding.tabLayoutChatroom.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: return
                binding.viewPager2.currentItem = position

                binding.layoutInputMessage.visibility = when(position) {
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
                object: AppBarStateChangedListener() {
                    override fun onStateChanged(appBarLayout: AppBarLayout, state: State) {
                        when(state) {
                            State.COLLAPSED -> appActivity.supportActionBar?.title = room.name
                            State.EXPANDED -> appActivity.supportActionBar?.title = ""
                            else -> {}
                        }
                    }
                }
            )
        }

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
            }
            else -> super.onOptionsItemSelected(item)
        }

    inner class ViewPager2Adapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle
    ): FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun getItemCount(): Int = TAB_COUNT

        override fun createFragment(position: Int): Fragment =
            when(position) {
                TAB_TEAM -> Fragment()
                TAB_STATISTICS -> Fragment()
                TAB_LIVE_CHAT -> LiveChatFragment()
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