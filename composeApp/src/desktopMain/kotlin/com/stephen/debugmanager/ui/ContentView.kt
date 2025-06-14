package com.stephen.debugmanager.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stephen.composeapp.generated.resources.*
import com.stephen.debugmanager.MainStateHolder
import com.stephen.debugmanager.data.Constants
import com.stephen.debugmanager.data.bean.MainTabItem
import com.stephen.debugmanager.data.uistate.DirectoryState
import com.stephen.debugmanager.ui.component.CenterText
import com.stephen.debugmanager.ui.component.DropdownSelector
import com.stephen.debugmanager.ui.component.SimpleDivider
import com.stephen.debugmanager.ui.pages.*
import com.stephen.debugmanager.ui.theme.defaultText
import com.stephen.debugmanager.ui.theme.pageTitleText
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.GlobalContext

@Composable
fun ContentView(windowWith: Dp) {

    val mainItemList = listOf(
        Constants.DEVICE_INFO to Res.drawable.ic_devices,
        Constants.INSTALL to Res.drawable.ic_software,
        Constants.FILE_MANAGE to Res.drawable.ic_file_manage,
        Constants.COMMAND to Res.drawable.ic_terminal,
        Constants.PERFORMANCE to Res.drawable.ic_performance,
        Constants.TOOLS to Res.drawable.ic_tools,
        Constants.AI_MODEL to Res.drawable.ic_robot,
        Constants.ABOUT to Res.drawable.ic_about,
    ).map { (name, icon) -> MainTabItem(name, icon) }

    val isMenuExpanded = remember { mutableStateOf(true) }

    LaunchedEffect(windowWith) {
        if (windowWith.value < 650) {
            isMenuExpanded.value = false
        }
    }

    val choosedTab = remember { mutableStateOf(mainItemList[0]) }

    val mainStateHolder by remember { mutableStateOf(GlobalContext.get().get<MainStateHolder>()) }

    val deviceMapState by mainStateHolder.deviceMapStateStateFlow.collectAsState()

    val deviceState by mainStateHolder.deviceStateStateFlow.collectAsState()

    val directoryState by mainStateHolder.directoryStateStateFlow.collectAsState()

    val appListState by mainStateHolder.appListStateStateFlow.collectAsState()

    LaunchedEffect(Unit) {
        mainStateHolder.getDeviceMap()
        mainStateHolder.getCurrentDeviceInfo()
    }

    Row(modifier = Modifier.fillMaxSize(1f)) {
        val navController = rememberNavController()
        AnimatedContent(targetState = isMenuExpanded.value) { expanded ->
            if (expanded) {
                Row {
                    SideTabBar(
                        deviceMapState.deviceMap,
                        deviceMapState.currentChoosedDevice.toString(),
                        onDeviceSelect = {
                            mainStateHolder.setChooseDevice(it.toInt())
                            mainStateHolder.getCurrentDeviceInfo()
                        },
                        mainItemList,
                        onItemClick = {
                            // 检查当前是否已经在目标页面，如果不是则进行导航
                            if (navController.currentDestination?.route != it.name) {
                                navController.navigate(it.name)
                            }
                            choosedTab.value = it
                        },
                        chooseTabItem = choosedTab.value,
                        modifier = Modifier.fillMaxHeight(1f).animateContentSize()
                    )
                    SimpleDivider(modifier = Modifier.width(2.dp).fillMaxHeight(1f))
                }
            }
        }
        // 右侧内容区
        Box(modifier = Modifier.weight(1f).animateContentSize()) {
            NavHost(navController, startDestination = Constants.DEVICE_INFO.toString()) {
                composable(Constants.DEVICE_INFO.toString()) {
                    DeviceInfoPage(
                        deviceState,
                        onRefresh = {
                            mainStateHolder.getCurrentDeviceInfo()
                        })
                }
                composable(Constants.INSTALL.toString()) {
                    ApkManagePage(appListState, deviceState.isConnected) {
                        mainStateHolder.getPackageList(it)
                    }
                }
                composable(Constants.FILE_MANAGE.toString()) {
                    FileManagePage(
                        DirectoryState(
                            directoryState.deviceCode,
                            directoryState.currentdirectory,
                            directoryState.subdirectories,
                        ),
                        deviceState.isConnected,
                        destinationCall = { destination ->
                            mainStateHolder.getFileList(destination)
                        }
                    )
                }
                composable(Constants.COMMAND.toString()) {
                    CommandPage(deviceState.isConnected)
                }
                composable(Constants.PERFORMANCE.toString()) {
                    PerformancePage(deviceState.isConnected, appListState)
                }
                composable(Constants.TOOLS.toString()) {
                    ToolsPage()
                }
                composable(Constants.ABOUT.toString()) {
                    AboutPage()
                }
                composable(Constants.AI_MODEL.toString()) {
                    AiModelPage()
                }
            }

            Image(
                painter = painterResource(
                    if (isMenuExpanded.value) Res.drawable.ic_menu_collapse
                    else Res.drawable.ic_menu_expand
                ),
                contentDescription = "menu_adjust",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.padding(10.dp).size(30.dp).clickable {
                    isMenuExpanded.value = !isMenuExpanded.value
                }.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
fun SideTabBar(
    deviceMap: Map<String, String>,
    deviceSelectedPosition: String,
    onDeviceSelect: (String) -> Unit,
    mainItemList: List<MainTabItem>,
    onItemClick: (name: MainTabItem) -> Unit,
    chooseTabItem: MainTabItem,
    modifier: Modifier = Modifier
) {
    val sideBarWidth = 180.dp

    LazyColumn(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        item {
            DropdownSelector(
                deviceMap,
                // 当拔掉设备，map长度减少时，防止deviceSelectedPosition越界，设为map长度为其最大值
                deviceSelectedPosition.toInt().coerceAtMost(deviceMap.size - 1).toString(),
                modifier = Modifier.width(sideBarWidth),
            ) {
                // 返回选中的设备位置，0123
                onDeviceSelect(it)
            }
        }
        item {
            Spacer(Modifier.height(40.dp))
        }
        item {
            Column(Modifier.width(sideBarWidth)) {
                mainItemList.forEach { it ->
                    SideTabItem(
                        icon = it.icon,
                        title = it.name,
                        isSelected = chooseTabItem == it,
                        modifier = Modifier.fillMaxWidth(1f).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onItemClick(it)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SideTabItem(icon: DrawableResource, title: String, modifier: Modifier, isSelected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(3.dp).clip(RoundedCornerShape(10))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(icon),
                modifier = Modifier.padding(end = 10.dp).size(22.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondary),
                contentDescription = "tab_icon"
            )
            CenterText(
                title,
                style = defaultText,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}