package com.openctv.browser.features.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.openctv.browser.R
import com.openctv.browser.core.designsystem.ExpressiveShape
import com.openctv.browser.core.designsystem.InsecurityMark
import com.openctv.browser.core.designsystem.ListGroup
import com.openctv.browser.core.designsystem.ListRow
import com.openctv.browser.core.designsystem.OpenCtvButton
import com.openctv.browser.core.designsystem.OpenCtvButtonStyle
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvIcons
import com.openctv.browser.core.designsystem.OpenCtvShapes
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.OverscanSafeArea
import com.openctv.browser.core.designsystem.asShape
import com.openctv.browser.core.webview.CertificateProblem
import com.openctv.browser.core.webview.SecurityWarning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Actions d'un avertissement de sécurité : la vue les transmet au ViewModel, qui décide. */
internal class SecurityWarningActions(
    val onReturnToSafety: () -> Unit,
    val onToggleOptions: () -> Unit,
    val onContinue: (isRemembered: Boolean) -> Unit,
)

/**
 * Avertissement de sécurité plein écran (page HTTP, certificat invalide) à la place de la page : pastille de
 * gravité, titre, explication, puis « Retour en sécurité » (focalisé à l'ouverture, [primaryFocus]) et
 * « Options avancées » qui déplie le panneau pour afficher quand même la page. Retour replie les options
 * avant tout (décidé par le ViewModel). Sans exception descriptible, les options ne sont pas proposées.
 */
@Composable
internal fun SecurityWarningContent(
    warning: SecurityWarning,
    isOptionsOpen: Boolean,
    actions: SecurityWarningActions,
    primaryFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val optionsFocus = remember { FocusRequester() }
    val checkboxFocus = remember { FocusRequester() }
    // Liés à l'avertissement : une case cochée pour un hôte ne doit jamais rester cochée pour le suivant.
    var isRemembered by remember(warning) { mutableStateOf(false) }
    val canContinue = warning.exception != null

    LaunchedEffect(warning) { primaryFocus.requestFocus() }
    // Dépliées, les options prennent le focus (la case) ; repliées, il revient sur le bouton qui les a ouvertes.
    var wasOptionsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isOptionsOpen) {
        if (isOptionsOpen) runCatching { checkboxFocus.requestFocus() }
        else if (wasOptionsOpen) runCatching { optionsFocus.requestFocus() }
        wasOptionsOpen = isOptionsOpen
    }

    val style = warning.style()
    // La zone sûre (overscan) borde le défilement : la colonne se centre si elle tient, sinon elle défile à
    // l'intérieur, sans jamais atteindre les bords de l'écran. Le focus garde l'élément visible (bringIntoView).
    Box(modifier.fillMaxSize().background(OpenCtvColors.Surface)) {
      OverscanSafeArea {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentWidth)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Les options dépliées gardent la page dans 540 dp : l'illustration s'efface.
            if (!isOptionsOpen) {
                Illustration(style)
                Spacer(Modifier.height(20.dp))
            }
            SeverityPill(style)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(style.titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = OpenCtvColors.OnSurface,
                textAlign = TextAlign.Center,
            )
            // Les options dépliées répètent l'explication et l'hôte : le texte d'introduction cède la place.
            if (!isOptionsOpen) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(style.bodyRes, warning.host ?: warning.url),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OpenCtvColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (warning is SecurityWarning.InvalidCertificate) {
                Spacer(Modifier.height(16.dp))
                CertificateCard(warning)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OpenCtvButton(onClick = actions.onReturnToSafety, modifier = Modifier.focusRequester(primaryFocus)) {
                    Text(stringResource(R.string.security_return_to_safety))
                }
                if (canContinue) {
                    OpenCtvButton(
                        onClick = actions.onToggleOptions,
                        style = OpenCtvButtonStyle.Text,
                        modifier = Modifier.focusRequester(optionsFocus),
                    ) {
                        Text(stringResource(R.string.security_advanced_options))
                        Spacer(Modifier.size(8.dp))
                        Icon(
                            painter = if (isOptionsOpen) OpenCtvIcons.ExpandLess else OpenCtvIcons.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (isOptionsOpen && canContinue) {
                Spacer(Modifier.height(16.dp))
                OptionsPanel(
                    warning = warning,
                    isRemembered = isRemembered,
                    onToggleRemembered = { isRemembered = !isRemembered },
                    onContinue = { actions.onContinue(isRemembered) },
                    checkboxFocus = checkboxFocus,
                )
            }
        }
        }
      }
    }
}

