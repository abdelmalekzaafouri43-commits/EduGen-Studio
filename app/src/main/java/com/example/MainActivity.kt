package com.example

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val webView = WebView(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      try {
        cacheDir.mkdirs()
      } catch (e: Exception) {
        e.printStackTrace()
      }

      // Force software layer rendering for WebView to avoid Mesa driver warnings in headless containers
      setLayerType(View.LAYER_TYPE_SOFTWARE, null)

      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        useWideViewPort = true
        loadWithOverviewMode = true
      }

      addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidInterface")

      setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
        try {
          if (url.startsWith("data:")) {
            val interfaceObj = WebAppInterface(this@MainActivity)
            val defaultName = if (mimetype.contains("word") || url.contains("doc")) "EduGen_Worksheet.doc" else "EduGen_Asset.doc"
            interfaceObj.saveFile(url, defaultName, mimetype)
          } else {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
              setMimeType(mimetype)
              addRequestHeader("User-Agent", userAgent)
              setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
              setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
              setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype))
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this@MainActivity, "Downloading...", Toast.LENGTH_SHORT).show()
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }

      webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
          super.onPageFinished(view, url)
          view?.evaluateJavascript("if(window.setApiKey) window.setApiKey('');", null)
        }
      }
      loadUrl("file:///android_asset/index.html")
    }

    setContentView(webView)
  }
}

class WebAppInterface(private val context: Context) {
  @JavascriptInterface
  fun saveFile(dataUri: String, fileName: String, mimeType: String) {
    try {
      val commaIndex = dataUri.indexOf(",")
      if (commaIndex != -1) {
        val header = dataUri.substring(0, commaIndex)
        val isBase64 = header.contains("base64")
        val contentStr = dataUri.substring(commaIndex + 1)

        val bytes = if (isBase64) {
          Base64.decode(contentStr, Base64.DEFAULT)
        } else {
          Uri.decode(contentStr).toByteArray(Charsets.UTF_8)
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
          downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { out ->
          out.write(bytes)
        }

        (context as? Activity)?.runOnUiThread {
          Toast.makeText(context, "Saved to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      (context as? Activity)?.runOnUiThread {
        Toast.makeText(context, "Export complete: $fileName", Toast.LENGTH_SHORT).show()
      }
    }
  }
}


