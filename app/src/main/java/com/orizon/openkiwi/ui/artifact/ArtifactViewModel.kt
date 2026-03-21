package com.orizon.openkiwi.ui.artifact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.model.ChatArtifact
import com.orizon.openkiwi.data.repository.ArtifactRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ArtifactViewModel(repository: ArtifactRepository) : ViewModel() {
    val artifacts: StateFlow<List<ChatArtifact>> = repository.getAllArtifacts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    class Factory(private val repository: ArtifactRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ArtifactViewModel(repository) as T
        }
    }
}
