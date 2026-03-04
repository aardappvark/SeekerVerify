package com.seekerverify.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.model.PortfolioCache
import com.seekerverify.app.model.SharePriceSnapshot
import com.seekerverify.app.model.TransactionRecord
import com.seekerverify.app.rpc.PriceClient
import com.seekerverify.app.rpc.SkrRpcClient
import com.seekerverify.app.rpc.SolRpcClient
import com.seekerverify.app.rpc.StakingRpcClient
import com.seekerverify.app.widget.SeekerWidgetProvider
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

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

    // Staking P&L (from on-chain cost_basis = share_price at stake time)
    private val _originalDeposit = MutableStateFlow(0.0)
    val originalDeposit: StateFlow<Double> = _originalDeposit.asStateFlow()

    private val _stakingRewards = MutableStateFlow(0.0)
    val stakingRewards: StateFlow<Double> = _stakingRewards.asStateFlow()

    private val _stakingPnlPercent = MutableStateFlow(0.0)
    val stakingPnlPercent: StateFlow<Double> = _stakingPnlPercent.asStateFlow()

    // Estimated yield projections (APY-based)
    private val _estDailyYield = MutableStateFlow(0.0)
    val estDailyYield: StateFlow<Double> = _estDailyYield.asStateFlow()

    private val _estMonthlyYield = MutableStateFlow(0.0)
    val estMonthlyYield: StateFlow<Double> = _estMonthlyYield.asStateFlow()

    private val _estAnnualYield = MutableStateFlow(0.0)
    val estAnnualYield: StateFlow<Double> = _estAnnualYield.asStateFlow()

    private val _estYtdYield = MutableStateFlow(0.0)
    val estYtdYield: StateFlow<Double> = _estYtdYield.asStateFlow()

    // Prices
    private val _skrPriceUsd = MutableStateFlow<Double?>(null)
    val skrPriceUsd: StateFlow<Double?> = _skrPriceUsd.asStateFlow()

    private val _solPriceUsd = MutableStateFlow<Double?>(null)
    val solPriceUsd: StateFlow<Double?> = _solPriceUsd.asStateFlow()

    // Total value in USD
    private val _totalValueUsd = MutableStateFlow<Double?>(null)
    val totalValueUsd: StateFlow<Double?> = _totalValueUsd.asStateFlow()

    // Share price history for sparkline
    private val _sharePriceHistory = MutableStateFlow<List<SharePriceSnapshot>>(emptyList())
    val sharePriceHistory: StateFlow<List<SharePriceSnapshot>> = _sharePriceHistory.asStateFlow()

    // Recent on-chain transactions (parsed by PredictorViewModel, read from cache here)
    private val _transactionHistory = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val transactionHistory: StateFlow<List<TransactionRecord>> = _transactionHistory.asStateFlow()

    // Loading / Error
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Whether cached data was loaded
    private val _isCachedData = MutableStateFlow(false)
    val isCachedData: StateFlow<Boolean> = _isCachedData.asStateFlow()

    /**
     * Load cached portfolio data instantly from device storage.
     * Called before the network fetch to show data immediately.
     */
    fun loadCachedPortfolio() {
        val cache = prefs.getPortfolioCache() ?: return

        _solBalance.value = cache.solBalance
        _stakedSol.value = cache.stakedSol
        _skrBalance.value = cache.skrBalance
        _stakedSkr.value = cache.stakedSkr
        _cooldownSkr.value = cache.cooldownSkr
        _isStaked.value = cache.isStaked
        _solPriceUsd.value = cache.solPriceUsd
        _skrPriceUsd.value = cache.skrPriceUsd
        _totalValueUsd.value = cache.totalValueUsd
        _estimatedApy.value = cache.estimatedApy
        _originalDeposit.value = cache.costBasis
        _stakingRewards.value = cache.rewards

        // Recalculate projections from cached values
        if (cache.isStaked && cache.stakedSkr > 0) {
            calculateYieldProjections(cache.stakedSkr, cache.estimatedApy)
            if (cache.costBasis > 0) {
                _stakingPnlPercent.value = cache.rewards / cache.costBasis * 100
            }
        }

        _sharePriceHistory.value = prefs.getSharePriceHistory()
        _transactionHistory.value = prefs.getTransactionHistory()
        _isCachedData.value = true
        Log.w(TAG, "Portfolio loaded from cache (age ${(System.currentTimeMillis() - cache.cachedAt) / 1000}s)")
    }

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

                        // P&L from on-chain cost_basis (share_price at stake time)
                        _originalDeposit.value = info.originalDepositDisplay
                        _stakingRewards.value = info.rewardsDisplay
                        if (info.originalDepositDisplay > 0) {
                            _stakingPnlPercent.value =
                                (info.rewardsDisplay / info.originalDepositDisplay * 100)
                        }

                        // Track first-staked date
                        if (info.isStaked) {
                            prefs.setFirstStakedAt(System.currentTimeMillis())
                        }

                        // Save share price snapshot for dynamic APY tracking (once per day)
                        if (info.sharePrice > 0) {
                            prefs.saveSharePriceSnapshot(
                                System.currentTimeMillis(),
                                info.sharePrice
                            )
                        }

                        // Calculate dynamic APY from price history
                        val history = prefs.getSharePriceHistory()
                        val historyPairs = history.map { it.timestamp to it.sharePrice }
                        _estimatedApy.value = StakingRpcClient.estimateApy(historyPairs)
                        _sharePriceHistory.value = history

                        // Calculate yield projections
                        if (info.isStaked && info.stakedDisplay > 0) {
                            calculateYieldProjections(info.stakedDisplay, _estimatedApy.value)
                        }
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

            // Refresh tx history in case prediction ran while portfolio was loading
            val freshTx = prefs.getTransactionHistory()
            if (freshTx.isNotEmpty()) _transactionHistory.value = freshTx

            _isLoading.value = false
            _isCachedData.value = false
            Log.d(TAG, "Portfolio loaded: ${_solBalance.value} SOL, ${_stakedSol.value} staked SOL, " +
                "${_skrBalance.value} SKR liquid, ${_stakedSkr.value} SKR staked, " +
                "$$${_totalValueUsd.value} total")

            // Save portfolio cache for instant reload
            savePortfolioCache()

            // Update widget data
            try {
                val solTotal = _solBalance.value + _stakedSol.value
                val skrTotal = _skrBalance.value + _stakedSkr.value + _cooldownSkr.value
                SeekerWidgetProvider.writeWidgetData(
                    getApplication(),
                    tier = "--", // tier is set by PredictorViewModel
                    solBalance = String.format("%.2f", solTotal),
                    skrBalance = String.format("%.0f", skrTotal)
                )
            } catch (_: Exception) { }
        }
    }

    private fun calculateYieldProjections(stakedAmount: Double, apy: Double) {
        val rate = apy / 100.0
        _estDailyYield.value = stakedAmount * rate / 365.0
        _estMonthlyYield.value = stakedAmount * rate / 12.0
        _estAnnualYield.value = stakedAmount * rate

        // YTD: notional yield estimate since Jan 1 of current year
        val now = System.currentTimeMillis()
        val jan1 = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val daysSinceJan1 = ((now - jan1) / 86_400_000.0).coerceAtLeast(0.0)
        _estYtdYield.value = stakedAmount * rate * daysSinceJan1 / 365.0
    }

    private fun savePortfolioCache() {
        try {
            val cache = PortfolioCache(
                solBalance = _solBalance.value,
                stakedSol = _stakedSol.value,
                skrBalance = _skrBalance.value,
                stakedSkr = _stakedSkr.value,
                cooldownSkr = _cooldownSkr.value,
                isStaked = _isStaked.value,
                solPriceUsd = _solPriceUsd.value,
                skrPriceUsd = _skrPriceUsd.value,
                totalValueUsd = _totalValueUsd.value,
                estimatedApy = _estimatedApy.value,
                costBasis = _originalDeposit.value,
                rewards = _stakingRewards.value,
                sharePrice = 0L, // not strictly needed for cache display
                cachedAt = System.currentTimeMillis()
            )
            prefs.savePortfolioCache(cache)
        } catch (_: Exception) { }
    }

    companion object {
        private const val TAG = "SeekerVerify"
    }
}
