package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors

private val RedShadow = 3.dp
private val BlackShadow = 6.dp

/** Letreiro de pôster: texto em papel com sombras duras vermelha e preta. Lido uma vez só, como título. */
@Composable
fun PosterTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayMedium,
    /** Lido como título pelo TalkBack. O placar usa o mesmo visual sem ser título. */
    heading: Boolean = true,
) {
    Box(modifier.padding(end = BlackShadow, bottom = BlackShadow)) {
        Text(
            text,
            style = style,
            color = RiffColors.Ink,
            modifier = Modifier.offset(BlackShadow, BlackShadow).clearAndSetSemantics {},
        )
        Text(
            text,
            style = style,
            color = RiffColors.TourRed,
            modifier = Modifier.offset(RedShadow, RedShadow).clearAndSetSemantics {},
        )
        Text(
            text,
            style = style,
            color = RiffColors.Paper,
            modifier = if (heading) Modifier.semantics { heading() } else Modifier,
        )
    }
}
