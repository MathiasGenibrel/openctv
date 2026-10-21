package com.openctv.browser.features.home

import com.google.common.truth.Truth.assertThat
import com.openctv.browser.core.common.Resource
import com.openctv.browser.features.home.domain.HomeFavorite
import com.openctv.browser.features.home.domain.HomeFocus
import org.junit.Test

class HomeFocusTest {

    private val arte = HomeFavorite("https://arte.tv", "Arte", "arte.tv", "A", 0)
    private val withArte = Resource.Success(listOf(arte))
    private val none = Resource.Success(emptyList<HomeFavorite>())

    @Test
    fun `Given a first visit When the home opens Then the search bar is focused, or the add button without favorites`() {
        assertThat(HomeFocus.restore(null, withArte)).isEqualTo(HomeFocus.Search)
        assertThat(HomeFocus.restore(null, none)).isEqualTo(HomeFocus.Add)
        assertThat(HomeFocus.restore(null, Resource.Loading)).isEqualTo(HomeFocus.Search)
    }

    @Test
    fun `Given the user left from the settings button When he comes back Then the settings button is focused`() {
        assertThat(HomeFocus.restore(HomeFocus.Settings, withArte)).isEqualTo(HomeFocus.Settings)
        assertThat(HomeFocus.restore(HomeFocus.History, withArte)).isEqualTo(HomeFocus.History)
    }

    @Test
    fun `Given the user left from a favorite that was removed since When he comes back Then the search bar is focused`() {
        assertThat(HomeFocus.restore(HomeFocus.Favorite("https://gone.example"), withArte)).isEqualTo(HomeFocus.Search)
        assertThat(HomeFocus.restore(HomeFocus.Favorite("https://arte.tv"), none)).isEqualTo(HomeFocus.Add)
    }

    @Test
    fun `Given the user left from the add tile When the favorites are still loading Then the search bar holds the focus meanwhile`() {
        assertThat(HomeFocus.restore(HomeFocus.Add, Resource.Loading)).isEqualTo(HomeFocus.Search)
        assertThat(HomeFocus.restore(HomeFocus.Add, Resource.Error("disk"))).isEqualTo(HomeFocus.Add)
        assertThat(HomeFocus.restore(HomeFocus.Add, withArte)).isEqualTo(HomeFocus.Add)
    }

    @Test
    fun `Given favorites When the user presses Down on the search bar Then the first tile is targeted`() {
        val wiki = HomeFavorite("https://wikipedia.org", "Wikipedia", "wikipedia.org", "W", 1)

        assertThat(HomeFocus.belowSearch(Resource.Success(listOf(arte, wiki)))).isEqualTo(HomeFocus.Favorite("https://arte.tv"))
    }

    @Test
    fun `Given no favorite or a storage error When the user presses Down on the search bar Then the add button is targeted`() {
        assertThat(HomeFocus.belowSearch(none)).isEqualTo(HomeFocus.Add)
        assertThat(HomeFocus.belowSearch(Resource.Error("disk"))).isEqualTo(HomeFocus.Add)
    }

    @Test
    fun `Given the favorites are loading When the user presses Down on the search bar Then the default move applies`() {
        assertThat(HomeFocus.belowSearch(Resource.Loading)).isNull()
    }
}
