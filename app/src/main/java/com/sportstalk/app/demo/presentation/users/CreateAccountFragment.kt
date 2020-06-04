package com.sportstalk.app.demo.presentation.users

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import com.sportstalk.app.demo.databinding.FragmentCreateAccountBinding
import com.sportstalk.app.demo.presentation.BaseFragment

class CreateAccountFragment: BaseFragment() {

    private lateinit var binding: FragmentCreateAccountBinding

    override fun enableBackPressedCallback(): Boolean = true
    override fun onBackPressedCallback(): OnBackPressedCallback.() -> Unit = {
        appNavController.popBackStack()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCreateAccountBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

}