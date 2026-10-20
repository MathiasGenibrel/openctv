package com.openctv.browser.core.common

import app.cash.turbine.turbineScope
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceTest {

    private fun load(dispatchers: DispatcherProvider, failure: Exception?): Flow<Resource<String>> =
        flow {
            emit(Resource.Loading)
            try {
                if (failure != null) throw failure
                emit(Resource.Success("ok"))
            } catch (e: Exception) {
                emit(Resource.Error(e.message))
            }
        }.flowOn(dispatchers.io)

    @Test
    fun `Given a source that works When it is loaded on the injected dispatcher Then Loading is followed by Success`() =
        runTest {
            turbineScope {
                // Given
                val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))

                // When
                val turbine = load(dispatchers, failure = null).testIn(backgroundScope)

                // Then
                assertThat(turbine.awaitItem()).isEqualTo(Resource.Loading)
                assertThat(turbine.awaitItem()).isEqualTo(Resource.Success("ok"))
                turbine.awaitComplete()
            }
        }

    @Test
    fun `Given a source that fails When it is loaded Then the error is exposed as Resource Error without throwing`() =
        runTest {
            turbineScope {
                // Given
                val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))

                // When
                val turbine = load(dispatchers, failure = IllegalStateException("boom")).testIn(backgroundScope)

                // Then
                assertThat(turbine.awaitItem()).isEqualTo(Resource.Loading)
                assertThat(turbine.awaitItem()).isEqualTo(Resource.Error("boom"))
                turbine.awaitComplete()
            }
        }
}
