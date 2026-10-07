package com.rodrigomachado.riff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.rodrigomachado.riff.core.ui.theme.RiffTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RiffTheme {
                Scaffold(Modifier.fillMaxSize()) { padding ->
                    CatalogScreen(Modifier.padding(padding))
                }
            }
        }
    }
}
