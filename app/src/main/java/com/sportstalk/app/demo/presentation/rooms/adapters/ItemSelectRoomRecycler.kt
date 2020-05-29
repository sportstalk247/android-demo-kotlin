package com.sportstalk.app.demo.presentation.rooms.adapters

import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.ItemSelectRoomBinding
import com.sportstalk.models.chat.ChatRoom
import com.squareup.cycler.ItemComparator
import com.squareup.cycler.Recycler

typealias OnScrollFetchChatRoomsNext = ((String) -> Unit)
typealias OnSelectChatRoom = ((ChatRoom) -> Unit)

object ItemSelectRoomRecycler {
    inline fun adopt(
        recyclerView: RecyclerView,
        crossinline onScrollFetchChatRoomsNext: OnScrollFetchChatRoomsNext = {},
        crossinline onSelectChatRoom: OnSelectChatRoom = {}
    ): Recycler<ChatRoom> =
        Recycler.adopt(recyclerView) {
            row<ChatRoom, MaterialCardView> {
                create(R.layout.item_select_room) {
                    val binding = ItemSelectRoomBinding.bind(view)
                    bind { index, item ->
                        binding.actvRoomName.text = item.name
                        binding.actvSlug.text = item.customid
                        binding.actvDescription.text = item.description
                        binding.actvInRoomCount.text = item.inroom?.toString(10) ?: ""

                        with(binding.chipActions) {
                            val enableActions = item.enableactions == true
                            isChecked = enableActions
                            isEnabled = enableActions
                        }
                        with(binding.chipEnterExit) {
                            val enableEnterExit = item.enableenterandexit == true
                            isChecked = enableEnterExit
                            isEnabled = enableEnterExit
                        }
                        with(binding.chipOpen) {
                            val isOpen = item.open == true
                            isChecked = isOpen
                            isEnabled = isOpen
                        }
                        with(binding.chipProfanityFilter) {
                            val enableProfanityFilter = item.enableprofanityfilter == true
                            isChecked = enableProfanityFilter
                            isEnabled = enableProfanityFilter
                        }

                        // When Scrolling at the bottom of the recycler view, perform fetch next with cursor
                        if (index >= (recyclerView.adapter?.itemCount ?: 0) - 1) {
                            onScrollFetchChatRoomsNext.invoke(item.id!!)
                        }
                        // Set on select Chat Room
                        binding.root.setOnClickListener { onSelectChatRoom.invoke(item) }
                    }
                }

            }

            itemComparator = object : ItemComparator<ChatRoom> {
                override fun areSameContent(oldItem: ChatRoom, newItem: ChatRoom): Boolean =
                    oldItem == newItem

                override fun areSameIdentity(oldItem: ChatRoom, newItem: ChatRoom): Boolean =
                    oldItem.id == newItem.id
            }

        }

}