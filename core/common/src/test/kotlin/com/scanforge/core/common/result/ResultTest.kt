package com.scanforge.core.common.result

import app.cash.turbine.test
import com.scanforge.core.common.error.AppError
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.IOException

class ResultTest {

    @Test
    fun `map transforms only the success value`() {
        val result: Result<Int> = Result.Success(2)
        assertEquals(Result.Success(4), result.map { it * 2 })
    }

    @Test
    fun `map preserves failure and loading`() {
        val failure: Result<Int> = Result.Failure(AppError.NotFound("x"))
        val loading: Result<Int> = Result.Loading
        assertEquals(failure, failure.map { it * 2 })
        assertEquals(loading, loading.map { it * 2 })
    }

    @Test
    fun `fold dispatches on the active case`() {
        val cases = listOf<Result<Int>>(Result.Success(1), Result.Failure(AppError.Storage()), Result.Loading)
        val labels = cases.map { it.fold(onSuccess = { "s" }, onFailure = { "f" }, onLoading = { "l" }) }
        assertEquals(listOf("s", "f", "l"), labels)
    }

    @Test
    fun `dataOrNull returns value for success and null otherwise`() {
        assertEquals(7, Result.Success(7).dataOrNull())
        assertNull(Result.Failure(AppError.Unknown()).dataOrNull())
        assertNull(Result.Loading.dataOrNull())
    }

    @Test
    fun `asResult emits Loading then Success`() = runTest {
        flowOf(42).asResult().test {
            assertEquals(Result.Loading, awaitItem())
            assertEquals(Result.Success(42), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `asResult maps a thrown exception to Failure`() = runTest {
        flow<Int> { throw IOException("disk gone") }.asResult().test {
            assertEquals(Result.Loading, awaitItem())
            val item = awaitItem()
            assert(item is Result.Failure && (item as Result.Failure).error is AppError.Io)
            awaitComplete()
        }
    }
}
