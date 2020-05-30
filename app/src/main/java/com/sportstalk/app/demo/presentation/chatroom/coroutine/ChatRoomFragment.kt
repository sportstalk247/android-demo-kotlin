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
import com.jakewharton.rxbinding3.view.clicks
import com.sportstalk.SportsTalk247
import com.sportstalk.api.ChatClient
import com.sportstalk.api.polling.coroutines.allEventUpdates
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentChatRoomBinding
import com.sportstalk.app.demo.extensions.throttleFirst
import com.sportstalk.app.demo.presentation.chatroom.adapters.ItemChatEventRecycler
import com.sportstalk.models.ClientConfig
import com.sportstalk.models.SportsTalkException
import com.sportstalk.models.chat.ChatEvent
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.chat.ExecuteChatCommandRequest
import com.sportstalk.models.chat.JoinChatRoomRequest
import com.sportstalk.models.users.User
import com.squareup.cycler.Recycler
import com.squareup.cycler.toDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.stringify
import java.time.LocalDateTime
import java.util.*

class ChatRoomFragment : Fragment() {

    private lateinit var binding: FragmentChatRoomBinding
    private lateinit var appNavController: NavController

    private lateinit var user: User
    private lateinit var room: ChatRoom
    private val json: Json by lazy {
        Json(
            JsonConfiguration.Stable.copy(
                prettyPrint = false,
                isLenient = true
            )
        )
    }

    private val config: ClientConfig by lazy {
        ClientConfig(
            appId = "5ec0dc805617e00918446168",
            apiToken = "R-GcA7YsG0Gu3DjEVMWcJA60RkU9uyH0Wmn2pnEbzJzA",
            endpoint = "https://qa-talkapi.sportstalk247.com/api/v3/"
        )
    }

    private val chatClient: ChatClient by lazy {
        SportsTalk247.ChatClient(config = config)
    }

    private lateinit var recycler: Recycler<ChatEvent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        user = requireArguments().getParcelable(INPUT_ARG_USER)!!
        room = requireArguments().getParcelable(INPUT_ARG_ROOM)!!
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

        // Bare implementation, setup get updates event subscription
        chatClient.allEventUpdates(
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

                // Update chat list
                recycler.update {
                    data = events.toMutableList().apply {
                        for (i in 0 until data.size) {
                            add(i, data[i])
                        }
                    }
                        .distinctBy { it.id }
                        .sortedByDescending { it.added }
                        .toDataSource()
                }
            }
            .launchIn(lifecycleScope)

        // Launch Coroutine Scope
        lifecycleScope.launchWhenCreated {
            // Join Room upon enter
            try {
                val joinResponse = withContext(Dispatchers.IO) {
                    chatClient.joinRoom(
                        chatRoomId = room.id!!,
                        request = JoinChatRoomRequest(
                            userid = user.userid!!,
                            handle = user.handle!!
                        )
                    )
                        .await()
                }

                // SUCCESS, display prompt
                Toast.makeText(
                    requireContext(),
                    "Successfully Joined the room!",
                    Toast.LENGTH_SHORT
                ).show()

                // Display initial list of events
                // Update chat list
                val initialEvents = joinResponse.eventscursor?.events ?: listOf()
                recycler.update {
                    data = initialEvents.toMutableList().apply {
                        for (i in 0 until data.size) {
                            add(i, data[i])
                        }
                    }
                        .distinctBy { it.id }
                        .sortedByDescending { it.added }
                        .toDataSource()

                    binding.recyclerView.scrollToPosition(0)
                }

            } catch (err: SportsTalkException) {
                err.printStackTrace()
                // DISPLAY Error Prompt
                Toast.makeText(
                    requireContext(),
                    err.message,
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Then, start listen to this room's event updates
            chatClient.startListeningToChatUpdates(forRoomId = room.id!!)
        }

        // Click send chat message
        binding.btnSend.clicks()
            .asFlow()
            .throttleFirst(500)
            .onEach {
                // Extract chat message string input value
                val chatMsg = binding.tietChatMessage.text?.toString() ?: ""

                try {
                    // Perform `executeChatCommand` operation
                    val response = withContext(Dispatchers.IO) {
                        chatClient.executeChatCommand(
                            chatRoomId = room.id!!,
                            request = ExecuteChatCommandRequest(
                                command = chatMsg,
                                userid = user.userid!!
                            )
                        )
                            .await()
                    }

                    // SUCCESS, display prompt
                    Toast.makeText(
                        requireContext(),
                        "Message sent!\n\"${response.speech?.body ?: ""}\"",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (err: SportsTalkException) {
                    err.printStackTrace()

                    // DISPLAY Error Prompt
                    Toast.makeText(
                        requireContext(),
                        err.message,
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    // Clear input text field
                    binding.tietChatMessage.setText("")
                    // Scroll to bottom after sometime
                    delay(750)
                    binding.recyclerView.scrollToPosition(0)
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> appNavController.popBackStack()
            // Explicitly Start Listening to Event Updates
            R.id.action_start_listen -> {
                chatClient.startListeningToChatUpdates(forRoomId = room.id!!)
                true
            }
            // Explicitly STOP Listening from Event Updates
            R.id.action_stop_listen -> {
                chatClient.stopListeningToChatUpdates(forRoomId = room.id!!)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    companion object {
        val TAG = ChatRoomFragment::class.java.simpleName

        const val INPUT_ARG_ROOM = "input-arg-room"
        const val INPUT_ARG_USER = "input-arg-user"
    }

}