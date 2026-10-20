package com.openctv.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.OverscanSafeArea

private const val SampleUrl = "https://www.arte.tv"

/** Premier écran minimal : un seul élément, focalisé dès l'ouverture. */
@Composable
fun StartScreen(onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val initialFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { initialFocus.requestFocus() }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = { onOpenUrl(SampleUrl) }, modifier = Modifier.focusRequester(initialFocus)) {
            Text(text = stringResource(R.string.home_open_sample))
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun StartScreenPreview() {
    OpenCtvTheme {
        OverscanSafeArea { StartScreen(onOpenUrl = {}) }
    }
}
