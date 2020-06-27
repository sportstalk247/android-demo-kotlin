package com.sportstalk.app.demo.presentation.listrooms.adapters

import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.ItemAdminListChatroomBinding
import com.sportstalk.models.chat.ChatRoom
import com.squareup.cycler.ItemComparator
import com.squareup.cycler.Recycler
import java.text.DecimalFormat

typealias OnItemUpdateChatRoom = ((ChatRoom) -> Unit)
typealias OnItemDeleteChatRoom = ((ChatRoom) -> Unit)

object ItemAdminListChatRooms {

    inline fun adopt(
        recyclerView: RecyclerView,
        crossinline onItemUpdateChatRoom: OnItemUpdateChatRoom = {},
        crossinline onItemDeleteChatRoom: OnItemDeleteChatRoom = {}
    ): Recycler<ChatRoom> =
        Recycler.adopt(recyclerView) {
            row<ChatRoom, MaterialCardView> {
                create(R.layout.item_admin_list_chatroom) {
                    val binding = ItemAdminListChatroomBinding.bind(view)
                    bind { index, item ->
                        binding.actvRoomName.text = item.name
                        binding.actvRoomDescription.text = item.description
                        binding.actvRoomFansCount.text = binding.root.context
                            .getString(R.string.n_fans_inside, (item.inroom ?: 0L).toString(10))

                        // Click Listener
                        binding.btnUpdate.setOnClickListener {
                            onItemUpdateChatRoom(item)
                        }
                        binding.btnDelete.setOnClickListener {
                            onItemDeleteChatRoom(item)
                        }
                    }
                }
            }

            itemComparator = object: ItemComparator<ChatRoom> {
                override fun areSameContent(oldItem: ChatRoom, newItem: ChatRoom): Boolean =
                    oldItem == newItem

                override fun areSameIdentity(oldItem: ChatRoom, newItem: ChatRoom): Boolean =
                    oldItem.id == newItem.id
            }
        }

}