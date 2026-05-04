package com.cfb.fblite

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnCookie: ImageButton
    private lateinit var splashOverlay: FrameLayout

    private val splashHandler = Handler(Looper.getMainLooper())
    private var dotRunnable: Runnable? = null
    private var splashDismissed = false
    private var lastPausedAt = 0L
    private var lastFreshFeedAt = 0L

    private val FB_URL       = "https://lite.facebook.com/"
    private val FB_ALT_URL   = "https://m.facebook.com/"
    private val FB_MSG_URL   = "https://www.facebook.com/messages/t/"
    private val FB_DOMAINS   = listOf(
        "https://www.facebook.com",
        "https://lite.facebook.com",
        "https://m.facebook.com",
        "https://mbasic.facebook.com",
        ".facebook.com",
        "facebook.com"
    )
    private val PREFS_NAME   = "cfb_cookie_store"
    private val KEY_COOKIES  = "saved_cookies"
    private val TELEGRAM_URL = "https://t.me/JAHID_1"

    private val MOBILE_UA = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
    private val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)

        webView       = findViewById(R.id.webView)
        btnCookie     = findViewById(R.id.btnCookie)
        splashOverlay = findViewById(R.id.splashOverlay)

        buildSplash()
        val hasSavedCookies = applyStoredCookies()
        setupWebView()
        setupFloatingButton()

        if (hasSavedCookies) {
            // Fresh feed on app start without clearing cookies/login.
            loadFreshFeed(clearSnapshot = true)
        } else if (savedInstanceState == null) {
            webView.loadUrl(FB_URL)
        }
        splashHandler.postDelayed({ hideSplash() }, 3000)
    }

    override fun onPause() {
        lastPausedAt = System.currentTimeMillis()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized && lastPausedAt > 0L) {
            splashHandler.postDelayed({
                if (isLoggedIn() && isFeedLikeUrl(webView.url)) {
                    loadFreshFeed(clearSnapshot = true)
                }
            }, 350)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dotRunnable?.let { splashHandler.removeCallbacks(it) }
        try { webView.destroy() } catch (_: Exception) {}
    }

    // ── Splash ────────────────────────────────────────────────────

    private fun buildSplash() {
        splashOverlay.setBackgroundColor(Color.WHITE)
        splashOverlay.isClickable = false
        splashOverlay.isFocusable = false

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val circle = FrameLayout(this).apply {
            val sz = dp(72)
            layoutParams = LinearLayout.LayoutParams(sz, sz).also {
                it.bottomMargin = dp(20)
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(2), Color.parseColor("#D0D0D0"))
            }
        }
        val fLetter = TextView(this).apply {
            text = "f"
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1877F2"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        circle.addView(fLetter)

        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val dots = Array(5) { i ->
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).also {
                    if (i < 4) it.marginEnd = dp(7)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#DDDDDD"))
                }
            }
        }
        dots.forEach { dotsRow.addView(it) }

        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                if (splashDismissed) return
                dots.forEachIndexed { i, dot ->
                    (dot.background as? GradientDrawable)?.setColor(
                        if (i == step % 5) Color.parseColor("#1877F2")
                        else Color.parseColor("#DDDDDD")
                    )
                }
                step++
                splashHandler.postDelayed(this, 350)
            }
        }
        dotRunnable = runnable
        splashHandler.post(runnable)

        center.addView(circle)
        center.addView(dotsRow)
        splashOverlay.addView(center, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).also { it.gravity = Gravity.CENTER })

        val bottomBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        bottomBox.addView(TextView(this).apply {
            text = "from"; textSize = 13f
            setTextColor(Color.parseColor("#8A8D91")); gravity = Gravity.CENTER
        })
        bottomBox.addView(TextView(this).apply {
            text = "\u221E Meta"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1877F2")); gravity = Gravity.CENTER
        })
        splashOverlay.addView(bottomBox, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            it.bottomMargin = dp(52)
        })
    }

    private fun hideSplash() {
        if (splashDismissed) return
        splashDismissed = true
        dotRunnable?.let { splashHandler.removeCallbacks(it) }
        splashOverlay.isClickable = false
        splashOverlay.isFocusable = false
        splashOverlay.animate().alpha(0f).setDuration(120)
            .withEndAction {
                splashOverlay.visibility = View.GONE
                splashOverlay.isClickable = false
                splashOverlay.isFocusable = false
                webView.requestFocus()
                btnCookie.bringToFront()
            }.start()
    }

    // ── WebView ───────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isClickable = true
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocusFromTouch()

        webView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            databaseEnabled                  = true
            cacheMode                        = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically         = true
            mixedContentMode                 = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort                  = true
            loadWithOverviewMode             = false
            setSupportZoom(false)
            displayZoomControls              = false
            builtInZoomControls              = false
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            allowFileAccess = true
            allowContentAccess = true
            textZoom = 100
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            userAgentString = MOBILE_UA
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(AppBridge(), "CFB")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                // Restore original smooth Lite scrolling/clicking: no forced Messenger/Desktop route.
                view.settings.userAgentString = MOBILE_UA
                view.settings.useWideViewPort = true
                view.settings.loadWithOverviewMode = false
            }

            override fun onPageFinished(view: WebView, url: String) {
                CookieManager.getInstance().flush()
                injectPageFixes(view, url)
                splashHandler.postDelayed({ injectPageFixes(view, view.url ?: url) }, 700)
                splashHandler.postDelayed({ injectPageFixes(view, view.url ?: url) }, 1800)
                splashHandler.postDelayed({ hideSplash() }, 150)
                // Fix: Account Center white screen — force repaint after short delay
                if (url.contains("accountscenter") || url.contains("privacy_center")) {
                    splashHandler.postDelayed({
                        view.evaluateJavascript(
                            "(function(){try{" +
                            "document.body.style.display='none';" +
                            "var _h=document.body.offsetHeight;" +
                            "document.body.style.display='';" +
                            "document.body.style.setProperty('visibility','visible','important');" +
                            "document.body.style.setProperty('opacity','1','important');" +
                            "}catch(e){}})()", null)
                    }, 350)
                    splashHandler.postDelayed({
                        view.evaluateJavascript(
                            "(function(){try{" +
                            "document.documentElement.style.setProperty('visibility','visible','important');" +
                            "document.documentElement.style.setProperty('opacity','1','important');" +
                            "document.body.style.setProperty('visibility','visible','important');" +
                            "document.body.style.setProperty('opacity','1','important');" +
                            "}catch(e){}})()", null)
                    }, 900)
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
            }

            override fun onReceivedError(view: WebView, code: Int, desc: String, url: String) {
                hideSplash()
                if (url == FB_URL) view.loadUrl(FB_ALT_URL)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleUrlInsideApp(view, url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                return handleUrlInsideApp(view, request.url.toString())
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                hideSplash()
                view.visibility = View.VISIBLE
                view.setBackgroundColor(Color.WHITE)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                try {
                    val last = view.url ?: FB_URL
                    hideSplash()
                    view.postDelayed({ view.loadUrl(last) }, 250)
                } catch (_: Exception) {}
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                if (p >= 95) injectPageFixes(view, view.url ?: "")
            }
            override fun onCloseWindow(window: WebView?) {
                // Do not navigate back/home when Facebook closes an internal video popup.
                try { window?.destroy() } catch (_: Exception) {}
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                // Facebook video/reels sometimes opens a target=_blank popup inside WebView.
                // The old code called goBackOrHome() here, which froze video and blocked bottom buttons.
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(this@MainActivity)
                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.settings.userAgentString = MOBILE_UA
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                        return handlePopupUrl(request.url.toString())
                    }
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun shouldOverrideUrlLoading(popupView: WebView, url: String): Boolean {
                        return handlePopupUrl(url)
                    }
                    override fun onPageStarted(popupView: WebView, url: String, favicon: Bitmap?) {
                        handlePopupUrl(url)
                    }
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    private fun injectPageFixes(view: WebView, url: String) {
        val loggedIn = isLoggedIn()
        // FB root URLs only treated as login when NOT logged in — fixes video scroll lock
        val isLoginUrl = url.contains("/login") || url.contains("/checkpoint") ||
            (!loggedIn && (
                url == FB_URL || url == FB_ALT_URL ||
                (!url.contains("/home") && !url.contains("/feed") &&
                 !url.contains("/groups") && !url.contains("/notifications") &&
                 !url.contains("/messages") && !url.contains("/settings") &&
                 !url.contains("/profile") && !url.contains("/friends") &&
                 !url.contains("/pages") && !url.contains("/marketplace") &&
                 !url.contains("/stories") && !url.contains("/watch") &&
                 !url.contains("/video") && !url.contains("accountscenter"))
            ))

        view.evaluateJavascript("""
        (function(){
          try {
            // ── Viewport fix ─────────────────────────────────────────
            var mv = document.querySelector('meta[name="viewport"]');
            if (!mv) { mv = document.createElement('meta'); mv.name = 'viewport'; document.head.appendChild(mv); }
            mv.content = 'width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no';

            // ── Base CSS (inject once) ────────────────────────────────
            if (!document.getElementById('cfb-style-v14')) {
              var s = document.createElement('style');
              s.id = 'cfb-style-v14';
              s.innerHTML =
                'html,body { max-width:100vw !important; overflow-x:hidden !important; }' +
                'html,body { overscroll-behavior-x:none !important; }' +
                '.cfb-promo-hidden { display:none !important; visibility:hidden !important; height:0 !important; min-height:0 !important; max-height:0 !important; opacity:0 !important; pointer-events:none !important; }' +
                '.cfb-open-close { position:fixed !important; z-index:2147483647 !important; width:38px !important; height:38px !important; border-radius:50% !important; border:1px solid rgba(0,0,0,.28) !important; background:#fff !important; color:#111 !important; font-size:24px !important; font-weight:900 !important; line-height:34px !important; text-align:center !important; box-shadow:0 2px 8px rgba(0,0,0,.25) !important; padding:0 !important; margin:0 !important; pointer-events:auto !important; touch-action:manipulation !important; }' +
                '.cfb-clear-hit { position:fixed !important; z-index:2147483646 !important; background:transparent !important; border:0 !important; padding:0 !important; margin:0 !important; }' +
                '.cfb-video-clean { display:none !important; visibility:hidden !important; opacity:0 !important; pointer-events:none !important; }' +
                '.cfb-unblock { pointer-events:auto !important; touch-action:auto !important; }';
              document.head.appendChild(s);
            }

            // ── SCROLL CONTROL ────────────────────────────────────────
            // Detect login page via DOM (most reliable)
            var hasCUser = (document.cookie || '').indexOf('c_user=') > -1;
            var hasLoginBtn = !!(
              document.querySelector('input[name="email"]') ||
              document.querySelector('input[type="email"]') ||
              document.querySelector('#loginbutton') ||
              document.querySelector('[data-sigil*="login"]')
            );
            var isLoginPage = !hasCUser && (${isLoginUrl} || hasLoginBtn);

            // IMPORTANT: Do NOT use overflow:hidden on body – it breaks touch events on
            // absolutely-positioned elements like the input X clear button.
            // Instead use overscroll-behavior + scroll reset to prevent bounce/scroll.
            window._cfbIsLoginPage = !!isLoginPage;
            if (isLoginPage) {
              document.documentElement.style.setProperty('overscroll-behavior', 'none', 'important');
              document.body.style.setProperty('overscroll-behavior', 'none', 'important');
              // Keep only login page fixed. The listener checks current page state,
              // so it will not freeze feed/video/groups after login.
              if (!window._cfbScrollLockListenerInstalled) {
                window._cfbScrollLockListenerInstalled = true;
                window.addEventListener('scroll', function() {
                  if (window._cfbIsLoginPage) window.scrollTo(0, 0);
                }, { passive: true });
              }
            } else {
              // Feed / settings / groups / video — allow normal vertical scroll and touch.
              window._cfbIsLoginPage = false;
              document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
              document.body.style.setProperty('overflow-y', 'auto', 'important');
              document.documentElement.style.setProperty('overscroll-behavior-y', 'auto', 'important');
              document.body.style.setProperty('overscroll-behavior-y', 'auto', 'important');
              document.documentElement.style.setProperty('touch-action', 'auto', 'important');
              document.body.style.setProperty('touch-action', 'auto', 'important');
            }

            // ── X BUTTON FIX ──────────────────────────────────────────
            // Ensure nothing blocks pointer/touch events on clear and close buttons.
            // We do NOT change overflow or position on the body, so the native hit test stays correct.
            function enableInteractiveElements() {
              // 1) Enable ALL children inside input containers (covers the clear X)
              var inputs = document.querySelectorAll('input, textarea');
              for (var i = 0; i < inputs.length; i++) {
                var container = inputs[i].parentElement;
                if (!container) continue;
                var children = container.querySelectorAll('*');
                for (var j = 0; j < children.length; j++) {
                  var ch = children[j];
                  if (ch !== inputs[i]) {
                    ch.style.setProperty('pointer-events', 'auto', 'important');
                    ch.style.setProperty('touch-action', 'manipulation', 'important');
                    ch.style.setProperty('cursor', 'pointer', 'important');
                  }
                }
              }

              // 2) Enable Accounts Center / Meta close X buttons (top-right X)
              var allEls = document.querySelectorAll('[role="button"], button, a, span, div');
              for (var k = 0; k < allEls.length; k++) {
                var el = allEls[k];
                var aria = (el.getAttribute('aria-label') || '').toLowerCase();
                var txt  = (el.textContent || '').trim();
                // Match: aria says close/dismiss, OR text is × ✕ X
                if (aria.indexOf('close') > -1 || aria.indexOf('dismiss') > -1 ||
                    txt === '\u00D7' || txt === '\u2715' || txt === 'X') {
                  // Only pointer-events + touch-action — never change position or z-index
                  // (changing position breaks absolutely-positioned clear buttons)
                  el.style.setProperty('pointer-events', 'auto', 'important');
                  el.style.setProperty('touch-action', 'manipulation', 'important');
                }
              }
            }

            enableInteractiveElements();
            setTimeout(enableInteractiveElements, 600);
            setTimeout(enableInteractiveElements, 1500);

            // ── HIDE "Get Facebook for Android" PROMO BANNER ─────────
            // This banner shows at the top of the login page.
            // We target it specifically by text content and hide its bar container.
            function hideFbAppPromo() {
              var allNodes = document.querySelectorAll('a, div, td, tr, li, section, article');
              for (var n = 0; n < allNodes.length; n++) {
                var node = allNodes[n];
                // Only check leaf-ish nodes (avoid matching the whole page body)
                if (node.children.length > 6) continue;
                var t = (node.innerText || node.textContent || '').trim().toLowerCase();
                if (t.length > 120) continue; // Skip large containers
                if (t.indexOf('get facebook for android') > -1 ||
                    t.indexOf('browse faster') > -1 ||
                    t.indexOf('get the facebook app') > -1 ||
                    t.indexOf('open in app') > -1) {
                  // Walk up to find the banner row/bar (full-width, short height)
                  var bar = node;
                  var cur = node.parentElement;
                  for (var up = 0; up < 5; up++) {
                    if (!cur || cur === document.body) break;
                    var w = cur.offsetWidth || 0;
                    var h = cur.offsetHeight || 0;
                    if (w > window.innerWidth * 0.7 && h < 90) {
                      bar = cur;
                    }
                    cur = cur.parentElement;
                  }
                  bar.classList.add('cfb-promo-hidden');
                  bar.style.setProperty('display', 'none', 'important');
                }
              }
            }

            hideFbAppPromo();
            setTimeout(hideFbAppPromo, 500);
            setTimeout(hideFbAppPromo, 1500);

            // ── OPEN APP / GET FACEBOOK PROMPT CLOSE (single X only) ─────
            function cfbText(el){ return ((el && (el.innerText || el.textContent)) || '').replace(/\s+/g,' ').trim(); }
            function cfbIsOpenPromptText(t){
              t = (t || '').replace(/\s+/g,' ').trim().toLowerCase();
              return t === 'open app' || t === 'open facebook' || t === 'open in app' ||
                     t === 'get facebook' || t.indexOf('get facebook for android') > -1 ||
                     t.indexOf('browse faster') > -1 || t.indexOf('get the facebook app') > -1;
            }
            function cfbVisible(el){ try{ var r=el.getBoundingClientRect(); return r.width>0 && r.height>0; }catch(e){ return false; } }
            function cfbPromptRoot(el){
              var best = el, cur = el;
              for (var i=0; i<7 && cur && cur !== document.body && cur !== document.documentElement; i++){
                var r = cur.getBoundingClientRect();
                var t = cfbText(cur);
                if (r.width > 20 && r.height > 14 && r.width <= window.innerWidth*1.05 && r.height <= Math.max(74, window.innerHeight*0.26) && t.length < 220) best = cur;
                cur = cur.parentElement;
              }
              return best;
            }
            function cfbClosePrompt(target){
              try {
                var root = cfbPromptRoot(target);
                if (!root || root === document.body || root === document.documentElement) return;
                root.classList.add('cfb-promo-hidden');
                root.style.setProperty('display','none','important');
                root.style.setProperty('pointer-events','none','important');
                var id = root.getAttribute('data-cfb-open-id');
                if (id) {
                  var b = document.querySelector('.cfb-open-close[data-for="'+id+'"]');
                  if (b) b.remove();
                }
              } catch(e){}
            }
            function cfbPlaceCloseButtons(){
              var btns = document.querySelectorAll('.cfb-open-close');
              for (var i=0;i<btns.length;i++){
                var b = btns[i], id = b.getAttribute('data-for');
                var root = id ? document.querySelector('[data-cfb-open-id="'+id+'"]') : null;
                if (!root || !cfbVisible(root) || root.classList.contains('cfb-promo-hidden')) { try{b.remove();}catch(e){}; continue; }
                var r = root.getBoundingClientRect();
                b.style.left = Math.max(4, Math.min(window.innerWidth-42, r.right-42)) + 'px';
                b.style.top  = Math.max(4, Math.min(window.innerHeight-42, r.top+4)) + 'px';
                b.style.display = 'block';
              }
            }
            function cfbAddCloseButtonFor(target){
              try {
                var root = cfbPromptRoot(target);
                if (!root || root === document.body || root === document.documentElement || !cfbVisible(root)) return;
                var rr = root.getBoundingClientRect();
                if (rr.height > window.innerHeight*0.30 || rr.width > window.innerWidth*1.10) return;
                var id = root.getAttribute('data-cfb-open-id');
                if (!id) { id = 'open_' + Math.random().toString(36).slice(2); root.setAttribute('data-cfb-open-id', id); }
                var existing = document.querySelector('.cfb-open-close[data-for="'+id+'"]');
                if (existing) return;
                var b = document.createElement('button');
                b.type = 'button'; b.className = 'cfb-open-close'; b.textContent = '×';
                b.setAttribute('aria-label','Close'); b.setAttribute('data-for', id);
                b.addEventListener('click', function(ev){ ev.preventDefault(); ev.stopPropagation(); cfbClosePrompt(root); return false; }, true);
                b.addEventListener('touchend', function(ev){ ev.preventDefault(); ev.stopPropagation(); cfbClosePrompt(root); return false; }, {capture:true, passive:false});
                document.documentElement.appendChild(b);
                cfbPlaceCloseButtons();
              } catch(e){}
            }
            function hideOtherPromos() {
              var nodes = document.querySelectorAll('a, button, div, span, [role="button"], td, tr, section, article');
              var seen = [];
              for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                if (!cfbVisible(el)) continue;
                var t = cfbText(el);
                var aria = (el.getAttribute && (el.getAttribute('aria-label') || '')) || '';
                if (cfbIsOpenPromptText(t) || cfbIsOpenPromptText(aria)) {
                  var root = cfbPromptRoot(el);
                  if (seen.indexOf(root) === -1) { seen.push(root); cfbAddCloseButtonFor(root); }
                  if (!el._cfbOpenBlocked) {
                    el._cfbOpenBlocked = true;
                    el.addEventListener('click', function(ev){ ev.preventDefault(); ev.stopPropagation(); return false; }, true);
                  }
                }
                var lt = t.toLowerCase(), la = aria.toLowerCase();
                if (lt === 'not now' || lt === 'maybe later' || lt === 'no thanks' || la === 'not now') {
                  cfbClosePrompt(el);
                }
              }
              cfbPlaceCloseButtons();
            }

            hideOtherPromos();
            setTimeout(hideOtherPromos, 800);
            setTimeout(hideOtherPromos, 2000);

            // ── INTERNAL MESSAGES FIX ───────────────────────────────
            // If Facebook shows the browser Messenger block page, route chats to
            // mbasic messages so the chat list/box opens inside this WebView.
            function fixMessengerBlockPage(){
              // Normal Messenger: do not force desktop/messages/t routes or replace links.
            }

            // ── INPUT CLEAR X FIX ───────────────────────────────────
            // Fixes Facebook login/email-number clear X. It does not block page scrolling.
            function installClearHitFix(){
              if (window._cfbClearFixInstalled) return;
              window._cfbClearFixInstalled = true;
              var hit = document.createElement('button');
              hit.type = 'button';
              hit.className = 'cfb-clear-hit';
              hit.setAttribute('aria-label','clear input');
              hit.style.display = 'none';
              document.documentElement.appendChild(hit);
              var active = null;
              function okInput(el){
                if (!el || !el.tagName) return false;
                var tag = el.tagName.toLowerCase();
                if (tag !== 'input' && tag !== 'textarea') return false;
                var typ = (el.getAttribute('type') || '').toLowerCase();
                return typ === '' || typ === 'text' || typ === 'email' || typ === 'tel' || typ === 'number' || typ === 'search' || tag === 'textarea';
              }
              function clearActive(){
                if (!okInput(active)) return false;
                active.value = '';
                try { active.setAttribute('value',''); } catch(e){}
                active.dispatchEvent(new Event('input', {bubbles:true}));
                active.dispatchEvent(new Event('change', {bubbles:true}));
                active.focus();
                place();
                return true;
              }
              function place(){
                if (!okInput(active) || !active.value) { hit.style.display='none'; return; }
                var r = active.getBoundingClientRect();
                if (r.width < 70 || r.height < 24) { hit.style.display='none'; return; }
                var vv = window.visualViewport;
                var ox = vv ? vv.offsetLeft : 0, oy = vv ? vv.offsetTop : 0;
                hit.style.left = Math.max(0, ox + r.right - 56) + 'px';
                hit.style.top = Math.max(0, oy + r.top - 4) + 'px';
                hit.style.width = '56px';
                hit.style.height = Math.max(42, r.height + 8) + 'px';
                hit.style.display = 'block';
              }
              document.addEventListener('focusin', function(e){ if(okInput(e.target)){ active=e.target; setTimeout(place,80); } }, true);
              document.addEventListener('input', function(e){ if(e.target===active) place(); }, true);
              window.addEventListener('scroll', place, true);
              window.addEventListener('resize', place, true);
              if (window.visualViewport) window.visualViewport.addEventListener('resize', place);
              hit.addEventListener('touchstart', function(e){ e.preventDefault(); e.stopPropagation(); clearActive(); }, {capture:true, passive:false});
              hit.addEventListener('click', function(e){ e.preventDefault(); e.stopPropagation(); clearActive(); return false; }, true);
              document.addEventListener('touchstart', function(e){
                if (!e.touches || !e.touches[0]) return;
                var t=e.touches[0];
                // First try active input, then scan visible inputs. This fixes the
                // Facebook login X even when the native clear icon does not receive touch.
                var targetInput = null;
                var candidates = [];
                if (okInput(active)) candidates.push(active);
                var allInputs = document.querySelectorAll('input, textarea');
                for (var ii=0; ii<allInputs.length; ii++) candidates.push(allInputs[ii]);
                for (var ci=0; ci<candidates.length; ci++) {
                  var inp = candidates[ci];
                  if (!okInput(inp) || !inp.value) continue;
                  var r = inp.getBoundingClientRect();
                  if (r.width < 70 || r.height < 24) continue;
                  if (t.clientX > r.right-76 && t.clientX < r.right+20 && t.clientY > r.top-14 && t.clientY < r.bottom+14) {
                    targetInput = inp; break;
                  }
                }
                if (targetInput) {
                  active = targetInput;
                  e.preventDefault(); e.stopPropagation(); clearActive();
                }
              }, {capture:true, passive:false});
            }

            // ── META ACCOUNTS CENTER X FIX ──────────────────────────
            function installAccountsCenterCloseFix(){
              if (window._cfbAcCloseInstalled) return;
              window._cfbAcCloseInstalled = true;
              document.addEventListener('click', function(e){
                try {
                  var path = (location.href || '').toLowerCase();
                  var body = ((document.body && document.body.innerText) || '').toLowerCase();
                  var isAc = path.indexOf('accountscenter') > -1 || body.indexOf('accounts center') > -1;
                  if (!isAc) return;
                  var x = e.clientX || 0, y = e.clientY || 0;
                  var topRightTap = x > (window.innerWidth - 90) && y < 110;
                  var t = e.target, txt = ((t && t.textContent) || '').trim();
                  var aria = ((t && t.getAttribute && t.getAttribute('aria-label')) || '').toLowerCase();
                  var isCloseText = txt === '×' || txt === '✕' || txt === 'X' || aria.indexOf('close') > -1 || aria.indexOf('dismiss') > -1;
                  if (topRightTap || isCloseText) {
                    e.preventDefault(); e.stopPropagation();
                    if (window.CFB && CFB.goBack) CFB.goBack();
                    else if (history.length > 1) history.back();
                    else location.href = 'https://lite.facebook.com/';
                  }
                } catch(_e){}
              }, true);
            }

            // ── VIDEO/REELS TOUCH FIX ─────────────────────────────
            // Video pages must stay scrollable. This never hides body/main/feed and never changes Home.
            function cfbVisible(el){ try{ var r=el.getBoundingClientRect(); return r.width>0 && r.height>0; }catch(e){ return false; } }
            function isCfbVideoSurface(){
              try {
                var u = (location.href || '').toLowerCase();
                var t = ((document.title || '') + ' ' + ((document.body && document.body.innerText) || '').slice(0,500)).toLowerCase();
                return u.indexOf('/watch')>-1 || u.indexOf('/reel')>-1 || u.indexOf('/videos')>-1 ||
                       t.indexOf('reels')>-1 || t.indexOf('watch')>-1;
              } catch(e){ return false; }
            }
            function hideVideoPrompts(){
              if (!isCfbVideoSurface()) return;
              try {
                window._cfbIsLoginPage = false;
                document.documentElement.style.setProperty('overflow-y','auto','important');
                document.body.style.setProperty('overflow-y','auto','important');
                document.documentElement.style.setProperty('touch-action','pan-y','important');
                document.body.style.setProperty('touch-action','pan-y','important');
                document.documentElement.style.setProperty('pointer-events','auto','important');
                document.body.style.setProperty('pointer-events','auto','important');
                var nodes = document.querySelectorAll('main, [role="main"], video, a, button, [role="button"], [tabindex]');
                for (var i=0;i<nodes.length;i++) {
                  nodes[i].style.setProperty('pointer-events','auto','important');
                  nodes[i].style.setProperty('touch-action','manipulation','important');
                }
                var scrollables = document.querySelectorAll('html, body, main, [role="main"], div');
                for (var j=0;j<scrollables.length;j++) {
                  var el = scrollables[j];
                  if (el===document.documentElement || el===document.body || el.scrollHeight > el.clientHeight + 40) {
                    el.style.setProperty('overflow-y','auto','important');
                    el.style.setProperty('-webkit-overflow-scrolling','touch','important');
                  }
                }
                // Block only external app buttons; do not remove their parents/overlays.
                var btns = document.querySelectorAll('a, button, [role="button"], div[role="button"]');
                for (var b=0;b<btns.length;b++){
                  var x = btns[b];
                  var txt = (x.innerText || x.textContent || '').replace(/\s+/g,' ').trim().toLowerCase();
                  var href = (x.getAttribute('href') || '').toLowerCase();
                  if (txt === 'open app' || txt === 'open in app' || href.indexOf('intent://') === 0 || href.indexOf('fb://') === 0) {
                    x.removeAttribute('href');
                    x.onclick = function(ev){ if(ev){ev.preventDefault();ev.stopPropagation();} return false; };
                  }
                }
              } catch(e) {}
            }

            function installPromptObserver(){
              if (window._cfbPromptObserver) return;
              var timer = null;
              window._cfbPromptObserver = new MutationObserver(function(){
                if (timer) return;
                timer = setTimeout(function(){ timer=null; try{ hideVideoPrompts(); fixMessengerBlockPage(); }catch(e){} }, 900);
              });
              try { window._cfbPromptObserver.observe(document.documentElement, {childList:true, subtree:true}); } catch(e){}
            }

            fixMessengerBlockPage();
            installClearHitFix();
            installAccountsCenterCloseFix();
            hideVideoPrompts();
            setTimeout(fixMessengerBlockPage, 600);
            setTimeout(hideVideoPrompts, 600);
            setTimeout(hideVideoPrompts, 1600);
            installPromptObserver();

          } catch(e) {}
        })();
        """.trimIndent(), null)
    }

    // ── Native bridge / URL routing ───────────────────────────────

    inner class AppBridge {
        @JavascriptInterface
        fun goBack() {
            runOnUiThread { goBackOrHome() }
        }
    }

    private fun isInternalMessagePage(url: String?): Boolean = false

    private fun shouldUseInternalMessages(url: String?): Boolean = false

    private fun handleUrlInsideApp(view: WebView, url: String): Boolean {
        val u = url.lowercase()
        return when {
            // Block external app/deep links from Open App / Open Facebook prompts.
            u.startsWith("intent://") || u.startsWith("fb://") || u.startsWith("fb-messenger://") ||
                u.startsWith("market://") || u.contains("play.google.com/store/apps") -> true
            isFbUrl(url) -> false
            u.startsWith("http://") || u.startsWith("https://") -> {
                view.loadUrl(url)
                true
            }
            else -> true
        }
    }

    private fun handlePopupUrl(url: String): Boolean {
        val u = url.lowercase()
        if (u.isBlank() || u == "about:blank") return true
        if (u.startsWith("intent://") || u.startsWith("fb://") || u.startsWith("fb-messenger://") ||
            u.startsWith("market://") || u.contains("play.google.com/store/apps")) {
            return true
        }
        if (isFbUrl(url)) {
            webView.post { webView.loadUrl(url) }
            return true
        }
        if (u.startsWith("http://") || u.startsWith("https://")) {
            webView.post { webView.loadUrl(url) }
        }
        return true
    }

    private fun isFeedLikeUrl(url: String?): Boolean {
        val u = (url ?: "").lowercase()
        if (u.isBlank() || u == "about:blank") return true
        if (!u.contains("facebook.com")) return false
        if (u.contains("/watch") || u.contains("/reel") || u.contains("/videos") ||
            u.contains("/messages") || u.contains("accountscenter") || u.contains("/settings") ||
            u.contains("/notifications") || u.contains("/groups") || u.contains("/profile")) return false
        return u == FB_URL.lowercase() || u == FB_ALT_URL.lowercase() ||
               u.contains("/home") || u.contains("/feed") || u.contains("sk=h_chr") ||
               u.startsWith(FB_URL.lowercase()) || u.startsWith(FB_ALT_URL.lowercase())
    }

    private fun loadFreshFeed(clearSnapshot: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastFreshFeedAt < 2500) return
        lastFreshFeedAt = now
        try {
            if (clearSnapshot) {
                webView.stopLoading()
                webView.clearCache(true)
                webView.clearHistory()
                webView.clearFormData()
            }
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            val headers = mapOf(
                "Cache-Control" to "no-cache, no-store, must-revalidate, max-age=0",
                "Pragma" to "no-cache"
            )
            webView.loadUrl("https://m.facebook.com/home.php?sk=h_chr&cfbfresh=$now", headers)
            splashHandler.postDelayed({
                if (::webView.isInitialized) webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
            }, 4500)
        } catch (_: Exception) {
            try { webView.loadUrl(FB_URL) } catch (_: Exception) {}
        }
    }

    // ── Cookies ───────────────────────────────────────────────────

    private fun prefs(): SharedPreferences =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readCookiesFromManager(): String {
        for (url in FB_DOMAINS.filter { it.startsWith("http") }) {
            val c = CookieManager.getInstance().getCookie(url)
            if (!c.isNullOrBlank()) return c
        }
        return ""
    }

    private fun isLoggedIn() = readCookiesFromManager().contains("c_user=")

    private fun applyViaCookieManager(cookieString: String) {
        val cm = CookieManager.getInstance()
        parseCookiePairs(cookieString).forEach { pair ->
            FB_DOMAINS.forEach { domain ->
                try { cm.setCookie(domain, pair) } catch (_: Exception) {}
            }
        }
        cm.flush()
    }

    private fun injectCookiesViaJs(cookieString: String) {
        val lines = parseCookiePairs(cookieString).joinToString("\n") { pair ->
            val safe = pair.replace("\\", "\\\\").replace("'", "\\'")
            "try{document.cookie='$safe; domain=.facebook.com; path=/';}catch(e){}"
        }
        if (lines.isNotEmpty()) webView.evaluateJavascript("(function(){$lines})();", null)
    }

    private fun applyCookiesAllMethods(cookieString: String) {
        applyViaCookieManager(cookieString)
        injectCookiesViaJs(cookieString)
    }

    private fun applyStoredCookies(): Boolean {
        val saved = prefs().getString(KEY_COOKIES, "") ?: ""
        if (saved.isNotEmpty()) {
            applyViaCookieManager(saved)
            return true
        }
        return false
    }

    private fun parseCookiePairs(raw: String): List<String> =
        raw.split(";").map { it.trim() }.filter { it.isNotEmpty() && it.contains("=") }

    // ── Logout ────────────────────────────────────────────────────

    private fun performFullLogout() {
        CookieManager.getInstance().apply { removeAllCookies(null); flush() }
        webView.clearCache(true); webView.clearHistory(); webView.clearFormData()
        webView.evaluateJavascript(
            "(function(){try{localStorage.clear();sessionStorage.clear();}catch(e){}})()", null)
        WebStorage.getInstance().deleteAllData()
        prefs().edit().remove(KEY_COOKIES).apply()
        splashDismissed = false
        splashOverlay.alpha = 1f
        splashOverlay.visibility = View.VISIBLE
        dotRunnable?.let { splashHandler.post(it) }
        webView.loadUrl(FB_URL)
        toast("Logged out!")
    }

    // ── Floating button ───────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingButton() {
        var dX = 0f; var dY = 0f; var downX = 0f; var downY = 0f; var moved = false
        btnCookie.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - e.rawX; dY = v.y - e.rawY
                    downX = e.rawX; downY = e.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val p = v.parent as View
                    v.x = (e.rawX + dX).coerceIn(0f, (p.width - v.width).toFloat())
                    v.y = (e.rawY + dY).coerceIn(0f, (p.height - v.height).toFloat())
                    if (Math.abs(e.rawX - downX) > 7 || Math.abs(e.rawY - downY) > 7) moved = true
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) openPanel(); true }
                else -> true
            }
        }
    }

    // ── Panel ─────────────────────────────────────────────────────

    private fun openPanel() {
        webView.evaluateJavascript(
            "(function(){try{return document.cookie;}catch(e){return '';}})()"
        ) { jsRaw ->
            runOnUiThread {
                val cm = readCookiesFromManager()
                val js = jsRaw?.trim('"')?.replace("\\u003D","=")?.replace("\\u0026","&") ?: ""
                val viewCookies = when {
                    !isLoggedIn()   -> ""
                    cm.isNotEmpty() -> cm
                    js.isNotEmpty() -> js
                    else -> ""
                }
                buildPanel(viewCookies)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildPanel(viewCookies: String) {
        val prefs = prefs()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = vbox(dp(12), dp(10), dp(12), dp(10)).also {
            it.background = roundBg("#FFFFFF", 20, "#C8DCFF", 2)
        }

        val titleRow = hbox(Gravity.CENTER_VERTICAL, 0, 0, 0, dp(6))
        titleRow.addView(
            tv("C LITE FB", 17f, Typeface.DEFAULT_BOLD, "#0877F2", Gravity.CENTER),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        titleRow.addView(gradBtn("LOGOUT","#EA580C","#F97316").also { btn ->
            btn.textSize = 10f
            btn.setOnClickListener { dialog.dismiss(); performFullLogout() }
        }, LinearLayout.LayoutParams(dp(72), dp(30)))
        root.addView(titleRow)

        val viewBox = editBox(viewCookies,
            if (viewCookies.isEmpty()) "Login first to see cookies" else "",
            false, 2, 5)
        val viewHeader = hbox(Gravity.CENTER_VERTICAL, 0, dp(2), 0, dp(3))
        val btnRefresh = gradBtn("REFRESH","#0891B2","#22D3EE").also { btn ->
            btn.textSize = 10f
            btn.setOnClickListener {
                btn.isEnabled = false; btn.text = "..."
                webView.evaluateJavascript(
                    "(function(){try{return document.cookie;}catch(e){return '';}})()"
                ) { jsRaw ->
                    runOnUiThread {
                        val cm = readCookiesFromManager()
                        val js = jsRaw?.trim('"')?.replace("\\u003D","=")?.replace("\\u0026","&") ?: ""
                        val fresh = when {
                            !isLoggedIn()   -> ""
                            cm.isNotEmpty() -> cm
                            js.isNotEmpty() -> js
                            else -> ""
                        }
                        viewBox.setText(fresh)
                        viewBox.hint = if (fresh.isEmpty()) "Login first to see cookies" else ""
                        btn.isEnabled = true; btn.text = "REFRESH"
                        toast(if (fresh.isEmpty()) "Not logged in" else "Refreshed!")
                    }
                }
            }
        }
        val btnCopy = gradBtn("COPY","#0B5ED7","#27B5FF").also { btn ->
            btn.textSize = 10f
            btn.setOnClickListener {
                val c = viewBox.text.toString()
                if (c.isEmpty()) { toast("No cookies to copy"); return@setOnClickListener }
                clip(c); toast("Copied!")
            }
        }
        viewHeader.addView(tv("View Box",12f,Typeface.DEFAULT_BOLD,"#101828"),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        viewHeader.addView(btnRefresh, LinearLayout.LayoutParams(dp(74), dp(30)))
        viewHeader.addView(Space(this), LinearLayout.LayoutParams(dp(4), 1))
        viewHeader.addView(btnCopy,    LinearLayout.LayoutParams(dp(68), dp(30)))
        root.addView(viewHeader); root.addView(viewBox)

        root.addView(tv("Input Box",12f,Typeface.DEFAULT_BOLD,"#101828").also { it.setPadding(0,dp(7),0,dp(3)) })
        val inputBox = editBox("","Paste cookies here  (datr=...; c_user=...; xs=...)",true,2,5)
        root.addView(inputBox)

        val bottomRow = hbox(Gravity.CENTER, 0, dp(8), 0, 0)
        val gap = LinearLayout.LayoutParams(dp(5), 1)
        bottomRow.addView(gradBtn("INPUT","#10A34A","#40D67B").also { btn ->
            btn.setOnClickListener {
                val raw = inputBox.text.toString().trim()
                if (raw.isEmpty()) { toast("Paste cookies first!"); return@setOnClickListener }
                prefs.edit().putString(KEY_COOKIES, raw).apply()
                applyCookiesAllMethods(raw)
                dialog.dismiss(); loadFreshFeed(clearSnapshot = true); toast("Logging in...")
            }
        }, LinearLayout.LayoutParams(0, dp(36), 1f))
        bottomRow.addView(Space(this), gap)
        bottomRow.addView(gradBtn("CLEAR","#E11D48","#FB7185").also { btn ->
            btn.setOnClickListener { inputBox.setText(""); toast("Cleared") }
        }, LinearLayout.LayoutParams(0, dp(36), 1f))
        bottomRow.addView(Space(this), gap)
        bottomRow.addView(gradBtn("CLOSE","#6B21A8","#A855F7").also { btn ->
            btn.setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, dp(36), 1f))
        root.addView(bottomRow)

        root.addView(tv("@JAHID_1",11f,Typeface.DEFAULT_BOLD,"#0877F2",Gravity.CENTER).also { t ->
            t.setPadding(0,dp(6),0,0); t.isClickable = true
            t.setOnClickListener {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))) }
                catch (_: Exception) { toast("Cannot open Telegram") }
            }
        })

        dialog.setContentView(root)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun vbox(l:Int,t:Int,r:Int,b:Int)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(l,t,r,b)}
    private fun hbox(g:Int,l:Int,t:Int,r:Int,b:Int)=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=g;setPadding(l,t,r,b)}
    private fun tv(text:String,size:Float,face:Typeface=Typeface.DEFAULT,color:String,gravity:Int=Gravity.START)=TextView(this).apply{this.text=text;textSize=size;typeface=face;setTextColor(Color.parseColor(color));this.gravity=gravity}
    private fun editBox(text:String,hint:String,editable:Boolean,minLines:Int,maxLines:Int)=EditText(this).apply{
        setText(text);this.hint=hint;isFocusable=editable;isFocusableInTouchMode=editable;isCursorVisible=editable
        this.minLines=minLines;this.maxLines=maxLines;gravity=Gravity.TOP or Gravity.START
        inputType=if(editable) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_NULL
        setTextColor(Color.parseColor("#0F172A"));setHintTextColor(Color.parseColor("#98A2B3"));textSize=12f
        background=roundBg("#F0F5FF",10,"#C8DCFF",1);setPadding(dp(8),dp(6),dp(8),dp(6))
    }
    private fun gradBtn(label:String,s:String,e:String)=Button(this).apply{
        text=label;textSize=11f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)
        background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor(s),Color.parseColor(e))).also{it.cornerRadius=dp(9).toFloat()}
        minHeight=0;minWidth=0;setPadding(dp(2),0,dp(2),0)
    }
    private fun roundBg(c:String,r:Int,sc:String,sw:Int)=GradientDrawable().apply{
        setColor(Color.parseColor(c));cornerRadius=dp(r).toFloat();setStroke(dp(sw),Color.parseColor(sc))
    }
    private fun clip(text:String){(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("fb_cookies",text))}
    private fun toast(msg:String)=Toast.makeText(this,msg,Toast.LENGTH_SHORT).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun isFbUrl(url:String)=url.contains("facebook.com")||url.contains("fbcdn.net")||url.contains("fb.com")||url.contains("accountkit.com")

    private fun goBackOrHome() {
        if (webView.canGoBack()) webView.goBack()
        else webView.loadUrl(FB_URL)
    }

    override fun onSaveInstanceState(out: Bundle) {
        // Do not save WebView page snapshot. Saving it brings back old feed after reopening the app.
        super.onSaveInstanceState(out)
    }

    override fun onRestoreInstanceState(saved: Bundle) {
        // Do not restore WebView state; cookies are enough for login and feed is loaded fresh.
        super.onRestoreInstanceState(saved)
    }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed(){goBackOrHome()}
}
