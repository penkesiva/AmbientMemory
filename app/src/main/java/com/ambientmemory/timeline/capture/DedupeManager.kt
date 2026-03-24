package com.ambientmemory.timeline.capture

import com.ambientmemory.timeline.data.repo.AmbientSettings
import com.ambientmemory.timeline.data.repo.MemoryRepository
import java.io.File

data class DedupeDecision(
    val accepted: Boolean,
    val reason: String?,
    val imageHash: String?,
)

class DedupeManager(
    private val repository: MemoryRepository,
) {
    suspend fun evaluateNewCapture(
        captureSessionId: Long,
        newFile: File,
        currentActivityState: String,
        nowMillis: Long,
        settings: AmbientSettings,
    ): DedupeDecision {
        val hash =
            ImageHasher.dHashFromFile(newFile, maxDimension = 320)
                ?: return DedupeDecision(true, null, null)

        val previous = repository.getLastAcceptedRawCapture(captureSessionId)
        if (previous == null) {
            return DedupeDecision(true, null, hash)
        }

        val prevHash = previous.imageHash
        val timeDeltaSec = (nowMillis - previous.timestampMillis) / 1000
        if (prevHash != null && timeDeltaSec < settings.dedupeTimeWindowSeconds) {
            val dist = ImageHasher.hammingDistanceHex(prevHash, hash)
            if (dist <= settings.dedupeHashThreshold &&
                previous.activityState == currentActivityState
            ) {
                return DedupeDecision(
                    accepted = false,
                    reason = "near_duplicate_hash_time_activity",
                    imageHash = hash,
                )
            }
        }
        return DedupeDecision(true, null, hash)
    }
}
