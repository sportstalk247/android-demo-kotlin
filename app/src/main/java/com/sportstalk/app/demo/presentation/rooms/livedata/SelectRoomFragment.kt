package com.sportstalk.app.demo.presentation.rooms.livedata

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.distinctUntilChanged
import com.sportstalk.app.demo.databinding.FragmentSelectRoomBinding
import com.sportstalk.app.demo.extensions.SingleLiveEvent
import com.sportstalk.app.demo.presentation.rooms.adapters.ItemSelectRoomRecycler
import com.sportstalk.models.chat.ChatRoom
import com.squareup.cycler.Recycler
import com.squareup.cycler.toDataSource
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.*

class SelectRoomFragment : Fragment() {

    private lateinit var binding: FragmentSelectRoomBinding

    private val viewModel: SelectRoomViewModel by viewModel()

    private val cursor = MutableLiveData<Optional<String>>()
    private val onScrollFetchNext = SingleLiveEvent<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSelectRoomBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "binding.swipeRefresh")
            recycler.clear()
            viewModel.fetch(cursor = null)
        }

        // Scroll Bottom Attempt Fetch More
        onScrollFetchNext
            .distinctUntilChanged()
            .observe(viewLifecycleOwner, Observer {
                    val _cursor = cursor.value!!
                    viewModel.fetch(
                        cursor = if (_cursor.isPresent) _cursor.get()
                        else null
                    )
                }
            )


        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            takeProgressFetchRooms(state.progressFetchRooms)
            takeRooms(state.rooms)
            Log.d(TAG, "viewModel.state.cursor = state.cursor")
            cursor.postValue(Optional.ofNullable(state.cursor))
        })

        viewModel.effect.observe(viewLifecycleOwner, Observer { effect ->
            when (effect) {
                is SelectRoomViewModel.ViewEffect.NavigateToChatRoom -> {
                    // TODO::
                    Toast.makeText(
                        requireContext(),
                        "Click Room: `${effect.which.slug}`",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is SelectRoomViewModel.ViewEffect.ErrorFetchRoom -> {
                    // TODO::
                    Toast.makeText(
                        requireContext(),
                        "Click Room: `${effect.err.message}`",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        binding.fabAdd.setOnClickListener {
            Log.d(TAG, "binding.fabAdd.setOnClickListener")
        }

        // On view created, Perform fetch
        viewModel.fetch(cursor = null)
    }

    private fun takeProgressFetchRooms(inProgress: Boolean) {
        Log.d(TAG, "takeProgressFetchRooms() -> inProgress = $inProgress")
        binding.swipeRefresh.isRefreshing = inProgress
    }

    private fun takeRooms(rooms: List<ChatRoom>) {
        Log.d(TAG, "takeRooms() -> rooms = $rooms")

        recycler.update {
            data = rooms.toDataSource()
        }
    }

    private val recycler: Recycler<ChatRoom> by lazy {
        ItemSelectRoomRecycler.adopt(
            recyclerView = binding.recyclerView,
            onScrollFetchChatRoomsNext = { cursor: String ->
                // Attempt scroll next
                onScrollFetchNext.postValue(cursor)
            },
            onSelectChatRoom = { chatRoom: ChatRoom ->
                // Select Chat Room
                viewModel.selectRoom(which = chatRoom)
            }
        )
    }

    companion object {
        val TAG = SelectRoomFragment::class.java.simpleName
    }

}