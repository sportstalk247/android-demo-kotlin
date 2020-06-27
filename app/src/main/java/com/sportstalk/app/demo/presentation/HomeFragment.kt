package com.sportstalk.app.demo.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.jakewharton.rxbinding3.viewpager2.pageSelections
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentHomeBinding
import com.sportstalk.app.demo.presentation.inappsettings.InAppSettingsFragment
import com.sportstalk.app.demo.presentation.listrooms.ListChatRoomsFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx2.asFlow

class HomeFragment : BaseFragment() {

    private lateinit var binding: FragmentHomeBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater)

        // Setup ViewPager2 and BottomNavigationView
        binding.viewPager2.adapter = ViewPager2Adapter(childFragmentManager, lifecycle)
        binding.viewPager2.pageSelections()
            .skipInitialValue()
            .asFlow()
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

        return binding.root
    }

    inner class ViewPager2Adapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun getItemCount(): Int = TAB_COUNT

        override fun createFragment(position: Int): Fragment =
            when (position) {
                TAB_FAN -> ListChatRoomsFragment()
                TAB_ADMIN -> Fragment()
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