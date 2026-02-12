package com.seekerverify.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.rpc.CommunityRpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommunityViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    private val _totalSeekers = MutableStateFlow(0L)
    val totalSeekers: StateFlow<Long> = _totalSeekers.asStateFlow()

    private val _userPosition = MutableStateFlow<Long?>(null)
    val userPosition: StateFlow<Long?> = _userPosition.asStateFlow()

    private val _percentile = MutableStateFlow<Double?>(null)
    val percentile: StateFlow<Double?> = _percentile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadCommunity(walletAddress: String, rpcUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true

            val memberNumber = prefs.getMemberNumber()
            _userPosition.value = memberNumber

            CommunityRpcClient.getCommunityStats(memberNumber, rpcUrl).fold(
                onSuccess = { stats ->
                    _totalSeekers.value = stats.totalSeekers

                    // Calculate percentile (lower member number = earlier adopter)
                    memberNumber?.let { num ->
                        if (stats.totalSeekers > 0) {
                            val pct = (1.0 - (num.toDouble() / stats.totalSeekers)) * 100
                            _percentile.value = pct.coerceIn(0.0, 100.0)
                        }
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Community load failed: ${e.message}")
                }
            )

            _isLoading.value = false
        }
    }

    companion object {
        private const val TAG = "SeekerVerify"
    }
}
