/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.components

import android.content.Context
import android.view.View
import android.view.LayoutInflater
import android.util.AttributeSet
import org.plainphone.ui.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Compose owns scrolling and its accessibility actions. Android draws its native scrollbar in
 * the empty edge gutter. It has no touch/semantic action and never covers a text/control column.
 */
@Composable
internal fun TaskScroll(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll), content = content)
        AndroidView(factory = { LayoutInflater.from(it).inflate(R.layout.task_scroll_indicator, null, false) as NativeScrollIndicator },
            modifier = Modifier.align(Alignment.CenterEnd).width(12.dp).fillMaxHeight(),
            update = { it.showScroll(scroll.value, scroll.maxValue) })
    }
}
internal class NativeScrollIndicator(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var offset = 0
    private var maximum = 0
    init {
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = false
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
        setWillNotDraw(false)
    }
    fun showScroll(offset: Int, maximum: Int) {
        this.offset = offset
        this.maximum = if (maximum == Int.MAX_VALUE) 0 else maximum
        isVerticalScrollBarEnabled = this.maximum > 0
        awakenScrollBars()
        invalidate()
    }
    override fun computeVerticalScrollOffset() = offset
    override fun computeVerticalScrollExtent() = height
    override fun computeVerticalScrollRange() = (height.toLong() + maximum).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
