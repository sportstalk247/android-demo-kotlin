package com.sportstalk.app.demo.presentation.listrooms.adapters

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.ItemAdminListChatroomBinding
import com.sportstalk.models.chat.ChatRoom

typealias OnItemUpdateChatRoom = ((ChatRoom) -> Unit)
typealias OnItemDeleteChatRoom = ((ChatRoom) -> Unit)
typealias OnItemSendAnnouncement = ((ChatRoom) -> Unit)

class ItemAdminListChatRoomAdapter(
    private val onItemUpdateChatRoom: OnItemUpdateChatRoom = {},
    private val onItemDeleteChatRoom: OnItemDeleteChatRoom = {},
    private val onItemSendAnnouncement: OnItemSendAnnouncement = {}
) : RecyclerView.Adapter<ItemAdminListChatRoomAdapter.ItemAdminListChatRoomViewHolder>() {

    private var items: List<ChatRoom> = listOf()

    /**
     * Each time data is set, we update this variable so that if DiffUtil calculation returns
     * after repetitive updates, we can ignore the old calculation
     */
    private var dataVersion = 0

    @MainThread
    fun clear() {
        replace(listOf())
    }

    @MainThread
    fun update(item: ChatRoom) {
        synchronized(items) {
            items = ArrayList(items).apply {
                val index = items.indexOfFirst { oldItem -> oldItem.id == item.id }
                if (index >= 0) {
                    set(index, item)
                    notifyItemChanged(index)
                }
            }
        }
    }

    @MainThread
    fun remove(item: ChatRoom) {
        synchronized(items) {
            items = ArrayList(items).apply {
                val index = items.indexOfFirst { oldItem -> oldItem.id == item.id }
                if (index >= 0) {
                    removeAt(index)
                    notifyItemRemoved(index)
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    @MainThread
    fun update(itemUpdates: List<ChatRoom>) {
        // DO Nothing if `itemUpdates` is empty
        if (itemUpdates.isEmpty()) return

        val updatedList = ArrayList(items).apply {
            itemUpdates.forEach { update ->
                val index = items.indexOfFirst { oldItem -> oldItem.id == update.id }
                if (index >= 0) {
                    set(index, update)
                } else {
                    add(0, update)
                }
            }
        }
            .distinctBy { it.id }

        // Perform Update
        replace(updatedList)
    }

    @SuppressLint("StaticFieldLeak")
    @MainThread
    private fun replace(update: List<ChatRoom>) {
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
                            oldItems[oldItemPosition].id == update[newItemPosition].id

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
                    diffResult.dispatchUpdatesTo(this@ItemAdminListChatRoomAdapter)
                }
            }
                .execute()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ItemAdminListChatRoomViewHolder =
        ItemAdminListChatRoomViewHolder(
            ItemAdminListChatroomBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ItemAdminListChatRoomViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)

        // Click Listener
        holder.binding.btnUpdate.setOnClickListener {
            onItemUpdateChatRoom(item)
        }
        holder.binding.btnDelete.setOnClickListener {
            onItemDeleteChatRoom(item)
        }
        holder.binding.btnSendAnnouncement.setOnClickListener {
            onItemSendAnnouncement(item)
        }
    }

    inner class ItemAdminListChatRoomViewHolder(
        val binding: ItemAdminListChatroomBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatRoom) {
            binding.actvRoomName.text = item.name
            binding.actvRoomDescription.text = item.description
            binding.actvRoomFansCount.text = binding.root.context
                .getString(R.string.n_fans_inside, (item.inroom ?: 0L).toString(10))
        }

    }

}