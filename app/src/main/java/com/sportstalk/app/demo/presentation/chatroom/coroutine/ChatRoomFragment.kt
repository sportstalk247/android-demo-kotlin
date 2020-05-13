package com.sportstalk.app.demo.presentation.chatroom.coroutine

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.sportstalk.api.ChatApiService
import com.sportstalk.api.polling.allEventUpdatesFlow
import com.sportstalk.app.demo.databinding.FragmentChatRoomBinding
import com.sportstalk.app.demo.presentation.chatroom.adapters.ItemChatEventRecycler
import com.sportstalk.models.chat.ChatEvent
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.users.User
import com.squareup.cycler.Recycler
import com.squareup.cycler.getMutatedData
import com.squareup.cycler.toDataSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.stringify
import org.koin.android.ext.android.get
import java.time.LocalDateTime
import java.util.*

class ChatRoomFragment : Fragment() {

    private lateinit var binding: FragmentChatRoomBinding
    private lateinit var appNavController: NavController

    private lateinit var recycler: Recycler<ChatEvent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatRoomBinding.inflate(inflater)
        appNavController = findNavController()
        recycler = ItemChatEventRecycler.adopt(
            recyclerView = binding.recyclerView,
            onSelectChatEvent = { chatEvent: ChatEvent ->
                // TODO::
                Toast.makeText(
                    requireContext(),
                    "Click Chat: `${chatEvent.id}`",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        return binding.root
    }

    @ImplicitReflectionSerializer
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.let { appActivity ->
            appActivity.setSupportActionBar(binding.toolbar)
            appActivity.actionBar?.setHomeButtonEnabled(true)
            appActivity.actionBar?.setDisplayShowHomeEnabled(true)
        }

        val user: User = requireArguments().getParcelable(INPUT_ARG_USER)!!
        val room: ChatRoom = requireArguments().getParcelable(INPUT_ARG_ROOM)!!
        val json: Json = Json(
            JsonConfiguration.Stable.copy(
                prettyPrint = false,
                isLenient = true
            )
        )

        // Bare implementation
        get<ChatApiService>().allEventUpdatesFlow(
            chatRoomId = room.id!!,
            lifecycleOwner = viewLifecycleOwner
        )
            .distinctUntilChanged()
            .onEach { events ->
                val strTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val now = LocalDateTime.now()
                    "${now.hour}:${now.minute}:${now.second}.${now.nano}"
                } else {
                    val now = Date(System.currentTimeMillis())
                    "${now.time}"
                }
                Log.d(TAG, "[$strTime] -> events = ${json.stringify(events)}")

                recycler.update {
                    data = events.toMutableList().apply {
                        for(i in 0 until data.size) {
                            add(i, data[i])
                        }
                    }
                        .distinctBy { it.id }
                        .sortedBy { it.added }
                        .toDataSource()

                    binding.recyclerView.scrollToPosition(data.size - 1)
                }
            }
            .launchIn(lifecycleScope)

        binding.btnSend.setOnClickListener {
            binding.tietChatMessage.setText("")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> appNavController.popBackStack()
            else -> super.onOptionsItemSelected(item)
        }

    companion object {
        val TAG = ChatRoomFragment::class.java.simpleName

        const val INPUT_ARG_ROOM = "input-arg-room"
        const val INPUT_ARG_USER = "input-arg-user"
    }

}