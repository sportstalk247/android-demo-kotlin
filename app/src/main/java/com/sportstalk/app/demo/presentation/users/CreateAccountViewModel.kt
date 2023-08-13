package com.sportstalk.app.demo.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.coroutine.api.UserClient
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.datamodels.SportsTalkException
import com.sportstalk.datamodels.users.CreateUpdateUserRequest
import com.sportstalk.datamodels.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class CreateAccountViewModel(
    /*
    * Typical use case to access SDK client instance:
    *   val config = ClientConfig(appId = "...", apiToken = "...", endpoint = "...")
    *   // Instantiate via Factory
    *   val userClient = SportsTalk247.UserClient(config = config)
    */
    val userClient: UserClient,
    val preferences: SportsTalkDemoPreferences
) : ViewModel() {

    private val displayName = MutableStateFlow<String?>(null)
    private val handleName = MutableStateFlow<String?>(null)
    private val profileLink = MutableStateFlow<String?>(null)
    private val photoLink = MutableStateFlow<String?>(null)

    private val validationDisplayName = MutableStateFlow/*Channel*/<Boolean>(false/*Channel.RENDEZVOUS*/)
    private val validationHandleName = MutableStateFlow/*Channel*/<Boolean>(false/*Channel.RENDEZVOUS*/)
    private val validationProfileLink = MutableStateFlow/*Channel*/<Boolean>(false/*Channel.RENDEZVOUS*/)
    private val validationPhotoLink = MutableStateFlow/*Channel*/<Boolean>(false/*Channel.RENDEZVOUS*/)
    private val progressCreateUser = MutableStateFlow/*Channel*/<Boolean>(false/*Channel.RENDEZVOUS*/)

    val state = object : ViewState {
        override fun validationDisplayName(): Flow<Boolean> =
            validationDisplayName
                /*.consumeAsFlow()*/
                .asStateFlow()

        override fun validationHandleName(): Flow<Boolean> =
            validationHandleName
                /*.consumeAsFlow()*/
                .asStateFlow()

        override fun validationProfileLink(): Flow<Boolean> =
            validationProfileLink
                /*.consumeAsFlow()*/
                .asStateFlow()

        override fun validationPhotoLink(): Flow<Boolean> =
            validationPhotoLink
                /*.consumeAsFlow()*/
                .asStateFlow()

        override fun enableSubmit(): Flow<Boolean> =
            /*combine(
                validationDisplayName.consumeAsFlow(),
                validationHandleName.consumeAsFlow()
            ) { validDisplayName, validHandle -> validDisplayName && validHandle }*/
            validationDisplayName.asStateFlow()

        override fun progressCreateUser(): Flow<Boolean> =
            progressCreateUser
                /*.receiveAsFlow()*/
                .asStateFlow()
    }

    private val _effect = Channel<ViewEffect>(Channel.RENDEZVOUS)
    val effect: Flow<ViewEffect>
        get() = _effect.receiveAsFlow()

    init {
        // Display Name Validation
        displayName
            .asStateFlow()
            .map {
                Regex(REGEX_DISPLAYNAME).containsMatchIn(it ?: "")
            }
            .onEach { isValid ->
                validationDisplayName.emit(isValid)
            }
            .launchIn(viewModelScope)

        // Handlename Validation
        handleName
            .asStateFlow()
            .map { Regex(REGEX_HANDLENAME).containsMatchIn(it ?: "") }
            .onEach { isValid ->
                validationHandleName.emit(isValid)
            }
            .launchIn(viewModelScope)

        // Profile Link URL Validation
        profileLink
            .asStateFlow()
            .map { Regex(REGEX_IMAGE_URL).containsMatchIn(it ?: "") }
            .onEach { isValid ->
                validationProfileLink.emit(isValid)
            }
            .launchIn(viewModelScope)

        // Photo Link URL Validation
        photoLink
            .asStateFlow()
            .map { Regex(REGEX_IMAGE_URL).containsMatchIn(it ?: "") }
            .onEach { isValid ->
                validationPhotoLink.emit(isValid)
            }
            .launchIn(viewModelScope)

    }

    fun displayName(displayName: String) =
        this.displayName.tryEmit(displayName)

    fun handleName(handleName: String) =
        this.handleName.tryEmit(handleName)

    fun profileLink(profileLink: String) =
        this.profileLink.tryEmit(profileLink)

    fun photoLink(photoLink: String) =
        this.photoLink.tryEmit(photoLink)

    fun submit() {
        viewModelScope.launch {
            performCreateUser()
        }
    }

    private suspend fun performCreateUser() {
        try {
            // DISPLAY Progress Indicator
            this.progressCreateUser.emit(true)

            val response = withContext(Dispatchers.IO) {
                userClient.createOrUpdateUser(
                    request = CreateUpdateUserRequest(
                        userid = UUID.randomUUID().toString(),
                        displayname = displayName.value ?: "",
                        handle = handleName.value
                            ?.replaceFirst("@", "")
                            ?: "",
                        profileurl = profileLink.value ?: "",
                        pictureurl = photoLink.value ?: ""
                    )
                )
            }

            // Store to app preference the created user
            preferences.currentUser = response

            // EMIT Success Created User
            this._effect.send(
                ViewEffect.SuccessCreateUser(response)
            )

        } catch (err: SportsTalkException) {
            // EMIT Error
            this._effect.send(ViewEffect.ErrorCreateUser(err))
        } finally {
            // HIDE Progress Indicator
            this.progressCreateUser.emit(false)
        }

    }

    interface ViewState {
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
        fun enableSubmit(): Flow<Boolean>

        /**
         * Emits [true] upon start Create User operation. Emits [false] when done.
         */
        fun progressCreateUser(): Flow<Boolean>
    }

    sealed class ViewEffect {
        data class SuccessCreateUser(val user: User) : ViewEffect()
        data class ErrorCreateUser(val err: SportsTalkException) : ViewEffect()
    }

    companion object {
        private val REGEX_DISPLAYNAME = "^(([a-zA-Z]+)(\\s)*([a-zA-Z0-9]+\$)?){4,}"
        private val REGEX_HANDLENAME = "^@?(?=[a-zA-Z0-9._]{4,}\$)(?!.*[_.]{2})[^_.].*[^_.]\$"
        private val REGEX_IMAGE_URL = "(http(s?):)([/|.|\\w|\\s|-])*"
    }

}