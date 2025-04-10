package com.stephen.debugmanager.ui.pages

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.stephen.composeapp.generated.resources.Res
import com.stephen.composeapp.generated.resources.ic_add_one
import com.stephen.composeapp.generated.resources.ic_delete_two
import com.stephen.debugmanager.MainStateHolder
import com.stephen.debugmanager.ui.component.BasePage
import com.stephen.debugmanager.ui.component.CenterText
import com.stephen.debugmanager.ui.component.CommonButton
import com.stephen.debugmanager.ui.component.FileChooseWidget
import com.stephen.debugmanager.ui.component.WrappedEditText
import com.stephen.debugmanager.ui.component.rememberToastState
import com.stephen.debugmanager.ui.theme.groupTitleText
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ToolsPage() {

    val mainStateHolder by remember { mutableStateOf(GlobalContext.get().get<MainStateHolder>()) }

    var logFolderPath by remember { mutableStateOf("") }

    val logTag = remember { mutableStateOf("") }

    val logTagList = remember { mutableStateListOf<String>() }

    val toastState = rememberToastState()

    BasePage("实用工具") {
        LazyColumn {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Column(
                        modifier = Modifier
                            .animateContentSize()
                            .fillMaxRowHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp)
                    ) {
                        CenterText(
                            "日志文件处理",
                            style = groupTitleText,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                            FileChooseWidget(
                                tintText = "将日志文件夹拖到此处 或 点击选取",
                                modifier = Modifier.fillMaxWidth(1f).weight(0.5f).padding(bottom = 10.dp),
                                path = logFolderPath,
                                isChooseFile = false
                            ) {
                                logFolderPath = it
                                toastState.show("开始之前，尽量缩小范围，删除无关日志，以免搜索耗时过长")
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(1f).weight(0.5f).padding(bottom = 10.dp)
                            ) {
                                WrappedEditText(
                                    value = logTag.value,
                                    tipText = "Tag(区分大小写)",
                                    onValueChange = {
                                        logTag.value = it
                                    },
                                    onEnterPressed = {
                                        if (logTagList.size >= 10) {
                                            toastState.show("最多只能添加10个tag")
                                        } else if (logTagList.contains(logTag.value)) {
                                            toastState.show("已存在该tag")
                                        } else if (logTag.value.isNotEmpty()) {
                                            logTagList.add(logTag.value)
                                            logTag.value = ""
                                        } else {
                                            toastState.show("请先输入tag")
                                        }
                                    },
                                    modifier = Modifier.padding(end = 10.dp).weight(1f)
                                )
                                Image(
                                    painter = painterResource(Res.drawable.ic_add_one),
                                    contentDescription = "add a tag",
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                                    modifier = Modifier.size(36.dp).clickable {
                                        if (logTagList.size >= 10) {
                                            toastState.show("最多只能添加10个tag")
                                        } else if (logTagList.contains(logTag.value)) {
                                            toastState.show("已存在该tag")
                                        } else if (logTag.value.isNotEmpty()) {
                                            logTagList.add(logTag.value)
                                            logTag.value = ""
                                        } else {
                                            toastState.show("请先输入tag")
                                        }
                                    }
                                )
                            }
                            FlowRow(
                                verticalArrangement = Arrangement.Center, maxItemsInEachRow = 3,
                                modifier = Modifier.width(IntrinsicSize.Max).padding(bottom = 10.dp)
                            ) {
                                logTagList.forEach {
                                    LogTagItem(
                                        tag = it,
                                        modifier = Modifier.padding(5.dp).clip(RoundedCornerShape(10))
                                            .background(MaterialTheme.colorScheme.onSurface)
                                            .padding(5.dp),
                                        onRemove = {
                                            logTagList.remove(it)
                                        }
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                CommonButton(
                                    "开始处理",
                                    onClick = {
                                        if (logFolderPath.isEmpty()) {
                                            toastState.show("请先选择日志文件")
                                        } else if (logTagList.isEmpty()) {
                                            toastState.show("请先输入待寻找的tag")
                                        } else {
                                            toastState.show("开始处理，完成后将自动打开所在文件夹")
                                            mainStateHolder.processLogFiles(
                                                logFolderPath,
                                                logTagList
                                            )
                                        }
                                    },
                                    btnColor = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun LogTagItem(
    tag: String,
    modifier: Modifier,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        CenterText(text = tag, modifier = Modifier.padding(end = 10.dp))
        Image(
            painter = painterResource(Res.drawable.ic_delete_two),
            contentDescription = "delete tag",
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier.padding(horizontal = 5.dp).size(20.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onRemove()
            }
        )
    }
}