package com.sportstalk.app.demo.presentation.inappsettings

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jakewharton.rxbinding3.view.clicks
import com.sportstalk.app.demo.BuildConfig
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentInappSettingsBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx2.asFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class InAppSettingsFragment : BaseFragment() {

    private lateinit var binding: FragmentInappSettingsBinding
    private val viewModel: InAppSettingsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentInappSettingsBinding.inflate(inflater, container, false)
        binding.actvAppVersion.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.let { actv ->
            actv.setSupportActionBar(binding.toolbar)
            actv.supportActionBar?.title = getString(R.string.inapp_settings)
        }

        viewModel.state.urlEndpoint()
            .onEach { binding.actvUrlEndpointValue.text = it }
            .launchIn(lifecycleScope)

        viewModel.state.authToken()
            .onEach { binding.actvAuthTokenValue.text = it }
            .launchIn(lifecycleScope)

        viewModel.state.appId()
            .onEach { binding.actvAppIdValue.text = it }
            .launchIn(lifecycleScope)

        viewModel.effect
            .onEach { effect ->
                when(effect) {
                    is InAppSettingsViewModel.ViewEffect.SuccessReset -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.reset_was_successful,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .launchIn(lifecycleScope)

        binding.containerUrlEndpoint.clicks()
            .throttleFirst(1000, TimeUnit.MILLISECONDS)
            .asFlow()
            .onEach {
                val textInputLayout = LayoutInflater.from(requireContext())
                    .inflate(
                        R.layout.layout_inapp_settings_input_text,
                        binding.root,
                        false
                    ) as TextInputLayout
                val tietInputText =
                    textInputLayout.findViewById<TextInputEditText>(R.id.tietInputText)
                        .apply {
                            setText(binding.actvUrlEndpointValue.text)
                            selectAll()
                        }

                // Display Alert Prompt With Input Text
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.url_endpoint)
                    .setView(textInputLayout)
                    .setPositiveButton(R.string.apply) { _, which ->
                        viewModel.urlEndpoint(
                            tietInputText.text?.toString()
                        )
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
            .launchIn(lifecycleScope)

        binding.containerAuthToken.clicks()
            .throttleFirst(1000, TimeUnit.MILLISECONDS)
            .asFlow()
            .onEach {
                val textInputLayout = LayoutInflater.from(requireContext())
                    .inflate(
                        R.layout.layout_inapp_settings_input_text,
                        binding.root,
                        false
                    ) as TextInputLayout
                val tietInputText =
                    textInputLayout.findViewById<TextInputEditText>(R.id.tietInputText)
                        .apply {
                            setText(binding.actvAuthTokenValue.text)
                            selectAll()
                        }

                // Display Alert Prompt With Input Text
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.auth_token)
                    .setView(textInputLayout)
                    .setPositiveButton(R.string.apply) { _, which ->
                        viewModel.authToken(
                            tietInputText.text?.toString()
                        )
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
            .launchIn(lifecycleScope)

        binding.containerAppId.clicks()
            .throttleFirst(1000, TimeUnit.MILLISECONDS)
            .asFlow()
            .onEach {
                val textInputLayout = LayoutInflater.from(requireContext())
                    .inflate(
                        R.layout.layout_inapp_settings_input_text,
                        binding.root,
                        false
                    ) as TextInputLayout
                val tietInputText =
                    textInputLayout.findViewById<TextInputEditText>(R.id.tietInputText)
                        .apply {
                            setText(binding.actvAppIdValue.text)
                            selectAll()
                        }

                // Display Alert Prompt With Input Text
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.app_id)
                    .setView(textInputLayout)
                    .setPositiveButton(R.string.apply) { _, which ->
                        viewModel.appId(
                            tietInputText.text?.toString()
                        )
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
            .launchIn(lifecycleScope)

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.inapp_settings, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when(item.itemId) {
            R.id.action_reset -> {
                // Display Alert Prompt With Input Text
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.reset)
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        viewModel.reset()
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

}