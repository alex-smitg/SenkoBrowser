package com.alexsmitg.senkobrowser

import android.app.Activity
import android.app.ComponentCaller
import android.app.DownloadManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.internal.WindowUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult


import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Matcher
import java.util.regex.Pattern

private var geckoRuntime: GeckoRuntime? = null



class MainActivity : AppCompatActivity() {
    private lateinit var geckoView: GeckoView
    private lateinit var editText: EditText
    private lateinit var progressView: ProgressBar
    private lateinit var pageTitleText: TextView
    private lateinit var bottomMenu: LinearLayout
    private lateinit var hideBarsButton: AppCompatButton
    private lateinit var topFrame: FrameLayout
    private lateinit var bottomMenuFrame: FrameLayout

    private var currentUrl: String = ""

    private var geckoSession: GeckoSession? = null

    private var fullScreen: Boolean = false
    private var desktopMode: Boolean = false
    private var canGoBack: Boolean = false


    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private var pendingFilePrompt: GeckoSession.PromptDelegate.FilePrompt? = null

    private var blocked_websites: MutableList<String> = mutableListOf<String>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val fileName = "BLOCK_LIST"
        val str = application.assets.open(fileName).bufferedReader().use{
            it.readText()
        }



        blocked_websites = mutableListOf<String>()
        str.split("\n").forEach {
            blocked_websites.add(it)
        }
        Log.i("fox", blocked_websites.toString())


        geckoView = findViewById(R.id.geckoview)
        progressView = findViewById(R.id.pageProgress)
        pageTitleText = findViewById(R.id.pageTitleText)
        bottomMenu = findViewById(R.id.bottomMenu)
        hideBarsButton = findViewById(R.id.hideBarsButton)
        topFrame = findViewById(R.id.topFrame)
        bottomMenuFrame = findViewById(R.id.bottomMenuFrame)
        val homeImage: ImageView = findViewById(R.id.homeButton)
        homeImage.setOnClickListener { view ->
            go("resource://android/assets/index.html")
        }

        setupEditText()

        hideBarsButton.setOnClickListener { view ->
            progressView.visibility = View.GONE
            editText.visibility = View.GONE
            bottomMenu.visibility = View.GONE
        }


        val wic = WindowCompat.getInsetsController(window, window.decorView)
        wic.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE


