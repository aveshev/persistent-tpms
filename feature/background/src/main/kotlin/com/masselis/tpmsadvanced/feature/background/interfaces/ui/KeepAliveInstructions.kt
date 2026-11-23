@file:Suppress("MaxLineLength")

package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.KeepAliveInstructionsViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.KeepAliveInstructionsViewModel.State
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import com.masselis.tpmsadvanced.feature.background.usecase.KeepAliveInstructionsUseCase.Companion.BASE_URL
import com.masselis.tpmsadvanced.feature.background.usecase.KeepAliveInstructionsUseCase.Instructions
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * Full screen instructions from dontkillmyapp.com for the phone's brand, so that the user doesn't
 * have to find them on the website.
 */
@Composable
internal fun KeepAliveInstructions(
    onDismissRequest: () -> Unit,
    viewModel: KeepAliveInstructionsViewModel = viewModel {
        Bindings.featureBackgroundInternal.keepAliveInstructionsViewModel()
    },
) {
    val state by viewModel.stateFlow.collectAsState()
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        KeepAliveInstructions(
            state = state,
            pageUrl = viewModel.pageUrl,
            onDismissRequest = onDismissRequest,
            onRetry = viewModel::load,
            onOpenWebsite = {
                context.startActivity(Intent(Intent.ACTION_VIEW, viewModel.pageUrl.toUri()))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
private fun KeepAliveInstructions(
    state: State,
    pageUrl: String,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onOpenWebsite: () -> Unit,
) = Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text(
                    text = (state as? State.Loaded)
                        ?.let { stringResource(CommonR.string.keep_monitoring_running_on, it.instructions.vendorName) }
                        ?: stringResource(CommonR.string.keep_monitoring_running)
                )
            },
            navigationIcon = {
                IconButton(onClick = onDismissRequest) {
                    Icon(ImageVector.vectorResource(R.drawable.close_24px), contentDescription = stringResource(CommonR.string.close))
                }
            },
            actions = {
                TextButton(onClick = onOpenWebsite) { Text(text = stringResource(CommonR.string.website)) }
            },
        )
    },
) { padding ->
    when (state) {
        State.Loading -> Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }

        State.Failed -> Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(CommonR.string.the_instructions_for_your_phone_could_not),
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text(text = stringResource(CommonR.string.try_again)) }
        }

        is State.Loaded -> MaterialTheme.colorScheme.let { colors ->
            state
                .instructions
                .asHtml(pageUrl, text = colors.onSurface, background = colors.surface, link = colors.primary)
                .let { html ->
                    // A new page only when the content changes, not on every recomposition
                    key(html) {
                        AndroidView(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    setBackgroundColor(colors.surface.toArgb())
                                    // No JavaScript: the content comes from a third party, and
                                    // anchors and <details> work without it
                                    settings.allowFileAccess = false
                                    webViewClient = ExternalLinksClient
                                    loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)
                                }
                            },
                        )
                    }
                }
        }
    }
}

/** Anchors scroll the page, every other link opens in the browser */
private object ExternalLinksClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        request
            .url
            .takeIf { it.fragment == null || it.buildUpon().fragment(null).toString() != BASE_URL }
            ?.also { view.context.startActivity(Intent(Intent.ACTION_VIEW, it)) }
            .let { it != null }
}

/** The site's HTML, styled like the app, with the credit its CC-BY licence requires */
private fun Instructions.asHtml(pageUrl: String, text: Color, background: Color, link: Color) = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
    body { font-family: sans-serif; line-height: 1.5; margin: 16px; color: ${text.css}; background: ${background.css}; }
    a { color: ${link.css}; }
    img { max-width: 100%; height: auto; }
    figure { margin: 16px 0; }
    figcaption { font-size: 0.85em; opacity: 0.7; }
    blockquote { margin: 16px 0; padding-left: 12px; border-left: 3px solid ${link.css}; }
    summary { font-weight: bold; }
    .scope { padding: 12px; border-radius: 12px; background: ${link.css}1F; }
    .credit { margin-top: 32px; font-size: 0.85em; opacity: 0.7; }
    </style>
    </head>
    <body>
    ${shownFor?.let { "<p class=\"scope\">These are the steps for $it. <a href=\"$pageUrl\">Steps for other Android versions</a></p>" }.orEmpty()}
    ${explanation.takeIf { it.isNotBlank() }?.let { "<details><summary>Why $vendorName stops apps</summary>$it</details>" }.orEmpty()}
    $userSolution
    <p class="credit">From <a href="https://dontkillmyapp.com/">dontkillmyapp.com</a> by Urbandroid Team, licensed CC BY.</p>
    </body>
    </html>
""".trimIndent()

// The RGB part only, CSS colours have no leading alpha
@Suppress("MagicNumber")
private val Color.css get() = "#%06X".format(toArgb() and 0xFFFFFF)

@Preview
@Composable
private fun KeepAliveInstructionsLoadingPreview() =
    KeepAliveInstructions(State.Loading, BASE_URL, onDismissRequest = {}, onRetry = {}, onOpenWebsite = {})

@Preview
@Composable
private fun KeepAliveInstructionsFailedPreview() =
    KeepAliveInstructions(State.Failed, BASE_URL, onDismissRequest = {}, onRetry = {}, onOpenWebsite = {})