@Composable
private fun Illustration(style: WarningStyle) {
    // Un Painter par site d'appel : voir [WarningStyle].
    Box(
        modifier = Modifier.size(80.dp).background(style.container, ExpressiveShape.Cookie12.asShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(style.illustrationIcon(), contentDescription = null, tint = style.onContainer, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun SeverityPill(style: WarningStyle) {
    Row(
        modifier = Modifier
            .background(style.container, OpenCtvShapes.Full)
            .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(style.badgeIcon(), contentDescription = null, tint = style.onContainer, modifier = Modifier.size(18.dp))
        Text(stringResource(style.badgeRes), style = MaterialTheme.typography.labelLarge, color = style.onContainer)
    }
}

/** Cause du refus et « Émis pour · valide jusqu'au » : de quoi reconnaître son propre appareil. */
@Composable
private fun CertificateCard(warning: SecurityWarning.InvalidCertificate) {
    val validUntil = warning.validUntil?.let(::formatDate)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OpenCtvColors.SurfaceContainer, OpenCtvShapes.Medium)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardLine(stringResource(R.string.security_cert_cause), causeText(warning.problem, validUntil), OpenCtvColors.Error)
        val issuedTo = warning.issuedTo ?: warning.host ?: warning.url
        val issued = if (validUntil != null) "$issuedTo · $validUntil" else issuedTo
        CardLine(stringResource(R.string.security_cert_issued_to), issued, OpenCtvColors.OnSurfaceVariant)
    }
}

@Composable
private fun CardLine(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = OpenCtvColors.OnSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun causeText(problem: CertificateProblem, validUntil: String?): String = when (problem) {
    CertificateProblem.Expired ->
        if (validUntil != null) stringResource(R.string.security_cause_expired_on, validUntil) else stringResource(R.string.security_cause_expired)
    CertificateProblem.NameMismatch -> stringResource(R.string.security_cause_name_mismatch)
    CertificateProblem.Untrusted -> stringResource(R.string.security_cause_untrusted)
    CertificateProblem.NotYetValid -> stringResource(R.string.security_cause_not_yet_valid)
    CertificateProblem.Invalid -> stringResource(R.string.security_cause_invalid)
}

private fun formatDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withZone(ZoneId.systemDefault()).format(instant)

@Composable
private fun OptionsPanel(
    warning: SecurityWarning,
    isRemembered: Boolean,
    onToggleRemembered: () -> Unit,
    onContinue: () -> Unit,
    checkboxFocus: FocusRequester,
) {
    val host = warning.host ?: warning.url
    val isCertificate = warning is SecurityWarning.InvalidCertificate
    val explanation = stringResource(warning.optionsNote().textRes, host)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OpenCtvColors.SurfaceContainer, OpenCtvShapes.Large)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = OpenCtvColors.OnSurfaceVariant,
        )
        ListGroup {
            ListRow(
                icon = if (isRemembered) OpenCtvIcons.CheckBox else OpenCtvIcons.CheckBoxOutlineBlank,
                label = stringResource(if (isCertificate) R.string.security_cert_remember else R.string.security_http_remember, host),
                supporting = stringResource(if (isCertificate) R.string.security_cert_remember_hint else R.string.security_http_remember_hint),
                onClick = onToggleRemembered,
                modifier = Modifier.focusRequester(checkboxFocus),
            )
        }
        OpenCtvButton(
            onClick = onContinue,
            style = if (isCertificate) OpenCtvButtonStyle.OutlineError else OpenCtvButtonStyle.Outline,
        ) {
            Icon(
                painter = if (isCertificate) OpenCtvIcons.Warning else OpenCtvIcons.NoEncryption,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(if (isCertificate) R.string.security_cert_continue else R.string.security_http_show))
        }
    }
}

private val ContentWidth = 720.dp


/** Couleurs, icône et textes d'un avertissement : HTTP = modéré (`tertiary`), certificat = grave (`error`). */
private class WarningStyle(
    val container: Color,
    val onContainer: Color,
    // Fabriques et non `Painter` : un `Painter` vectoriel garde un cache de dessin à une seule taille. Partagé
    // entre l'illustration (40 dp) et la pastille (18 dp), il dessinait le glyphe deux fois, tronqué. Chaque
    // site d'appel crée donc le sien (`painterResource` → `rememberVectorPainter` distincts).
    val illustrationIcon: @Composable () -> Painter,
    val badgeIcon: @Composable () -> Painter,
    val badgeRes: Int,
    val titleRes: Int,
    val bodyRes: Int,
)

@Composable
private fun SecurityWarning.style(): WarningStyle = when (this) {
    is SecurityWarning.InsecureHttp -> WarningStyle(
        container = OpenCtvColors.TertiaryContainer,
        onContainer = OpenCtvColors.OnTertiaryContainer,
        illustrationIcon = { InsecurityMark.Http.icon },
        badgeIcon = { OpenCtvIcons.Warning },
        badgeRes = R.string.security_http_badge,
        titleRes = R.string.security_http_title,
        bodyRes = R.string.security_http_body,
    )
    is SecurityWarning.InvalidCertificate -> WarningStyle(
        container = OpenCtvColors.Error,
        onContainer = OpenCtvColors.OnError,
        illustrationIcon = { InsecurityMark.Certificate.icon },
        badgeIcon = { OpenCtvIcons.GppBad },
        badgeRes = R.string.security_cert_badge,
        titleRes = R.string.security_cert_title,
        bodyRes = R.string.security_cert_body,
    )
}

private val PreviewActions = SecurityWarningActions({}, {}, {})

@Preview(device = Devices.TV_1080p)
@Composable
private fun HttpWarningPreview() {
    OpenCtvTheme {
        SecurityWarningContent(
            SecurityWarning.InsecureHttp("http://192.168.1.10/", "192.168.1.10"),
            isOptionsOpen = false, actions = PreviewActions, primaryFocus = FocusRequester(),
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HttpWarningOptionsPreview() {
    OpenCtvTheme {
        SecurityWarningContent(
            SecurityWarning.InsecureHttp("http://192.168.1.10/", "192.168.1.10"),
            isOptionsOpen = true, actions = PreviewActions, primaryFocus = FocusRequester(),
        )
    }
}

private val PreviewCertificate = SecurityWarning.InvalidCertificate(
    url = "https://expired.badssl.com/",
    host = "expired.badssl.com",
    problem = CertificateProblem.Expired,
    issuedTo = "*.badssl.com",
    validUntil = Instant.parse("2015-04-12T23:59:59Z"),
    fingerprint = "ab".repeat(32),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun CertificateWarningPreview() {
    OpenCtvTheme {
        SecurityWarningContent(PreviewCertificate, isOptionsOpen = false, actions = PreviewActions, primaryFocus = FocusRequester())
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun CertificateWarningOptionsPreview() {
    OpenCtvTheme {
        SecurityWarningContent(PreviewCertificate, isOptionsOpen = true, actions = PreviewActions, primaryFocus = FocusRequester())
    }
}