        //is not working
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) {view, windowInsets ->
            Log.d("fox", "///")
            Toast.makeText(applicationContext, "immers", Toast.LENGTH_SHORT).show()
            if (windowInsets.isVisible(WindowInsetsCompat.Type.statusBars()) ||
                windowInsets.isVisible(WindowInsetsCompat.Type.navigationBars())) {

            }

            ViewCompat.onApplyWindowInsets(view, windowInsets)
        }


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


        val file = File(application.filesDir, "ublock.xpi")

        application.assets.open("extensions/ublock.xpi").use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }



        val res: GeckoResult<org.mozilla.geckoview.WebExtension>? = geckoRuntime?.webExtensionController?.install(file.toURI().toString())


        geckoRuntime?.webExtensionController?.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                p0: WebExtension,
                p1: Array<out String?>,
                p2: Array<out String?>,
                p3: Array<out String?>
            ): GeckoResult<WebExtension.PermissionPromptResponse?>? {
                val result = GeckoResult<WebExtension.PermissionPromptResponse?>()

                result.complete(WebExtension.PermissionPromptResponse(true, true, false))
                return result
            }
        }

        geckoRuntime?.webExtensionController?.list()?.then( { it ->
            Log.d("fox",  it.toString())
            GeckoResult.fromValue(null)
        },
        { ex ->
            GeckoResult.fromValue(null)
        })





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



                    override fun onChoicePrompt(
                        p0: GeckoSession,
                        choicePrompt: GeckoSession.PromptDelegate.ChoicePrompt
                    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse?>? {

                        val builder = AlertDialog.Builder(this@MainActivity)
                        builder.setTitle(choicePrompt.message)

                        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse?>()

                        val array: Array<CharSequence> = Array<CharSequence>(choicePrompt.choices.size, {i -> ""})


                        choicePrompt.choices.forEachIndexed { i: Int, c: GeckoSession.PromptDelegate.ChoicePrompt.Choice ->
                            array[i] = c.label
                            Log.d("fox", c.label)
                        }
                        builder.setItems(array) { dialog, which ->
                            Log.d("fox", which.toString())
                            result.complete(choicePrompt.confirm(choicePrompt.choices[which]))
                        }
                        builder.setOnCancelListener {
                            result.complete(choicePrompt.dismiss())
                        }

                        builder.create().show()

                        return result

                    }

                }


                geckoView.setSession(geckoSession!!)
            }
        }






        val action: String? = intent?.action
        val data: Uri? = intent?.data
        if (action.equals("android.intent.action.VIEW")) {
            go(data.toString())
        } else {
            go("resource://android/assets/index.html")
        }

        Log.d("fox", action ?: "...")
        Log.d("fox", data.toString())


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
                            go("resource://android/assets/index.html")
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
                return GeckoResult.fromValue("data:text/html," + "<p>Error TwT</p>" + "<p>$p1</p>")
            }
        }
    }

    private fun getFilename(response: WebResponse): String {
        var filename: String
        var contentDispositionHeader: String
        if (response.headers.containsKey("content-disposition")) {
            contentDispositionHeader = response.headers["content-disposition"] ?: " "
        } else {
            contentDispositionHeader = response.headers.getOrDefault("Content-Disposition", "default filename=SenkoDownload")
        }
        val pattern: Pattern = Pattern.compile("(filename=\"?)(.+)(\"?)")
        val matcher: Matcher = pattern.matcher(contentDispositionHeader)

        if (matcher.find()) {
            filename = matcher.group(2).replace("\\s", "%20").replace("\"", "")
        } else {
            filename = "SenkoDownload"
        }

        return filename
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadFile(response: WebResponse) {
        val filename = getFilename(response)
        Toast.makeText(applicationContext, "d $filename", Toast.LENGTH_SHORT).show()

        var mime: String? = response.headers["Content-Type"]
        if (mime != null) {
            if (";" in mime) {
                mime = mime.split(";")[0].trim()
            } else {
                mime = mime.trim()
            }
        }
        if (mime == null || mime.isEmpty()) {
            mime = "*/*"
        }

        val contentValues: ContentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mime)
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)

        val collection: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val fileUri: Uri? = contentResolver.insert(collection, contentValues)
        if (fileUri == null) {
            Toast.makeText(applicationContext, "Unable to access directory", Toast.LENGTH_SHORT).show()
        }
        Toast.makeText(applicationContext, "Downloading $filename", Toast.LENGTH_SHORT).show()

        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)


        if (fileUri != null) {
            try {
                val out = contentResolver.openOutputStream(fileUri)
                var len = 0
                while (true) {
                    len = response.body?.read(buffer) ?: -1
                    if (len == -1) {
                        break
                    }
                    out?.write(buffer, 0, len)

                }

            } catch (e: Exception) {
                Log.d("fox", ":(")
            }
        }
        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        if (fileUri != null) {
            contentResolver.update(fileUri, contentValues, null, null)
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
                            + " baseUri="
                            + element.baseUri
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
                    go(element.baseUri ?: "")
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

            override fun onFullScreen(geckoSession: GeckoSession, fs : Boolean) {
                fullScreen = fs

                val wic = WindowCompat.getInsetsController(window, window.decorView)
                if (fullScreen) {
                    progressView.visibility = View.GONE
                    editText.visibility = View.GONE
                    bottomMenu.visibility = View.GONE
                    bottomMenuFrame.visibility = View.GONE
                    topFrame.visibility = View.GONE
                    supportActionBar?.hide()
                    wic.hide(WindowInsetsCompat.Type.systemBars())

                } else {
                    editText.visibility = View.VISIBLE
                    bottomMenu.visibility = View.VISIBLE
                    bottomMenuFrame.visibility = View.VISIBLE
                    topFrame.visibility = View.VISIBLE
                    supportActionBar?.show()
                    wic.show(WindowInsetsCompat.Type.systemBars())
                }

                super.onFullScreen(geckoSession, fs)
            }

            @RequiresApi(Build.VERSION_CODES.Q) //TODO: surround with api check
            override fun onExternalResponse(p0: GeckoSession, response: WebResponse) {
                downloadFile(response)
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
                    progressView.visibility = View.GONE
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