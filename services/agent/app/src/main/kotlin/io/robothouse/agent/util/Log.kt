package io.robothouse.agent.util

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Extension property that provides a lazily resolved logger named after the owning class.
 */
val Any.log get() = KotlinLogging.logger(this.javaClass.name)
