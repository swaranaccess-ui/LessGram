package com.lessgram.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val INSTAGRAM = "https://www.instagram.com/"

private const val HOME = 0
private const val REELS = 1
private const val DM = 2
private const val SEARCH = 3
private const val PROFILE = 4

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK

        createNotificationChannel()

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        setContent {
            LessGramApp()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "instagram",
                "Instagram notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

@Composable
private fun LessGramApp() {

    var selectedTab by remember {
        mutableIntStateOf(HOME)
    }

    var webKey by remember {
        mutableIntStateOf(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {

            when (selectedTab) {

                HOME -> {
                    BlockedScreen(
                        title = "You don't need to see everything.",
                        subtitle = "Your time is better spent building your own life."
                    )
                }

                REELS -> {
                    BlockedScreen(
                        title = "One reel is enough.",
                        subtitle = "If someone sent you one, watch that one. Then leave."
                    )
                }

                DM -> {
                    InstagramWeb(
                        initialUrl =
                            "https://www.instagram.com/direct/inbox/",
                        mode = WebMode.DM,
                        key = webKey
                    )
                }

                SEARCH -> {
                    InstagramWeb(
                        initialUrl = INSTAGRAM,
                        mode = WebMode.SEARCH,
                        key = webKey
                    )
                }

                PROFILE -> {
                    InstagramWeb(
                        initialUrl = INSTAGRAM,
                        mode = WebMode.PROFILE,
                        key = webKey
                    )
                }
            }
        }

        BottomBar(
            selected = selectedTab,
            onSelect = {
                selectedTab = it
                webKey++
            }
        )
    }
}

@Composable
private fun BlockedScreen(
    title: String,
    subtitle: String
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 28.dp, vertical = 40.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    Color(0xFF181818),
                    CircleShape
                ),

            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = Icons.Outlined.Block,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(30.dp)
        )

        Text(
            text = title,
            color = Color.White,
            fontSize = 27.sp,
            lineHeight = 34.sp,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        Text(
            text = subtitle,
            color = Color(0xFF8A8A8A),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}

private enum class WebMode {

    DM,
    SEARCH,
    PROFILE
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InstagramWeb(
    initialUrl: String,
    mode: WebMode,
    key: Int
) {

    AndroidView(

        modifier = Modifier.fillMaxSize(),

        factory = { context ->

            WebView(context).apply {

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                setBackgroundColor(AndroidColor.BLACK)

                settings.apply {

                    javaScriptEnabled = true

                    domStorageEnabled = true

                    databaseEnabled = true

                    loadsImagesAutomatically = true

                    mediaPlaybackRequiresUserGesture = false

                    cacheMode = WebSettings.LOAD_DEFAULT

                    userAgentString =
                        WebSettings.getDefaultUserAgent(context)
                }

                CookieManager.getInstance().apply {

                    setAcceptCookie(true)

                    setAcceptThirdPartyCookies(
                        this@apply,
                        true
                    )
                }

                webChromeClient = WebChromeClient()

                webViewClient = object : WebViewClient() {

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {

                        val url = request.url.toString()

                        if (!url.startsWith(INSTAGRAM)) {
                            return true
                        }

                        /*
                         * Never allow the main Reels feed.
                         */
                        if (isReelsFeed(url)) {

                            view.loadUrl(INSTAGRAM)

                            return true
                        }

                        /*
                         * Explore is blocked.
                         */
                        if (url.contains("/explore", true)) {

                            if (mode != WebMode.SEARCH) {
                                view.loadUrl(INSTAGRAM)
                                return true
                            }
                        }

                        /*
                         * When inside a DM, allow a SINGLE
                         * shared Reel, but not the Reels feed.
                         */
                        if (mode == WebMode.DM) {

                            if (isSingleReel(url)) {

                                injectSingleReelProtection(
                                    view,
                                    url
                                )

                                return false
                            }
                        }

                        return false
                    }

                    override fun onPageFinished(
                        view: WebView,
                        url: String
                    ) {

                        super.onPageFinished(
                            view,
                            url
                        )

                        injectCommonCss(view)

                        when (mode) {

                            WebMode.DM -> {

                                /*
                                 * Keep DM clean and hide
                                 * distracting navigation.
                                 */
                                injectDmProtection(view)
                            }

                            WebMode.SEARCH -> {

                                /*
                                 * Start from Instagram home,
                                 * then activate Instagram's
                                 * own search UI.
                                 */
                                openInstagramSearch(view)
                            }

                            WebMode.PROFILE -> {

                                /*
                                 * Start from Instagram home,
                                 * then click the logged-in
                                 * user's Profile button.
                                 */
                                openInstagramProfile(view)
                            }
                        }

                        if (isSingleReel(url)) {

                            injectSingleReelProtection(
                                view,
                                url
                            )
                        }
                    }
                }

                loadUrl(initialUrl)
            }
        },

        update = { view ->

            /*
             * Reload only when switching to a new
             * WebView destination.
             */
            if (key > 0) {

                when (mode) {

                    WebMode.DM -> {
                        view.loadUrl(
                            "https://www.instagram.com/direct/inbox/"
                        )
                    }

                    WebMode.SEARCH -> {
                        view.loadUrl(INSTAGRAM)
                    }

                    WebMode.PROFILE -> {
                        view.loadUrl(INSTAGRAM)
                    }
                }
            }
        }
    )
}

/*
 * Instagram Reels feed:
 *
 * /reels/
 *
 * A shared Reel:
 *
 * /reel/ABC123/
 */
private fun isReelsFeed(
    url: String
): Boolean {

    val lower = url.lowercase()

    return lower.contains("/reels/") &&
            !lower.contains("/reel/")
}

private fun isSingleReel(
    url: String
): Boolean {

    return url.lowercase()
        .contains("/reel/")
}

/*
 * Open Instagram's own Search interface.
 *
 * We don't use /web/search/ because that route
 * is not reliable in the current Instagram web UI.
 */
private fun openInstagramSearch(
    view: WebView
) {

    val javascript = """
        (function() {

            function openSearch() {

                var elements =
                    document.querySelectorAll(
                        'a,button,[role="button"]'
                    );

                for (var i = 0; i < elements.length; i++) {

                    var el = elements[i];

                    var label =
                        (
                            el.getAttribute('aria-label') ||
                            el.getAttribute('title') ||
                            el.innerText ||
                            ''
                        ).toLowerCase();

                    var href =
                        el.getAttribute('href') || '';

                    if (
                        label === 'search' ||
                        label.indexOf('search') !== -1 ||
                        href.indexOf('/explore/search') !== -1
                    ) {

                        try {
                            el.click();
                            return;
                        } catch(e) {}
                    }
                }
            }

            setTimeout(openSearch, 900);

        })();
    """.trimIndent()

    view.evaluateJavascript(
        javascript,
        null
    )
}

/*
 * Open the logged-in user's own Profile.
 */
private fun openInstagramProfile(
    view: WebView
) {

    val javascript = """
        (function() {

            function openProfile() {

                var elements =
                    document.querySelectorAll(
                        'a,button,[role="button"]'
                    );

                for (var i = 0; i < elements.length; i++) {

                    var el = elements[i];

                    var label =
                        (
                            el.getAttribute('aria-label') ||
                            el.getAttribute('title') ||
                            ''
                        ).toLowerCase();

                    if (
                        label === 'profile' ||
                        label.indexOf('profile') !== -1
                    ) {

                        try {
                            el.click();
                            return;
                        } catch(e) {}
                    }
                }
            }

            setTimeout(openProfile, 900);

        })();
    """.trimIndent()

    view.evaluateJavascript(
        javascript,
        null
    )
}

private fun injectCommonCss(
    view: WebView
) {

    val javascript = """
        (function() {

            var id = 'lessgram-common';

            var old =
                document.getElementById(id);

            if (old) {
                old.remove();
            }

            var style =
                document.createElement('style');

            style.id = id;

            style.innerHTML = `

                html,
                body {
                    background: #000 !important;
                }

                /*
                 * Hide Explore links.
                 */
                a[href*="/explore/"] {
                    display: none !important;
                }

                /*
                 * Hide Reels feed links.
                 */
                a[href*="/reels/"] {
                    display: none !important;
                }

            `;

            if (document.head) {
                document.head.appendChild(style);
            }

        })();
    """.trimIndent()

    view.evaluateJavascript(
        javascript,
        null
    )
}

private fun injectDmProtection(
    view: WebView
) {

    val javascript = """
        (function() {

            var id = 'lessgram-dm';

            var old =
                document.getElementById(id);

            if (old) {
                old.remove();
            }

            var style =
                document.createElement('style');

            style.id = id;

            style.innerHTML = `

                /*
                 * Hide Instagram's Reels entry
                 * while using DM.
                 */
                a[href*="/reels/"] {
                    display: none !important;
                }

            `;

            if (document.head) {
                document.head.appendChild(style);
            }

        })();
    """.trimIndent()

    view.evaluateJavascript(
        javascript,
        null
    )
}

/*
 * This is the important fix for the
 * "I can swipe to another Reel" problem.
 *
 * Once a shared Reel is opened:
 *
 * - vertical swipes are blocked
 * - touch scrolling is blocked
 * - navigating to another Reel is blocked
 * - browser history changes to another Reel
 *   are immediately cancelled
 */
private fun injectSingleReelProtection(
    view: WebView,
    originalUrl: String
) {

    val safeUrl =
        originalUrl
            .replace("\\", "\\\\")
            .replace("'", "\\'")

    val javascript = """
        (function() {

            var originalReel = '$safeUrl';

            window.lessGramOriginalReel =
                originalReel;

            /*
             * Stop vertical swipe gestures.
             */
            if (!window.lessGramTouchInstalled) {

                window.lessGramTouchInstalled = true;

                var startX = 0;
                var startY = 0;

                document.addEventListener(
                    'touchstart',
                    function(e) {

                        if (
                            e.touches &&
                            e.touches.length > 0
                        ) {

                            startX =
                                e.touches[0].clientX;

                            startY =
                                e.touches[0].clientY;
                        }

                    },
                    true
                );

                document.addEventListener(
                    'touchmove',
                    function(e) {

                        if (
                            e.touches &&
                            e.touches.length > 0
                        ) {

                            var dx =
                                e.touches[0].clientX -
                                startX;

                            var dy =
                                e.touches[0].clientY -
                                startY;

                            /*
                             * Block vertical scrolling.
                             */
                            if (
                                Math.abs(dy) >
                                Math.abs(dx)
                            ) {

                                e.preventDefault();
                                e.stopPropagation();
                            }
                        }

                    },
                    {
                        capture: true,
                        passive: false
                    }
                );
            }

            /*
             * Watch URL changes caused by
             * Instagram's SPA navigation.
             */
            if (!window.lessGramHistoryInstalled) {

                window.lessGramHistoryInstalled = true;

                var originalPush =
                    history.pushState;

                history.pushState =
                    function() {

                        originalPush.apply(
                            history,
                            arguments
                        );

                        checkUrl();
                    };

                var originalReplace =
                    history.replaceState;

                history.replaceState =
                    function() {

                        originalReplace.apply(
                            history,
                            arguments
                        );

                        checkUrl();
                    };

                window.addEventListener(
                    'popstate',
                    checkUrl
                );
            }

            function checkUrl() {

                var current =
                    window.location.href;

                if (
                    current.indexOf('/reel/') !== -1 &&
                    current !== originalReel
                ) {

                    history.replaceState(
                        null,
                        '',
                        originalReel
                    );

                    window.location.href =
                        originalReel;
                }
            }

            /*
             * Check repeatedly because
             * Instagram can change its URL
             * without a normal page load.
             */
            if (!window.lessGramUrlWatcher) {

                window.lessGramUrlWatcher =
                    setInterval(
                        checkUrl,
                        250
                    );
            }

        })();
    """.trimIndent()

    view.evaluateJavascript(
        javascript,
        null
    )
}

@Composable
private fun BottomBar(
    selected: Int,
    onSelect: (Int) -> Unit
) {

    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.Black,
        contentColor = Color.White
    ) {

        Nav(
            icon = Icons.Outlined.Home,
            label = "Home",
            selected = selected == HOME
        ) {
            onSelect(HOME)
        }

        Nav(
            icon = Icons.Outlined.PlayCircleOutline,
            label = "Reels",
            selected = selected == REELS
        ) {
            onSelect(REELS)
        }

        Nav(
            icon = Icons.Outlined.MailOutline,
            label = "DM",
            selected = selected == DM
        ) {
            onSelect(DM)
        }

        Nav(
            icon = Icons.Outlined.Search,
            label = "Search",
            selected = selected == SEARCH
        ) {
            onSelect(SEARCH)
        }

        Nav(
            icon = Icons.Outlined.AccountCircle,
            label = "Profile",
            selected = selected == PROFILE
        ) {
            onSelect(PROFILE)
        }
    }
}

@Composable
private fun RowScope.Nav(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    NavigationBarItem(

        selected = selected,

        onClick = onClick,

        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label
            )
        },

        label = null,

        colors =
            NavigationBarItemDefaults.colors(

                selectedIconColor =
                    Color.White,

                unselectedIconColor =
                    Color(0xFF777777),

                indicatorColor =
                    Color.Transparent
            )
    )
}
