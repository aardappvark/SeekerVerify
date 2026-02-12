package com.seekerverify.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.engine.PredictorEngine
import com.seekerverify.app.model.AirdropTier
import com.seekerverify.app.rpc.ActivityRpcClient
import com.seekerverify.app.rpc.StakingRpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PredictorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    private val _result = MutableStateFlow<PredictorEngine.PredictorResult?>(null)
    val result: StateFlow<PredictorEngine.PredictorResult?> = _result.asStateFlow()

    private val _metrics = MutableStateFlow<PredictorEngine.ActivityMetrics?>(null)
    val metrics: StateFlow<PredictorEngine.ActivityMetrics?> = _metrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun runPrediction(walletAddress: String, rpcUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            Log.d(TAG, "Running prediction for ${walletAddress.take(8)}...")

            try {
                // Check staking status
                var isStaked = false
                StakingRpcClient.getStakingInfo(walletAddress, rpcUrl).fold(
                    onSuccess = { info -> isStaked = info.isStaked },
                    onFailure = { }
                )

                // Gather activity metrics
                val activityMetrics = ActivityRpcClient.getActivityMetrics(
                    walletAddress = walletAddress,
                    rpcUrl = rpcUrl,
                    isStaked = isStaked,
                    hasSkrDomain = false // TODO: integrate domain check
                )

                _metrics.value = activityMetrics

                // Run predictor
                val prediction = PredictorEngine.predict(activityMetrics)
                _result.value = prediction

                Log.d(TAG, "Prediction complete: ${prediction.predictedTier.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Prediction failed: ${e.message}", e)
                _error.value = "Prediction failed: ${e.message}"
            }

            _isLoading.value = false
        }
    }

    companion object {
        private const val TAG = "SeekerVerify"
    }
}
