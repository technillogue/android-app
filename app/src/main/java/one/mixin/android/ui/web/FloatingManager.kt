package one.mixin.android.ui.web

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.webkit.WebViewClient
import androidx.annotation.ColorInt
import androidx.core.view.drawToBitmap
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.initRenderScript
import one.mixin.android.extension.isNightMode
import one.mixin.android.extension.putString
import one.mixin.android.extension.remove
import one.mixin.android.extension.toast
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.SINGLE_THREAD
import one.mixin.android.vo.App
import one.mixin.android.widget.MixinWebView
import org.jetbrains.anko.collections.forEachByIndex

private const val PREF_FLOATING = "floating"
private var screenshot: Bitmap? = null

fun getScreenshot() = screenshot

fun refreshScreenshot(context: Context) {
    MixinApplication.get().currentActivity?.let { activity ->
        val rootView: View = activity.window.decorView.findViewById(android.R.id.content)
        if (!rootView.isLaidOut) return@let

        val screenBitmap = rootView.drawToBitmap()
        val resultBitmap = Bitmap.createScaledBitmap(
            screenBitmap,
            screenBitmap.width / 3,
            screenBitmap.height / 3,
            false
        )

        val cv = Canvas(resultBitmap)
        cv.drawBitmap(resultBitmap, 0f, 0f, Paint())
        cv.drawRect(
            0f,
            0f,
            screenBitmap.width.toFloat(),
            screenBitmap.height.toFloat(),
            Paint().apply {
                color = if (context.isNightMode()) {
                    Color.parseColor("#CC1C1C1C")
                } else {
                    Color.parseColor("#E6F6F7FA")
                }
            }
        )
        screenshot = resultBitmap
    }
    initRenderScript(context)
}

fun expand(context: Context) {
    refreshScreenshot(context)
    WebActivity.show(context)
    FloatingWebClip.getInstance().hide()
}

fun collapse(activity: Activity) {
    if (clips.size > 0) {
        FloatingWebClip.getInstance().show(activity)
    }
}

var clips = mutableListOf<WebClip>()

data class WebClip(
    var url: String,
    var app: App?,
    @ColorInt
    val titleColor: Int,
    val name: String?,
    val thumb: Bitmap?,
    val icon: Bitmap?,
    val conversationId: String?,
    val shareable: Boolean?,
    @Transient val webView: MixinWebView?,
    @Transient val isFinished: Boolean = false
)

fun updateClip(index: Int, webClip: WebClip) {
    if (index < clips.size) {
        if (clips[index].webView != webClip.webView) {
            clips[index].webView?.destroy()
            clips[index].webView?.webViewClient = object : WebViewClient() {}
            clips[index].webView?.webChromeClient = null
        }
        clips.removeAt(index)
        clips.add(index, webClip)
    }
}

fun showClip(activity: Activity) {
    collapse(activity)
}

fun holdClip(activity: Activity, webClip: WebClip) {
    if (!clips.contains(webClip)) {
        if (clips.size >= 6) {
            activity.toast(R.string.web_full)
        } else {
            clips.add(webClip)
            FloatingWebClip.getInstance().show(activity)
        }
    }
}

private fun initClips(activity: Activity) {
    MixinApplication.appScope.launch(SINGLE_THREAD) {
        val content =
            activity.defaultSharedPreferences.getString(PREF_FLOATING, null) ?: return@launch
        val type = object : TypeToken<List<WebClip>>() {}.type
        val list = GsonHelper.customGson.fromJson<List<WebClip>>(content, type)
        clips.clear()
        if (list.isEmpty()) return@launch
        clips.addAll(list)
        MixinApplication.get().currentActivity?.let { activity ->
            withContext(Dispatchers.Main) {
                FloatingWebClip.getInstance().show(activity)
            }
        }
    }
}

fun refresh(activity: Activity) {
    if (clips.isEmpty()) {
        initClips(activity)
    } else {
        FloatingWebClip.getInstance().show(activity, false)
    }
}

fun releaseClip(index: Int) {
    if (index < clips.size && index >= 0) {
        clips[index].webView?.destroy()
        clips[index].webView?.webViewClient = object : WebViewClient() {}
        clips[index].webView?.webChromeClient = null
        clips.removeAt(index)
        if (clips.isEmpty()) {
            saveJob?.cancel()
            saveJob = null
            MixinApplication.appContext.defaultSharedPreferences.remove(PREF_FLOATING)
            FloatingWebClip.getInstance().hide()
        } else {
            FloatingWebClip.getInstance().reload()
        }
    }
}

var saveJob: Job? = null
fun saveClips() {
    saveJob?.cancel()
    saveJob = MixinApplication.appScope.launch(SINGLE_THREAD) {
        val localClips = mutableListOf<WebClip>().apply {
            addAll(clips)
        }
        MixinApplication.appContext.defaultSharedPreferences.putString(
            PREF_FLOATING,
            GsonHelper.customGson.toJson(localClips)
        )
    }
}

fun replaceApp(app: App) {
    var hasChange = false
    clips.forEachIndexed { index, webClip ->
        if (webClip.url == webClip.app?.homeUri && webClip.app?.appId == app.appId) {
            webClip.url = app.homeUri
            webClip.app = app
            clips[index] = webClip
            hasChange = true
        }
    }
    if (hasChange) {
        saveClips()
    }
}

fun releaseAll() {
    clips.forEach { clip ->
        clip.webView?.destroy()
        clip.webView?.webViewClient = object : WebViewClient() {}
        clip.webView?.webChromeClient = null
    }
    clips.clear()
    saveJob?.cancel()
    saveJob = null
    MixinApplication.appContext.defaultSharedPreferences.remove(PREF_FLOATING)
    FloatingWebClip.getInstance().hide()
}
