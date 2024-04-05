package com.example.hackingprojectjniapp

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.hackingprojectjniapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    val STORAGE = "storage"
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileRequestCode = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        val myWebView: WebView = findViewById(R.id.webview)
        myWebView.settings.javaScriptEnabled = true
        myWebView.settings.domStorageEnabled = true

        myWebView.webViewClient = WebViewClient()
        myWebView.webChromeClient = object : WebChromeClient() {
            // For Android 5.0+
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams.createIntent()
                try {
                    startActivityForResult(intent, fileRequestCode)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }

                return true
            }
        }

//        myWebView.loadUrl("http://16.16.238.235:8080/noticewrite")
        myWebView.loadUrl("http://10.0.2.2:3001")
        myWebView.addJavascriptInterface(WebAppInterface(this), "Android")

        myWebView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val request = DownloadManager.Request(Uri.parse(url))
            var fileName = contentDisposition
            if (fileName != null && fileName.isNotEmpty()) {
                val idxFileName = fileName.indexOf("filename=")
                if (idxFileName > -1) {
                    fileName = fileName.substring(idxFileName + 9).trim { it <= ' ' }
                }
                if (fileName.endsWith(";")) {
                    fileName = fileName.substring(0, fileName.length - 1)
                }
                if (fileName.startsWith("\"") && fileName.startsWith("\"")) {
                    fileName = fileName.substring(1, fileName.length - 1)
                }
            } else {
                // 파일명(확장자포함) 확인이 안되었을 때 기존방식으로 진행
                fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            }
            val adjustedMimeType = if (mimetype.isNullOrEmpty() || mimetype == "application/octet-stream") {
                guessMimeTypeFromUrl(url)
            } else {
                mimetype
            }
            request.setMimeType(adjustedMimeType)
            val cookies = CookieManager.getInstance().getCookie(url)
            request.addRequestHeader("cookie", cookies)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading file...")
            request.setTitle(fileName)
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )

            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(applicationContext, "다운로드 파일", Toast.LENGTH_LONG).show()
        }

//       여기가 C++ 관련 코드!!!
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // Example of a call to a native method
//        binding.sampleText.text = stringFromJNI()
    }

    fun guessMimeTypeFromUrl(url: String): String {
        // URL에서 파일 확장자 추출 및 MIME 타입 추측 로직 구현
        // 이 부분은 실제 파일 유형과 URL 구조에 따라 달라질 수 있음
        return when {
            url.endsWith(".jpg") || url.endsWith(".jpeg") -> "image/jpeg"
            url.endsWith(".png") -> "image/png"
            url.endsWith(".apk") -> "application/vnd.android.package-archive"
            // 기타 다른 파일 확장자에 대한 처리
            else -> "application/octet-stream"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == fileRequestCode) {
            if (resultCode == Activity.RESULT_OK) {
                val result = if (data == null || resultCode != Activity.RESULT_OK) null else data.data
                filePathCallback?.onReceiveValue(arrayOf(Uri.parse(result.toString())))
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val myWebView: WebView = findViewById(R.id.webview)
            if(myWebView.canGoBack()){
                myWebView.goBack()
            }
            else {
                finish()
            }
        }
    }

    class WebAppInterface(private val mContext : Context) {
        @JavascriptInterface
        fun saveMobileStorage(key:String,value:String){
            (mContext as MainActivity).writeJwtSharedPreference(key,value)
        }

        @JavascriptInterface
        fun loadMobileStorage(key:String):String{
            val mobileVal = (mContext as MainActivity).readJwtSharedPreference(key)
            return mobileVal
        }
    }

    fun writeJwtSharedPreference(key:String,value:String) {
        val sp = getSharedPreferences(STORAGE,Context.MODE_PRIVATE)
        val editor = sp.edit()
        editor.putString(key,value)
        editor.apply()
    }

    fun readJwtSharedPreference(key:String):String{
        val sp = getSharedPreferences(STORAGE,Context.MODE_PRIVATE)
        return sp.getString(key,"") ?: ""
    }

    /**
     * A native method that is implemented by the 'hackingprojectjniapp' native library,
     * which is packaged with this application.
     */

    //       여기 아래부터 C++ 관련 코드!!!
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'hackingprojectjniapp' library on application startup.
        init {
            System.loadLibrary("hackingprojectjniapp")
        }
    }
}