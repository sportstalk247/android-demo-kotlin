package com.sportstalk.app.demo.presentation

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    /**
     * Log TAG
     */
    protected val TAG = this::class.java.simpleName

}