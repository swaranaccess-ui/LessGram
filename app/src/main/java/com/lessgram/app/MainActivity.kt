package com.lessgram.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout

    private var currentSection = SECTION_HOME

    /*
     * When a Reel is opened from a DM, we remember the first Reel URL.
     * If Instagram tries to move to another Reel, we stop it.
     */
    private var openedDmReelUrl: String? = null
    private var reelMode = false

    private var downX = 0f
    private var downY = 0f
    private var blockingReelSwipe = false

    companion object {
        private const val SECTION_HOME = 0
        private const val SECTION_REELS = 1
        private const val SECTION_DM = 2
        private const val SECTION_SEARCH = 3
        private const val SECTION_PROFILE = 4

        private const val INSTAGRAM = "https://www.instagram.com/"
        private const val INSTAGRAM_DM = "https://www.instagram.com/direct/inbox/"
        private const val INSTAGRAM_SEARCH = "https://www.instagram.com/explore/search/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        webView = WebView(this)

        configureWebView()
        setupWebViewNavigation()
        setupReelTouchBlocking()

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)

        /*
         * Start on the blocked Home screen.
         */
        showBlockedScreen(
            "You don't need to see\neverything.",
            "Your time is better spent building your own life."
        )

        addBottomNavigation()
        setupBackButton()
    }

    // ============================================================
    // WEBVIEW
    // ============================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {

        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)

        settings.allowFileAccess = false
        settings.allowContentAccess = true

        /*
         * Instagram needs cookies for login/session.
         */
        CookieManager.getInstance().setAcceptCookie(true)

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            CookieManager
                .getInstance()
                .setAcceptThirdPartyCookies(webView, true)
        }

        webView.setBackgroundColor(Color.BLACK)

        /*
         * Use Instagram's mobile website.
         */
        webView.settings.userAgentString =
            webView.settings.userAgentString +
                    " LessGram/1.0"
    }

    private fun setupWebViewNavigation() {

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                val url = request.url.toString()

                if (!url.contains("instagram.com")) {
                    return true
                }

                /*
                 * NEVER allow Instagram Home.
                 */
                if (isHomeUrl(url)) {
                    showBlockedScreen(
                        "You don't need to see\neverything.",
                        "Your time is better spent building your own life."
                    )

                    currentSection = SECTION_HOME
                    reelMode = false

                    return true
                }

                /*
                 * NEVER allow the Reels feed.
                 */
                if (isReelsFeed(url)) {

                    if (reelMode && openedDmReelUrl != null) {
                        /*
                         * This is probably Instagram trying to move
                         * from the shared Reel to another Reel.
                         */
                        return true
                    }

                    showBlockedScreen(
                        "One reel is enough.",
                        "You came here for one Reel. Not an endless feed."
                    )

                    currentSection = SECTION_REELS
                    reelMode = false

                    return true
                }

                /*
                 * A Reel opened from a DM is allowed.
                 */
                if (isIndividualReel(url)) {

                    if (!reelMode) {
                        reelMode = true
                        openedDmReelUrl = url
                    }

                    return false
                }

                /*
                 * DM is allowed.
                 */
                if (url.contains("/direct/")) {
                    currentSection = SECTION_DM
                    reelMode = false
                    openedDmReelUrl = null

                    return false
                }

                /*
                 * Search is allowed.
                 */
                if (isSearchUrl(url)) {
                    currentSection = SECTION_SEARCH
                    reelMode = false
                    openedDmReelUrl = null

                    return false
                }

                /*
                 * Profile pages are allowed.
                 */
                if (isProfileUrl(url)) {
                    currentSection = SECTION_PROFILE
                    reelMode = false
                    openedDmReelUrl = null

                    return false
                }

                return false
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {
                super.onPageFinished(view, url)

                /*
                 * If Instagram internally changes the URL while viewing
                 * a DM-shared Reel, don't allow another Reel.
                 */
                if (reelMode && openedDmReelUrl != null) {

                    if (
                        isIndividualReel(url) &&
                        !sameReel(openedDmReelUrl!!, url)
                    ) {
                        webView.goBack()
                        return
                    }
                }

                /*
                 * Remove Instagram's own bottom navigation when possible.
                 * LessGram provides its own navigation.
                 */
                hideInstagramNavigation()

                /*
                 * Keep Home/Explore/Reels from being exposed through
                 * Instagram's own UI.
                 */
                injectDistractionProtection()
            }
        }
    }

    // ============================================================
    // REEL SWIPE BLOCK
    // ============================================================

    private fun setupReelTouchBlocking() {

        webView.setOnTouchListener { _, event ->

            if (!reelMode) {
                blockingReelSwipe = false
                return@setOnTouchListener false
            }

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    blockingReelSwipe = false

                    false
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx = event.x - downX
                    val dy = event.y - downY

                    /*
                     * Vertical movement = Reel swipe.
                     *
                     * Consume it so Instagram cannot swipe to the
                     * next/previous Reel.
                     */
                    if (abs(dy) > abs(dx) && abs(dy) > 12f) {
                        blockingReelSwipe = true
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP -> {

                    if (blockingReelSwipe) {
                        blockingReelSwipe = false
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    blockingReelSwipe = false
                    true
                }

                else -> false
            }
        }
    }

    // ============================================================
    // BOTTOM NAVIGATION
    // ============================================================

    private fun addBottomNavigation() {

        val navHeight = dp(72)

        val nav = FrameLayout(this)

        nav.setBackgroundColor(Color.BLACK)

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            navHeight
        )

        params.gravity = android.view.Gravity.BOTTOM

        root.addView(nav, params)

        /*
         * HOME
         */
        val home = createNavButton("⌂")
        home.setOnClickListener {

            reelMode = false
            openedDmReelUrl = null

            currentSection = SECTION_HOME

            showBlockedScreen(
                "You don't need to see\neverything.",
                "Your time is better spent building your own life."
            )
        }

        addNavButton(nav, home, 0)

        /*
         * REELS
         */
        val reels = createNavButton("▷")

        reels.setOnClickListener {

            reelMode = false
            openedDmReelUrl = null

            currentSection = SECTION_REELS

            showBlockedScreen(
                "One reel is enough.",
                "You came here for one Reel. Not an endless feed."
            )
        }

        addNavButton(nav, reels, 1)

        /*
         * DM
         */
        val dm = createNavButton("✉")

        dm.setOnClickListener {

            reelMode = false
            openedDmReelUrl = null

            currentSection = SECTION_DM

            webView.visibility = View.VISIBLE

            webView.loadUrl(INSTAGRAM_DM)
        }

        addNavButton(nav, dm, 2)

        /*
         * SEARCH
         */
        val search = createNavButton("⌕")

        search.setOnClickListener {

            reelMode = false
            openedDmReelUrl = null

            currentSection = SECTION_SEARCH

            webView.visibility = View.VISIBLE

            /*
             * Directly open Instagram's search page.
             */
            webView.loadUrl(INSTAGRAM_SEARCH)
        }

        addNavButton(nav, search, 3)

        /*
         * PROFILE
         */
        val profile = createNavButton("◎")

        profile.setOnClickListener {

            reelMode = false
            openedDmReelUrl = null

            currentSection = SECTION_PROFILE

            webView.visibility = View.VISIBLE

            /*
             * IMPORTANT:
             * Do NOT load Instagram root.
             *
             * First try Instagram's account/profile destination.
             */
            webView.loadUrl("https://www.instagram.com/accounts/edit/")
        }

        addNavButton(nav, profile, 4)
    }

    private fun addNavButton(
        nav: FrameLayout,
        button: android.widget.TextView,
        position: Int
    ) {

        val width = resources.displayMetrics.widthPixels / 5

        val params = FrameLayout.LayoutParams(
            width,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        params.leftMargin = width * position

        nav.addView(button, params)
    }

    private fun createNavButton(
        text: String
    ): android.widget.TextView {

        val button = android.widget.TextView(this)

        button.text = text
        button.textSize = 32f
        button.gravity = android.view.Gravity.CENTER
        button.setTextColor(Color.WHITE)
        button.setBackgroundColor(Color.TRANSPARENT)

        return button
    }

    // ============================================================
    // BLOCKED SCREENS
    // ============================================================

    private fun showBlockedScreen(
        title: String,
        subtitle: String
    ) {

        webView.visibility = View.GONE

        /*
         * Find the custom blocked screen if it exists.
         */
        var blocked = root.findViewWithTag<View>("LESSGRAM_BLOCK")

        if (blocked == null) {

            val layout = android.widget.LinearLayout(this)

            layout.orientation =
                android.widget.LinearLayout.VERTICAL

            layout.gravity =
                android.view.Gravity.CENTER

            layout.setBackgroundColor(Color.BLACK)

            layout.tag = "LESSGRAM_BLOCK"

            /*
             * Add enough bottom padding so the message never
             * collides with the navigation bar.
             */
            layout.setPadding(
                dp(35),
                dp(40),
                dp(35),
                dp(120)
            )

            val icon = android.widget.TextView(this)

            icon.text = "⊘"
            icon.textSize = 54f
            icon.gravity = android.view.Gravity.CENTER
            icon.setTextColor(Color.WHITE)

            val circleParams =
                android.widget.LinearLayout.LayoutParams(
                    dp(100),
                    dp(100)
                )

            circleParams.gravity =
                android.view.Gravity.CENTER

            layout.addView(icon, circleParams)

            val titleView =
                android.widget.TextView(this)

            titleView.textSize = 32f
            titleView.setTextColor(Color.WHITE)
            titleView.gravity = android.view.Gravity.CENTER
            titleView.setTypeface(null, android.graphics.Typeface.NORMAL)

            val titleParams =
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )

            titleParams.topMargin = dp(35)

            layout.addView(titleView, titleParams)

            val subtitleView =
                android.widget.TextView(this)

            subtitleView.textSize = 20f
            subtitleView.setTextColor(Color.rgb(145, 145, 145))
            subtitleView.gravity = android.view.Gravity.CENTER

            val subtitleParams =
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )

            subtitleParams.topMargin = dp(18)

            layout.addView(subtitleView, subtitleParams)

            root.addView(
                layout,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            blocked = layout
        }

        val layout =
            blocked as android.widget.LinearLayout

        val titleView =
            layout.getChildAt(1) as android.widget.TextView

        val subtitleView =
            layout.getChildAt(2) as android.widget.TextView

        titleView.text = title
        subtitleView.text = subtitle

        blocked.visibility = View.VISIBLE
        blocked.bringToFront()

        /*
         * Put navigation back on top.
         */
        val nav = root.getChildAt(root.childCount - 2)

        if (nav != null) {
            nav.bringToFront()
        }
    }

    // ============================================================
    // INSTAGRAM PAGE
    // ============================================================

    private fun showInstagram() {

        val blocked =
            root.findViewWithTag<View>("LESSGRAM_BLOCK")

        blocked?.visibility = View.GONE

        webView.visibility = View.VISIBLE
        webView.bringToFront()

        /*
         * Navigation needs to stay above WebView.
         */
        val nav = root.getChildAt(root.childCount - 1)

        nav?.bringToFront()
    }

    // ============================================================
    // URL DETECTION
    // ============================================================

    private fun isHomeUrl(url: String): Boolean {

        val uri = android.net.Uri.parse(url)

        val path = uri.path ?: "/"

        return (
                path == "/" ||
                        path.isEmpty()
                )
    }

    private fun isReelsFeed(url: String): Boolean {

        val uri = android.net.Uri.parse(url)

        val path =
            (uri.path ?: "").lowercase()

        return (
                path == "/reels/" ||
                        path == "/reels" ||
                        path.startsWith("/reels/")
                )
    }

    private fun isIndividualReel(url: String): Boolean {

        val uri = android.net.Uri.parse(url)

        val path =
            (uri.path ?: "").lowercase()

        return (
                path.startsWith("/reel/") ||
                        path.startsWith("/reels/videos/")
                )
    }

    private fun isSearchUrl(url: String): Boolean {

        val uri = android.net.Uri.parse(url)

        val path =
            (uri.path ?: "").lowercase()

        return (
                path.contains("/explore/search") ||
                        path.contains("/search")
                )
    }

    private fun isProfileUrl(url: String): Boolean {

        val uri = android.net.Uri.parse(url)

        val path =
            (uri.path ?: "").trim('/')

        if (path.isEmpty()) {
            return false
        }

        /*
         * These are NOT profile pages.
         */
        val blocked = listOf(
            "accounts",
            "explore",
            "reels",
            "direct",
            "about",
            "developer",
            "privacy",
            "terms",
            "legal",
            "web"
        )

        val firstPart =
            path.split("/").firstOrNull()
                ?: return false

        return (
                !blocked.contains(firstPart.lowercase()) &&
                        !path.contains("search")
                )
    }

    private fun sameReel(
        first: String,
        second: String
    ): Boolean {

        val firstUri =
            android.net.Uri.parse(first)

        val secondUri =
            android.net.Uri.parse(second)

        val firstPath =
            firstUri.path ?: ""

        val secondPath =
            secondUri.path ?: ""

        return firstPath == secondPath
    }

    // ============================================================
    // INSTAGRAM UI PROTECTION
    // ============================================================

    private fun hideInstagramNavigation() {

        val javascript = """
            (function() {
                try {

                    var selectors = [
                        'nav',
                        '[role="navigation"]'
                    ];

                    selectors.forEach(function(selector) {

                        document.querySelectorAll(selector)
                            .forEach(function(el) {

                                if (el.closest('body')) {

                                    el.style.setProperty(
                                        'display',
                                        'none',
                                        'important'
                                    );
                                }
                            });
                    });

                } catch(e) {}
            })();
        """.trimIndent()

        webView.evaluateJavascript(javascript, null)
    }

    private fun injectDistractionProtection() {

        val javascript = """
            (function() {

                try {

                    var style =
                        document.getElementById(
                            'lessgram-style'
                        );

                    if (!style) {

                        style =
                            document.createElement('style');

                        style.id =
                            'lessgram-style';

                        document.head.appendChild(style);
                    }

                    style.innerHTML = `

                        /*
                         * Hide Instagram bottom navigation.
                         */
                        nav {
                            display: none !important;
                        }

                        /*
                         * Hide Explore/Reels shortcuts where
                         * Instagram exposes them.
                         */
                        a[href="/explore/"],
                        a[href="/reels/"] {
                            display: none !important;
                        }

                    `;

                } catch(e) {}

            })();
        """.trimIndent()

        webView.evaluateJavascript(javascript, null)
    }

    // ============================================================
    // BACK BUTTON
    // ============================================================

    private fun setupBackButton() {

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    /*
                     * If viewing a DM-shared Reel, going back returns
                     * to the DM conversation.
                     */
                    if (reelMode) {

                        reelMode = false
                        openedDmReelUrl = null

                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            currentSection = SECTION_DM
                            webView.loadUrl(INSTAGRAM_DM)
                        }

                        return
                    }

                    /*
                     * Normal WebView navigation.
                     */
                    if (
                        webView.visibility == View.VISIBLE &&
                        webView.canGoBack()
                    ) {
                        webView.goBack()
                        return
                    }

                    /*
                     * Otherwise return to LessGram Home.
                     */
                    currentSection = SECTION_HOME

                    showBlockedScreen(
                        "You don't need to see\neverything.",
                        "Your time is better spent building your own life."
                    )
                }
            }
        )
    }

    // ============================================================
    // UTILITY
    // ============================================================

    private fun dp(value: Int): Int {

        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }

    override fun onDestroy() {

        webView.stopLoading()
        webView.webViewClient = null
        webView.destroy()

        super.onDestroy()
    }
}
