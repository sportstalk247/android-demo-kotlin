package com.sportstalk.app.demo.presentation.chatroom.listparticipants.adapters

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.ItemChatroomParticipantBinding
import com.sportstalk.models.users.User

typealias OnTapChatParticipantItem = ((User) -> Unit)

class ItemChatroomParticipantAdapter(
    private val me: User,
    initialItems: List<User> = listOf(),
    private val onTapChatParticipantItem: OnTapChatParticipantItem = {}
) : RecyclerView.Adapter<ItemChatroomParticipantAdapter.ItemChatroomParticipantViewHolder>() {

    private var items: List<User> = ArrayList(initialItems)

    /**
     * Each time data is set, we update this variable so that if DiffUtil calculation returns
     * after repetitive updates, we can ignore the old calculation
     */
    private var dataVersion = 0

    @MainThread
    fun update(item: User) {
        synchronized(items) {
            items = ArrayList(items).apply {
                val index = items.indexOfFirst { oldItem -> oldItem.userid == item.userid }
                if (index >= 0) {
                    set(index, item)
                    notifyItemChanged(index)
                }
            }
        }
    }

    @MainThread
    fun remove(item: User) {
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
    fun update(itemUpdates: List<User>) {
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
        replace(updatedList)
    }

    @SuppressLint("StaticFieldLeak")
    @MainThread
    fun replace(update: List<User>) {
        dataVersion++
        if (items.isEmpty()) {
            items = update
            notifyDataSetChanged()
        } else if (update.isEmpty()) {
            val oldSize = items.size
            items = listOf()
            notifyItemRangeRemoved(0, oldSize)
        } else {
            val startVersion = dataVersion
            val oldItems = ArrayList(items)

            object : AsyncTask<Void, Void, DiffUtil.DiffResult>() {
                override fun doInBackground(vararg voids: Void): DiffUtil.DiffResult {
                    return DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                        override fun getOldListSize(): Int = oldItems.size
                        override fun getNewListSize(): Int = update.size
                        override fun areItemsTheSame(
                            oldItemPosition: Int,
                            newItemPosition: Int
                        ): Boolean =
                            oldItems[oldItemPosition].userid == update[newItemPosition].userid

                        override fun areContentsTheSame(
                            oldItemPosition: Int,
                            newItemPosition: Int
                        ): Boolean =
                            oldItems[oldItemPosition] == update[newItemPosition]
                    })
                }

                override fun onPostExecute(diffResult: DiffUtil.DiffResult) {
                    if (startVersion != dataVersion) {
                        // ignore update
                        return
                    }
                    items = update
                    diffResult.dispatchUpdatesTo(this@ItemChatroomParticipantAdapter)
                }
            }
                .execute()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ItemChatroomParticipantViewHolder =
        ItemChatroomParticipantViewHolder(
            ItemChatroomParticipantBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ItemChatroomParticipantViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onTapChatParticipantItem(item) }
    }

    inner class ItemChatroomParticipantViewHolder(
        val binding: ItemChatroomParticipantBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(item: User) {
            val context = itemView.context

            // Profile Picture
            Glide.with(context)
                .load(item.profileurl)
                .error(R.drawable.ic_profile_default)
                .into(binding.civProfile)

            // Display Name
            binding.actvDisplayName.text = item.displayname
            binding.actvDisplayHandle.text = "@${item.handle ?: ""}"

            // `Banned` badge
            binding.actvBanned.visibility = when (item.banned) {
                true -> View.VISIBLE
                else -> View.GONE
            }

        }
    }

}