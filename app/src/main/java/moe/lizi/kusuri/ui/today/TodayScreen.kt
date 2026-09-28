package moe.lizi.kusuri.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import moe.lizi.kusuri.R
import moe.lizi.kusuri.ui.components.PlaceholderScreen

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        text = stringResource(R.string.placeholder_coming_soon),
        modifier = modifier,
    )
}
