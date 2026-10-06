package com.lessgram.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent { LessGramApp() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("instagram", "Instagram notifications", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

@Composable
private fun LessGramApp() {
    var tab by remember { mutableIntStateOf(HOME) }
    var searchKey by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                HOME -> BlockedScreen(
                    title = "You don't need to see everything.",
                    subtitle = "Your time is better spent building your own life."
                )
                REELS -> BlockedScreen(
                    title = "One reel is enough.",
                    subtitle = "If someone sent you one, watch that one. Then leave."
                )
                DM -> InstagramWeb(
                    initialUrl = "https://www.instagram.com/direct/inbox/",
                    key = searchKey,
                    allowReelPages = true
                )
                SEARCH -> InstagramWeb(
                    initialUrl = "https://www.instagram.com/web/search/",
                    key = searchKey,
                    allowReelPages = false
                )
                PROFILE -> InstagramWeb(
                    initialUrl = INSTAGRAM,
                    key = searchKey,
                    allowReelPages = false
                )
            }
        }
        BottomBar(tab) {
            tab = it
            searchKey++
        }
    }
}

@Composable
private fun BlockedScreen(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(92.dp).background(Color(0xFF171717), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Block, null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(title, color = Color.White, fontSize = 28.sp)
        Spacer(Modifier.height(12.dp))
        Text(subtitle, color = Color.Gray, fontSize = 15.sp)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InstagramWeb(initialUrl: String, key: Int, allowReelPages: Boolean) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setBackgroundColor(AndroidColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.loadsImagesAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString = WebSettings.getDefaultUserAgent(context)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (!url.startsWith(INSTAGRAM)) return true
                        if (!allowReelPages && isReelOrExplore(url)) {
                            view.loadUrl(INSTAGRAM)
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        injectLessGramCss(view, allowReelPages)
                    }
                }
                loadUrl(initialUrl)
            }
        },
        update = { view ->
            if (key == 0) view.loadUrl(initialUrl)
        }
    )
}

private fun isReelOrExplore(url: String): Boolean =
    url.contains("/reels", true) || url.contains("/explore", true)

private fun injectLessGramCss(view: WebView, allowReelPages: Boolean) {
    val block = if (allowReelPages) "" else """
        a[href*='/reels/'], a[href*='/reels'], a[href*='/explore/'], a[href*='/explore'] { display:none !important; }
    """
    val js = """
        (function(){
          var id='lessgram-style';
          var old=document.getElementById(id); if(old) old.remove();
          var s=document.createElement('style'); s.id=id;
          s.innerHTML=`$block
            body { background:#000 !important; }
          `;
          document.head.appendChild(s);
        })();
    """.trimIndent()
    view.evaluateJavascript(js, null)
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color.Black, contentColor = Color.White) {
        Nav(Icons.Outlined.Home, "Home", selected == HOME) { onSelect(HOME) }
        Nav(Icons.Outlined.PlayCircleOutline, "Reels", selected == REELS) { onSelect(REELS) }
        Nav(Icons.Outlined.MailOutline, "DM", selected == DM) { onSelect(DM) }
        Nav(Icons.Outlined.Search, "Search", selected == SEARCH) { onSelect(SEARCH) }
        Nav(Icons.Outlined.AccountCircle, "Profile", selected == PROFILE) { onSelect(PROFILE) }
    }
}

@Composable
private fun RowScope.Nav(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, label) },
        label = null,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            unselectedIconColor = Color(0xFF777777),
            indicatorColor = Color.Transparent
        )
    )
}
