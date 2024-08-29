package com.olrak.ispeed.dashboard.screens.automatic_track

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.olrak.ispeed.app.shared.data.TrackInternetModel
import com.olrak.ispeed.app.shared.data.UserDetails
import com.olrak.ispeed.app.shared.domain.FirebaseUseCase
import com.olrak.ispeed.app.shared.extension.coRunCatching
import com.olrak.ispeed.app.shared.local.DatabaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomaticTrackViewModel @Inject constructor(
    private val firebaseUseCase: FirebaseUseCase,
    private val databaseUseCase: DatabaseUseCase,
    private val factory: InternetListFactory
) : ViewModel() {

    private val _userDetails = MutableLiveData<UserDetails>()

    val userDetails: LiveData<UserDetails> get() = _userDetails

    private val _uiState = MutableStateFlow<TrackState>(ShowTrackNoData)

    val uiItems = _uiState.asStateFlow()

    val totalCount = MutableLiveData(0)

    var isLoading = false

    init {
        getUserDetails()
    }

    private fun getUserDetails() {
        viewModelScope.launch {
            coRunCatching {
                firebaseUseCase.getUserDetails()
            }.onSuccess {
                _userDetails.value = it
            }.onFailure {
                _userDetails.value = null
            }
        }
    }

    fun getTrackInternetList(count: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            coRunCatching {
                databaseUseCase.getInternetStatusList(count = count)
            }.onSuccess {
                isLoading = false
                _uiState.value = GetTrackList(it)
            }.onFailure {
                isLoading = false
                _uiState.value = ShowTrackDismissLoading
            }
        }
    }

    fun getTotalTrackCount() {
        viewModelScope.launch {
            coRunCatching {
                databaseUseCase.getRowCount()
            }.onSuccess {
                it?.let {
                    totalCount.value = it
                }
            }
        }
    }

    fun getUiItems(response: MutableList<TrackInternetModel>?) {
        factory.createOverview(data = response).also { uiItems ->
            _uiState.value = GetUiItems(uiItems)
        }
    }
}