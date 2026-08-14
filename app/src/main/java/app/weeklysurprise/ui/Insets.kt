package app.weeklysurprise.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 让内容避开状态栏和导航栏。
 *
 * Android 15（API 35）起应用默认铺满整个屏幕，内容会延伸到系统栏底下。
 * 不处理的话，顶部标题会被状态栏压住、列表最后一条会被导航栏吃掉 ——
 * 后者尤其隐蔽：短列表看不出问题，只有内容长到需要滚动才暴露。
 */
fun View.applySystemBarInsets(top: Boolean = true, bottom: Boolean = true) {
    // 保留布局里原本写的内边距，系统栏高度在此基础上追加
    val initialTop = paddingTop
    val initialBottom = paddingBottom

    if (this is ViewGroup) clipToPadding = false

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            top = if (top) initialTop + bars.top else initialTop,
            bottom = if (bottom) initialBottom + bars.bottom else initialBottom,
        )
        insets
    }
}
