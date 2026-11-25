package com.masselis.tpmsadvanced.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.common.R as CommonR

/**
 * A full screen page of the introduction shown at the first launch: its [content] scrolls, under
 * it the progress through the [stepCount] steps, [step] being this one, and its buttons. [onSkip]
 * adds a "Skip" at the top.
 */
@Suppress("LongParameterList")
@Composable
public fun OnboardingPage(
    step: Int,
    stepCount: Int,
    primary: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    onSkip: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            // Takes its room even without the button, the pages' content stays in place
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                onSkip?.also { TextButton(onClick = it) { Text(stringResource(CommonR.string.onboarding_skip)) } }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                content = content,
            )
            Spacer(Modifier.height(16.dp))
            OnboardingStepDots(step = step, stepCount = stepCount)
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
            ) {
                secondary?.also { TextButton(onClick = onSecondary) { Text(it) } }
                Spacer(Modifier.weight(1f))
                Button(onClick = onPrimary) { Text(primary) }
            }
        }
    }
}

/** The introduction's progress: a dot per step, the current one [step] (from 0) highlighted */
@Composable
public fun OnboardingStepDots(step: Int, stepCount: Int, modifier: Modifier = Modifier) {
    val description = stringResource(CommonR.string.onboarding_step, step + 1, stepCount)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        repeat(stepCount) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        color = if (it == step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
            )
        }
    }
}

@Preview
@Composable
private fun OnboardingPagePreview() {
    OnboardingPage(
        step = 1,
        stepCount = 7,
        primary = "Next",
        onPrimary = {},
        secondary = "Back",
        onSkip = {},
    ) {
        Text("Title", style = MaterialTheme.typography.headlineSmall)
        Text("Some text explaining the step")
    }
}
