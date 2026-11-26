package com.folio.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.folio.app.ui.FolioApp
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
  private val requestedSymbol = mutableStateOf<Pair<String, Long>?>(null)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    handleIntent(intent)
    setContent {
      FolioApp(requestedSymbol.value)
    }
  }
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }
  private fun handleIntent(intent: Intent?) {
    intent?.getStringExtra("symbol")?.let { requestedSymbol.value = it to System.nanoTime() }
  }
}
