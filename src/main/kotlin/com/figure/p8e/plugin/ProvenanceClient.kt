package com.figure.p8e.plugin

import com.google.protobuf.Any
import com.google.protobuf.Message
import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastMode
import cosmos.tx.v1beta1.TxOuterClass
import cosmos.tx.v1beta1.TxOuterClass.TxBody
import io.grpc.ManagedChannel
import io.provenance.client.common.gas.GasEstimate
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.GasEstimator
import io.provenance.client.grpc.PbClient
import io.provenance.metadata.v1.ContractSpecificationRequest
import io.provenance.metadata.v1.ContractSpecificationResponse
import io.provenance.metadata.v1.ScopeSpecificationRequest
import io.provenance.metadata.v1.ScopeSpecificationResponse
import io.provenance.msgfees.v1.CalculateTxFeesRequest
import org.slf4j.Logger
import java.net.URI
import java.util.concurrent.TimeUnit

fun Collection<Message>.toTxBody(): TxBody = TxBody.newBuilder()
    .addAllMessages(this.map { it.toAny() })
    .build()
fun Message.toAny(typeUrlPrefix: String = "") = Any.pack(this, typeUrlPrefix)

fun fixedLimitTransactionGasEstimator(location: P8eLocationExtension): GasEstimator =
    { tx: TxOuterClass.Tx, adjustment: Double ->
        val estimate = msgFeeClient.calculateTxFees(
            CalculateTxFeesRequest.newBuilder()
                .setTxBytes(tx.toByteString())
                .setGasAdjustment(adjustment.toFloat())
                .build()
        )
        GasEstimate(
            limit = if (location.fixedGasLimit > 0) {
                location.fixedGasLimit
            } else {
                estimate.estimatedGas
            },
            feesCalculated = estimate.totalFeesList,
            msgFees = estimate.additionalFeesList
        )
    }

class ProvenanceClient(channel: ManagedChannel, val logger: Logger, val location: P8eLocationExtension) {
    private val inner = PbClient(
        location.chainId!!,
        URI(location.provenanceUrl!!),
        fixedLimitTransactionGasEstimator(location),
        channel = channel
    )
    private val queryTimeoutSeconds = location.provenanceQueryTimeoutSeconds.toLong()

    fun scopeSpecification(request: ScopeSpecificationRequest): ScopeSpecificationResponse =
        inner.metadataClient.withDeadlineAfter(queryTimeoutSeconds, TimeUnit.SECONDS).scopeSpecification(request)

    fun contractSpecification(request: ContractSpecificationRequest): ContractSpecificationResponse =
        inner.metadataClient.withDeadlineAfter(queryTimeoutSeconds, TimeUnit.SECONDS).contractSpecification(request)

    private class SequenceMismatch(message: String): Exception(message)
    fun writeTx(signer: BaseReqSigner, txBody: TxBody) {
        retryForException(SequenceMismatch::class.java, 5) {
            val response = inner.estimateAndBroadcastTx(
                txBody,
                signers = listOf(signer),
                mode = BroadcastMode.BROADCAST_MODE_BLOCK, // faux block, will poll in background
                gasAdjustment = location.txFeeAdjustment.toDouble(),
                txHashHandler = { logger.trace("Preparing to broadcast $it") }
            )

            if (response.txResponse.code != 0) {
                val message = "error broadcasting tx (code ${response.txResponse.code}, rawLog: ${response.txResponse.rawLog})"
                if (response.txResponse.rawLog.contains("account sequence mismatch")) {
                    throw SequenceMismatch(message)
                }
                throw Exception(message)
            }

            logger.info("sent tx = ${response.txResponse.txhash}")
            logger.trace("tx response = ${response.txResponse}")
        }
    }

    private fun <E: Throwable, R> retryForException(exceptionClass: Class<E>, numTries: Int, block: () -> R): R {
        var lastException: Throwable? = null
        for (n in 1..numTries) {
            if (lastException != null) {
                logger.warn("retrying due to exception: ${lastException.message}")
            }
            try {
                return block()
            } catch (e: Throwable) {
                if (e.javaClass == exceptionClass) {
                    lastException = e
                    continue
                }
                throw e
            }
        }
        throw lastException ?: Exception("retry limit reached without a last exception: should not get here")
    }
}
