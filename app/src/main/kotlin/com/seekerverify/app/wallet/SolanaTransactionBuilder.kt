package com.seekerverify.app.wallet

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal Solana transaction builder for Memo program transactions.
 * Adapted from AarDappvark Toolkit's production-tested SolanaTransactionBuilder.
 */
object SolanaTransactionBuilder {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    // Memo Program v2: MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr
    private val MEMO_PROGRAM_ID = decodeBase58("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr")

    /**
     * Build a Solana transaction that writes a memo on-chain.
     *
     * Transaction structure:
     * - 1 signer (fee payer / wallet)
     * - 1 instruction: Memo program with UTF-8 encoded memo data
     * - Signer must be in the memo instruction's account list
     *
     * @param feePayer Wallet public key (32 bytes)
     * @param recentBlockhash Recent blockhash (32 bytes, decoded from base58)
     * @param memo The memo string to write on-chain
     * @return Full serialized transaction with empty signature slot, ready for MWA signing
     */
    fun buildMemoTransaction(
        feePayer: ByteArray,
        recentBlockhash: ByteArray,
        memo: String
    ): ByteArray {
        require(feePayer.size == 32) { "Fee payer must be 32 bytes" }
        require(recentBlockhash.size == 32) { "Blockhash must be 32 bytes" }

        val memoBytes = memo.toByteArray(Charsets.UTF_8)

        val buffer = ByteBuffer.allocate(512)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // --- Message Header ---
        buffer.put(1.toByte())  // num_required_signatures: 1 (fee payer)
        buffer.put(0.toByte())  // num_readonly_signed_accounts: 0
        buffer.put(1.toByte())  // num_readonly_unsigned_accounts: 1 (memo program)

        // --- Account Keys (2 total) ---
        writeCompactU16(buffer, 2)
        buffer.put(feePayer, 0, feePayer.size)               // index 0: signer, writable (fee payer)
        buffer.put(MEMO_PROGRAM_ID, 0, MEMO_PROGRAM_ID.size) // index 1: readonly, unsigned (memo program)

        // --- Recent Blockhash ---
        buffer.put(recentBlockhash, 0, recentBlockhash.size)

        // --- Instructions (1 instruction) ---
        writeCompactU16(buffer, 1)

        // Memo instruction:
        buffer.put(1.toByte())           // program_id_index = 1 (memo program)
        writeCompactU16(buffer, 1)       // 1 account in this instruction
        buffer.put(0.toByte())           // account index 0 (signer)
        writeCompactU16(buffer, memoBytes.size) // data length
        buffer.put(memoBytes, 0, memoBytes.size) // memo data (UTF-8)

        // Extract message bytes
        val messageBytes = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(messageBytes)

        // Wrap as full transaction with empty signature slot
        return wrapMessageAsTransaction(messageBytes, numSigners = 1)
    }

    /**
     * Decode a Base58 string to bytes.
     */
    fun decodeBase58(input: String): ByteArray {
        var bi = BigInteger.ZERO
        for (ch in input) {
            val digit = ALPHABET.indexOf(ch)
            require(digit >= 0) { "Invalid Base58 character: $ch" }
            bi = bi.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit.toLong()))
        }
        val bytes = bi.toByteArray()
        // Strip leading zero byte from BigInteger sign extension
        val stripped = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        // Pad with leading zeros for Base58 leading '1's
        val leadingOnes = input.takeWhile { it == '1' }.length
        return ByteArray(leadingOnes) + stripped
    }

    /**
     * Wrap a Solana message into a full serialized transaction.
     * MWA signAndSendTransactions expects the full transaction format:
     *   [compact-u16 num_signatures] [64-byte empty signature per signer] [message bytes]
     */
    private fun wrapMessageAsTransaction(messageBytes: ByteArray, numSigners: Int = 1): ByteArray {
        val buffer = ByteBuffer.allocate(1 + numSigners * 64 + messageBytes.size)
        // Compact-u16 for numSigners (always < 128, so single byte)
        buffer.put(numSigners.toByte())
        // Empty signature slots (64 zero bytes each) — wallet fills these in
        for (i in 0 until numSigners) {
            buffer.put(ByteArray(64))
        }
        // Message bytes
        buffer.put(messageBytes)
        return buffer.array()
    }

    /**
     * Write compact-u16 encoding (Solana's variable-length encoding).
     */
    private fun writeCompactU16(buffer: ByteBuffer, value: Int) {
        when {
            value < 128 -> {
                buffer.put(value.toByte())
            }
            value < 16384 -> {
                buffer.put(((value and 0x7F) or 0x80).toByte())
                buffer.put((value shr 7).toByte())
            }
            else -> {
                buffer.put(((value and 0x7F) or 0x80).toByte())
                buffer.put((((value shr 7) and 0x7F) or 0x80).toByte())
                buffer.put((value shr 14).toByte())
            }
        }
    }
}
