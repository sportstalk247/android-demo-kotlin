package com.sportstalk.app.demo.presentation.chatroom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sportstalk.app.demo.databinding.FragmentChatroomLiveChatBinding
import com.sportstalk.app.demo.presentation.BaseFragment

class LiveChatFragment: BaseFragment() {

    private lateinit var binding: FragmentChatroomLiveChatBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatroomLiveChatBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

}