package com.ambientmemory.timeline.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * CameraX is driven by [Lifecycle] state. [androidx.lifecycle.LifecycleService] does not reliably
 * reach [Lifecycle.State.RESUMED], which can leave the camera session inactive. This owner is moved
 * explicitly through CREATED → STARTED → RESUMED while a capture session runs.
 */
class CameraSessionLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    fun startSession() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun endSession() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        if (!registry.currentState.isAtLeast(Lifecycle.State.CREATED)) return
        if (registry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
