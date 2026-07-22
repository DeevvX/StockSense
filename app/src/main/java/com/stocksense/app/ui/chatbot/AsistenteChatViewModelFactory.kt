package com.stocksense.app.ui.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AsistenteChatViewModelFactory(
    private val openAiApiKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AsistenteChatViewModel(openAiApiKey) as T
    }
}