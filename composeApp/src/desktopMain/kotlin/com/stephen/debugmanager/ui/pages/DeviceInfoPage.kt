package com.stephen.debugmanager.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stephen.debugmanager.MainStateHolder
import com.stephen.debugmanager.data.Constants.PULL_FILE_TOAST
import com.stephen.debugmanager.data.uistate.DeviceState
import com.stephen.debugmanager.ui.component.*
import com.stephen.debugmanager.ui.theme.groupTitleText
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceInfoPage(deviceState: DeviceState, onRefresh: () -> Unit) {

    val mainStateHolder by remember { mutableStateOf(GlobalContext.get().get<MainStateHolder>()) }

    val toastState = rememberToastState()

    val mockInputSting = remember { mutableStateOf("") }

    val recordTime = remember { mutableStateOf("") }

    BasePage("设备信息") {
        Box {
            LazyColumn {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(
                            modifier = Modifier.width(IntrinsicSize.Max)
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.weight(1f).padding(bottom = 10.dp)
                                        .align(Alignment.Top)
                                ) {
                                    CenterText("设备基础信息", style = groupTitleText)
                                }
                                CommonButton(
                                    onClick = { onRefresh() },
                                    text = "刷新",
                                )
                            }

                            NameValueText("SerialNumber", deviceState.serial ?: "null")

                            NameValueText("型号", deviceState.name ?: "null")

                            NameValueText("内部代号", deviceState.innerName ?: "null")

                            NameValueText("制造商", deviceState.manufacturer ?: "null")

                            NameValueText("操作系统版本", deviceState.systemVersion ?: "null")

                            NameValueText("版本构建类型", deviceState.buildType ?: "null")

                            NameValueText("Android版本", deviceState.sdkVersion ?: "null")

                            NameValueText("CPU架构", deviceState.cpuArch ?: "null")

                            NameValueText("分辨率", deviceState.resolution ?: "null")

                            NameValueText("显示密度", deviceState.density ?: "null")
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxRowHeight()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(10.dp)
                        ) {
                            CenterText(
                                "快捷操作",
                                style = groupTitleText,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            FlowRow(
                                maxItemsInEachRow = 3,
                                modifier = Modifier.width(IntrinsicSize.Max)
                            ) {

                                val itemModifier = Modifier
                                    .fillMaxRowHeight(1f)
                                    .weight(1f)
                                    .padding(5.dp)

                                CommonButton(
                                    "ROOT", onClick = {
                                        mainStateHolder.root()
                                    },
                                    modifier = itemModifier
                                )
                                CommonButton(
                                    "REMOUNT", onClick = {
                                        mainStateHolder.remount()
                                        toastState.show("已执行，如果是刷完机首次remount，请先重启设备")
                                    },
                                    modifier = itemModifier
                                )
                                CommonButton(
                                    "To Recovery", onClick = {
                                        mainStateHolder.rebootRecovery()
                                    },
                                    modifier = itemModifier,
                                    btnColor = MaterialTheme.colorScheme.error
                                )
                                CommonButton(
                                    "REBOOT", onClick = {
                                        mainStateHolder.rebootDevice()
                                    },
                                    modifier = itemModifier,
                                    btnColor = MaterialTheme.colorScheme.error
                                )
                                CommonButton(
                                    "To Fastboot", onClick = {
                                        mainStateHolder.rebootFastboot()
                                    },
                                    modifier = itemModifier,
                                    btnColor = MaterialTheme.colorScheme.error
                                )
                                CommonButton(
                                    "关机", onClick = {
                                        mainStateHolder.powerOff()
                                    },
                                    modifier = itemModifier,
                                    btnColor = MaterialTheme.colorScheme.error
                                )
                                CommonButton(
                                    "开始抓取trace", onClick = {
                                        mainStateHolder.startCollectTrace()
                                        toastState.show("默认抓取10s，$PULL_FILE_TOAST")
                                    },
                                    modifier = itemModifier
                                )
                                CommonButton(
                                    "打开Google设置", onClick = {
                                        mainStateHolder.openAndroidSettings()
                                    },
                                    modifier = itemModifier
                                )
                                CommonButton(
                                    "打开scrcpy投屏", onClick = {
                                        mainStateHolder.openScreenCopy()
                                    },
                                    modifier = itemModifier
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxRowHeight()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(10.dp)
                        ) {
                            CenterText(
                                "录屏与截屏",
                                style = groupTitleText,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    WrappedEditText(
                                        value = recordTime.value,
                                        tipText = "设置时长(s)",
                                        onValueChange = {
                                            recordTime.value = it
                                        },
                                        modifier = Modifier.padding(horizontal = 5.dp).weight(1f)
                                    )
                                    CommonButton(
                                        "开始录屏", onClick = {
                                            if (mainStateHolder.isRecording) {
                                                toastState.show("上次录制还未完成")
                                            } else if (recordTime.value.isEmpty()) {
                                                toastState.show("请先输入录制时长")
                                            } else {
                                                runCatching {
                                                    val timeInt = recordTime.value.toInt()
                                                    mainStateHolder.startScreenRecord(timeInt)
                                                    recordTime.value = ""
                                                    toastState.show("已开始，录制期间会显示手指点击位置，$PULL_FILE_TOAST")
                                                }.onFailure {
                                                    toastState.show("请输入正确的时长")
                                                    recordTime.value = ""
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                val btnModifier = Modifier
                                    .fillMaxWidth(1f)
                                    .padding(vertical = 5.dp)

                                CommonButton(
                                    "截屏保存", onClick = {
                                        mainStateHolder.screenshot()
                                        toastState.show(PULL_FILE_TOAST)
                                    },
                                    modifier = btnModifier
                                )

                                CommonButton(
                                    "清空录屏缓存", onClick = {
                                        if (mainStateHolder.isRecording) {
                                            toastState.show("录屏中，请稍后再试")
                                        } else {
                                            mainStateHolder.clearRecordCache()
                                            toastState.show("已清空缓存，节省空间")
                                        }
                                    },
                                    modifier = btnModifier,
                                    btnColor = MaterialTheme.colorScheme.error
                                )

                                CommonButton(
                                    "清空截屏图片缓存", onClick = {
                                        mainStateHolder.clearScreenShotsCache()
                                        toastState.show("已清空缓存，节省空间")
                                    },
                                    modifier = btnModifier,
                                    btnColor = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxRowHeight()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(10.dp)
                        ) {
                            CenterText(
                                "模拟按键",
                                style = groupTitleText,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            FlowRow(
                                maxItemsInEachRow = 3,
                                modifier = Modifier.width(IntrinsicSize.Max)
                            ) {

                                val itemModifier = Modifier
                                    .fillMaxRowHeight(1f)
                                    .weight(1f)
                                    .padding(5.dp)

                                CommonButton(
                                    "返回按键", onClick = {
                                        mainStateHolder.mockBackPressed()
                                    },
                                    modifier = itemModifier
                                )
                                CommonButton(
                                    "回到桌面", onClick = {
                                        mainStateHolder.mockHomePressed()
                                    },
                                    modifier = itemModifier
                                )
                                CommonButton(
                                    "最近任务", onClick = {
                                        mainStateHolder.mockRecentPressed()
                                    },
                                    modifier = itemModifier
                                )

                                CommonButton(
                                    "亮屏", onClick = {
                                        mainStateHolder.turnOnScreen()
                                    },
                                    modifier = itemModifier
                                )
                                CommonButton(
                                    "灭屏", onClick = {
                                        mainStateHolder.turnOffScreen()
                                    },
                                    modifier = itemModifier
                                )
                                CommonButton(
                                    "锁屏", onClick = {
                                        mainStateHolder.lockScreen()
                                    },
                                    modifier = itemModifier
                                )

                                CommonButton(
                                    "静音开关", onClick = {
                                        mainStateHolder.muteDevice()
                                    },
                                    modifier = itemModifier
                                )
                                CommonButton(
                                    "音量+", onClick = {
                                        mainStateHolder.volumeUp()
                                    },
                                    modifier = itemModifier
                                )
                                CommonButton(
                                    "音量-", onClick = {
                                        mainStateHolder.volumeDown()
                                    },
                                    modifier = itemModifier
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    WrappedEditText(
                                        value = mockInputSting.value,
                                        tipText = "模拟输入法(English Only)",
                                        onValueChange = { mockInputSting.value = it },
                                        modifier = Modifier.padding(start = 10.dp, end = 10.dp)
                                            .weight(1f),
                                        onEnterPressed = {
                                            mainStateHolder.inputText(mockInputSting.value)
                                            mockInputSting.value = ""
                                        }
                                    )
                                    CommonButton(
                                        "确认", onClick = {
                                            mainStateHolder.inputText(mockInputSting.value)
                                            mockInputSting.value = ""
                                        },
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 设备未连接，显示提示文案
            if (deviceState.isConnected.not()) {
                DeviceNoneConnectShade()
            }
        }
    }
}