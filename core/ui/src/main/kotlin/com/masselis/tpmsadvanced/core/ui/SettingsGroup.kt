@file:Suppress("TooManyFunctions")

package com.masselis.tpmsadvanced.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * The title above a [SettingsGroup], with an optional [action] at its end acting on the whole
 * group, such as a text button sorting it
 */
@Composable
public fun SettingsSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
): Unit = Row(
    // Along the bottom of the title, so that the button doesn't make the header any taller
    verticalAlignment = Alignment.Bottom,
    modifier = modifier.padding(end = 4.dp),
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .weight(1f)
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    action?.invoke()
}

/**
 * Settings items drawn like the Android settings app: one card per item, separated by a thin gap,
 * the group's outer corners being rounder than the inner ones.
 */
@Composable
public fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
): Unit = Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp)),
    content = content,
)

/** A [SettingsGroup] item with a switch, the whole item toggles it */
@Composable
public fun SwitchSettingsItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
        .padding(start = 16.dp),
) {
    ItemTexts(headline, listOfNotNull(supporting?.let(::AnnotatedString)), Modifier.enabledAlpha(enabled))
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/**
 * A [SettingsGroup] item that both toggles a feature and, once it is on, opens the page holding
 * the feature's details: the text and the chevron open the page, the switch toggles. With
 * [openableWhenOff], the page opens whatever the switch is.
 *
 * [supporting] holds alternatives, from the most detailed to the shortest: the first one that fits
 * a single line is shown, the last one being cut if none does.
 */
@Composable
public fun SwitchNavigationSettingsItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: List<AnnotatedString> = emptyList(),
    enabled: Boolean = true,
    openableWhenOff: Boolean = false,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.settingsItem(MaterialTheme.colorScheme.settingsItem),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .weight(1f)
            .clickable(enabled = enabled && (checked || openableWhenOff), onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
    ) {
        ItemTexts(headline, supporting, Modifier.enabledAlpha(enabled), singleLineSupporting = true)
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.chevron_right_24px),
            contentDescription = "Open $headline",
            modifier = Modifier
                .size(32.dp)
                .enabledAlpha(enabled && (checked || openableWhenOff)),
        )
    }
    VerticalDivider(Modifier.height(40.dp))
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/**
 * A [SettingsGroup] item with a button at its end, such as one removing the item. [struckOut]
 * greys out and strikes through the headline alone, without changing the item's height, the
 * [action] staying usable (e.g. to undo a removal). Without [enabled], the texts are greyed out,
 * the [action] staying usable too.
 */
@Composable
public fun ActionSettingsItem(
    headline: String,
    modifier: Modifier = Modifier,
    // A String, or an AnnotatedString to style part of it
    supporting: CharSequence? = null,
    struckOut: Boolean = false,
    enabled: Boolean = true,
    headlineColor: Color = Color.Unspecified,
    action: @Composable () -> Unit,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .padding(start = 16.dp, end = 4.dp),
) {
    ItemTexts(
        headline = headline,
        supporting = listOfNotNull(supporting?.let { it as? AnnotatedString ?: AnnotatedString(it.toString()) }),
        modifier = Modifier.enabledAlpha(enabled),
        headlineModifier = Modifier.enabledAlpha(struckOut.not()),
        headlineColor = headlineColor,
        headlineDecoration = TextDecoration.LineThrough.takeIf { struckOut },
    )
    action()
}

/**
 * A [SettingsGroup] item made of texts. [onClick] makes the whole item clickable, [opensPage] adding
 * a chevron hinting it opens a page rather than a dialog.
 */
@Composable
public fun TextSettingsItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    opensPage: Boolean = false,
    enabled: Boolean = true,
    headlineColor: Color = Color.Unspecified,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .run { onClick?.let { clickable(enabled = enabled, onClick = it) } ?: this }
        .padding(start = 16.dp, end = if (opensPage) 8.dp else 16.dp),
) {
    ItemTexts(
        headline = headline,
        supporting = listOfNotNull(supporting?.let(::AnnotatedString)),
        modifier = Modifier.enabledAlpha(enabled),
        headlineColor = headlineColor,
    )
    if (opensPage) Icon(
        imageVector = ImageVector.vectorResource(R.drawable.chevron_right_24px),
        contentDescription = null,
        modifier = Modifier
            .size(32.dp)
            .enabledAlpha(enabled),
    )
}

