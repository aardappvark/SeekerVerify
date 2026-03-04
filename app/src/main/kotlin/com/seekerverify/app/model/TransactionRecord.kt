package com.seekerverify.app.model

import kotlinx.serialization.Serializable

/**
 * Lightweight on-chain transaction record stored after parsing getTransaction responses.
 * Captures the essential info for the "Recent Activity" display in Portfolio screen.
 */
@Serializable
data class TransactionRecord(
    val signature: String,          // first 16 chars of the signature
    val blockTime: Long,            // epoch seconds (from Solana blockTime)
    val success: Boolean,           // true if tx succeeded (meta.err == null)
    val topPrograms: List<String>,  // human-readable names of non-system programs invoked
    val isDapp: Boolean             // true if at least one non-system program was invoked
)
