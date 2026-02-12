package com.seekerverify.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midmightbit.sgt.SgtChecker
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.rpc.DomainRpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IdentityViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    private val _memberNumber = MutableStateFlow<Long?>(null)
    val memberNumber: StateFlow<Long?> = _memberNumber.asStateFlow()

    private val _sgtMintAddress = MutableStateFlow<String?>(null)
    val sgtMintAddress: StateFlow<String?> = _sgtMintAddress.asStateFlow()

    // .skr domain
    private val _skrDomain = MutableStateFlow<String?>(null)
    val skrDomain: StateFlow<String?> = _skrDomain.asStateFlow()

    private val _allSkrDomains = MutableStateFlow<List<DomainRpcClient.DomainInfo>>(emptyList())
    val allSkrDomains: StateFlow<List<DomainRpcClient.DomainInfo>> = _allSkrDomains.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadIdentity(walletAddress: String, rpcUrl: String) {
        // Load from cache first
        _memberNumber.value = prefs.getMemberNumber()
        _sgtMintAddress.value = prefs.getSgtMintAddress()

        if (!prefs.shouldRecheckSgt() && _memberNumber.value != null) {
            Log.d(TAG, "Identity loaded from cache: Seeker #${_memberNumber.value}")
            // Still load domains (they're fast and cached separately)
            loadDomains(walletAddress, rpcUrl)
            return
        }

        // Fetch fresh data
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            Log.d(TAG, "Fetching SGT info for ${walletAddress.take(8)}...")

            val result = SgtChecker.getWalletSgtInfo(walletAddress, rpcUrl)
            result.fold(
                onSuccess = { info ->
                    Log.d(TAG, "SGT info: hasSgt=${info.hasSgt}, member=#${info.memberNumber}")
                    if (info.hasSgt) {
                        _memberNumber.value = info.memberNumber
                        _sgtMintAddress.value = info.sgtMintAddress
                        prefs.setSgtStatus(true, info.memberNumber, info.sgtMintAddress)
                    } else {
                        _error.value = "No SGT found in this wallet"
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "SGT info fetch failed: ${e.message}", e)
                    _error.value = "Failed to fetch SGT info: ${e.message}"
                    // Fall back to cached values
                    _memberNumber.value = prefs.getMemberNumber()
                    _sgtMintAddress.value = prefs.getSgtMintAddress()
                }
            )

            _isLoading.value = false
        }

        // Load domains in parallel
        loadDomains(walletAddress, rpcUrl)
    }

    private fun loadDomains(walletAddress: String, rpcUrl: String) {
        viewModelScope.launch {
            // Try main domain first (faster, single account lookup)
            DomainRpcClient.getMainDomain(walletAddress, rpcUrl).fold(
                onSuccess = { mainDomain ->
                    if (mainDomain != null) {
                        _skrDomain.value = mainDomain.fullDomain
                        Log.d(TAG, "Main .skr domain: ${mainDomain.fullDomain}")
                    }
                },
                onFailure = { e ->
                    Log.d(TAG, "Main domain lookup failed: ${e.message}")
                }
            )

            // Then fetch all domains
            DomainRpcClient.getSkrDomains(walletAddress, rpcUrl).fold(
                onSuccess = { domains ->
                    _allSkrDomains.value = domains
                    if (domains.isNotEmpty() && _skrDomain.value == null) {
                        // Use first active domain as display
                        val activeDomain = domains.firstOrNull { !it.isExpired }
                        _skrDomain.value = activeDomain?.fullDomain
                    }
                    Log.d(TAG, "Found ${domains.size} .skr domain(s)")
                },
                onFailure = { e ->
                    Log.d(TAG, "Domain lookup failed: ${e.message}")
                }
            )
        }
    }

    companion object {
        private const val TAG = "SeekerVerify"
    }
}
