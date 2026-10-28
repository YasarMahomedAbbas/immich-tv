package io.github.yasarmahomedabbas.immichtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.yasarmahomedabbas.immichtv.ui.home.HomeScreen
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImmichTvTheme {
                HomeScreen()
            }
        }
    }
}