/**
 * A [SettingsGroup] item choosing one of a few [options] with segmented buttons. The buttons sit at
 * the item's end while the headline fits next to them on one line. Otherwise, as with a large font
 * or display size, they go below it and take the item's whole width.
 */
@Suppress("LongMethod")
@Composable
public fun <T> SegmentedSettingsItem(
    headline: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
): Unit = Layout(
    contents = listOf(
        { Text(headline, style = MaterialTheme.typography.bodyLarge) },
        {
            SingleChoiceSegmentedButtonRow {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        // The check mark would widen the selected segment, its fill already tells it apart
                        icon = {},
                        label = { Text(label(option)) },
                    )
                }
            }
        },
    ),
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .padding(horizontal = 16.dp),
) { (headlineMeasurables, buttonsMeasurables), constraints ->
    val headline = headlineMeasurables.single()
    val buttons = buttonsMeasurables.single()
    val width = constraints.maxWidth
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val padding = 16.dp.roundToPx()
    val sideBySide = headline.maxIntrinsicWidth(Constraints.Infinity) + padding +
        buttons.maxIntrinsicWidth(Constraints.Infinity) <= width
    if (sideBySide) {
        val buttonsPlaceable = buttons.measure(loose)
        val headlinePlaceable = headline.measure(loose.copy(maxWidth = width - padding - buttonsPlaceable.width))
        val height = maxOf(headlinePlaceable.height + 2 * padding, buttonsPlaceable.height)
        layout(width, height) {
            headlinePlaceable.place(0, (height - headlinePlaceable.height) / 2)
            buttonsPlaceable.place(width - buttonsPlaceable.width, (height - buttonsPlaceable.height) / 2)
        }
    } else {
        val headlinePlaceable = headline.measure(loose)
        // A fixed width spreads the segments evenly over the whole item
        val buttonsPlaceable = buttons.measure(Constraints.fixedWidth(width))
        // The buttons' touch target already pads them vertically, hence the smaller gaps around them
        val gap = 8.dp.roundToPx()
        layout(width, padding + headlinePlaceable.height + gap + buttonsPlaceable.height + gap) {
            headlinePlaceable.place(0, padding)
            buttonsPlaceable.place(0, padding + headlinePlaceable.height + gap)
        }
    }
}

/** A [SettingsGroup] item of a single choice list, the whole item selects it */
@Composable
public fun RadioSettingsItem(
    headline: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(end = 16.dp),
) {
    RadioButton(
        selected = selected,
        onClick = null,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ItemTexts(headline, emptyList())
}

/** A [SettingsGroup] item of a multiple choice list, the whole item toggles it */
@Composable
public fun CheckboxSettingsItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        )
        .padding(end = 16.dp),
) {
    Checkbox(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ItemTexts(headline, emptyList(), Modifier.enabledAlpha(enabled))
}

/**
 * A [SettingsGroup] item showing or hiding the items following it, such as the less relevant
 * choices of a list
 */
@Composable
public fun ExpandSettingsItem(
    headline: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .clickable(onClick = onClick)
        .padding(start = 16.dp, end = 8.dp),
) {
    ItemTexts(headline, emptyList(), headlineColor = MaterialTheme.colorScheme.onSurfaceVariant)
    Icon(
        imageVector = ImageVector.vectorResource(R.drawable.chevron_right_24px),
        contentDescription = if (expanded) "Hide $headline" else "Show $headline",
        modifier = Modifier
            .size(32.dp)
            .rotate(if (expanded) -90f else 90f),
    )
}

/** The explanation at the top of a settings page, above its first [SettingsSectionHeader] */
@Composable
public fun SettingsIntro(
    text: String,
    modifier: Modifier = Modifier,
): Unit = Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
)

