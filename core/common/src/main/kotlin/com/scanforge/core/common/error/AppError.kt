package com.scanforge.core.common.error

import java.io.IOException

/**
 * Framework-agnostic error taxonomy. The presentation layer maps these to localized strings; lower
 * layers translate their own failures (SQLite, IO, OCR engine) into one of these cases via
 * [AppError.from] or by constructing the specific subtype.
 */
sealed class AppError(
    open val cause: Throwable? = null,
) {
    /** Local persistence failed (Room/SQLite). */
    data class Storage(override val cause: Throwable? = null) : AppError(cause)

    /** Filesystem / IO failure (reading or writing image or PDF files). */
    data class Io(override val cause: Throwable? = null) : AppError(cause)

    /** A requested entity does not exist. */
    data class NotFound(val id: String) : AppError()

    /** OCR/document processing failed. Reserved for the OCR phase. */
    data class Processing(val reason: String, override val cause: Throwable? = null) : AppError(cause)

    /** Anything we could not classify. */
    data class Unknown(override val cause: Throwable? = null) : AppError(cause)

    companion object {
        /** Best-effort mapping from a raw [Throwable] to an [AppError]. */
        fun from(throwable: Throwable): AppError = when (throwable) {
            is IOException -> Io(throwable)
            else -> Unknown(throwable)
        }
    }
}
