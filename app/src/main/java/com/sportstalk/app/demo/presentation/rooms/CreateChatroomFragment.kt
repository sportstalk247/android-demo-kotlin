package com.sportstalk.app.demo.presentation.rooms

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.jakewharton.rxbinding3.widget.textChanges
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentCreateChatroomBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx2.asFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class CreateChatroomFragment : BaseFragment() {

    private lateinit var binding: FragmentCreateChatroomBinding

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

    private val viewModel: CreateChatroomViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCreateChatroomBinding.inflate(inflater)
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
         * Emits [true] if Room name value is valid. Otherwise, emits [false].
         */
        viewModel.state.validationRoomName()
            .onEach { takeValidationRoomName(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if Room name is valid. Otherwise, emits [false].
         */
        viewModel.state.enableSubmit()
            .onEach { takeEnableSubmit(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] upon start Create Chatroom operation. Emits [false] when done.
         */
        viewModel.state.progressCreateChatroom()
            .onEach { takeProgressCreateChatroom(it) }
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

        // Room Name
        binding.tietName.textChanges()
            .skipInitialValue()
            .asFlow()
            .debounce(350)
            .map { it.toString() }
            .onEach { viewModel.roomName(it) }
            .launchIn(lifecycleScope)

        // Room Description
        binding.tietDescription.textChanges()
            .asFlow()
            .debounce(350)
            .map { it.toString() }
            .onEach { viewModel.roomDescription(it) }
            .launchIn(lifecycleScope)

        // Room Custom ID
        binding.tietCustomId.textChanges()
            .asFlow()
            .debounce(350)
            .map { it.toString() }
            .onEach { viewModel.roomCustomId(it) }
            .launchIn(lifecycleScope)

        binding.switchEnableActions.checkedChanges()
            .asFlow()
            .onEach { checked ->
                viewModel.roomAction(checked)
                binding.switchEnableActions.text = when (checked) {
                    true -> getString(R.string.action_enabled)
                    false -> getString(R.string.action_disabled)
                }
            }
            .launchIn(lifecycleScope)

        binding.switchEnableEnterExit.checkedChanges()
            .asFlow()
            .onEach { checked ->
                viewModel.roomEnterExit(checked)
                binding.switchEnableEnterExit.text = when (checked) {
                    true -> getString(R.string.enter_exit_enabled)
                    false -> getString(R.string.enter_exit_disabled)
                }
            }
            .launchIn(lifecycleScope)

        binding.switchRoomOpen.checkedChanges()
            .asFlow()
            .onEach { checked ->
                viewModel.roomIsOpen(checked)
                binding.switchRoomOpen.text = when (checked) {
                    true -> getString(R.string.room_is_open)
                    false -> getString(R.string.room_is_closed)
                }
            }
            .launchIn(lifecycleScope)

        binding.switchProfanityFilter.checkedChanges()
            .asFlow()
            .onEach { checked ->
                viewModel.roomProfanityEnabled(checked)
                binding.switchProfanityFilter.text = when (checked) {
                    true -> getString(R.string.profanity_filter_enabled)
                    false -> getString(R.string.profanity_filter_disabled)
                }
            }
            .launchIn(lifecycleScope)

        // Click "Submit"
        binding.fabSubmit.clicks()
            .throttleFirst(1000, TimeUnit.MILLISECONDS)
            .asFlow()
            .onEach {
                viewModel.submit()
            }
            .launchIn(lifecycleScope)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                requireActivity().onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun takeValidationRoomName(isValid: Boolean) {
        Log.d(TAG, "takeValidationRoomName() -> isValid = $isValid")

        when (isValid) {
            true -> {
                binding.tilName.error = ""
                binding.tilName.isErrorEnabled = false
            }
            false -> {
                binding.tilName.error = getString(R.string.invalid_roomname)
                binding.tilName.isErrorEnabled = true
            }
        }
    }

    private fun takeEnableSubmit(isEnabled: Boolean) {
        Log.d(TAG, "takeEnableSubmit() -> isEnabled = $isEnabled")

        binding.fabSubmit.isEnabled = isEnabled
    }

    private fun takeProgressCreateChatroom(inProgress: Boolean) {
        Log.d(TAG, "takeProgressCreateChatroom() -> inProgress = $inProgress")

        when (inProgress) {
            true -> {
                // DISPLAY Progress Indicator
                binding.progressBar.visibility = View.VISIBLE

                // DISABLE All other input UI components
                binding.tilName.isEnabled = false
                binding.tilDescription.isEnabled = false
                binding.tilCustomId.isEnabled = false
                binding.switchEnableActions.isEnabled = false
                binding.switchEnableEnterExit.isEnabled = false
                binding.switchRoomOpen.isEnabled = false
                binding.switchProfanityFilter.isEnabled = false
                binding.fabSubmit.isEnabled = false
            }
            false -> {
                // HIDE Progress Indicator
                binding.progressBar.visibility = View.GONE

                // ENABLE All input UI components
                binding.tilName.isEnabled = true
                binding.tilDescription.isEnabled = true
                binding.tilCustomId.isEnabled = true
                binding.switchEnableActions.isEnabled = true
                binding.switchEnableEnterExit.isEnabled = true
                binding.switchRoomOpen.isEnabled = true
                binding.switchProfanityFilter.isEnabled = true
                binding.fabSubmit.isEnabled = true
            }
        }
    }

    private fun takeViewEffect(effect: CreateChatroomViewModel.ViewEffect) {
        Log.d(TAG, "takeViewEffect() -> effect = ${effect::class.java.simpleName}")

        when (effect) {
            is CreateChatroomViewModel.ViewEffect.SuccessCreateChatroom -> {
                // Success Prompt
                Toast.makeText(
                    requireContext(),
                    getString(R.string.chatroom_successfully_created, effect.room.name),
                    Toast.LENGTH_LONG
                ).show()

                // Pop back
                appNavController.popBackStack()
            }
            is CreateChatroomViewModel.ViewEffect.ErrorCreateChatroom -> {
                // Display ERROR Prompt
                Toast.makeText(
                    requireContext(),
                    effect.err.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

}