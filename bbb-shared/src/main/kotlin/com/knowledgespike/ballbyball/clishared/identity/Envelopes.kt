@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.knowledgespike.ballbyball.clishared.identity

import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.CanonicalNamespaces
import com.knowledgespike.ballbyball.identity.DeterministicIdentity
import com.knowledgespike.ballbyball.identity.ProviderId
import com.knowledgespike.ballbyball.identity.Sha256Digest
import com.knowledgespike.ballbyball.identity.SourceRecordId
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SourceReference(
    val provider: ProviderId,
    val providerRecordKey: String,
    val sourceRecordId: SourceRecordId,
    val rawContentDigest: Sha256Digest
)

@Serializable
data class SourceMatchEnvelope(
    @EncodeDefault val version: Int = CURRENT_ENVELOPE_VERSION,
    val source: SourceReference,
    val match: BbbMatchData
)

@Serializable
sealed interface MergeEvidence {
    @Serializable
    @SerialName("singleSource")
    data class SingleSource(val sourceRecordId: SourceRecordId) : MergeEvidence
}

@Serializable
data class CanonicalMatchEnvelope(
    @EncodeDefault val version: Int = CURRENT_ENVELOPE_VERSION,
    val canonicalMatchId: CanonicalMatchId,
    val sources: List<SourceReference>,
    val mergeEvidence: MergeEvidence,
    val match: BbbMatchData
)

object SingleSourceCanonicalizer {
    fun canonicalize(sourceEnvelope: SourceMatchEnvelope): CanonicalMatchEnvelope = CanonicalMatchEnvelope(
        canonicalMatchId = DeterministicIdentity.canonicalMatchId(
            namespace = CanonicalNamespaces.SINGLE_SOURCE,
            stableKey = sourceEnvelope.source.sourceRecordId.value.toString()
        ),
        sources = listOf(sourceEnvelope.source),
        mergeEvidence = MergeEvidence.SingleSource(sourceEnvelope.source.sourceRecordId),
        match = sourceEnvelope.match
    )
}

const val CURRENT_ENVELOPE_VERSION = 1