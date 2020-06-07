package com.sportstalk.app.demo.presentation.users

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.textChanges
import com.sportstalk.SportsTalk247
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentCreateAccountBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.app.demo.presentation.chatroom.coroutine.ChatRoomFragment
import com.sportstalk.models.ClientConfig
import com.sportstalk.models.chat.ChatRoom
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx2.asFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.concurrent.TimeUnit

class CreateAccountFragment: BaseFragment() {

    private lateinit var binding: FragmentCreateAccountBinding

    private val config: ClientConfig by lazy {
        ClientConfig(
            appId = getString(R.string.sportstalk247_appid),
            apiToken = getString(R.string.sportstalk247_authToken),
            endpoint = getString(R.string.sportstalk247_urlEndpoint)
        )
    }

    private val viewModel: CreateAccountViewModel by viewModel {
        parametersOf(
            SportsTalk247.UserClient(config = config)
        )
    }

    override fun enableBackPressedCallback(): Boolean = true
    override fun onBackPressedCallback(): OnBackPressedCallback.() -> Unit = {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.discard_changes)
            .setPositiveButton(android.R.string.ok) { dialog, which ->
                dialog.dismiss()
                appNavController.popBackStack()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, which ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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

        // Setup Toolbar
        (requireActivity() as? AppCompatActivity)?.let { actv ->
            actv.setSupportActionBar(binding.toolbar)
            actv.supportActionBar?.title = getString(R.string.rooms)
            actv.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        ///////////////////////////////
        // Bind ViewModel State
        ///////////////////////////////

        /**
         * Emits [true] if display name value is valid. Otherwise, emits [false].
         */
        viewModel.state.validationDisplayName()
            .onEach { takeValidationDisplayName(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if display name value is valid. Otherwise, emits [false].
         */
        viewModel.state.validationHandleName()
            .onEach { takeValidationHandleName(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if Photo Link value is valid. Otherwise, emits [false].
         */
        viewModel.state.validationProfileLink()
            .onEach { takeValidationProfileLink(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if Photo Link value is valid. Otherwise, emits [false].
         */
        viewModel.state.validationPhotoLink()
            .onEach { takeValidationPhotoLink(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if display name and handle are valid. Otherwise, emits [false].
         */
        viewModel.state.enableSubmit()
            .onEach { takeEnableSubmit(it) }
            .launchIn(lifecycleScope)

        viewModel.state.progressCreateUser()
            .onEach { takeProgressCreateUser(it) }
            .launchIn(lifecycleScope)

        ///////////////////////////////
        // Bind View Effect
        ///////////////////////////////
        viewModel.effect
            .onEach { takeViewEffect(it) }
            .launchIn(lifecycleScope)

        ///////////////////////////////
        // Bind UI Input Actions
        ///////////////////////////////

        binding.tietDisplayName.textChanges()
            .skipInitialValue()
            .asFlow()
            .debounce(350)
            .map { it.toString() }
            .onEach { viewModel.displayName(it) }
            .launchIn(lifecycleScope)

        binding.tietHandleUsername.textChanges()
            .skipInitialValue()
            .asFlow()
            .debounce(350)
            .map { it.toString() }
            .onEach { viewModel.handleName(it) }
            .launchIn(lifecycleScope)

        binding.tietProfileLink.textChanges()
            .skipInitialValue()
            .asFlow()
            .debounce(350)
            .map { it.toString() }
            .onEach { viewModel.profileLink(it) }
            .launchIn(lifecycleScope)

        binding.tietPhotoLink.textChanges()
            .skipInitialValue()
            .asFlow()
            .debounce(350)
            .map { it.toString() }
            .onEach { viewModel.photoLink(it) }
            .launchIn(lifecycleScope)

        binding.fabSubmit.clicks()
            .throttleFirst(1000, TimeUnit.MILLISECONDS)
            .asFlow()
            .onEach { viewModel.submit() }
            .launchIn(lifecycleScope)
    }

    private fun takeValidationDisplayName(isValid: Boolean) {
        Log.d(TAG, "takeValidationDisplayName() -> isValid = $isValid")

        when(isValid) {
            true -> {
                binding.tilDisplayName.error = ""
                binding.tilDisplayName.isErrorEnabled = false
            }
            false -> {
                binding.tilDisplayName.error = getString(R.string.invalid_displayname)
                binding.tilDisplayName.isErrorEnabled = true
            }
        }
    }

    private fun takeValidationHandleName(isValid: Boolean) {
        Log.d(TAG, "takeValidationHandleName() -> isValid = $isValid")

        when(isValid) {
            true -> {
                binding.tilHandleUsername.error = ""
                binding.tilHandleUsername.isErrorEnabled = false
            }
            false -> {
                binding.tilHandleUsername.error = getString(R.string.invalid_displayname)
                binding.tilHandleUsername.isErrorEnabled = true
            }
        }
    }

    private fun takeValidationProfileLink(isValid: Boolean) {
        Log.d(TAG, "takeValidationProfileLink() -> isValid = $isValid")

        when(isValid) {
            true -> {
                binding.tilProfileLink.error = ""
                binding.tilProfileLink.isErrorEnabled = false
            }
            false -> {
                binding.tilProfileLink.error = getString(R.string.invalid_displayname)
                binding.tilProfileLink.isErrorEnabled = true
            }
        }
    }

    private fun takeValidationPhotoLink(isValid: Boolean) {
        Log.d(TAG, "takeValidationPhotoLink() -> isValid = $isValid")

        when(isValid) {
            true -> {
                binding.tilPhotoLink.error = ""
                binding.tilPhotoLink.isErrorEnabled = false
            }
            false -> {
                binding.tilPhotoLink.error = getString(R.string.invalid_displayname)
                binding.tilPhotoLink.isErrorEnabled = true
            }
        }
    }

    private fun takeEnableSubmit(isValid: Boolean) {
        Log.d(TAG, "takeEnableSubmit() -> isValid = $isValid")

        binding.fabSubmit.isEnabled = isValid
    }

    private fun takeProgressCreateUser(inProgress: Boolean) {
        Log.d(TAG, "takeProgressCreateUser() -> inProgress = $inProgress")

        when(inProgress) {
            true -> {
                binding.progressBar.visibility = View.VISIBLE

                binding.tilDisplayName.isEnabled = false
                binding.tilHandleUsername.isEnabled = false
                binding.tilProfileLink.isEnabled = false
                binding.tilPhotoLink.isEnabled = false
                binding.fabSubmit.isEnabled = false
            }
            false -> {
                binding.progressBar.visibility = View.GONE

                binding.tilDisplayName.isEnabled = true
                binding.tilHandleUsername.isEnabled = true
                binding.tilProfileLink.isEnabled = true
                binding.tilPhotoLink.isEnabled = true
                binding.fabSubmit.isEnabled = true
            }
        }

    }

    private fun takeViewEffect(effect: CreateAccountViewModel.ViewEffect) {
        Log.d(TAG, "takeViewEffect() -> effect = ${effect::class.java.simpleName}")

        when(effect) {
            is CreateAccountViewModel.ViewEffect.SuccessCreateUser -> {
                // Display ERROR Prompt
                Toast.makeText(
                    requireContext(),
                    R.string.account_created_successfully,
                    Toast.LENGTH_LONG
                ).show()

                // Navigate to ChatRoom Screen
                arguments?.getParcelable<ChatRoom>(INPUT_ARG_ROOM)?.let { chatRoom ->
                    appNavController.navigate(
                        R.id.action_fragmentCreateAccount_to_fragmentChatroom,
                        bundleOf(
                            ChatRoomFragment.INPUT_ARG_ROOM to chatRoom,
                            ChatRoomFragment.INPUT_ARG_USER to effect.user
                        )
                    )
                }
            }
            is CreateAccountViewModel.ViewEffect.ErrorCreateUser -> {
                // Display ERROR Prompt
                Toast.makeText(
                    requireContext(),
                    effect.err.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when(item.itemId) {
            android.R.id.home -> {
                requireActivity().onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    companion object {
        const val INPUT_ARG_ROOM = "input-arg-room"
    }

}