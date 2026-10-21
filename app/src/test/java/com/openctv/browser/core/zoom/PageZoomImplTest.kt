package com.openctv.browser.core.zoom

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

private class FakeSiteZoomStore(initial: Set<String> = emptySet()) : SiteZoomStore {
    val stored = MutableStateFlow(initial)
    var failure: Exception? = null
    override val entries: Flow<Set<String>> = stored

    override suspend fun update(transform: (Set<String>) -> Set<String>) {
        failure?.let { throw it }
        stored.value = transform(stored.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PageZoomImplTest {
    private fun TestScope.zooms(store: FakeSiteZoomStore) = PageZoomImpl(
        store = store,
        dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        scope = backgroundScope,
    )

    private fun PageZoomImpl.current(): List<SiteZoom> = (sites.value as Resource.Success).data

    @Test
    fun `Given nothing stored When the app starts Then no site has its own zoom`() = runTest {
        val repository = zooms(FakeSiteZoomStore())
        assertThat(repository.sites.value).isEqualTo(Resource.Loading)

        runCurrent()

        assertThat(repository.current()).isEmpty()
    }

    @Test
    fun `Given a zoom chosen for a site When the app restarts Then the site keeps its zoom`() = runTest {
        val store = FakeSiteZoomStore()
        zooms(store).set("gitlab-dashboard.tools.internal", PageZoom.Half)
        runCurrent()

        val afterRestart = zooms(store)
        runCurrent()

        assertThat(afterRestart.current()).containsExactly(SiteZoom("gitlab-dashboard.tools.internal", PageZoom.Half))
    }

    @Test
    fun `Given a site already zoomed When the user picks another level Then the site keeps a single entry`() = runTest {
        val repository = zooms(FakeSiteZoomStore())
        runCurrent()

        repository.set("arte.tv", PageZoom.Half)
        repository.set("arte.tv", PageZoom.ThreeQuarters)
        runCurrent()

        assertThat(repository.current()).containsExactly(SiteZoom("arte.tv", PageZoom.ThreeQuarters))
    }

    @Test
    fun `Given several zoomed sites When the list is read Then it is sorted by host`() = runTest {
        val repository = zooms(FakeSiteZoomStore())
        runCurrent()

        repository.set("wikipedia.org", PageZoom.Half)
        repository.set("arte.tv", PageZoom.TwoThirds)
        runCurrent()

        assertThat(repository.current().map { it.host }).containsExactly("arte.tv", "wikipedia.org").inOrder()
    }

    @Test
    fun `Given a zoomed site When its zoom is removed Then only that site goes back to the default`() = runTest {
        val repository = zooms(FakeSiteZoomStore())
        runCurrent()
        repository.set("arte.tv", PageZoom.Half)
        repository.set("wikipedia.org", PageZoom.TwoThirds)

        repository.remove("arte.tv")
        runCurrent()

        assertThat(repository.current()).containsExactly(SiteZoom("wikipedia.org", PageZoom.TwoThirds))
    }

    @Test
    fun `Given the storage fails When a zoom is saved Then an error is returned and the list is unchanged`() = runTest {
        val store = FakeSiteZoomStore().apply { failure = IllegalStateException("disk full") }
        val repository = zooms(store)
        runCurrent()

        val result = repository.set("arte.tv", PageZoom.Half)
        runCurrent()

        assertThat(result).isEqualTo(Resource.Error("disk full"))
        assertThat(repository.current()).isEmpty()
    }

    @Test
    fun `Given a corrupted entry in the storage When the list is read Then it is skipped and the others stay`() = runTest {
        val repository = zooms(FakeSiteZoomStore(setOf("arte.tv|50", "garbage", "nas.maison|42")))

        runCurrent()

        assertThat(repository.current()).containsExactly(SiteZoom("arte.tv", PageZoom.Half))
    }
}
