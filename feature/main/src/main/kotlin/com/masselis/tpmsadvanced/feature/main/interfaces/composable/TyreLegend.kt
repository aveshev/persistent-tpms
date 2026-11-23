package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.common.Fraction
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * The top of the pressure and temperature settings pages: demo tyres drawn exactly like the real
 * ones, each captioned with what its colour means, above an explanation [text].
 */
@Composable
internal fun TyreLegend(
    text: String,
    entries: List<Pair<State, String>>,
    modifier: Modifier = Modifier,
) = Column(modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
    // Demo tyres never reach State.DetectionIssue, the only state needing a snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        entries.forEach { (state, caption) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Tyre(state, snackbarHostState, Modifier.height(96.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Preview
@Composable
internal fun TyreLegendPreview() {
    TyreLegend(
        text = stringResource(CommonR.string.explanation_of_the_colours),
        entries = listOf(
            State.Normal.BlueToGreen(Fraction(0f)) to stringResource(CommonR.string.legend_cold),
            State.Normal.BlueToGreen(Fraction(1f)) to stringResource(CommonR.string.legend_normal),
            State.Alerting() to stringResource(CommonR.string.legend_hot),
        ),
    )
}
