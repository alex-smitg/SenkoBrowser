package com.alexsmitg.senkobrowser

import android.app.Activity
import android.app.ComponentCaller
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult


import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.net.HttpURLConnection
import java.net.URL

private var geckoRuntime: GeckoRuntime? = null

class MainActivity : AppCompatActivity() {
    private lateinit var geckoView: GeckoView
    private lateinit var editText: EditText
    private lateinit var progressView: ProgressBar
    private lateinit var pageTitleText: TextView

    private var currentUrl: String = ""
    private var canGoBack: Boolean = false

    private var geckoSession: GeckoSession? = null

    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private var pendingFilePrompt: GeckoSession.PromptDelegate.FilePrompt? = null

    private var blocked_websites: Array<String> = emptyArray()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        geckoView = findViewById(R.id.geckoview)
        progressView = findViewById(R.id.pageProgress)
        pageTitleText = findViewById(R.id.pageTitleText)
        val homeImage: ImageView = findViewById(R.id.homeButton)
        homeImage.setOnClickListener { view ->
            go("resource://android/assets/index.html")
        }

        setupEditText()


        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (pendingFilePrompt == null) return@registerForActivityResult

            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val uris = mutableListOf<Uri>()
                data?.data?.let { uris.add(it) }
                data?.clipData?.let {
                    for (i in 0 until it.itemCount) uris.add(it.getItemAt(i).uri)
                }
                pendingFilePrompt?.confirm(this, uris.toTypedArray())

            } else {
                pendingFilePrompt?.dismiss()
            }
            pendingFilePrompt = null
        }



        onBackPressedDispatcher.addCallback(this) {
            if (canGoBack) {
                geckoSession?.goBack()
            } else {
                finish()
            }

        }

        if (geckoRuntime == null) {
            geckoRuntime = GeckoRuntime.create(this)
        }

        if (geckoSession == null) {
            geckoSession = GeckoSession()

            if (geckoRuntime != null) {
                geckoSession!!.open(geckoRuntime!!)
                geckoSession!!.progressDelegate = createProgressDelegate()
                geckoSession!!.settings.useTrackingProtection = true
                geckoSession!!.contentDelegate = createContentDelegate()
                geckoSession!!.navigationDelegate = createNavigationDelegate()
                geckoSession!!.promptDelegate = object : GeckoSession.PromptDelegate {
                    override fun onFilePrompt(
                        p0: GeckoSession,
                        prompt: GeckoSession.PromptDelegate.FilePrompt
                    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse?>? {
                        pendingFilePrompt = prompt

                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = if (prompt.mimeTypes?.isNotEmpty() ?: false) prompt.mimeTypes?.get(0) else "/"
                        }

                        filePickerLauncher.launch(intent)
                        return super.onFilePrompt(p0, prompt)
                    }

                }


                geckoView.setSession(geckoSession!!)
            }
        }

        go("resource://android/assets/index.html")
        window.statusBarColor = Color.parseColor("#FFC107")


    }

    private fun go(text: String) {
        geckoSession?.loadUri(text)
        geckoView.requestFocus()
    }

    private fun setupEditText() {
        editText = findViewById(R.id.editText)
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                go(editText.text.toString())
                return@setOnEditorActionListener false

            }
            false
        }
    }
    


    private fun createNavigationDelegate(): GeckoSession.NavigationDelegate {
        return object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                p0: GeckoSession,
                url: String?,
                p2: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                p3: Boolean
            ) {
                if (url != null) {
                    currentUrl = url
                    editText.setText(currentUrl)
                    for (a: String in blocked_websites) {

                        if (a.lowercase() in url.lowercase()) {
                            go("about:blank")
                        }
                    }
                };

            }

            override fun onCanGoBack(p0: GeckoSession, p1: Boolean) {
                canGoBack = p1
            }

            override fun onCanGoForward(p0: GeckoSession, p1: Boolean) {
                super.onCanGoForward(p0, p1)
            }

            override fun onLoadRequest(
                p0: GeckoSession,
                p1: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                return super.onLoadRequest(p0, p1)
            }

            override fun onSubframeLoadRequest(
                p0: GeckoSession,
                p1: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                return super.onSubframeLoadRequest(p0, p1)
            }

            override fun onNewSession(p0: GeckoSession, p1: String): GeckoResult<GeckoSession>? {
                go(p1)
                return super.onNewSession(p0, p1)
            }

            override fun onLoadError(
                p0: GeckoSession,
                p1: String?,
                p2: WebRequestError
            ): GeckoResult<String>? {
                p1?.let { Log.d("fox", it) }
                return GeckoResult.fromValue("data:text/html," + "<p>Error TwT</p>")
            }
        }
    }

    private fun createContentDelegate(): GeckoSession.ContentDelegate {
        return object : GeckoSession.ContentDelegate {
            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement
            ) {
                Log.d(
                    "fox",
                    "onContextMenu screenX="
                            + screenX
                            + " screenY="
                            + screenY
                            + " type="
                            + element.type
                            + " linkUri="
                            + element.linkUri
                            + " title="
                            + element.title
                            + " alt="
                            + element.altText
                            + " srcUri="
                            + element.srcUri
                );

                val view = layoutInflater.inflate(R.layout.context_menu, null)

                if (element.title.isNullOrBlank()) {
                    view.findViewById<TextView>(R.id.textTitle).text = element.altText;
                } else {
                    view.findViewById<TextView>(R.id.textTitle).text = element.title;
                }

                view.findViewById<TextView>(R.id.textSrcUri).text = element.srcUri;
                view.findViewById<TextView>(R.id.textLinkUri).text = element.linkUri;

                view.findViewById<Button>(R.id.button).setOnClickListener {
                    element.linkUri?.let { it1 -> go(it1) }
                }

                AlertDialog.Builder(this@MainActivity).setView(view).create().show()
            }

            override fun onKill(p0: GeckoSession) {
                super.onKill(p0)
                geckoRuntime?.let { p0.open(it) }
                if (geckoView.session != null) {
                    geckoView.setSession(p0)
                }
                go(editText.text.toString())

            }

            override fun onExternalResponse(p0: GeckoSession, response: WebResponse) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val connection = URL(response.uri).openConnection() as HttpURLConnection
                    connection.requestMethod = "HEAD"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    val mime = connection.contentType
                    connection.disconnect()
                    if (mime.startsWith("application/") || mime.startsWith("image/")
                        || mime.startsWith("video/") || mime.startsWith("audio/")) {
                        val uri = Uri.parse(response.uri)
                        val request = DownloadManager.Request(uri)
                            .setTitle(uri.lastPathSegment)
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                uri.lastPathSegment
                            )

                        val downloadManager =
                            applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        downloadManager.enqueue(request)

                    }


                    }

                super.onExternalResponse(p0, response)
            }

            override fun onTitleChange(p0: GeckoSession, p1: String?) {
                pageTitleText.text = p1
                Log.d("fox", p1 ?: "a")
            }
        }
    }

    private fun createProgressDelegate(): GeckoSession.ProgressDelegate {
        return object : GeckoSession.ProgressDelegate {
            override fun onPageStart(p0: GeckoSession, p1: String) {

            }

            override fun onPageStop(p0: GeckoSession, p1: Boolean) {
                super.onPageStop(p0, p1)
            }


            override fun onProgressChange(session: GeckoSession, progress: Int) {
                progressView.progress = progress
                if (progress in 1..99) {
                    progressView.visibility = View.VISIBLE
                } else {
                    progressView.progress = 0
                }
                super.onProgressChange(session, progress)
            }

            override fun onSecurityChange(
                p0: GeckoSession,
                p1: GeckoSession.ProgressDelegate.SecurityInformation
            ) {
                super.onSecurityChange(p0, p1)
            }

            override fun onSessionStateChange(p0: GeckoSession, p1: GeckoSession.SessionState) {
                super.onSessionStateChange(p0, p1)
            }

        }
    }


    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)

        if (Intent.ACTION_SHUTDOWN.equals(intent.action)) {
            //runtime destroy
        }

        setIntent(intent)

        if (intent.data != null) {
            val uri: Uri? = intent.data;
            if (uri != null) {
                Log.d("fox", uri.toString())
                go(uri.toString())
            }
        }
    }
}