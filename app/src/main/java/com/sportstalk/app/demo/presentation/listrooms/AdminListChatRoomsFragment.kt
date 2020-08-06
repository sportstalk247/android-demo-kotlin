package com.sportstalk.app.demo.presentation.listrooms

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.jakewharton.rxbinding3.view.clicks
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentAdminListChatroomBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.app.demo.presentation.listrooms.adapters.ItemAdminListChatRoomAdapter
import com.sportstalk.app.demo.presentation.rooms.UpdateChatroomFragment
import com.sportstalk.app.demo.presentation.utils.EndlessRecyclerViewScrollListener
import com.sportstalk.models.chat.ChatRoom
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx2.asFlow
import org.koin.android.ext.android.getKoin
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.androidx.viewmodel.koin.getViewModel
import java.util.concurrent.TimeUnit

class AdminListChatRoomsFragment : BaseFragment() {

    private lateinit var binding: FragmentAdminListChatroomBinding

    private lateinit var adapter: ItemAdminListChatRoomAdapter
    private lateinit var scrollListener: RecyclerView.OnScrollListener

    private val viewModel: AdminListChatRoomsViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAdminListChatroomBinding.inflate(inflater)

        binding.recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        adapter = ItemAdminListChatRoomAdapter(
            onItemUpdateChatRoom = { selected ->
                Log.d(
                    TAG,
                    "ItemAdminListChatRooms.adopt() -> onItemUpdateChatRoom() -> selected = $selected"
                )

                viewModel.update(which = selected)
            },
            onItemDeleteChatRoom = { selected ->
                Log.d(
                    TAG,
                    "ItemAdminListChatRooms.adopt() -> onItemDeleteChatRoom() -> selected = $selected"
                )

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Chatroom")
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        viewModel.delete(which = selected)
                    }
                    .setNegativeButton(android.R.string.no) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            },
            onItemSendAnnouncement = { which ->
                Log.d(
                    TAG,
                    "ItemAdminListChatRooms.adopt() -> onItemSendAnnouncement() -> which = $which"
                )

                val textInputLayout = LayoutInflater.from(requireContext())
                    .inflate(
                        R.layout.layout_inapp_settings_input_text,
                        binding.root,
                        false
                    ) as TextInputLayout
                val tietInputText =
                    textInputLayout.findViewById<TextInputEditText>(R.id.tietInputText)

                // Display Alert Prompt With Input Text
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.send_announcement)
                    .setView(textInputLayout)
                    .setPositiveButton(R.string.apply) { _, _ ->
                        // Attempt send announcement
                        viewModel.sendAnnouncement(
                            message = tietInputText.text?.toString() ?: "",
                            which = which
                        )
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
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
         * Emits [true] upon start SDK Delete Chatroom Operation. Emits [false] when done.
         */
        viewModel.state.progressDeleteChatRoom()
            .onEach { inProgress: Boolean ->
                takeProgressDeleteChatRoom(inProgress)
            }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start SDK Execute Chat Command Operation(Announcement). Emits [false] when done.
         */
        viewModel.state.progressSendAnnouncement()
            .onEach(::takeProgressSendAnnouncement)
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

        binding.fabAdd.clicks()
            .throttleFirst(1000, TimeUnit.MILLISECONDS)
            .asFlow()
            .onEach {
                // Navigate to Create Chatroom
                appNavController.navigate(
                    R.id.action_fragmentHome_to_fragmentCreateChatroom
                )
            }
            .launchIn(lifecycleScope)

        binding.swipeRefresh.refreshes()
            .asFlow()
            .onEach {
                // Perform fetch upon Refresh
                viewModel.fetchInitial()
            }
            .launchIn(lifecycleScope)

        viewModel.fetchInitial()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.admin_list_chatroom, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_search -> {
                // TODO:: R.id.action_search
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

    private fun takeProgressDeleteChatRoom(inProgress: Boolean) {
        Log.d(TAG, "takeProgressDeleteChatRoom() -> inProgress = $inProgress")
        binding.swipeRefresh.isRefreshing = inProgress
        binding.recyclerView.isEnabled = !inProgress
    }

    private suspend fun takeProgressSendAnnouncement(inProgress: Boolean) {
        Log.d(TAG, "takeProgressSendAnnouncement() -> inProgress = $inProgress")
        binding.swipeRefresh.isRefreshing = inProgress
        binding.recyclerView.isEnabled = !inProgress
    }

    private fun takeViewEffect(effect: AdminListChatRoomsViewModel.ViewEffect) {
        Log.d(TAG, "takeViewEffect() -> effect = ${effect::class.java.simpleName}")

        when (effect) {
            is AdminListChatRoomsViewModel.ViewEffect.ClearListChatrooms -> {
                adapter.clear()
            }
            is AdminListChatRoomsViewModel.ViewEffect.ErrorFetchListChatrooms -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() }
                        ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is AdminListChatRoomsViewModel.ViewEffect.NavigateToChatRoomDetails -> {
                // Navigate to Update Chatroom Screen
                if (appNavController.currentDestination?.id == R.id.fragmentHome) {
                    appNavController.navigate(
                        R.id.action_fragmentHome_to_fragmentUpdateChatroom,
                        bundleOf(
                            UpdateChatroomFragment.INPUT_ARG_USER to effect.admin,
                            UpdateChatroomFragment.INPUT_ARG_ROOM to effect.which
                        )
                    )
                }
            }
            is AdminListChatRoomsViewModel.ViewEffect.SuccessDeleteRoom -> {
                // Refresh list
                viewModel.fetchInitial()
            }
            is AdminListChatRoomsViewModel.ViewEffect.ErrorDeleteRoom -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() }
                        ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is AdminListChatRoomsViewModel.ViewEffect.SuccessSendAnnouncement -> {
                Toast.makeText(
                    requireContext(),
                    R.string.announcement_successfully_sent,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is AdminListChatRoomsViewModel.ViewEffect.ErrorSendAnnouncement -> {
                Toast.makeText(
                    requireContext(),
                    effect.err.message?.takeIf { it.isNotEmpty() }
                        ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

}