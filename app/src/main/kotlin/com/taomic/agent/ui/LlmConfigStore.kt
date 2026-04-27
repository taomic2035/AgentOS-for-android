package com.taomic.agent.ui

import android.content.Context
import com.taomic.agent.llm.LlmAdapter
import com.taomic.agent.llm.OpenAiCompatClient

/**
 * LLM 用户可配置项的存储。V0.1 用 SharedPreferences；V0.9 合规阶段切到
 * EncryptedSharedPreferences + Android KeyStore（api_key 不能明文）。
 *
 * 默认值取自 [LlmAdapter]（火山方舟 Doubao）。用户可在引导页 / 设置页修改。
 */
class LlmConfigStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("llm_config", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, LlmAdapter.DEFAULT_BASE_URL) ?: LlmAdapter.DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_BASE_URL, value).apply()
        }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value).apply()
        }

    var model: String
        get() = prefs.getString(KEY_MODEL, LlmAdapter.DEFAULT_MODEL) ?: LlmAdapter.DEFAULT_MODEL
        set(value) {
            prefs.edit().putString(KEY_MODEL, value).apply()
        }

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    /** 根据当前配置创建 LlmClient 实例。 */
    fun createClient(): OpenAiCompatClient = OpenAiCompatClient(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
    )

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
    }
}
