package com.sportstalk.app.demo.presentation.chatroom.adapters

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.*
import com.sportstalk.models.chat.ChatEvent
import com.sportstalk.models.chat.EventType
import com.sportstalk.models.users.User
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

typealias OnTapChatEventItem = ((ChatEvent) -> Unit)
typealias OnTapReactChatEventItem = ((ChatEvent, Boolean) -> Unit)

class ItemChatEventAdapter(
    private val me: User,
    initialItems: List<ChatEvent> = listOf(),
    private val onTapChatEventItem: OnTapChatEventItem = {},
    private val onTapReactChatEventItem: OnTapReactChatEventItem = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val UTC_MSG_SENT_FORMATTER: SimpleDateFormat =
        /*SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.getDefault())*/
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

    private var items: List<ChatEvent> = ArrayList(initialItems)
    fun getItem(position: Int) = items[position]

    /**
     * Each time data is set, we update this variable so that if DiffUtil calculation returns
     * after repetitive updates, we can ignore the old calculation
     */
    private var dataVersion = 0

    @MainThread
    fun update(item: ChatEvent) {
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
    fun remove(item: ChatEvent) {
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
    fun update(itemUpdates: List<ChatEvent>) {
        // DO Nothing if `itemUpdates` is empty
        if(itemUpdates.isEmpty()) return

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
            .sortedByDescending { it.ts }
            .distinctBy { it.id }

        // Perform Update
        replace(updatedList)
    }

    @SuppressLint("StaticFieldLeak")
    @MainThread
    private fun replace(update: List<ChatEvent>) {
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
                    diffResult.dispatchUpdatesTo(this@ItemChatEventAdapter)
                }
            }
                .execute()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return when {
            // "Announcement" implementation
            item.eventtype == EventType.ANNOUNCEMENT -> VIEW_TYPE_ANNOUNCEMENT
            item.eventtype == EventType.ACTION -> VIEW_TYPE_ACTION
            item.eventtype == EventType.CUSTOM -> VIEW_TYPE_UNKNOWN_EVENTTYPE
            item.userid == me.userid -> VIEW_TYPE_SENT
            item.userid != me.userid -> VIEW_TYPE_RECEIVED
            else -> super.getItemViewType(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when(viewType) {
            VIEW_TYPE_ACTION -> ItemChatEventActionViewHolder(
                ItemChatroomLiveChatActionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            VIEW_TYPE_SENT -> ItemChatEventSentViewHolder(
                ItemChatroomLiveChatSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            VIEW_TYPE_RECEIVED -> ItemChatEventReceivedViewHolder(
                ItemChatroomLiveChatReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            VIEW_TYPE_ANNOUNCEMENT -> ItemChatEventAnnouncementViewHolder(
                ItemChatroomLiveChatAnnouncementBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            VIEW_TYPE_UNKNOWN_EVENTTYPE -> ItemChatEventUnknownEventTypeViewHolder(
                ItemChatroomLiveChatUnknownEventtypeBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            else -> object: RecyclerView.ViewHolder(parent) {}
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when(holder) {
            is ItemChatEventActionViewHolder -> {
                holder.bind(me, item)
                holder.binding.cardViewMessage.setOnClickListener(null)
            }
            is ItemChatEventSentViewHolder -> {
                holder.bind(me, item)
                holder.binding.cardViewMessage.setOnClickListener { onTapChatEventItem(item) }
            }
            is ItemChatEventReceivedViewHolder -> {
                holder.bind(me, item)
                holder.binding.cardViewMessage.setOnClickListener { onTapChatEventItem(item) }
                val iReactedToThisMessage = item.reactions
                    .any { rxn ->
                        rxn.users.any { usr -> usr.userid == me.userid }
                    }
                holder.binding.btnLike.setOnClickListener {
                    onTapReactChatEventItem.invoke(item, iReactedToThisMessage)
                }
            }
            is ItemChatEventAnnouncementViewHolder -> {
                holder.bind(item)
                holder.binding.cardViewMessage.setOnClickListener(null)
            }
            is ItemChatEventUnknownEventTypeViewHolder -> {
                holder.bind(item)
                holder.binding.cardViewMessage.setOnClickListener(null)
            }
        }
    }

    private val reactionCountFormatter = DecimalFormat("###,###,###.#")

    inner class ItemChatEventSentViewHolder(
        val binding: ItemChatroomLiveChatSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(me: User, item: ChatEvent) {
            val context = itemView.context

            // Profile Picture
            Glide.with(context)
                .load(item.user?.profileurl)
                .error(R.drawable.ic_profile_default)
                .into(binding.civProfile)

            // Display Name
            binding.actvDisplayName.text = item.user?.displayname
            binding.actvDisplayHandle.text = "@${item.user?.handle ?: ""}"
            binding.actvChatMessage.text = item.body

            // ChatEvent Reaction Count
            binding.actvReactionCount.text = when (item.reactions.size) {
                in 1..999 -> reactionCountFormatter.format(item.reactions.size)
                in 1000..999_999 -> "${reactionCountFormatter.format((item.reactions.size.toFloat() / 1_000f))}K"
                in 1_000_000..999_999_999 -> "${reactionCountFormatter.format((item.reactions.size.toFloat() / 1_000_000f))}M"
                else -> null
            }
            // ChatEvent Relative Time Sent: ex. "Just now"
            binding.actvSent.text = item.added?.let { added ->
                val date = UTC_MSG_SENT_FORMATTER.parse(added) ?: return@let null
                DateUtils.getRelativeDateTimeString(
                    context,
                    date.time,
                    DateUtils.DAY_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    DateUtils.FORMAT_SHOW_YEAR
                ).toString()
            }

            ///////////////////////////////
            // Quoted Reply
            ///////////////////////////////
            if(item.replyto != null) {
                binding.actvRepliedTo.text = context.getString(
                    R.string.you_replied_to,
                    "@${item.replyto?.user?.handle ?: ""}"
                )
                binding.actvRepliedMessage.text = item.replyto?.body

                binding.containerReply.visibility = View.VISIBLE
            } else {
                binding.containerReply.visibility = View.GONE
            }
        }
    }

    inner class ItemChatEventReceivedViewHolder(
        val binding: ItemChatroomLiveChatReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(me: User, item: ChatEvent) {
            val context = itemView.context

            // Profile Picture
            Glide.with(context)
                .load(item.user?.profileurl)
                .error(R.drawable.ic_profile_default)
                .into(binding.civProfile)

            // Display Name
            binding.actvDisplayName.text = item.user?.displayname
            binding.actvDisplayHandle.text = "@${item.user?.handle ?: ""}"
            binding.actvChatMessage.text = item.body

            val iReactedToThisMessage = item.reactions
                .any { rxn ->
                    rxn.users.any { usr -> usr.userid == me.userid }
                }

            ImageViewCompat.setImageTintList(
                binding.btnLike,
                when (iReactedToThisMessage) {
                    true -> ContextCompat.getColorStateList(context, R.color.blue_like)
                    false -> ContextCompat.getColorStateList(
                        context,
                        android.R.color.tertiary_text_light
                    )
                }
            )
            binding.btnLike.requestLayout()

            // ChatEvent Reaction Count
            binding.actvReactionCount.text = when (item.reactions.size) {
                in 1..999 -> reactionCountFormatter.format(item.reactions.size)
                in 1000..999_999 -> "${reactionCountFormatter.format((item.reactions.size.toFloat() / 1_000f))}K"
                in 1_000_000..999_999_999 -> "${reactionCountFormatter.format((item.reactions.size.toFloat() / 1_000_000f))}M"
                else -> null
            }
            // ChatEvent Relative Time Sent: ex. "Just now"
            binding.actvSent.text = item.added?.let { added ->
                val date = UTC_MSG_SENT_FORMATTER.parse(added) ?: return@let null
                DateUtils.getRelativeDateTimeString(
                    context,
                    date.time,
                    DateUtils.DAY_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    DateUtils.FORMAT_SHOW_YEAR
                ).toString()
            }

            ///////////////////////////////
            // Quoted Reply
            ///////////////////////////////
            if(item.replyto != null) {
                binding.actvRepliedTo.text = context.getString(
                    R.string.others_replied_to,
                    "@${item.user?.handle ?: ""}",
                    "@${item.replyto?.user?.handle ?: ""}"
                )
                binding.actvRepliedMessage.text = item.replyto?.body

                binding.containerReply.visibility = View.VISIBLE
            } else {
                binding.containerReply.visibility = View.GONE
            }

        }
    }

    inner class ItemChatEventActionViewHolder(
        val binding: ItemChatroomLiveChatActionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(me: User, item: ChatEvent) {
            binding.actvChatMessage.text = item.body
        }
    }

    inner class ItemChatEventAnnouncementViewHolder(
        val binding: ItemChatroomLiveChatAnnouncementBinding
    ): RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatEvent) {
            binding.actvChatMessage.text = item.body
        }
    }

    inner class ItemChatEventUnknownEventTypeViewHolder(
        val binding: ItemChatroomLiveChatUnknownEventtypeBinding
    ): RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatEvent) {
            binding.actvChatMessage.text = item.body
        }
    }

    companion object {
        private const val VIEW_TYPE_SENT = 0x04
        private const val VIEW_TYPE_RECEIVED = 0x08
        private const val VIEW_TYPE_ACTION = 0x00
        private const val VIEW_TYPE_ANNOUNCEMENT = 0x01
        private const val VIEW_TYPE_UNKNOWN_EVENTTYPE = 0xFF

    }
}