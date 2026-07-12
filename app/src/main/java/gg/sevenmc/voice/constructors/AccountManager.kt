package gg.sevenmc.voice.constructors

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

object AccountManager {
    val accounts = mutableListOf<Account>()
    var selectedAccount: Account? = null
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("account_manager", Context.MODE_PRIVATE)
        load()
    }

    fun selectAccount(account: Account) {
        selectedAccount = account
    }

    fun save() {
        prefs.edit().putString("accounts", gson.toJson(accounts)).apply()
    }

    private fun load() {
        val json = prefs.getString("accounts", null) ?: return
        val list = gson.fromJson(json, Array<Account>::class.java).toList()
        accounts.addAll(list)
        if (accounts.isNotEmpty()) selectedAccount = accounts.last()
    }
}
