package com.openctv.browser.core.zoom

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageZoomTest {
    private val dashboard = "http://gitlab-dashboard.tools.internal/"

    @Test
    fun `Given each zoom level When the page is laid out Then it sees its width over the TV 960 px`() {
        assertThat(PageZoom.entries.map { it.pageWidth }).containsExactly(960, 1280, 1440, 1920).inOrder()
        PageZoom.entries.forEach { zoom ->
            assertThat(zoom.pageWidth * zoom.factor).isWithin(0.01f).of(960f)
        }
        assertThat(PageZoom.Full.factor).isEqualTo(1f)
        assertThat(PageZoom.Half.factor).isEqualTo(0.5f)
    }

    @Test
    fun `Given a site without its own zoom When the effective level is asked Then the default applies`() {
        val sites = listOf(SiteZoom("gitlab-dashboard.tools.internal", PageZoom.Half))

        assertThat(effectiveZoom("https://arte.tv/fr", sites, PageZoom.Full)).isEqualTo(PageZoom.Full)
        assertThat(effectiveZoom("https://arte.tv/fr", sites, PageZoom.ThreeQuarters)).isEqualTo(PageZoom.ThreeQuarters)
    }

    @Test
    fun `Given a site with its own zoom When any of its pages is shown Then its level applies whatever the scheme port or path`() {
        val sites = listOf(SiteZoom("gitlab-dashboard.tools.internal", PageZoom.Half))

        assertThat(effectiveZoom(dashboard, sites, PageZoom.Full)).isEqualTo(PageZoom.Half)
        assertThat(effectiveZoom("https://GITLAB-DASHBOARD.tools.internal:8443/a/b?c#d", sites, PageZoom.Full))
            .isEqualTo(PageZoom.Half)
    }

    @Test
    fun `Given a zoom for an exact host When another name of the same site is shown Then it keeps the default`() {
        val sites = listOf(SiteZoom("arte.tv", PageZoom.Half))

        assertThat(effectiveZoom("https://www.arte.tv/", sites, PageZoom.Full)).isEqualTo(PageZoom.Full)
        assertThat(effectiveZoom("https://evil.arte.tv.example/", sites, PageZoom.Full)).isEqualTo(PageZoom.Full)
    }

    @Test
    fun `Given an address without a readable host When the effective level is asked Then it is the default`() {
        val sites = listOf(SiteZoom("arte.tv", PageZoom.Half))

        assertThat(effectiveZoom(null, sites, PageZoom.TwoThirds)).isEqualTo(PageZoom.TwoThirds)
        assertThat(effectiveZoom("about:blank", sites, PageZoom.TwoThirds)).isEqualTo(PageZoom.TwoThirds)
        assertThat(effectiveZoom("https://exa mple.org", sites, PageZoom.TwoThirds)).isEqualTo(PageZoom.TwoThirds)
    }

    @Test
    fun `Given a stored zoom When it is read back Then host and level are restored`() {
        val site = SiteZoom("192.168.1.10", PageZoom.TwoThirds)

        assertThat(SiteZoomCodec.decode(SiteZoomCodec.encode(site))).isEqualTo(site)
    }

    @Test
    fun `Given unreadable stored entries When they are decoded Then they are ignored`() {
        listOf("", "arte.tv", "arte.tv|", "|50", "arte.tv|42", "arte.tv|50|x").forEach { stored ->
            assertThat(SiteZoomCodec.decode(stored)).isNull()
        }
    }
}
