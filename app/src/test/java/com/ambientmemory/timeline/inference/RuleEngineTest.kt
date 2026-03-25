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
                sceneSource = "mlkit",
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
                sceneSource = "mlkit",
            )
        val out = engine.infer(scene, "STILL")
        assertEquals("working", out.activity)
    }

    @Test
    fun bathroom_cup_is_not_eating() {
        val scene =
            SceneUnderstandingResult(
                placeCategory = "bathroom",
                objects = listOf("bathroom", "vanity", "sink", "mirror", "cup", "blue", "spray", "bottle"),
                peopleCount = 0,
                rawSceneText = "A bathroom vanity with a sink, mirror, and various toiletries. A blue cup.",
                privacyFlags = emptyMap(),
                structuredTags = emptyMap(),
                sceneSource = "perceptron",
            )
        val out = engine.infer(scene, "UNKNOWN")
        assertEquals("household", out.activity)
    }
}
