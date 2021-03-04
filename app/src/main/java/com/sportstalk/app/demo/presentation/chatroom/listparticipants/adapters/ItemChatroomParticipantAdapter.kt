package com.sportstalk.app.demo.presentation.chatroom.listparticipants.adapters

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.ItemChatroomParticipantBinding
import com.sportstalk.app.demo.databinding.ItemChatroomParticipantHeaderBouncedUsersBinding
import com.sportstalk.datamodels.users.User

typealias OnTapChatParticipantItem = ((User) -> Unit)
typealias OnTapBouncedUser = ((User) -> Unit)

class ItemChatroomParticipantAdapter(
    private val me: User,
    initialItems: List<User> = listOf(),
    bouncedItems: List<User> = listOf(),
    private val onTapChatParticipantItem: OnTapChatParticipantItem = {},
    private val onTapBouncedUser: OnTapBouncedUser = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<User> = ArrayList(initialItems)
    private var bouncedUsers: List<User> = ArrayList(bouncedItems)

    /**
     * Each time data is set, we update this variable so that if DiffUtil calculation returns
     * after repetitive updates, we can ignore the old calculation
     */
    private var dataVersion = 0
    private var dataBouncedUsersVersion = 0

    @MainThread
    fun updateParticipant(item: User) {
        synchronized(items) {
            items = ArrayList(items).apply {
                val index = items.indexOfFirst { oldItem -> oldItem.userid == item.userid }
                if (index >= 0) {
                    set(index, item)
                    notifyItemChanged(index)
                } else {
                    add(index, item)
                    notifyItemInserted(index)
                }
            }
        }
    }

    @MainThread
    fun removeParticipant(item: User) {
        synchronized(items) {
            items = ArrayList(items).apply {
                val index = items.indexOfFirst { oldItem -> oldItem.userid == item.userid }
                if (index >= 0) {
                    removeAt(index)
                    notifyItemRemoved(index)
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    @MainThread
    fun updateParticipant(itemUpdates: List<User>) {
        // DO Nothing if `itemUpdates` is empty
        if(itemUpdates.isEmpty()) return

        val updatedList = ArrayList(items).apply {
            itemUpdates.forEach { update ->
                val index = items.indexOfFirst { oldItem -> oldItem.userid == update.userid }
                if (index >= 0) {
                    set(index, update)
                } else {
                    add(0, update)
                }
            }
        }
            .sortedByDescending { it.displayname }
            .distinctBy { it.userid }

        // Perform Update
        replaceParticipants(updatedList)
    }

    @SuppressLint("StaticFieldLeak")
    @MainThread
    fun replaceParticipants(update: List<User>) {
//        dataVersion++
//        if (items.isEmpty()) {
//            items = update
//            notifyItemRangeChanged(0, items.size)
//        } else if (update.isEmpty()) {
//            val oldSize = items.size
//            items = listOf()
//            notifyItemRangeRemoved(0, oldSize)
//        } else {
//            val startVersion = dataVersion
//            val oldItems = ArrayList(items)
//
//            object : AsyncTask<Void, Void, DiffUtil.DiffResult>() {
//                override fun doInBackground(vararg voids: Void): DiffUtil.DiffResult {
//                    return DiffUtil.calculateDiff(object : DiffUtil.Callback() {
//                        override fun getOldListSize(): Int = oldItems.size
//                        override fun getNewListSize(): Int = update.size
//                        override fun areItemsTheSame(
//                            oldItemPosition: Int,
//                            newItemPosition: Int
//                        ): Boolean =
//                            oldItems[oldItemPosition].userid == update[newItemPosition].userid
//
//                        override fun areContentsTheSame(
//                            oldItemPosition: Int,
//                            newItemPosition: Int
//                        ): Boolean =
//                            oldItems[oldItemPosition] == update[newItemPosition]
//                    })
//                }
//
//                override fun onPostExecute(diffResult: DiffUtil.DiffResult) {
//                    if (startVersion != dataVersion) {
//                        // ignore update
//                        return
//                    }
//                    items = update
//                    diffResult.dispatchUpdatesTo(this@ItemChatroomParticipantAdapter)
//                }
//            }
//                .execute()
//        }
        items = update
    }

    @MainThread
    fun updateBouncedUser(item: User) {
        synchronized(bouncedUsers) {
            bouncedUsers = ArrayList(bouncedUsers).apply {
                val index = bouncedUsers.indexOfFirst { oldItem -> oldItem.userid == item.userid }
                if (index >= 0) {
                    set(index + (items.size + 1), item)
                    notifyItemChanged(index)
                } else {
                    add(index + items.size + 1, item)
                    notifyItemInserted(index)
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    @MainThread
    fun replaceBouncedUsers(update: List<User>) {
//        dataBouncedUsersVersion++
//        if (bouncedUsers.isEmpty()) {
//            bouncedUsers = update
//            notifyItemRangeInserted(items.size + 1, bouncedUsers.size)
//        } else if (update.isEmpty()) {
//            val oldSize = bouncedUsers.size
//            bouncedUsers = listOf()
//            notifyItemRangeRemoved(items.size + 1, oldSize)
//        } else {
//            val startVersion = dataBouncedUsersVersion
//            val oldItems = ArrayList(bouncedUsers)
//
//            object : AsyncTask<Void, Void, DiffUtil.DiffResult>() {
//                override fun doInBackground(vararg voids: Void): DiffUtil.DiffResult {
//                    return DiffUtil.calculateDiff(object : DiffUtil.Callback() {
//                        override fun getOldListSize(): Int = oldItems.size
//                        override fun getNewListSize(): Int = update.size
//                        override fun areItemsTheSame(
//                            oldItemPosition: Int,
//                            newItemPosition: Int
//                        ): Boolean =
//                            oldItems[oldItemPosition].userid == update[newItemPosition].userid
//
//                        override fun areContentsTheSame(
//                            oldItemPosition: Int,
//                            newItemPosition: Int
//                        ): Boolean =
//                            oldItems[oldItemPosition] == update[newItemPosition]
//                    })
//                }
//
//                override fun onPostExecute(diffResult: DiffUtil.DiffResult) {
//                    if (startVersion != dataBouncedUsersVersion) {
//                        // ignore update
//                        return
//                    }
//                    bouncedUsers = update
//                    diffResult.dispatchUpdatesTo(this@ItemChatroomParticipantAdapter)
//                }
//            }
//                .execute()
//        }

        bouncedUsers = update
    }

    override fun getItemCount(): Int =
        when {
            bouncedUsers.isEmpty() -> items.size
            else -> items.size + 1 + bouncedUsers.size
        }

    override fun getItemViewType(position: Int): Int =
        when {
            bouncedUsers.isEmpty() -> VIEW_TYPE_ITEM_CHAT_PARTICIPANT
            else -> {
                when {
                    position < items.size -> {
                        VIEW_TYPE_ITEM_CHAT_PARTICIPANT
                    }
                    position == items.size -> {
                        VIEW_TYPE_HEADER_BOUNCED_USERS
                    }
                    else -> {
                        VIEW_TYPE_ITEM_BOUNCED_USER
                    }
                }
            }
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder =
        when(viewType) {
            VIEW_TYPE_ITEM_CHAT_PARTICIPANT, VIEW_TYPE_ITEM_BOUNCED_USER -> ItemChatroomParticipantViewHolder(
                ItemChatroomParticipantBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            VIEW_TYPE_HEADER_BOUNCED_USERS ->
                ItemChatroomParticipantHeaderBouncedUsersViewHolder(
                    ItemChatroomParticipantHeaderBouncedUsersBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            else -> object: RecyclerView.ViewHolder(View(parent.context)) {}
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder) {
            is ItemChatroomParticipantViewHolder -> {
                when {
                    position < items.size -> {
                        Log.d(TAG, "onBindViewHolder() -> [$position]: VIEW_TYPE_ITEM_CHAT_PARTICIPANT")
                        val item = items[position]
                        holder.bind(item)
                        holder.itemView.setOnClickListener { onTapChatParticipantItem(item) }
                    }
                    else/*position in ((items.size + 1) until (bouncedUsers.size + 1))*/ -> {
                        Log.d(TAG, "onBindViewHolder() -> [$position]: VIEW_TYPE_ITEM_BOUNCED_USER")
                        val item = bouncedUsers[position - (items.size + 1)]
                        holder.bind(item)
                        holder.itemView.setOnClickListener { onTapBouncedUser(item) }
                    }
                }
            }
            is ItemChatroomParticipantHeaderBouncedUsersViewHolder -> { }
        }
    }

    inner class ItemChatroomParticipantViewHolder(
        val binding: ItemChatroomParticipantBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(item: User) {
            val context = itemView.context

            // Profile Picture
            item.profileurl?.takeIf { it.isNotEmpty() }?.let { profileurl ->
                Glide.with(context)
                    .load(profileurl)
                    .error(R.drawable.ic_profile_default)
                    .into(binding.civProfile)
            } ?: run {
                binding.civProfile.setImageResource(R.drawable.ic_profile_default)
            }

            // Display Name
            binding.actvDisplayName.text = item.displayname
            binding.actvDisplayHandle.text = "@${item.handle}"

            // `Banned` badge
            binding.actvBanned.visibility = when (item.banned) {
                true -> View.VISIBLE
                else -> View.GONE
            }

        }
    }

    inner class ItemChatroomParticipantHeaderBouncedUsersViewHolder(
        private val binding: ItemChatroomParticipantHeaderBouncedUsersBinding
    ): RecyclerView.ViewHolder(binding.root)

    companion object {
        private val TAG = ItemChatroomParticipantAdapter::class.java.simpleName

        private const val VIEW_TYPE_ITEM_CHAT_PARTICIPANT = 0x01
        private const val VIEW_TYPE_HEADER_BOUNCED_USERS = 0x02
        private const val VIEW_TYPE_ITEM_BOUNCED_USER = 0x03
    }

}