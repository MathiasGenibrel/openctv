package com.openctv.browser.core.security

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.common.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeSiteExceptionsStore(initial: Set<String> = emptySet()) : SiteExceptionsStore {
    val stored = MutableStateFlow(initial)
    var failure: Exception? = null
    override val entries: Flow<Set<String>> = stored

    override suspend fun update(transform: (Set<String>) -> Set<String>) {
        failure?.let { throw it }
        stored.value = transform(stored.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityExceptionsImplTest {
    private val http = SiteException.Http("192.168.1.10")
    private val certificate = SiteException.Certificate("nas.maison", "ab12")

    private fun TestScope.exceptions(store: FakeSiteExceptionsStore) = SecurityExceptionsImpl(
        store = store,
        dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        scope = backgroundScope,
    )

    @Test
    fun `Given an exception allowed for the session only When the app restarts Then the warning comes back`() = runTest {
        val store = FakeSiteExceptionsStore()
        val session = exceptions(store)
        runCurrent()

        session.allow(http, isRemembered = false)
        runCurrent()
        assertThat(session.isAllowed(http)).isTrue()

        val afterRestart = exceptions(store)
        runCurrent()
        assertThat(afterRestart.isAllowed(http)).isFalse()
    }

    @Test
    fun `Given a remembered exception When the app restarts Then the page is allowed without warning`() = runTest {
        val store = FakeSiteExceptionsStore()
        exceptions(store).allow(certificate, isRemembered = true)
        runCurrent()

        val afterRestart = exceptions(store)
        runCurrent()

        assertThat(afterRestart.isAllowed(certificate)).isTrue()
        assertThat(afterRestart.isAllowed(SiteException.Certificate("nas.maison", "ff99"))).isFalse()
        assertThat(afterRestart.isAllowed(SiteException.Http("nas.maison"))).isFalse()
    }

    @Test
    fun `Given a remembered exception When it is removed Then the next visit warns again and Settings no longer lists it`() = runTest {
        val store = FakeSiteExceptionsStore()
        val exceptions = exceptions(store)
        exceptions.allow(http, isRemembered = true)
        runCurrent()

        exceptions.remove(http)
        runCurrent()

        assertThat(exceptions.isAllowed(http)).isFalse()
        assertThat((exceptions.persisted.value as Resource.Success).data).isEmpty()
    }

    @Test
    fun `Given the storage fails When the user asks to remember Then the exception still holds for the session and the error is reported`() = runTest {
        val store = FakeSiteExceptionsStore().apply { failure = IllegalStateException("disk full") }
        val exceptions = exceptions(store)
        runCurrent()

        val result = exceptions.allow(http, isRemembered = true)

        assertThat(result).isInstanceOf(Resource.Error::class.java)
        assertThat(exceptions.isAllowed(http)).isTrue()
    }

    @Test
    fun `Given exceptions stored out of order When Settings lists them Then they are sorted by host`() = runTest {
        val store = FakeSiteExceptionsStore(
            setOf("http|zeta.lan", "cert|alpha.lan|ab12", "garbage"),
        )
        val exceptions = exceptions(store)
        runCurrent()

        val hosts = (exceptions.persisted.value as Resource.Success).data.map { it.host }

        assertThat(hosts).containsExactly("alpha.lan", "zeta.lan").inOrder()
    }

    @Test
    fun `Given the remembered exceptions are not read yet When a page is checked Then nothing is allowed`() = runTest {
        val exceptions = exceptions(FakeSiteExceptionsStore(setOf("http|192.168.1.10")))

        assertThat(exceptions.isAllowed(http)).isFalse()
    }
}
