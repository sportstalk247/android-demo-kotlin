package com.sportstalk.app.demo.presentation.listrooms.adapters

import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.ItemListChatroomBinding
import com.sportstalk.models.chat.ChatRoom
import com.squareup.cycler.ItemComparator
import com.squareup.cycler.Recycler
import java.text.DecimalFormat

typealias OnSelectItemChatRoom = ((ChatRoom) -> Unit)

object ItemListChatRooms {

    inline fun adopt(
        recyclerView: RecyclerView,
        crossinline onSelectItemChatRoom: OnSelectItemChatRoom = {}
    ): Recycler<ChatRoom> =
        Recycler.adopt(recyclerView) {
            row<ChatRoom, MaterialCardView> {
                create(R.layout.item_list_chatroom) {
                    val binding = ItemListChatroomBinding.bind(view)
                    val decimalFormat = DecimalFormat("###,###,###,###,###")
                    bind { index, item ->
                        binding.actvScore.text = "0-0"
                        binding.actvRoomName.text = item.name
                        binding.actvAttendance.text = decimalFormat.format(item.inroom ?: 0L)

                        // Click Listener
                        binding.btnJoin.setOnClickListener {
                            onSelectItemChatRoom(item)
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