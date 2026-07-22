package com.stocksense.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ProcesadorImagenesViewModelFactory(
    private val openAiApiKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProcesadorImagenesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProcesadorImagenesViewModel(openAiApiKey) as T
        }
        throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
    }
}