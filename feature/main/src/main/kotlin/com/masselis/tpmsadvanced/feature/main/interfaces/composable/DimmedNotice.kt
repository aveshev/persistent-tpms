package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.feature.main.R

/**
 * Says the screen is dimmed, see [DimWhenIdle], where the alerts' silence button shows: the middle
 * of the vehicle, readable in the dark. Looks like that button, though a tap anywhere is what
 * brightens the screen.
 */
@Composable
public fun DimmedNotice(modifier: Modifier = Modifier): Unit = Surface(
    shape = ButtonDefaults.shape,
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier = modifier
        .heightIn(min = 72.dp)
        .testTag(DimWhenIdleTags.dimmed),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(R.drawable.light_mode_24px), contentDescription = null, Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Screen dimmed", style = MaterialTheme.typography.titleMedium)
            Text(text = "Tap anywhere to un-dim", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview
@Composable
private fun DimmedNoticePreview() = DimmedNotice()
