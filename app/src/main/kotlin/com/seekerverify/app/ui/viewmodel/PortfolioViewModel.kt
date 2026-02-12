package com.seekerverify.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seekerverify.app.rpc.PriceClient
import com.seekerverify.app.rpc.SkrRpcClient
import com.seekerverify.app.rpc.StakingRpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    // SKR Balance
    private val _skrBalance = MutableStateFlow(0.0)
    val skrBalance: StateFlow<Double> = _skrBalance.asStateFlow()

    private val _skrRawBalance = MutableStateFlow(0L)
    val skrRawBalance: StateFlow<Long> = _skrRawBalance.asStateFlow()

    // Staking
    private val _stakedAmount = MutableStateFlow(0.0)
    val stakedAmount: StateFlow<Double> = _stakedAmount.asStateFlow()

    private val _stakingRewards = MutableStateFlow(0.0)
    val stakingRewards: StateFlow<Double> = _stakingRewards.asStateFlow()

    private val _isStaked = MutableStateFlow(false)
    val isStaked: StateFlow<Boolean> = _isStaked.asStateFlow()

    private val _estimatedApy = MutableStateFlow(StakingRpcClient.estimateApy())
    val estimatedApy: StateFlow<Double> = _estimatedApy.asStateFlow()

    // Prices
    private val _skrPriceUsd = MutableStateFlow<Double?>(null)
    val skrPriceUsd: StateFlow<Double?> = _skrPriceUsd.asStateFlow()

    private val _solPriceUsd = MutableStateFlow<Double?>(null)
    val solPriceUsd: StateFlow<Double?> = _solPriceUsd.asStateFlow()

    // Total value in USD
    private val _totalValueUsd = MutableStateFlow<Double?>(null)
    val totalValueUsd: StateFlow<Double?> = _totalValueUsd.asStateFlow()

    // Loading / Error
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadPortfolio(walletAddress: String, rpcUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            Log.d(TAG, "Loading portfolio for ${walletAddress.take(8)}...")

            // Fetch SKR balance, staking, and prices in parallel
            val balanceJob = launch {
                SkrRpcClient.getSkrBalance(walletAddress, rpcUrl).fold(
                    onSuccess = { result ->
                        _skrBalance.value = result.displayAmount
                        _skrRawBalance.value = result.rawAmount
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Balance fetch failed: ${e.message}")
                    }
                )
            }

            val stakingJob = launch {
                StakingRpcClient.getStakingInfo(walletAddress, rpcUrl).fold(
                    onSuccess = { info ->
                        _stakedAmount.value = info.stakedDisplay
                        _stakingRewards.value = info.rewardsDisplay
                        _isStaked.value = info.isStaked
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Staking fetch failed: ${e.message}")
                    }
                )
            }

            val priceJob = launch {
                try {
                    val prices = PriceClient.getPrices()
                    _skrPriceUsd.value = prices.skrUsd
                    _solPriceUsd.value = prices.solUsd
                } catch (e: Exception) {
                    Log.e(TAG, "Price fetch failed: ${e.message}")
                }
            }

            // Wait for all
            balanceJob.join()
            stakingJob.join()
            priceJob.join()

            // Calculate total USD value
            _skrPriceUsd.value?.let { price ->
                val totalSkr = _skrBalance.value + _stakedAmount.value + _stakingRewards.value
                _totalValueUsd.value = totalSkr * price
            }

            _isLoading.value = false
            Log.d(TAG, "Portfolio loaded: ${_skrBalance.value} SKR liquid, " +
                "${_stakedAmount.value} staked, $$${_totalValueUsd.value} total")
        }
    }

    companion object {
        private const val TAG = "SeekerVerify"
    }
}
