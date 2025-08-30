package com.stephen.debugmanager.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.stephen.debugmanager.ui.theme.infoText
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 弹出式提示框
 *
 * @param visible 是否显示
 * @param title 标题
 * @param duration 显示的时长
 * @param onClose 关闭事件
 */
@Composable
fun WeToast(
    visible: Boolean,
    title: String,
    duration: Duration = 2000.milliseconds,
    onClose: () -> Unit
) {
    var localVisible by remember {
        mutableStateOf(visible)
    }

    LaunchedEffect(visible, duration, title) {
        if (visible && duration != Duration.INFINITE) {
            delay(duration)
            onClose()
        }
    }
    LaunchedEffect(visible) {
        if (!visible) {
            delay(150)
        }
        localVisible = visible
    }

    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                // 居中显示
                return IntOffset(
                    windowSize.width / 2 - popupContentSize.width / 2,
                    windowSize.height / 2 - popupContentSize.height / 2
                )
            }
        }
    }
    if (visible || localVisible) {
        Popup(popupPositionProvider = positionProvider) {
            AnimatedVisibility(
                visible = visible && localVisible,
                enter = fadeIn() + scaleIn(tween(100), initialScale = 0.8f),
                exit = fadeOut() + scaleOut(tween(100), targetScale = 0.8f)
            ) {
                Box(
                    modifier = Modifier.widthIn(max = 500.dp)
                        .clip(RoundedCornerShape(10))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CenterText(text = title, style = infoText, color = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
}


@Stable
interface ToastState {
    /**
     * 是否显示
     */
    val visible: Boolean

    /**
     * 显示提示框
     */
    fun show(
        title: String,
        duration: Duration = 3000.milliseconds
    )

    /**
     * 隐藏提示框
     */
    fun hide()
}

@Composable
fun rememberToastState(): ToastState {
    val state = remember { ToastStateImpl() }

    state.props?.let { props ->
        WeToast(
            visible = state.visible,
            title = props.title,
            duration = props.duration,
        ) {
            state.hide()
        }
    }

    return state
}

private class ToastStateImpl : ToastState {
    override var visible by mutableStateOf(false)
    var props by mutableStateOf<ToastProps?>(null)
        private set

    override fun show(title: String, duration: Duration) {
        props = ToastProps(title, duration)
        visible = true
    }

    override fun hide() {
        visible = false
    }
}

private data class ToastProps(
    val title: String,
    val duration: Duration,
)