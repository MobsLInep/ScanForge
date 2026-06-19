package com.scanforge.core.common.dispatchers

import javax.inject.Qualifier

/**
 * The set of coroutine dispatchers used across ScanForge. Concrete [kotlinx.coroutines.CoroutineDispatcher]
 * instances are provided by a Hilt module in an Android-aware layer; this enum + qualifier keep the
 * binding declarable from the pure-Kotlin layers.
 */
enum class ScanForgeDispatcher {
    Default,
    IO,
}

/** Qualifier for injecting a specific [ScanForgeDispatcher]. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: ScanForgeDispatcher)

/** Qualifier for the application-scoped [kotlinx.coroutines.CoroutineScope]. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
