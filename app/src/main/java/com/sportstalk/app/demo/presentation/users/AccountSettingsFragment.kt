package com.sportstalk.app.demo.presentation.users

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.textChanges
import com.sportstalk.app.demo.R
import com.sportstalk.app.demo.databinding.FragmentAccountSettingsBinding
import com.sportstalk.app.demo.presentation.BaseFragment
import com.sportstalk.models.users.User
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx2.asFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class AccountSettingsFragment : BaseFragment() {

    private lateinit var binding: FragmentAccountSettingsBinding
    private lateinit var menu: Menu

    private val viewModel: AccountSettingsViewModel by viewModel()

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
        binding = FragmentAccountSettingsBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Toolbar
        (requireActivity() as? AppCompatActivity)?.let { actv ->
            actv.setSupportActionBar(binding.toolbar)
            actv.supportActionBar?.title = getString(R.string.account_settings)
            actv.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        ///////////////////////////////
        // Bind ViewModel State
        ///////////////////////////////

        /**
         * Emits [true] if Get User Details Operation is in-progress. Emits [false] when done.
         */
        viewModel.state.progressFetchUserDetails()
            .onEach { takeProgressFetchUserDetails(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [User] instance from Get User Details Operation response.
         */
        viewModel.state.userDetails()
            .onEach { takeUserDetails(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if display name value is valid. Otherwise, emits [false].
         */
        viewModel.state.validationDisplayName()
            .onEach { takeValidationDisplayName(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if handlename value is valid. Otherwise, emits [false].
         */
        viewModel.state.validationHandleName()
            .onEach { takeValidationHandleName(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if Profile Link value is valid. Otherwise, emits [false].
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
        viewModel.state.enableSave()
            .onEach { takeEnableSave(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if Update User Operation is in-progress. Emits [false] when done.
         */
        viewModel.state.progressUpdateUser()
            .onEach { takeProgressUpdateUser(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if Update User Operation is in-progress. Emits [false] when done.
         */
        viewModel.state.progressDeleteUser()
            .onEach { takeProgressDeleteUser(it) }
            .launchIn(lifecycleScope)

        /**
         * Emits [true] if Ban Account Operation is in-progress. Emits [false] when done.
         */
        viewModel.state.progressBanAccount()
            .onEach { takeProgressBanAccount(it) }
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
            .debounce(350, TimeUnit.MILLISECONDS)
            .asFlow()
            .map { it.toString() }
            .onEach { viewModel.displayName(it) }
            .launchIn(lifecycleScope)

        binding.tietHandleUsername.textChanges()
            .skipInitialValue()
            .debounce(350, TimeUnit.MILLISECONDS)
            .asFlow()
            .map { it.toString() }
            .onEach { viewModel.handleName(it) }
            .launchIn(lifecycleScope)

        binding.tietProfileLink.textChanges()
            .skipInitialValue()
            .debounce(350, TimeUnit.MILLISECONDS)
            .asFlow()
            .map { it.toString() }
            .onEach { viewModel.profileLink(it) }
            .launchIn(lifecycleScope)

        binding.tietPhotoLink.textChanges()
            .skipInitialValue()
            .debounce(350, TimeUnit.MILLISECONDS)
            .asFlow()
            .map { it.toString() }
            .onEach { viewModel.photoLink(it) }
            .launchIn(lifecycleScope)

        // Update Photo Realtime
        binding.tietProfileLink.textChanges()
            .skipInitialValue()
            .filter { it.isNotEmpty() }
            .asFlow()
            .debounce(500)
            .map { it.toString() }
            .onEach { urlStr ->
                // Immediately Load Profile Picture
                Glide.with(requireContext())
                    .load(urlStr)
                    .error(R.drawable.ic_profile_default)
                    .into(binding.civPhotoLink)
            }
            .launchIn(lifecycleScope)

        // "DELETE Account"
        binding.fabDeleteAccount.clicks()
            .throttleFirst(1000, TimeUnit.MILLISECONDS)
            .asFlow()
            .onEach {
                // Perform Delete Account
                viewModel.deleteAccount()
            }
            .launchIn(lifecycleScope)

        // "Ban/Restore Account"
        binding.fabBanAccount.clicks()
            .throttleFirst(1000, TimeUnit.MILLISECONDS)
            .asFlow()
            .onEach {
                // Perform Ban/Restore Account
                viewModel.banRestoreAccount()
            }
            .launchIn(lifecycleScope)

        binding.swipeRefresh.refreshes()
            .asFlow()
            .onEach {
                // Perform Get User Details Operation
                viewModel.fetchUserDetails()
            }
            .launchIn(lifecycleScope)

        // Perform Get User Details Operation
        viewModel.fetchUserDetails()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.account_settings, menu)
        this.menu = menu
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                requireActivity().onBackPressed()
                true
            }
            R.id.action_save -> {
                // Perform CreateUpdateUser SDK Operation
                viewModel.save()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }


    private fun takeProgressFetchUserDetails(inProgress: Boolean) {
        Log.d(TAG, "takeProgressFetchUserDetails() -> inProgress = $inProgress")

        if (::menu.isInitialized) {
            val menuSave = this.menu.findItem(R.id.action_save)
            menuSave.isEnabled = !inProgress
        }
        when (inProgress) {
            true -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = true

                binding.tilDisplayName.isEnabled = false
                binding.tilHandleUsername.isEnabled = false
                binding.tilProfileLink.isEnabled = false
                binding.tilPhotoLink.isEnabled = false

                binding.fabDeleteAccount.isEnabled = false
                binding.fabBanAccount.isEnabled = false
            }
            false -> {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                binding.tilDisplayName.isEnabled = true
                binding.tilHandleUsername.isEnabled = true
                binding.tilProfileLink.isEnabled = true
                binding.tilPhotoLink.isEnabled = true

                binding.fabDeleteAccount.isEnabled = true
                binding.fabBanAccount.isEnabled = true
            }
        }
    }

    private fun takeUserDetails(user: User) {
        Log.d(TAG, "takeUserDetails() -> user = $user")

        // Set initial input details
        binding.tietDisplayName.setText(user.displayname)
        binding.tietHandleUsername.setText(user.handle)
        binding.tietProfileLink.setText(user.profileurl)
        binding.tietPhotoLink.setText(user.pictureurl)

        // Set Ban/Restore label
        binding.fabBanAccount.text = when (user.banned) {
            false -> getString(R.string.ban_account)
            else -> getString(R.string.restore_account)
        }
    }

    private fun takeValidationDisplayName(isValid: Boolean) {
        Log.d(TAG, "takeValidationDisplayName() -> isValid = $isValid")

        when (isValid) {
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

        when (isValid) {
            true -> {
                binding.tilHandleUsername.error = ""
                binding.tilHandleUsername.isErrorEnabled = false
            }
            false -> {
                binding.tilHandleUsername.error = getString(R.string.invalid_handlename)
                binding.tilHandleUsername.isErrorEnabled = true
            }
        }
    }

    private fun takeValidationProfileLink(isValid: Boolean) {
        Log.d(TAG, "takeValidationProfileLink() -> isValid = $isValid")

        when (isValid) {
            true -> {
                binding.tilProfileLink.error = ""
                binding.tilProfileLink.isErrorEnabled = false
            }
            false -> {
                binding.tilProfileLink.error = getString(R.string.invalid_profile_link)
                binding.tilProfileLink.isErrorEnabled = true
            }
        }
    }

    private fun takeValidationPhotoLink(isValid: Boolean) {
        Log.d(TAG, "takeValidationPhotoLink() -> isValid = $isValid")

        when (isValid) {
            true -> {
                binding.tilPhotoLink.error = ""
                binding.tilPhotoLink.isErrorEnabled = false
            }
            false -> {
                binding.tilPhotoLink.error = getString(R.string.invalid_picture_link)
                binding.tilPhotoLink.isErrorEnabled = true
            }
        }
    }

    private fun takeEnableSave(enable: Boolean) {
        Log.d(TAG, "takeEnableSave() -> enable = $enable")

        if (::menu.isInitialized) {
            val menuSave = this.menu.findItem(R.id.action_save)
            menuSave.isEnabled = enable
        }
    }

    private fun takeProgressUpdateUser(inProgress: Boolean) {
        Log.d(TAG, "takeProgressUpdateUser() -> inProgress = $inProgress")

        if (::menu.isInitialized) {
            val menuSave = this.menu.findItem(R.id.action_save)
            menuSave.isEnabled = !inProgress
        }
        when (inProgress) {
            true -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = true

                binding.tilDisplayName.isEnabled = false
                binding.tilHandleUsername.isEnabled = false
                binding.tilProfileLink.isEnabled = false
                binding.tilPhotoLink.isEnabled = false

                binding.fabDeleteAccount.isEnabled = false
                binding.fabBanAccount.isEnabled = false
            }
            false -> {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                binding.tilDisplayName.isEnabled = true
                binding.tilHandleUsername.isEnabled = true
                binding.tilProfileLink.isEnabled = true
                binding.tilPhotoLink.isEnabled = true

                binding.fabDeleteAccount.isEnabled = true
                binding.fabBanAccount.isEnabled = true
            }
        }
    }

    private fun takeProgressDeleteUser(inProgress: Boolean) {
        Log.d(TAG, "takeProgressDeleteUser() -> inProgress = $inProgress")

        if (::menu.isInitialized) {
            val menuSave = this.menu.findItem(R.id.action_save)
            menuSave.isEnabled = !inProgress
        }
        when (inProgress) {
            true -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = true

                binding.tilDisplayName.isEnabled = false
                binding.tilHandleUsername.isEnabled = false
                binding.tilProfileLink.isEnabled = false
                binding.tilPhotoLink.isEnabled = false

                binding.fabDeleteAccount.isEnabled = false
                binding.fabBanAccount.isEnabled = false
            }
            false -> {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                binding.tilDisplayName.isEnabled = true
                binding.tilHandleUsername.isEnabled = true
                binding.tilProfileLink.isEnabled = true
                binding.tilPhotoLink.isEnabled = true

                binding.fabDeleteAccount.isEnabled = true
                binding.fabBanAccount.isEnabled = true
            }
        }
    }

    private fun takeProgressBanAccount(inProgress: Boolean) {
        Log.d(TAG, "takeProgressBanAccount() -> inProgress = $inProgress")

        if (::menu.isInitialized) {
            val menuSave = this.menu.findItem(R.id.action_save)
            menuSave.isEnabled = !inProgress
        }
        when (inProgress) {
            true -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = true

                binding.tilDisplayName.isEnabled = false
                binding.tilHandleUsername.isEnabled = false
                binding.tilProfileLink.isEnabled = false
                binding.tilPhotoLink.isEnabled = false

                binding.fabDeleteAccount.isEnabled = false
                binding.fabBanAccount.isEnabled = false
            }
            false -> {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                binding.tilDisplayName.isEnabled = true
                binding.tilHandleUsername.isEnabled = true
                binding.tilProfileLink.isEnabled = true
                binding.tilPhotoLink.isEnabled = true

                binding.fabDeleteAccount.isEnabled = true
                binding.fabBanAccount.isEnabled = true
            }
        }
    }

    private fun takeViewEffect(effect: AccountSettingsViewModel.ViewEffect) {
        Log.d(TAG, "takeViewEffect() -> effect = ${effect::class.java.simpleName}")

        when (effect) {
            is AccountSettingsViewModel.ViewEffect.ErrorGetUserDetails -> {
                // Display Error Prompt
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            is AccountSettingsViewModel.ViewEffect.SuccessUpdateUser -> {
                // Display Success Prompt
                Toast.makeText(
                    requireContext(),
                    getString(R.string.account_successfully_updated),
                    Toast.LENGTH_LONG
                )
                    .show()

                // Navigate back
                appNavController.popBackStack()
            }
            is AccountSettingsViewModel.ViewEffect.ErrorUpdateUser -> {
                // Display Error Prompt
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            is AccountSettingsViewModel.ViewEffect.SuccessDeleteAccount -> {
                // Display Success Prompt
                Toast.makeText(
                    requireContext(),
                    getString(R.string.account_successfully_deleted),
                    Toast.LENGTH_LONG
                )
                    .show()

                // Navigate back
                appNavController.popBackStack()
            }
            is AccountSettingsViewModel.ViewEffect.ErrorDeleteAccount -> {
                // Display Error Prompt
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            is AccountSettingsViewModel.ViewEffect.SuccessBanRestoreAccount -> {
                val message = when (effect.user.banned) {
                    true -> getString(R.string.account_successfully_banned)
                    else -> getString(R.string.account_successfully_restored)
                }

                // Display Success Prompt
                Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            is AccountSettingsViewModel.ViewEffect.ErrorBanAccount -> {
                // Display Error Prompt
                Toast.makeText(
                    requireContext(),
                    effect.err.message ?: getString(R.string.something_went_wrong_please_try_again),
                    Toast.LENGTH_LONG
                )
                    .show()
            }
        }

    }

}