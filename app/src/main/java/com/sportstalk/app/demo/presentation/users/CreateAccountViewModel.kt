package com.sportstalk.app.demo.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.api.UserClient
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import com.sportstalk.models.SportsTalkException
import com.sportstalk.models.users.CreateUpdateUserRequest
import com.sportstalk.models.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
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

    private val displayName = ConflatedBroadcastChannel<String>()
    private val handleName = ConflatedBroadcastChannel<String>()
    private val profileLink = ConflatedBroadcastChannel<String>()
    private val photoLink = ConflatedBroadcastChannel<String>()

    private val validationDisplayName = Channel<Boolean>(Channel.RENDEZVOUS)
    private val validationHandleName = Channel<Boolean>(Channel.RENDEZVOUS)
    private val validationProfileLink = Channel<Boolean>(Channel.RENDEZVOUS)
    private val validationPhotoLink = Channel<Boolean>(Channel.RENDEZVOUS)
    private val progressCreateUser = Channel<Boolean>(Channel.RENDEZVOUS)

    val state = object : ViewState {
        override fun validationDisplayName(): Flow<Boolean> =
            validationDisplayName
                .consumeAsFlow()

        override fun validationHandleName(): Flow<Boolean> =
            validationHandleName
                .consumeAsFlow()

        override fun validationProfileLink(): Flow<Boolean> =
            validationProfileLink
                .consumeAsFlow()

        override fun validationPhotoLink(): Flow<Boolean> =
            validationPhotoLink
                .consumeAsFlow()

        override fun enableSubmit(): Flow<Boolean> =
            /*combine(
                validationDisplayName.consumeAsFlow(),
                validationHandleName.consumeAsFlow()
            ) { validDisplayName, validHandle -> validDisplayName && validHandle }*/
            validationDisplayName.consumeAsFlow()
                .onStart { emit(false) }

        override fun progressCreateUser(): Flow<Boolean> =
            progressCreateUser
                .receiveAsFlow()
    }

    private val _effect = Channel<ViewEffect>(Channel.RENDEZVOUS)
    val effect: Flow<ViewEffect>
        get() = _effect.receiveAsFlow()

    init {
        // Display Name Validation
        displayName
            .asFlow()
            .map {
                Regex(REGEX_DISPLAYNAME).containsMatchIn(it)
            }
            .onEach { isValid ->
                validationDisplayName.send(isValid)
            }
            .launchIn(viewModelScope)

        // Handlename Validation
        handleName
            .asFlow()
            .map { Regex(REGEX_HANDLENAME).containsMatchIn(it) }
            .onEach { isValid ->
                validationHandleName.send(isValid)
            }
            .launchIn(viewModelScope)

        // Profile Link URL Validation
        profileLink
            .asFlow()
            .map { Regex(REGEX_IMAGE_URL).containsMatchIn(it) }
            .onEach { isValid ->
                validationProfileLink.send(isValid)
            }
            .launchIn(viewModelScope)

        // Photo Link URL Validation
        photoLink
            .asFlow()
            .map { Regex(REGEX_IMAGE_URL).containsMatchIn(it) }
            .onEach { isValid ->
                validationPhotoLink.send(isValid)
            }
            .launchIn(viewModelScope)

    }

    fun displayName(displayName: String) =
        this.displayName.sendBlocking(displayName)

    fun handleName(handleName: String) =
        this.handleName.sendBlocking(handleName)

    fun profileLink(profileLink: String) =
        this.profileLink.sendBlocking(profileLink)

    fun photoLink(photoLink: String) =
        this.photoLink.sendBlocking(photoLink)

    fun submit() {
        viewModelScope.launch {
            performCreateUser()
        }
    }

    private suspend fun performCreateUser() {
        try {
            // DISPLAY Progress Indicator
            this.progressCreateUser.send(true)

            val response = withContext(Dispatchers.IO) {
                userClient.createOrUpdateUser(
                    request = CreateUpdateUserRequest(
                        userid = UUID.randomUUID().toString(),
                        displayname = displayName.valueOrNull ?: "",
                        handle = handleName.valueOrNull
                            ?.replaceFirst("@", "")
                            ?: "",
                        profileurl = profileLink.valueOrNull ?: "",
                        pictureurl = photoLink.valueOrNull ?: ""
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
            this.progressCreateUser.send(false)
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