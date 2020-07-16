package com.sportstalk.app.demo.presentation.listrooms

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.sportstalk.SportsTalk247
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentListChatroomBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.app.demo.presentation.chatroom.ChatRoomFragment
import com.sportstalk.app.demo.presentation.listrooms.adapters.ItemListChatRoomAdapter
import com.sportstalk.app.demo.presentation.users.CreateAccountFragment
import com.sportstalk.app.demo.presentation.utils.EndlessRecyclerViewScrollListener
import com.sportstalk.models.ClientConfig
import com.sportstalk.models.chat.ChatRoom
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx2.asFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class ListChatRoomsFragment : BaseFragment() {

    private lateinit var binding: FragmentListChatroomBinding
    private lateinit var menu: Menu

    private lateinit var adapter: ItemListChatRoomAdapter
    private lateinit var scrollListener: RecyclerView.OnScrollListener

    private val config: ClientConfig by lazy {
        ClientConfig(
            appId = getString(R.string.sportstalk247_appid),
            apiToken = getString(R.string.sportstalk247_authToken),
            endpoint = getString(R.string.sportstalk247_urlEndpoint)
        )
    }

    private val viewModel: ListChatRoomsViewModel by viewModel {
        parametersOf(
            SportsTalk247.ChatClient(config = config)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentListChatroomBinding.inflate(inflater)

        binding.recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        adapter = ItemListChatRoomAdapter(
            onSelectItemChatRoom = { chatRoom: ChatRoom ->
                Log.d(TAG, "onSelectItemChatRoom() -> chatRoom = $chatRoom")
                // Attempt Join Chatroom
                viewModel.join(which = chatRoom)
            }
        )
        binding.recyclerView.adapter = adapter

        scrollListener = object :
            EndlessRecyclerViewScrollListener(binding.recyclerView.layoutManager!! as LinearLayoutManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                Log.d(
                    TAG,
                    "EndlessRecyclerViewScrollListener:: onLoadMore() -> page/totalItemsCount = ${page}/${totalItemsCount}"
                )
                // Attempt fetch more
                viewModel.fetchMore()
            }
        }
        // Scroll Listeners
        binding.recyclerView.addOnScrollListener(scrollListener)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Toolbar
        (requireActivity() as? AppCompatActivity)?.let { actv ->
            actv.setSupportActionBar(binding.toolbar)
            actv.supportActionBar?.title = getString(R.string.rooms)
        }

        ///////////////////////////////
        // Bind ViewModel State
        ///////////////////////////////

        /**
         * Emits [true] upon start SDK List Chatrooms Operation. Emits [false] when done.
         */
        viewModel.state.progressFetchChatRooms()
            .onEach { inProgress: Boolean ->
                takeProgressFetchChatRooms(inProgress)
            }
            .launchIn(lifecycleScope)

        /**
         * Emits a list of [ChatRoom] everytime we receive response from SDK List Chatrooms Operation
         */
        viewModel.state.chatRooms()
            .onEach { chatRooms ->
                takeChatRooms(chatRooms)
            }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if account was already created. Emits [false] otherwise.
         */
        viewModel.state.enableAccountSettings()
            .onEach { takeEnableAccountSettings(it) }
            .launchIn(lifecycleScope)

        // View Effect
        viewModel.effect
            .onEach {
                takeViewEffect(it)
            }
            .launchIn(lifecycleScope)

        ///////////////////////////////
        // Bind UI Input Actions
        ///////////////////////////////

        binding.swipeRefresh.refreshes()
            .asFlow()
            .onEach {
                viewModel.fetchInitial()
            }
            .launchIn(lifecycleScope)

        // Then, fetch initial list
        viewModel.fetchInitial()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.list_chatroom, menu)

        this.menu = menu
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_search -> {
                // TODO:: R.id.action_search
                true
            }
            R.id.action_account_settings -> {
                // Navigate to Account Settings Screen
                if (appNavController.currentDestination?.id == R.id.fragmentHome) {
                    appNavController.navigate(
                        R.id.action_fragmentHome_to_fragmentAccountSettings
                    )
                }

                true
            }
            else -> super.onOptionsItemSelected(item)
        }


    override fun onDestroyView() {
        super.onDestroyView()

        // Remove Scroll Listener
        binding.recyclerView.removeOnScrollListener(scrollListener)
    }

    private fun takeProgressFetchChatRooms(inProgress: Boolean) {
        Log.d(TAG, "takeProgressFetchChatRooms() -> inProgress = $inProgress")
        binding.swipeRefresh.isRefreshing = inProgress
    }

    private fun takeChatRooms(chatRooms: List<ChatRoom>) {
        Log.d(TAG, "takeChatRooms() -> chatRooms = $chatRooms")

        adapter.update(chatRooms)
    }

    private fun takeEnableAccountSettings(isEnabled: Boolean) {
        Log.d(TAG, "takeEnableAccountSettings() -> isEnabled = $isEnabled")
        // Toggle menu action
        this.menu.findItem(R.id.action_account_settings)?.isEnabled = isEnabled
    }

    private fun takeViewEffect(effect: ListChatRoomsViewModel.ViewEffect) {
        Log.d(TAG, "takeViewEffect() -> effect = ${effect::class.java.simpleName}")
        when (effect) {
            is ListChatRoomsViewModel.ViewEffect.ClearListChatrooms -> {
                adapter.clear()
            }
            is ListChatRoomsViewModel.ViewEffect.NavigateToCreateProfile -> {
                if (appNavController.currentDestination?.id == R.id.fragmentHome) {
                    appNavController.navigate(
                        R.id.action_fragmentHome_to_fragmentCreateAccount,
                        bundleOf(
                            CreateAccountFragment.INPUT_ARG_ROOM to effect.which
                        )
                    )
                }
            }
            is ListChatRoomsViewModel.ViewEffect.NavigateToChatRoom -> {
                if (appNavController.currentDestination?.id == R.id.fragmentHome) {
                    appNavController.navigate(
                        R.id.action_fragmentHome_to_fragmentChatroom,
                        bundleOf(
                            /* TODO:: Bundle */ChatRoomFragment.INPUT_ARG_ROOM to effect.which,
                            /* TODO:: Bundle */
                            ChatRoomFragment.INPUT_ARG_USER to effect.who
                        )
                    )
                }
            }
        }
    }

}