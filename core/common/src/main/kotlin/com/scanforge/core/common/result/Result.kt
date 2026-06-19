package com.scanforge.core.common.result

import com.scanforge.core.common.error.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Domain-level result wrapper. Lives in the pure-Kotlin core so every layer — including the
 * Android-free domain — can speak in [Result] without leaking framework types.
 *
 * Errors are modelled as [AppError] rather than raw [Throwable] so the presentation layer can map
 * them to user-facing messages without knowing about exceptions.
 */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Failure(val error: AppError) : Result<Nothing>
    data object Loading : Result<Nothing>
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Failure -> this
    Result.Loading -> Result.Loading
}

inline fun <T, R> Result<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
    onLoading: () -> R,
): R = when (this) {
    is Result.Success -> onSuccess(data)
    is Result.Failure -> onFailure(error)
    Result.Loading -> onLoading()
}

/** Returns the success value or `null` for loading/failure states. */
fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success)?.data

/**
 * Wraps a stream of values into [Result], emitting [Result.Loading] first and converting any
 * thrown exception into a [Result.Failure]. Mirrors the common `asResult()` pattern.
 */
fun <T> Flow<T>.asResult(): Flow<Result<T>> =
    map<T, Result<T>> { Result.Success(it) }
        .onStart { emit(Result.Loading) }
        .catch { emit(Result.Failure(AppError.from(it))) }
