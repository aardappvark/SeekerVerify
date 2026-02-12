package com.seekerverify.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seekerverify.app.rpc.PriceClient
import com.seekerverify.app.rpc.SkrRpcClient
import com.seekerverify.app.rpc.SolRpcClient
import com.seekerverify.app.rpc.StakingRpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    // SOL Balance
    private val _solBalance = MutableStateFlow(0.0)
    val solBalance: StateFlow<Double> = _solBalance.asStateFlow()

    private val _stakedSol = MutableStateFlow(0.0)
    val stakedSol: StateFlow<Double> = _stakedSol.asStateFlow()

    private val _stakeAccountCount = MutableStateFlow(0)
    val stakeAccountCount: StateFlow<Int> = _stakeAccountCount.asStateFlow()

    // SKR Balance
    private val _skrBalance = MutableStateFlow(0.0)
    val skrBalance: StateFlow<Double> = _skrBalance.asStateFlow()

    private val _skrRawBalance = MutableStateFlow(0L)
    val skrRawBalance: StateFlow<Long> = _skrRawBalance.asStateFlow()

    // SKR Staking
    private val _stakedSkr = MutableStateFlow(0.0)
    val stakedSkr: StateFlow<Double> = _stakedSkr.asStateFlow()

    private val _cooldownSkr = MutableStateFlow(0.0)
    val cooldownSkr: StateFlow<Double> = _cooldownSkr.asStateFlow()

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

            // Fetch SOL, SKR balance, staking, and prices in parallel
            val solJob = launch {
                SolRpcClient.getSolBalance(walletAddress, rpcUrl).fold(
                    onSuccess = { info ->
                        _solBalance.value = info.solBalance
                        _stakedSol.value = info.stakedSol
                        _stakeAccountCount.value = info.stakeAccounts
                    },
                    onFailure = { e ->
                        Log.e(TAG, "SOL balance fetch failed: ${e.message}")
                    }
                )
            }

            val balanceJob = launch {
                SkrRpcClient.getSkrBalance(walletAddress, rpcUrl).fold(
                    onSuccess = { result ->
                        _skrBalance.value = result.displayAmount
                        _skrRawBalance.value = result.rawAmount
                    },
                    onFailure = { e ->
                        Log.e(TAG, "SKR balance fetch failed: ${e.message}")
                    }
                )
            }

            val stakingJob = launch {
                StakingRpcClient.getStakingInfo(walletAddress, rpcUrl).fold(
                    onSuccess = { info ->
                        _stakedSkr.value = info.stakedDisplay
                        _cooldownSkr.value = info.cooldownDisplay
                        _isStaked.value = info.isStaked
                    },
                    onFailure = { e ->
                        Log.e(TAG, "SKR staking fetch failed: ${e.message}")
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
            solJob.join()
            balanceJob.join()
            stakingJob.join()
            priceJob.join()

            // Calculate total USD value
            var totalUsd = 0.0
            _solPriceUsd.value?.let { solPrice ->
                totalUsd += (_solBalance.value + _stakedSol.value) * solPrice
            }
            _skrPriceUsd.value?.let { skrPrice ->
                totalUsd += (_skrBalance.value + _stakedSkr.value + _cooldownSkr.value) * skrPrice
            }
            if (totalUsd > 0) {
                _totalValueUsd.value = totalUsd
            }

            _isLoading.value = false
            Log.d(TAG, "Portfolio loaded: ${_solBalance.value} SOL, ${_stakedSol.value} staked SOL, " +
                "${_skrBalance.value} SKR liquid, ${_stakedSkr.value} SKR staked, " +
                "$$${_totalValueUsd.value} total")
        }
    }

    companion object {
        private const val TAG = "SeekerVerify"
    }
}
