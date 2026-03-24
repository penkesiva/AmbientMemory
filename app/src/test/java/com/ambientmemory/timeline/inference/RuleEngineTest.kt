package com.ambientmemory.timeline.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleEngineTest {
    private val engine = RuleEngine()

    @Test
    fun walking_outdoors_high_confidence() {
        val scene =
            SceneUnderstandingResult(
                placeCategory = "outdoors",
                objects = listOf("tree", "road", "sky"),
                peopleCount = 0,
                rawSceneText = "tree, road, sky",
                privacyFlags = emptyMap(),
                structuredTags = emptyMap(),
            )
        val out = engine.infer(scene, "WALKING")
        assertEquals("walking", out.activity)
    }

    @Test
    fun laptop_still_suggests_working() {
        val scene =
            SceneUnderstandingResult(
                placeCategory = "office",
                objects = listOf("laptop", "desk", "chair"),
                peopleCount = 1,
                rawSceneText = "laptop, desk, chair",
                privacyFlags = emptyMap(),
                structuredTags = emptyMap(),
            )
        val out = engine.infer(scene, "STILL")
        assertEquals("working", out.activity)
    }
}
