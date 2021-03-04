package com.sportstalk.app.demo.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.coroutine.api.UserClient
import com.sportstalk.datamodels.SportsTalkException
import com.sportstalk.datamodels.users.CreateUpdateUserRequest
import com.sportstalk.datamodels.users.DeleteUserResponse
import com.sportstalk.datamodels.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountSettingsViewModel(
    /*
    * Typical use case to access SDK client instance:
    *   val config = ClientConfig(appId = "...", apiToken = "...", endpoint = "...")
    *   // Instantiate via Factory
    *   val userClient = SportsTalk247.UserClient(config = config)
    */
    val userClient: UserClient,
    val preferences: SportsTalkDemoPreferences
) : ViewModel() {

    private val displayName = ConflatedBroadcastChannel<String?>(null)
    private val handleName = ConflatedBroadcastChannel<String?>(null)
    private val profileLink = ConflatedBroadcastChannel<String?>(null)
    private val photoLink = ConflatedBroadcastChannel<String?>(null)

    private val userDetails = ConflatedBroadcastChannel<User>(preferences.currentUser!!)

    private val validationDisplayName = ConflatedBroadcastChannel<Boolean?>(null)
    private val validationHandleName = ConflatedBroadcastChannel<Boolean?>(null)
    private val validationProfileLink = ConflatedBroadcastChannel<Boolean?>(null)
    private val validationPhotoLink = ConflatedBroadcastChannel<Boolean?>(null)
    private val progressFetchUserDetails = Channel<Boolean>(Channel.BUFFERED)
    private val progressUpdateUser = Channel<Boolean>(Channel.BUFFERED)
    private val progressDeleteUser = Channel<Boolean>(Channel.BUFFERED)
    private val progressBanAccount = Channel<Boolean>(Channel.BUFFERED)

    val state = object : ViewState {
        override fun progressFetchUserDetails(): Flow<Boolean> =
            progressFetchUserDetails
                .consumeAsFlow()

        override fun userDetails(): Flow<User> =
            userDetails.asFlow()

        override fun validationDisplayName(): Flow<Boolean> =
            validationDisplayName.asFlow()
                .filterNotNull()

        override fun validationHandleName(): Flow<Boolean> =
            validationHandleName.asFlow()
                .filterNotNull()

        override fun validationProfileLink(): Flow<Boolean> =
            validationProfileLink.asFlow()
                .filterNotNull()

        override fun validationPhotoLink(): Flow<Boolean> =
            validationPhotoLink.asFlow()
                .filterNotNull()

        override fun enableSave(): Flow<Boolean> =
            validationDisplayName.asFlow()
                .filterNotNull()

        override fun progressUpdateUser(): Flow<Boolean> =
            progressUpdateUser
                .consumeAsFlow()

        override fun progressDeleteUser(): Flow<Boolean> =
            progressDeleteUser
                .consumeAsFlow()

        override fun progressBanAccount(): Flow<Boolean> =
            progressBanAccount
                .consumeAsFlow()
    }

    private val _effect = Channel<ViewEffect>(Channel.BUFFERED)
    val effect: Flow<ViewEffect>
        get() = _effect
            .consumeAsFlow()

    init {
        // Display Name Validation
        displayName
            .asFlow()
            .filterNotNull()
            .map {
                /*Regex(AccountSettingsViewModel.REGEX_DISPLAYNAME).containsMatchIn(it)*/
                true
            }
            .onEach { isValid ->
                validationDisplayName.send(isValid)
            }
            .launchIn(viewModelScope)

        // Handlename Validation
        handleName
            .asFlow()
            .filterNotNull()
            .map {
                Regex(AccountSettingsViewModel.REGEX_HANDLENAME).containsMatchIn(it)
            }
            .onEach { isValid ->
                validationHandleName.send(isValid)
            }
            .launchIn(viewModelScope)

        // Profile Link URL Validation
        profileLink
            .asFlow()
            .filterNotNull()
            .map { Regex(AccountSettingsViewModel.REGEX_IMAGE_URL).containsMatchIn(it) }
            .onEach { isValid ->
                validationProfileLink.send(isValid)
            }
            .launchIn(viewModelScope)

        // Photo Link URL Validation
        photoLink
            .asFlow()
            .filterNotNull()
            .map { Regex(AccountSettingsViewModel.REGEX_IMAGE_URL).containsMatchIn(it) }
            .onEach { isValid ->
                validationPhotoLink.send(isValid)
            }
            .launchIn(viewModelScope)
    }

    fun fetchUserDetails() {
        viewModelScope.launch {
            performGetUserDetails()
        }
    }

    private suspend fun performGetUserDetails() {
        try {
            // DISPLAY Progress Indicator
            progressFetchUserDetails.send(true)

            val response = withContext(Dispatchers.IO) {
                userClient.getUserDetails(
                    userId = preferences.currentUser?.userid!!
                )
            }

            // EMIT Success
            userDetails.send(response)
            // Then, persist to app preferences
            preferences.currentUser = response

        } catch (err: SportsTalkException) {
            // EMIT Error
            _effect.send(
                ViewEffect.ErrorGetUserDetails(err)
            )
        } finally {
            // HIDE Progress Indicator
            progressFetchUserDetails.send(false)
        }
    }

    fun displayName(displayName: String) {
        this.displayName.sendBlocking(displayName)
    }

    fun handleName(handleName: String) {
        this.handleName.sendBlocking(handleName)
    }

    fun profileLink(profileLink: String) {
        this.profileLink.sendBlocking(profileLink)
    }

    fun photoLink(photoLink: String) {
        this.photoLink.sendBlocking(photoLink)
    }

    fun save() {
        viewModelScope.launch {
            performUpdateUser()
        }
    }

    private suspend fun performUpdateUser() {
        try {
            // DISPLAY Progress Indicator
            progressUpdateUser.send(true)

            val response = withContext(Dispatchers.IO) {
                userClient.createOrUpdateUser(
                    request = CreateUpdateUserRequest(
                        userid = preferences.currentUser?.userid!!,
                        displayname = displayName.value
                            ?: preferences.currentUser?.displayname,
                        handle = (handleName.value ?: preferences.currentUser?.handle)
                            ?.replaceFirst("@", ""),
                        profileurl = profileLink.value ?: preferences.currentUser?.profileurl,
                        pictureurl = photoLink.value ?: preferences.currentUser?.pictureurl
                    )
                )
            }

            // EMIT Success
            _effect.send(
                ViewEffect.SuccessUpdateUser(response)
            )
            // Then, persist to app preferences
            preferences.currentUser = response

        } catch (err: SportsTalkException) {
            // EMIT Error
            _effect.send(
                ViewEffect.ErrorUpdateUser(err)
            )
        } finally {
            // HIDE Progress Indicator
            progressUpdateUser.send(false)
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            performDeleteAccount()
        }
    }

    private suspend fun performDeleteAccount() {
        try {
            // DISPLAY Progress Indicator
            progressDeleteUser.send(true)

            val response = withContext(Dispatchers.IO) {
                userClient.deleteUser(
                    userId = preferences.currentUser?.userid!!
                )
            }

            // EMIT Success
            _effect.send(
                ViewEffect.SuccessDeleteAccount(response)
            )
            // Clear app preferences
            preferences.currentUser = null

        } catch (err: SportsTalkException) {
            // EMIT Error
            _effect.send(
                ViewEffect.ErrorDeleteAccount(err)
            )
        } finally {
            // HIDE Progress Indicator
            progressDeleteUser.send(false)
        }
    }

    fun banRestoreAccount() {
        viewModelScope.launch {
            performBanRestoreAccount()
        }
    }

    private suspend fun performBanRestoreAccount() {
        try {
            // DISPLAY Progress Indicator
            progressBanAccount.send(true)

            val response = withContext(Dispatchers.IO) {
                userClient.setBanStatus(
                    userId = preferences.currentUser?.userid!!,
                    // `true` - if not yet banned
                    // `false` - if already banned
                    applyeffect = !(preferences.currentUser?.banned ?: false)
                )
            }

            // EMIT Success
            _effect.send(
                ViewEffect.SuccessBanRestoreAccount(response)
            )
            // Then, persist to app preferences
            preferences.currentUser = response

        } catch (err: SportsTalkException) {
            // EMIT Error
            _effect.send(
                ViewEffect.ErrorBanAccount(err)
            )
        } finally {
            // HIDe Progress Indicator
            progressBanAccount.send(false)
        }
    }

    interface ViewState {
        /**
         * Emits [true] if Get User Details Operation is in-progress. Emits [false] when done.
         */
        fun progressFetchUserDetails(): Flow<Boolean>

        /**
         * Emits [User] instance from Get User Details Operation response.
         */
        fun userDetails(): Flow<User>

        /**
         * Emits [true] if display name value is valid. Otherwise, emits [false].
         */
        fun validationDisplayName(): Flow<Boolean>

        /**
         * Emits [true] if handlename value is valid. Otherwise, emits [false].
         */
        fun validationHandleName(): Flow<Boolean>

        /**
         * Emits [true] if Profile Link value is valid. Otherwise, emits [false].
         */
        fun validationProfileLink(): Flow<Boolean>

        /**
         * Emits [true] if Photo Link value is valid. Otherwise, emits [false].
         */
        fun validationPhotoLink(): Flow<Boolean>

        /**
         * Emits [true] if display name and handle are valid. Otherwise, emits [false].
         */
        fun enableSave(): Flow<Boolean>

        /**
         * Emits [true] if Update User Operation is in-progress. Emits [false] when done.
         */
        fun progressUpdateUser(): Flow<Boolean>

        /**
         * Emits [true] if Update User Operation is in-progress. Emits [false] when done.
         */
        fun progressDeleteUser(): Flow<Boolean>

        /**
         * Emits [true] if Ban Account Operation is in-progress. Emits [false] when done.
         */
        fun progressBanAccount(): Flow<Boolean>

    }

    sealed class ViewEffect {
        class ErrorGetUserDetails(val err: SportsTalkException) : ViewEffect()
        class SuccessUpdateUser(val user: User) : ViewEffect()
        class ErrorUpdateUser(val err: SportsTalkException) : ViewEffect()
        class SuccessDeleteAccount(val response: DeleteUserResponse) : ViewEffect()
        class ErrorDeleteAccount(val err: SportsTalkException) : ViewEffect()
        class SuccessBanRestoreAccount(val user: User) : ViewEffect()
        class ErrorBanAccount(val err: SportsTalkException) : ViewEffect()
    }

    companion object {
        private val REGEX_DISPLAYNAME = "^(([a-zA-Z]+)(\\s)*([a-zA-Z0-9]+\$)?){4,}"
        private val REGEX_HANDLENAME = "^@?(?=[a-zA-Z0-9._]{4,}\$)(?!.*[_.]{2})[^_.].*[^_.]\$"
        private val REGEX_IMAGE_URL = "(http(s?):)([/|.|\\w|\\s|-])*"
    }

}