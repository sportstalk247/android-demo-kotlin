package com.sportstalk.app.demo.presentation

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.app.demo.databinding.FragmentHomeBinding
import com.sportstalk.app.demo.presentation.chatroom.ChatRoomFragment
import com.sportstalk.app.demo.presentation.inappsettings.InAppSettingsFragment
import com.sportstalk.app.demo.presentation.listrooms.AdminListChatRoomsFragment
import com.sportstalk.app.demo.presentation.listrooms.ListChatRoomsFragment
import com.sportstalk.app.demo.presentation.users.CreateAccountFragment
import com.sportstalk.datamodels.ClientConfig
import com.sportstalk.datamodels.chat.ChatRoom
import com.sportstalk.datamodels.users.User
import com.sportstalk.reactive.rx2.SportsTalk247
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.android.inject
import reactivecircus.flowbinding.viewpager2.pageSelections

class HomeFragment : BaseFragment() {

    private lateinit var binding: FragmentHomeBinding
    private val preferences by inject<SportsTalkDemoPreferences>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Consume LiveData result from navigation popback Create Account
        appNavController.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Pair<User, ChatRoom>>(CreateAccountFragment.OUTPUT_ARG_CREATED_USER_FOR_ROOM)
            ?.observe(viewLifecycleOwner) { (createdUser, forRoom) ->
                Log.d(TAG, "savedStateHandle:: createdUser -> $createdUser")
                Log.d(TAG, "savedStateHandle:: forRoom -> $forRoom")

                // Remove result
                appNavController.currentBackStackEntry?.savedStateHandle?.remove<Pair<User, ChatRoom>>(CreateAccountFragment.OUTPUT_ARG_CREATED_USER_FOR_ROOM)

                lifecycleScope.launchWhenResumed {
                    delay(500)
                    if(appNavController.currentDestination?.id == R.id.fragmentHome) {
                        appNavController.navigate(
                            R.id.action_fragmentHome_to_fragmentChatroom,
                            bundleOf(
                                ChatRoomFragment.INPUT_ARG_ROOM to forRoom,
                                ChatRoomFragment.INPUT_ARG_USER to createdUser
                            )
                        )
                    }
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater)

        // Setup ViewPager2 and BottomNavigationView
        binding.viewPager2.offscreenPageLimit = 1

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe for preference changes. Notify viewpager refresh to re-create config
        combine(
            preferences.appIdChanges().distinctUntilChanged(),
            preferences.authTokenChanges().distinctUntilChanged(),
            preferences.urlEndpointChanges().distinctUntilChanged()
        ) { _appId, _authToken, _url -> Triple(_appId, _authToken, _url) }
            .distinctUntilChanged()
            .debounce(250)
            .onEach { (_appId, _authToken, _url) ->
                Log.d(TAG, "onViewCreated() -> _appId = $_appId")
                Log.d(TAG, "onViewCreated() -> _authToken = $_authToken")

                val isFirstLoad = binding.viewPager2.adapter?.itemCount != TAB_COUNT

                binding.viewPager2.adapter = ViewPager2Adapter(childFragmentManager, lifecycle)
                binding.viewPager2.pageSelections(emitImmediately = false)
                    .onEach { position ->
                        when (position) {
                            TAB_FAN -> binding.bottomNavView.selectedItemId = R.id.bottomNavFan
                            TAB_ADMIN -> binding.bottomNavView.selectedItemId = R.id.bottomNavAdmin
                            TAB_SETTINGS -> binding.bottomNavView.selectedItemId = R.id.bottomNavSettings
                        }
                    }
                    .launchIn(lifecycleScope)

                binding.bottomNavView.setOnNavigationItemSelectedListener { item: MenuItem ->
                    when (item.itemId) {
                        R.id.bottomNavFan -> {
                            binding.viewPager2.setCurrentItem(TAB_FAN, true)
                            true
                        }
                        R.id.bottomNavAdmin -> {
                            binding.viewPager2.setCurrentItem(TAB_ADMIN, true)
                            true
                        }
                        R.id.bottomNavSettings -> {
                            binding.viewPager2.setCurrentItem(TAB_SETTINGS, true)
                            true
                        }
                        else -> false
                    }
                }

                if(!isFirstLoad) binding.bottomNavView.selectedItemId = R.id.bottomNavSettings
            }
            .launchIn(lifecycleScope)

        val chatClient = SportsTalk247.ChatClient(getKoin().get<ClientConfig>())

    }

    inner class ViewPager2Adapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun getItemCount(): Int = TAB_COUNT

        override fun createFragment(position: Int): Fragment =
            when (position) {
                TAB_FAN -> ListChatRoomsFragment()
                TAB_ADMIN -> AdminListChatRoomsFragment()
                TAB_SETTINGS -> InAppSettingsFragment()
                else -> Fragment()
            }
    }

    companion object {
        const val TAB_COUNT = 3
        const val TAB_FAN = 0
        const val TAB_ADMIN = 1
        const val TAB_SETTINGS = 2
    }

}