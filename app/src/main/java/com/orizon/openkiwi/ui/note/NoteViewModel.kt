package com.orizon.openkiwi.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.data.local.dao.NoteDao
import com.orizon.openkiwi.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NoteViewModel(private val noteDao: NoteDao) : ViewModel() {

    val pendingNotes: StateFlow<List<NoteEntity>> = noteDao
        .getNotesByStatus("pending")
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val processedNotes: StateFlow<List<NoteEntity>> = noteDao
        .getNotesByStatus("processed")
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun markProcessed(id: Long) {
        viewModelScope.launch { noteDao.updateStatus(id, "processed") }
    }

    fun dismiss(id: Long) {
        viewModelScope.launch { noteDao.updateStatus(id, "dismissed") }
    }

    fun delete(id: Long) {
        viewModelScope.launch { noteDao.deleteNote(id) }
    }

    fun clearProcessed() {
        viewModelScope.launch { noteDao.deleteByStatus("processed") }
    }

    fun clearDismissed() {
        viewModelScope.launch { noteDao.deleteByStatus("dismissed") }
    }

    fun clearOld(daysAgo: Int = 7) {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - daysAgo * 86_400_000L
            noteDao.deleteNotesBefore(cutoff)
        }
    }

    class Factory(private val noteDao: NoteDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NoteViewModel(noteDao) as T
    }
}
