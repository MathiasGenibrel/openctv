package com.openctv.browser.core.navmode

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

private class FakeElementHostsStore(initial: Set<String> = emptySet()) : ElementHostsStore {
    val stored = MutableStateFlow(initial)
    var failure: Exception? = null
    override val hosts: Flow<Set<String>> = stored

    override suspend fun update(transform: (Set<String>) -> Set<String>) {
        failure?.let { throw it }
        stored.value = transform(stored.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationModeImplTest {
    private fun TestScope.modes(store: FakeElementHostsStore) = NavigationModeImpl(
        store = store,
        dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        scope = backgroundScope,
    )

    private fun NavigationModeImpl.current(): Set<String> = (elementHosts.value as Resource.Success).data

    @Test
    fun `Given nothing stored When the app starts Then every site is in cursor mode`() = runTest {
        val repository = modes(FakeElementHostsStore())
        assertThat(repository.elementHosts.value).isEqualTo(Resource.Loading)

        runCurrent()

        assertThat(repository.current()).isEmpty()
        assertThat(navigationModeOf("https://www.wikipedia.org/", repository.current())).isEqualTo(NavigationMode.Cursor)
    }

    @Test
    fun `Given wikipedia switched to elements When the app restarts Then wikipedia opens in elements mode`() = runTest {
        val store = FakeElementHostsStore()
        modes(store).set("wikipedia.org", NavigationMode.Elements)
        runCurrent()

        val afterRestart = modes(store)
        runCurrent()

        assertThat(navigationModeOf("https://wikipedia.org/wiki/Android", afterRestart.current()))
            .isEqualTo(NavigationMode.Elements)
    }

    @Test
    fun `Given wikipedia in elements mode When another host is opened Then it stays in cursor mode`() = runTest {
        val repository = modes(FakeElementHostsStore())
        runCurrent()

        repository.set("wikipedia.org", NavigationMode.Elements)
        runCurrent()

        assertThat(navigationModeOf("https://fr.wikipedia.org/", repository.current())).isEqualTo(NavigationMode.Cursor)
        assertThat(navigationModeOf("https://arte.tv/", repository.current())).isEqualTo(NavigationMode.Cursor)
    }

    @Test
    fun `Given a site in elements mode When it goes back to cursor Then it is forgotten`() = runTest {
        val repository = modes(FakeElementHostsStore(setOf("wikipedia.org")))
        runCurrent()

        repository.set("wikipedia.org", NavigationMode.Cursor)
        runCurrent()

        assertThat(repository.current()).isEmpty()
    }

    @Test
    fun `Given storage fails When a mode is saved Then an error is returned and the list is unchanged`() = runTest {
        val store = FakeElementHostsStore().apply { failure = IllegalStateException("disk") }
        val repository = modes(store)
        runCurrent()

        val result = repository.set("wikipedia.org", NavigationMode.Elements)
        runCurrent()

        assertThat(result).isEqualTo(Resource.Error("disk"))
        assertThat(repository.current()).isEmpty()
    }

    @Test
    fun `Given a page without readable host When the mode is resolved Then it stays in cursor mode`() {
        assertThat(navigationModeOf("about:blank", setOf("wikipedia.org"))).isEqualTo(NavigationMode.Cursor)
        assertThat(navigationModeOf(null, setOf("wikipedia.org"))).isEqualTo(NavigationMode.Cursor)
    }
}
