package com.sportstalk.app.demo

import android.content.Context
import androidx.core.content.edit
import com.sportstalk.models.users.User
import kotlinx.serialization.json.Json

class SportsTalkDemoPreferences(
    context: Context,
    private val json: Json
) {

    private val preferences = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)

    var currentUser: User?
        get() {
            return preferences.getString(KEY_CURRENT_USER, "")!!
                .takeIf { it.isNotEmpty() }
                ?.let { usrStr ->
                    json.parse(User.serializer(), usrStr)
                }
        }
        set(value) {
            value?.let { _value ->
                preferences.edit(true) {
                    putString(KEY_CURRENT_USER, json.stringify(User.serializer(), _value))
                }
            }
        }

    companion object {
        const val KEY_CURRENT_USER = "currentUser"
    }
}