/** A short explanation between a [SettingsSectionHeader] and its [SettingsGroup], or under a group */
@Composable
public fun SettingsSectionNote(
    text: String,
    modifier: Modifier = Modifier,
): Unit = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
)

/** Headline and supporting text of an item, taking the row's remaining width */
@Composable
private fun RowScope.ItemTexts(
    headline: String,
    supporting: List<AnnotatedString>,
    modifier: Modifier = Modifier,
    singleLineSupporting: Boolean = false,
    headlineModifier: Modifier = Modifier,
    headlineDecoration: TextDecoration? = null,
    headlineColor: Color = Color.Unspecified,
) = Column(
    modifier = modifier
        .weight(1f)
        .padding(vertical = 16.dp),
) {
    Text(
        text = headline,
        style = MaterialTheme.typography.bodyLarge,
        color = headlineColor,
        textDecoration = headlineDecoration,
        modifier = headlineModifier,
    )
    if (supporting.isEmpty()) return@Column
    // Same breathing room as the system settings between the two texts
    Spacer(Modifier.height(4.dp))
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (singleLineSupporting.not()) {
        supporting.forEach { Text(it, style = style, color = color) }
        return@Column
    }
    BoxWithConstraints {
        val measurer = rememberTextMeasurer()
        val width = constraints.maxWidth
        Text(
            text = supporting.firstOrNull { candidate ->
                measurer
                    .measure(candidate, style, maxLines = 1, constraints = Constraints(maxWidth = width))
                    .hasVisualOverflow
                    .not()
            } ?: supporting.last(),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A [SettingsGroup] item holding anything else than a switch */
@Composable
public fun SettingsItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
): Unit = Box(
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .padding(horizontal = 16.dp, vertical = 12.dp),
) {
    content()
}

private val ColorScheme.settingsItem: Color
    get() = surfaceColorAtElevation(3.dp)

private fun Modifier.settingsItem(color: Color): Modifier = this
    .fillMaxWidth()
    .clip(RoundedCornerShape(4.dp))
    .background(color)

private fun Modifier.enabledAlpha(enabled: Boolean): Modifier = alpha(if (enabled) 1f else DISABLED_ALPHA)

private const val DISABLED_ALPHA = 0.38f

@Preview
@Composable
internal fun SettingsGroupPreview() {
    Column {
        SettingsSectionHeader("Section")
        SettingsGroup {
            SwitchSettingsItem(headline = "Switch", checked = true, onCheckedChange = {})
            SwitchSettingsItem(
                headline = "Disabled switch",
                supporting = "With a supporting text",
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
            SwitchNavigationSettingsItem(
                headline = "Feature with details",
                supporting = listOf("A long summary of every detail of the feature", "A short summary")
                    .map(::AnnotatedString),
                checked = true,
                onCheckedChange = {},
                onClick = {},
            )
            RadioSettingsItem(headline = "Choice", selected = true, onClick = {})
            CheckboxSettingsItem(headline = "Multiple choice", checked = true, onCheckedChange = {})
            CheckboxSettingsItem(
                headline = "Disabled multiple choice",
                checked = true,
                enabled = false,
                onCheckedChange = {},
            )
            ExpandSettingsItem(headline = "More choices", expanded = false, onClick = {})
            TextSettingsItem(headline = "Page", supporting = "Current value", onClick = {}, opensPage = true)
            SegmentedSettingsItem(
                headline = "Segments",
                options = listOf("A", "B", "C"),
                selected = "B",
                onSelect = {},
                label = { it },
            )
            SettingsItem { Text("Anything else") }
        }
    }
}

@Preview(widthDp = 360, fontScale = 2f)
@Composable
internal fun SegmentedSettingsItemLargeFontPreview() {
    SettingsGroup {
        SegmentedSettingsItem(
            headline = "Pressure in",
            options = listOf("kPa", "bar", "psi"),
            selected = "psi",
            onSelect = {},
            label = { it },
        )
        SegmentedSettingsItem(
            headline = "Temperature in",
            options = listOf("°C", "°F"),
            selected = "°C",
            onSelect = {},
            label = { it },
        )
    }
}
