package com.sportstalk.app.demo.presentation.chatroom

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentChatroomBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.models.ClientConfig
import com.sportstalk.models.chat.ChatRoom
import com.sportstalk.models.users.User

class ChatRoomFragment : BaseFragment() {

    private lateinit var binding: FragmentChatroomBinding

    private lateinit var user: User
    private lateinit var room: ChatRoom

    private val config: ClientConfig by lazy {
        ClientConfig(
            appId = getString(R.string.sportstalk247_appid),
            apiToken = getString(R.string.sportstalk247_authToken),
            endpoint = getString(R.string.sportstalk247_urlEndpoint)
        )
    }

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
        binding = FragmentChatroomBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.let { appActivity ->
            appActivity.setSupportActionBar(binding.toolbar)
            appActivity.supportActionBar?.title = ""/*room.name*/
            appActivity.supportActionBar?.setHomeButtonEnabled(true)
            appActivity.supportActionBar?.setDisplayShowHomeEnabled(true)
        }

        // Initially set selected tab to `Live Chat`
        binding.tabLayoutChatroom.selectTab(
            binding.tabLayoutChatroom.getTabAt(2)
        )

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.chatroom, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                // TODO:: Call Exit Chatroom Operation
                appNavController.popBackStack()
            }
            else -> super.onOptionsItemSelected(item)
        }

    companion object {
        val TAG = ChatRoomFragment::class.java.simpleName

        const val INPUT_ARG_ROOM = "input-arg-room"
        const val INPUT_ARG_USER = "input-arg-user"
    }

}