package com.olrak.ispeed.app.shared.local

import com.olrak.ispeed.app.shared.data.TrackInternetModel
import javax.inject.Inject

class DatabaseUseCase @Inject constructor(
    private val localRepository: LocalDatabaseRepository
) {
    suspend fun getInternetStatusList(count: Int) = localRepository.getInternetStatusList(count)

    suspend fun getAllTrackList() = localRepository.getAllTrackList()

    suspend fun getRowCount() = localRepository.getRowCount()

    fun saveInternetStatus(trackInternetModel: TrackInternetModel) = localRepository.saveInternetStatus(trackInternetModel)
}