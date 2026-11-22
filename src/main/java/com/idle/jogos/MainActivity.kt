package com.idle.jogos

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val games = mutableListOf<Pair<String, String>>()
    private val views = mutableMapOf<String, WebView>()
    private lateinit var host: FrameLayout
    private lateinit var tabs: LinearLayout
    private lateinit var bar: LinearLayout
    private var current: String? = null
    private val autoPip = true
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        CookieManager.getInstance().setAcceptCookie(true)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#222222"))
        }
        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(this).apply { addView(tabs) }
        bar.addView(scroll, LinearLayout.LayoutParams(0, WC, 1f))
        bar.addView(botao("＋") { adicionar() })
        bar.addView(botao("▭") { minimizar() })
        host = FrameLayout(this)
        root.addView(bar, LinearLayout.LayoutParams(MP, WC))
        root.addView(host, LinearLayout.LayoutParams(MP, 0, 1f))
        setContentView(root)

        carregar()
        games.firstOrNull()?.let { abrir(it.second) } ?: atualizarAbas()
    }

    private fun botao(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.TRANSPARENT)
        isAllCaps = false
        setOnClickListener { f() }
    }

    private fun atualizarAbas() {
        tabs.removeAllViews()
        games.forEach { (nome, url) ->
            val bt = botao(nome) { abrir(url) }
            if (url == current) bt.setBackgroundColor(Color.parseColor("#6a4cff"))
            bt.setOnLongClickListener { remover(nome, url); true }
            tabs.addView(bt)
        }
    }

    private fun novoWeb(url: String): WebView {
        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.webViewClient = WebViewClient()
        w.webChromeClient = WebChromeClient()
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        host.addView(w, FrameLayout.LayoutParams(MP, MP))
        w.loadUrl(url)
        return w
    }

    private fun abrir(url: String) {
        current = url
        val w = views.getOrPut(url) { novoWeb(url) }
        views.values.forEach { it.visibility = View.INVISIBLE }
        w.visibility = View.VISIBLE
        atualizarAbas()
    }

    private fun adicionar() {
        val nome = EditText(this).apply { hint = "Nome do jogo" }
        val end = EditText(this).apply {
            hint = "Endereço (ex.: site.com/jogo)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(nome)
            addView(end)
        }
        AlertDialog.Builder(this).setTitle("Novo jogo").setView(box)
            .setPositiveButton("Adicionar") { _, _ ->
                var u = end.text.toString().trim()
                if (u.isEmpty()) return@setPositiveButton
                if (!u.startsWith("http")) u = "https://$u"
                val n = nome.text.toString().trim().ifEmpty { u.removePrefix("https://") }
                games.add(n to u)
                salvar()
                abrir(u)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun remover(nome: String, url: String) {
        AlertDialog.Builder(this).setTitle("Remover \"$nome\"?")
            .setPositiveButton("Remover") { _, _ ->
                games.removeAll { it.second == url }
                views.remove(url)?.let { host.removeView(it); it.destroy() }
                salvar()
                if (current == url) {
                    current = null
                    games.firstOrNull()?.let { abrir(it.second) } ?: atualizarAbas()
                } else atualizarAbas()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun salvar() {
        val a = JSONArray()
        games.forEach { a.put(JSONObject().put("n", it.first).put("u", it.second)) }
        getSharedPreferences("jogos", MODE_PRIVATE).edit().putString("lista", a.toString()).apply()
    }

    private fun carregar() {
        val a = JSONArray(getSharedPreferences("jogos", MODE_PRIVATE).getString("lista", "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            games.add(o.getString("n") to o.getString("u"))
        }
    }

    private fun minimizar() {
        enterPictureInPictureMode(
            PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()
        )
    }

    override fun onUserLeaveHint() {
        if (autoPip) minimizar()
    }

    override fun onPictureInPictureModeChanged(emPip: Boolean, c: Configuration) {
        super.onPictureInPictureModeChanged(emPip, c)
        bar.visibility = if (emPip) View.GONE else View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val w = current?.let { views[it] }
        if (w != null && w.canGoBack()) w.goBack() else moveTaskToBack(true)
    }
}
