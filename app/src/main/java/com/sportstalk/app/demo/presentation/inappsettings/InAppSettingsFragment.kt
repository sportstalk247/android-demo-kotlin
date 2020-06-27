package com.sportstalk.app.demo.presentation.inappsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        viewModel.state.urlEndpoint()
            .onEach { binding.actvUrlEndpointValue.text = it }
            .launchIn(lifecycleScope)

        viewModel.state.authToken()
            .onEach { binding.actvAuthTokenValue.text = it }
            .launchIn(lifecycleScope)

        viewModel.state.appId()
            .onEach { binding.actvAppIdValue.text = it }
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

                // Display Alert Prompt With Input Text
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.url_endpoint)
                    .setView(textInputLayout)
                    .setPositiveButton(R.string.apply) { _, which ->
                        viewModel.urlEndpoint(
                            tietInputText.text?.toString()
                        )
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

                // Display Alert Prompt With Input Text
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.auth_token)
                    .setView(textInputLayout)
                    .setPositiveButton(R.string.apply) { _, which ->
                        viewModel.authToken(
                            tietInputText.text?.toString()
                        )
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

                // Display Alert Prompt With Input Text
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.app_id)
                    .setView(textInputLayout)
                    .setPositiveButton(R.string.apply) { _, which ->
                        viewModel.authToken(
                            tietInputText.text?.toString()
                        )
                    }
                    .show()
            }
            .launchIn(lifecycleScope)

    }

}