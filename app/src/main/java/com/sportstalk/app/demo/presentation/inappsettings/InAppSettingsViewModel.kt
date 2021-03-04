package com.sportstalk.app.demo.presentation.inappsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportstalk.app.demo.SportsTalkDemoPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class InAppSettingsViewModel(
    private val preferences: SportsTalkDemoPreferences
): ViewModel() {

    private val urlEndpoint = ConflatedBroadcastChannel<String?>(preferences.urlEndpoint)
    private val authToken = ConflatedBroadcastChannel<String?>(preferences.authToken)
    private val appId = ConflatedBroadcastChannel<String?>(preferences.appId)
    val state = object: ViewState {
        override fun urlEndpoint(): Flow<String?> =
            urlEndpoint.asFlow()

        override fun authToken(): Flow<String?> =
            authToken.asFlow()

        override fun appId(): Flow<String?> =
            appId.asFlow()
    }

    private val _effect = Channel<ViewEffect>(Channel.BUFFERED)
    val effect: Flow<ViewEffect> =
        _effect.consumeAsFlow()

    init {
        preferences.urlEndpointChanges()
            .onEach { urlEndpoint.send(it) }
            .launchIn(viewModelScope)

        preferences.authTokenChanges()
            .onEach { authToken.send(it) }
            .launchIn(viewModelScope)

        preferences.appIdChanges()
            .onEach { appId.send(it) }
            .launchIn(viewModelScope)
    }

    fun urlEndpoint(urlEndpoint: String?) {
        preferences.urlEndpoint = urlEndpoint
    }

    fun authToken(authToken: String?) {
        preferences.authToken = authToken
    }

    fun appId(appId: String?) {
        preferences.appId = appId
    }

    fun reset() {
        // Clear operation automatically sets original values
        preferences.clear()
        // Emit [ViewEffect.SuccessReset]]
        viewModelScope.launch {
            _effect.send(ViewEffect.SuccessReset())
        }
    }

    interface ViewState {
        fun urlEndpoint(): Flow<String?>

        fun authToken(): Flow<String?>

        fun appId(): Flow<String?>
    }

    sealed class ViewEffect() {
        class SuccessReset(): ViewEffect()
    }

}