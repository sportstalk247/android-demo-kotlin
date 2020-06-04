package com.sportstalk.app.demo.presentation

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.sportstalk.app.demo.R

open class BaseFragment : Fragment() {

    // Top-level Nav Controller Instance
    open val appNavController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.navHostFragmentApp)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        requireActivity().onBackPressedDispatcher
            .addCallback(
                owner = this@BaseFragment,
                enabled = enableBackPressedCallback(),
                onBackPressed = onBackPressedCallback()
            )
    }

    /**
     * Helper functions to handle on back pressed callback
     * - Override this and set to `true` if you want the implementing Fragment to enable onBackPressedCallback implementation
     */
    open fun enableBackPressedCallback(): Boolean = false

    /**
     * Override this and set to `true` if you want the implementing Fragment to have its own onBackPressedCallback implementation
     */
    open fun onBackPressedCallback(): OnBackPressedCallback.() -> Unit = {}

    /**
     * Log TAG
     */
    protected val TAG = this::class.java.simpleName

}
