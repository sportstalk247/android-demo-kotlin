package com.sportstalk.app.demo.presentation.users.adapter

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.ItemSelectDemoUserBinding
import com.sportstalk.models.chat.ChatRoomParticipant
import com.squareup.cycler.ItemComparator
import com.squareup.cycler.Recycler

typealias OnScrollFetchParticipantsNext = ((String) -> Unit)
typealias OnSelectDemoUser = ((ChatRoomParticipant) -> Unit)

object ItemSelectDemoUserRecycler {
    inline fun adopt(
        recyclerView: RecyclerView,
        crossinline onScrollFetchParticipantsNext: OnScrollFetchParticipantsNext = {},
        crossinline onSelectDemoUser: OnSelectDemoUser = {}
    ): Recycler<ChatRoomParticipant> =
        Recycler.adopt(recyclerView) {
            row<ChatRoomParticipant, MaterialCardView> {
                create(R.layout.item_select_demo_user) {
                    val binding = ItemSelectDemoUserBinding.bind(view)
                    bind { index, item ->
                        item.user?.pictureurl?.let { pictureurl ->
                            Glide.with(binding.root.context)
                                .load(pictureurl)
                                .apply(
                                    RequestOptions.centerCropTransform()
                                )
                                .into(binding.civProfile)
                        } ?: binding.civProfile.setImageResource(0)

                        binding.actvDisplayName.text = item.user?.displayname
                        binding.actvHandle.text = item.user?.handle?.let { handle -> "@$handle" }

                        // When Scrolling at the bottom of the recycler view, perform fetch next with cursor
                        if (index >= (recyclerView.adapter?.itemCount ?: 0) - 1) {
                            onScrollFetchParticipantsNext.invoke(item.user?.userid!!)
                        }

                        // Set on select Chat Room
                        binding.root.setOnClickListener { onSelectDemoUser.invoke(item) }
                    }
                }
            }

            itemComparator = object : ItemComparator<ChatRoomParticipant> {
                override fun areSameContent(
                    oldItem: ChatRoomParticipant,
                    newItem: ChatRoomParticipant
                ): Boolean =
                    oldItem.user == newItem.user

                override fun areSameIdentity(
                    oldItem: ChatRoomParticipant,
                    newItem: ChatRoomParticipant
                ): Boolean =
                    oldItem.user?.userid == newItem.user?.userid
            }
        }
}