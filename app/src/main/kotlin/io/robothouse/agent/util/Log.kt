package io.robothouse.agent.util

import io.github.oshai.kotlinlogging.KotlinLogging

val Any.log get() = KotlinLogging.logger(this.javaClass.name)
