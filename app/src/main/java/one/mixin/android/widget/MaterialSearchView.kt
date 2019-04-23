package one.mixin.android.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.jakewharton.rxbinding3.widget.textChanges
import kotlinx.android.synthetic.main.view_search.view.*
import one.mixin.android.R
import one.mixin.android.extension.animateWidth
import one.mixin.android.extension.appCompatActionBarHeight
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.hideKeyboard
import one.mixin.android.extension.showKeyboard
import one.mixin.android.extension.translationX
import java.util.concurrent.TimeUnit

class MaterialSearchView : FrameLayout {
    var isOpen = false
        private set
    private var mClearingFocus: Boolean = false

    private var mCurrentQuery: CharSequence? = null
    var mOnQueryTextListener: OnQueryTextListener? = null
    private var mSearchViewListener: SearchViewListener? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        LayoutInflater.from(context).inflate(R.layout.view_search, this, true)
        initStyle(attrs, defStyleAttr)
        initSearchView()
    }

    @Suppress("unused")
    val currentQuery: String
        get() = if (!TextUtils.isEmpty(mCurrentQuery)) {
            mCurrentQuery.toString()
        } else ""

    private fun initStyle(attributeSet: AttributeSet?, defStyleAttribute: Int) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        val typedArray = context.obtainStyledAttributes(attributeSet,
            R.styleable.MaterialSearchView, defStyleAttribute, 0)
        if (typedArray != null) {
            if (typedArray.hasValue(R.styleable.MaterialSearchView_android_textColor)) {
                setTextColor(typedArray.getColor(R.styleable.MaterialSearchView_android_textColor,
                    ContextCompat.getColor(context, R.color.black)))
            }
            if (typedArray.hasValue(R.styleable.MaterialSearchView_android_textColorHint)) {
                setHintTextColor(typedArray.getColor(R.styleable.MaterialSearchView_android_textColorHint,
                    ContextCompat.getColor(context, R.color.gray_50)))
            }
            if (typedArray.hasValue(R.styleable.MaterialSearchView_android_hint)) {
                setHint(typedArray.getString(R.styleable.MaterialSearchView_android_hint))
            }
            if (typedArray.hasValue(R.styleable.MaterialSearchView_searchCloseIcon)) {
                setCancelIcon(typedArray.getResourceId(
                    R.styleable.MaterialSearchView_searchCloseIcon,
                    R.drawable.ic_action_navigation_close)
                )
            }
            if (typedArray.hasValue(R.styleable.MaterialSearchView_searchBackIcon)) {
                setBackIcon(typedArray.getResourceId(
                    R.styleable.MaterialSearchView_searchBackIcon,
                    R.drawable.ic_wallet)
                )
            }
            if (typedArray.hasValue(R.styleable.MaterialSearchView_android_inputType)) {
                setInputType(typedArray.getInteger(
                    R.styleable.MaterialSearchView_android_inputType,
                    InputType.TYPE_CLASS_TEXT)
                )
            }
            if (typedArray.hasValue(R.styleable.MaterialSearchView_searchBarHeight)) {
                setSearchBarHeight(typedArray.getDimensionPixelSize(
                    R.styleable.MaterialSearchView_searchBarHeight, context.appCompatActionBarHeight()))
            } else {
                setSearchBarHeight(context.appCompatActionBarHeight())
            }
            @Suppress("DEPRECATION")
            ViewCompat.setFitsSystemWindows(this,
                typedArray.getBoolean(R.styleable.MaterialSearchView_android_fitsSystemWindows, false))
            typedArray.recycle()
        }
    }

    @SuppressLint("CheckResult")
    private fun initSearchView() {
        search_et.setOnEditorActionListener { _, _, _ ->
            onSubmitQuery()
            true
        }

        search_et.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // When the text changes, filter
                if (search_et.text.isEmpty()) {
                    right_clear.visibility = View.GONE
                } else {
                    right_clear.visibility = View.VISIBLE
                }
            }

            override fun afterTextChanged(s: Editable) {}
        })

        search_et.textChanges().debounce(300, TimeUnit.MILLISECONDS).subscribe {
            this@MaterialSearchView.onTextChanged(it)
        }

        search_et.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                search_et.post { search_et.showKeyboard() }
            }
        }

        right_clear.setOnClickListener {
            if (search_et.text.isNotEmpty()) {
                search_et.setText("")
            }
        }

        search_tv.setOnClickListener {
            openSearch()
        }
    }

    var oldLeftX = 0f
    var oldSearchWidth = 0

    fun dragSearch(progress: Float) {
        right_ib.translationX = context.dpToPx(42f) * progress
        search_tv.alpha = 1 - progress
        left_ib.alpha = Math.max(1 - 2 * progress, 0f)
        back_ib.alpha = (Math.max(progress, .5f) - .5f) * 2
    }

    fun openSearch() {
        synchronized(this) {
            search_tv.animate().apply {
                setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        setListener(null)
                        search_et.isVisible = true
                    }
                })
            }.alpha(0f).start()
            showKeyboard()

            left_ib.animate().apply {
                setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        setListener(null)
                        back_ib.animate().setDuration(150L).alpha(1f).start()
                    }
                })
            }.setDuration(150L).alpha(0f).start()
            right_clear.visibility = View.GONE

            search_et.requestFocus()
            search_et.setText("")
            oldLeftX = left_ib.x
            oldSearchWidth = search_et.measuredWidth
            right_ib.translationX(context.dpToPx(42f).toFloat())
            search_et.animateWidth(search_et.width, oldSearchWidth + context.dpToPx(36f))
            mSearchViewListener?.onSearchViewOpened()
            isOpen = true
            search_et.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0F, 0F, 0))
            search_et.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 0F, 0F, 0))
        }
    }

    fun closeSearch() {
        synchronized(this) {
            search_tv.animate().apply {
                setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        setListener(null)
                        search_et.isGone = true
                    }
                })
            }.alpha(1f).start()
            back_ib.animate().apply {
                setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        setListener(null)
                        left_ib.animate().setDuration(150L).alpha(1f).start()
                    }
                })
            }.setDuration(150L).alpha(0f).start()
            right_clear.visibility = View.GONE

            right_ib.translationX(0f)
            search_et.animateWidth(search_et.width, oldSearchWidth)
            clearFocus()
            search_et.hideKeyboard()
            search_et.setText("")
            mSearchViewListener?.onSearchViewClosed()
            isOpen = false
        }
    }

    private fun onTextChanged(newText: CharSequence) {
        mCurrentQuery = search_et.text
        mOnQueryTextListener?.onQueryTextChange(newText.toString())
    }

    private fun onSubmitQuery() {
        val query = search_et.text

        if (query != null && TextUtils.getTrimmedLength(query) > 0) {
            if (mOnQueryTextListener == null) {
                closeSearch()
                search_et.setText("")
            }
        }
    }

    fun setSearchViewListener(mSearchViewListener: SearchViewListener) {
        this.mSearchViewListener = mSearchViewListener
    }

    fun setQuery(query: CharSequence?, submit: Boolean) {
        search_et.setText(query)

        if (query != null) {
            search_et.setSelection(search_et.length())
            mCurrentQuery = query
        }

        if (submit && !TextUtils.isEmpty(query)) {
            onSubmitQuery()
        }
    }

    fun setSearchBarColor(color: Int) {
        search_et.setBackgroundColor(color)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        if (factor < 0) return color

        val alpha = Math.round(Color.alpha(color) * factor)

        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun setTextColor(color: Int) {
        search_et.setTextColor(color)
    }

    fun setHintTextColor(color: Int) {
        search_et.setHintTextColor(color)
    }

    fun setHint(hint: CharSequence?) {
        search_et.hint = hint
    }

    fun setCancelIcon(resourceId: Int) {
        back_ib.setImageResource(resourceId)
    }

    fun setBackIcon(resourceId: Int) {
        left_ib.setImageResource(resourceId)
    }

    fun setInputType(inputType: Int) {
        search_et.inputType = inputType
    }

    fun setSearchBarHeight(height: Int) {
        search_view.minimumHeight = height
        search_view.layoutParams.height = height
    }

    fun setOnRightClickListener(onClickListener: OnClickListener) {
        right_ib.setOnClickListener(onClickListener)
    }

    fun setOnLeftClickListener(onClickListener: OnClickListener) {
        left_ib.setOnClickListener(onClickListener)
    }

    fun setOnBackClickListener(onClickListener: OnClickListener) {
        back_ib.setOnClickListener(onClickListener)
    }

    override fun clearFocus() {
        this.mClearingFocus = true
        hideKeyboard()
        super.clearFocus()
        search_et.clearFocus()
        this.mClearingFocus = false
    }

    interface OnQueryTextListener {
        fun onQueryTextChange(newText: String): Boolean
    }

    interface SearchViewListener {
        fun onSearchViewOpened()

        fun onSearchViewClosed()
    }
}
