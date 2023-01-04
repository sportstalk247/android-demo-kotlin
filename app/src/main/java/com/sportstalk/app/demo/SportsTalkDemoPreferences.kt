package com.sportstalk.app.demo

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sportstalk.datamodels.users.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json

class SportsTalkDemoPreferences(
    context: Context,
    private val json: Json
) {

    private val preferences = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)

    private val ORIGINAL_URL_ENDPOINT = context.getString(R.string.sportstalk247_urlEndpoint)
    private val ORIGINAL_AUTH_TOKEN = context.getString(R.string.sportstalk247_authToken)
    private val ORIGINAL_APP_ID = context.getString(R.string.sportstalk247_appid)

    var currentUser: User?
        get() {
            return preferences.getString(KEY_CURRENT_USER, "")!!
                .takeIf { it.isNotEmpty() }
                ?.let { usrStr ->
                    json.decodeFromString(User.serializer(), usrStr)
                }
        }
        set(value) {
            preferences.edit(true) {
                value?.let { _value ->
                    putString(KEY_CURRENT_USER, json.encodeToString(User.serializer(), _value))
                } ?: run {
                    putString(KEY_CURRENT_USER, null)
                }
            }
        }

    var urlEndpoint: String?
        get() {
            return preferences.getString(KEY_URL_ENDPOINT, "")!!.takeIf { it.isNotEmpty() }
                ?: ORIGINAL_URL_ENDPOINT
        }
    set(value) {
        preferences.edit(true) {
            putString(KEY_URL_ENDPOINT, value)
        }
    }

    fun urlEndpointChanges(): Flow<String?> = callbackFlow {
        // Preference Value Change Listener
        val onSharedPrefChanges = SharedPreferences.OnSharedPreferenceChangeListener { pref, key ->
            if(key == KEY_URL_ENDPOINT) {
                trySendBlocking(
                    pref.getString(key, "")!!.takeIf { it.isNotEmpty() }
                        ?: ORIGINAL_URL_ENDPOINT
                )
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(onSharedPrefChanges)
        // Emit initial value upon subscribe
        send(urlEndpoint)
        // Unregister on await close
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(onSharedPrefChanges) }
    }

    var authToken: String?
        get() {
            return preferences.getString(KEY_AUTH_TOKEN, "")!!.takeIf { it.isNotEmpty() }
                ?: ORIGINAL_AUTH_TOKEN
        }
    set(value) {
        preferences.edit(true) {
            putString(KEY_AUTH_TOKEN, value)
        }
    }
    fun authTokenChanges(): Flow<String?> = callbackFlow {
        // Preference Value Change Listener
        val onSharedPrefChanges = SharedPreferences.OnSharedPreferenceChangeListener { pref, key ->
            if(key == KEY_AUTH_TOKEN) {
                trySendBlocking(
                    pref.getString(key, "")!!.takeIf { it.isNotEmpty() }
                        ?: ORIGINAL_AUTH_TOKEN
                )
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(onSharedPrefChanges)
        // Emit initial value upon subscribe
        send(authToken)
        // Unregister on await close
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(onSharedPrefChanges) }
    }

    var appId: String?
        get() {
            return preferences.getString(KEY_APP_ID, "")!!.takeIf { it.isNotEmpty() }
                ?: ORIGINAL_APP_ID
        }
    set(value) {
        preferences.edit(true) {
            putString(KEY_APP_ID, value)
        }
    }
    fun appIdChanges(): Flow<String?> = callbackFlow {
        // Preference Value Change Listener
        val onSharedPrefChanges = SharedPreferences.OnSharedPreferenceChangeListener { pref, key ->
            if(key == KEY_APP_ID) {
                trySendBlocking(
                    pref.getString(key, "")!!.takeIf { it.isNotEmpty() }
                        ?: ORIGINAL_APP_ID
                )
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(onSharedPrefChanges)
        // Emit initial value upon subscribe
        send(appId)
        // Unregister on await close
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(onSharedPrefChanges) }
    }

    fun clear() {
        preferences.edit(true) {
            putString(KEY_URL_ENDPOINT, ORIGINAL_URL_ENDPOINT)
            putString(KEY_AUTH_TOKEN, ORIGINAL_AUTH_TOKEN)
            putString(KEY_APP_ID, ORIGINAL_APP_ID)
        }
    }

    companion object {
        const val KEY_CURRENT_USER = "currentUser"
        const val KEY_URL_ENDPOINT = "urlEndpoint"
        const val KEY_AUTH_TOKEN = "authToken"
        const val KEY_APP_ID = "appId"
    }
}