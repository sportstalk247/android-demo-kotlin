package com.sportstalk.app.demo.presentation.chatroom.adapters

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.ItemChatEventBinding
import com.sportstalk.models.chat.ChatEvent
import com.squareup.cycler.ItemComparator
import com.squareup.cycler.Recycler

typealias OnSelectChatEvent = ((ChatEvent) -> Unit)

object ItemChatEventRecycler {
    inline fun adopt(
        recyclerView: RecyclerView,
        crossinline onSelectChatEvent: OnSelectChatEvent = {}
    ): Recycler<ChatEvent> =
        Recycler.adopt(recyclerView) {
            row<ChatEvent, ConstraintLayout> {
                create(R.layout.item_chat_event) {
                    val binding = ItemChatEventBinding.bind(view)
                    bind { index, chatEvent ->
                        chatEvent.user?.profileurl?.let { profileurl ->
                            Glide.with(binding.root.context)
                                .load(profileurl)
                                .apply(
                                    RequestOptions.centerCropTransform()
                                )
                                .into(binding.civProfile)
                        } ?: binding.civProfile.setImageResource(0)

                        binding.actvDisplayHandle.text = "@${chatEvent.user?.handle}"

                        binding.actvChatMessage.text = chatEvent.body

                        // Set on select Chat Room
                        binding.root.setOnClickListener { onSelectChatEvent.invoke(chatEvent) }
                    }
                }
            }

            itemComparator = object : ItemComparator<ChatEvent> {
                override fun areSameContent(oldItem: ChatEvent, newItem: ChatEvent): Boolean =
                    oldItem == newItem

                override fun areSameIdentity(oldItem: ChatEvent, newItem: ChatEvent): Boolean =
                    oldItem.id == newItem.id
            }

        }

}