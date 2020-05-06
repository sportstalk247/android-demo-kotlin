package com.sportstalk.app.demo.presentation

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.sportstalk.app.demo.R

class MainActivity: AppCompatActivity() {

    // Top-level Nav Controller Instance
    protected val appNavController: NavController by lazy {
        Navigation.findNavController(this,
            R.id.navHostFragmentApp
        )
    }

    /**
     * Log TAG
     */
    protected val TAG = this::class.java.simpleName

}