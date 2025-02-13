package com.stephen.debugmanager

import androidx.compose.ui.graphics.ImageBitmap
import com.stephen.debugmanager.base.AdbClient
import com.stephen.debugmanager.base.PlatformAdapter
import com.stephen.debugmanager.data.FileOperationType
import com.stephen.debugmanager.data.PackageFilter
import com.stephen.debugmanager.model.AndroidAppHelper
import com.stephen.debugmanager.model.FileManager
import com.stephen.debugmanager.model.uistate.*
import com.stephen.debugmanager.utils.LogUtils
import com.stephen.debugmanager.utils.getDateString
import com.stephen.debugmanager.utils.size
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import java.text.SimpleDateFormat


class MainStateHolder(
    private val adbClient: AdbClient,
    private val platformAdapter: PlatformAdapter,
    private val fileManager: FileManager,
    private val appinfoHelper: AndroidAppHelper
) {

    // 连接的设备列表
    private val _deviceMapState = MutableStateFlow(DeviceMapState())
    val deviceMapStateStateFlow = _deviceMapState.asStateFlow()

    // 单个设备信息
    private val _deviceState = MutableStateFlow(DeviceState())
    val deviceStateStateFlow = _deviceState.asStateFlow()

    // 设备文件列表信息
    private val _fileState = MutableStateFlow(DirectoryState())
    val directoryStateStateFlow = _fileState.asStateFlow()

    // 设备app列表
    private val _appListState = MutableStateFlow(AppListState())
    val appListStateStateFlow = _appListState.asStateFlow()

    init {
        println("MainStateHolder init")
        recycleCheckConnection()
        platformAdapter.init()
        adbClient.init()
    }

    /**
     * 获取设备和position的map
     */
    fun getDeviceMap() {
        CoroutineScope(Dispatchers.IO).launch {
            _deviceMapState.update {
                it.copy(
                    deviceMap = adbClient.getDeviceMap(),
                    currentChoosedDevice = adbClient.choosedDevicePosition
                )
            }
            _deviceMapState.value = _deviceMapState.value.toUiState()
        }
    }

    /**
     * 设置选中的设备
     */
    fun setChooseDevice(postion: Int) {
        adbClient.setChoosedDevice(postion)
        _deviceMapState.update {
            it.copy(
                currentChoosedDevice = adbClient.choosedDevicePosition
            )
        }
        _deviceMapState.value = _deviceMapState.value.toUiState()
    }

    /**
     * 需要加入processing标志位，防止重复执行，getDeviceInfo时会再次调用
     */
    private var isInProcessing = false
    private fun prepareEnv() {
        if (!isInProcessing) {
            isInProcessing = true
            CoroutineScope(Dispatchers.IO).launch {
                LogUtils.printLog("prepareEnv", LogUtils.LogLevel.INFO)
                platformAdapter.executeCommandWithResult("${platformAdapter.localAdbPath} start-server")
                adbClient.runRootScript()
                adbClient.runRemountScript()
                appinfoHelper.tryToLaunchSaveAppInfoService()
                // 获取安装的app列表
                getPackageList()
                isInProcessing = false
            }
        }
    }

    fun root() {
        adbClient.runRootScript()
    }

    fun remount() {
        adbClient.runRemountScript()
    }

    /**
     * 获取设备基本信息
     */
    fun getCurrentDeviceInfo() {
        LogUtils.printLog("getDeviceInfo", LogUtils.LogLevel.INFO)
        runCatching {
            adbClient.runRootScript()
            adbClient.runRemountScript()
            // 根据选中设备的位置拿取设备信息
            CoroutineScope(Dispatchers.IO).launch {
                prepareEnv()
                val deviceName = adbClient.getExecuteResult(adbClient.choosedDevicePosition, "getprop ro.product.model")
                val sdkVersion =
                    adbClient.getExecuteResult(adbClient.choosedDevicePosition, "getprop ro.build.version.release")
                val manufacturer =
                    adbClient.getExecuteResult(adbClient.choosedDevicePosition, "getprop ro.product.brand")
                val systemVersion =
                    adbClient.getExecuteResult(
                        adbClient.choosedDevicePosition,
                        "getprop ro.vendor.build.version.incremental"
                    )
                val buildType =
                    adbClient.getExecuteResult(adbClient.choosedDevicePosition, "getprop ro.vendor.build.type")
                val innerName = adbClient.getExecuteResult(adbClient.choosedDevicePosition, "getprop ro.product.device")
                val architecture =
                    adbClient.getExecuteResult(adbClient.choosedDevicePosition, "getprop ro.product.cpu.abi")
                val displayResolution =
                    adbClient.getExecuteResult(adbClient.choosedDevicePosition, "wm size").split(": ").last()
                val displayDensity =
                    adbClient.getExecuteResult(adbClient.choosedDevicePosition, "wm density").split(": ").last()
                val serialNum = adbClient.serial.split(" ").last()
                _deviceState.update {
                    it.copy(
                        name = deviceName,
                        manufacturer = manufacturer,
                        sdkVersion = sdkVersion,
                        systemVersion = systemVersion,
                        buildType = buildType,
                        density = displayDensity,
                        innerName = innerName,
                        resolution = displayResolution,
                        cpuArch = architecture,
                        serial = serialNum
                    )
                }
                _deviceState.value = _deviceState.value.toUiState()
                // 初始化获取文件列表
                getFileList()
            }
        }.onFailure { e ->
            LogUtils.printLog("获取设备信息失败：${e.message}", LogUtils.LogLevel.ERROR)
        }
    }

    /**
     * 模拟返回按键
     */
    fun mockBackPressed() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell input keyevent 4")
        }
    }

    /**
     * 模拟回到桌面
     */
    fun mockHomePressed() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell input keyevent 3")
        }
    }

    /**
     * 截屏保存到windows
     */
    fun screenshot() {
        CoroutineScope(Dispatchers.IO).launch {
            val androidFileName = "/sdcard/ScreenShot_${getDateString()}.png"
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell screencap -p $androidFileName")
            delay(1000L)
            // 预留1s，等文件生成完毕，再pull
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} pull $androidFileName ${PlatformAdapter.desktopTempFolder}")
        }
    }

    /**
     * 清空录屏缓存文件
     */
    fun clearScreenShotsCache() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell rm /sdcard/ScreenShot_*")
        }
    }

    /**
     * 亮屏
     */
    fun turnOnScreen() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell input keyevent 224")
        }
    }

    /**
     * 灭屏
     */
    fun turnOffScreen() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell input keyevent 223")
        }
    }

    /**
     * 静音
     */
    fun muteDevice() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell input keyevent 164")
        }
    }

    /**
     * 音量加
     */
    fun volumeUp() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell input keyevent 24")
        }
    }

    /**
     * 音量减
     */
    fun volumeDown() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell input keyevent 25")
        }
    }

    /**
     * 模拟文字输入
     */
    fun inputText(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell input text $text")
        }
    }

    /**
     * 打开投屏
     */
    fun openScreenCopy() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localScrcpyPath} ${adbClient.serial}")
        }
    }

    /**
     * 卸载装进去的app，需要在主线程。执行完毕再退出DebnugManager
     */
    fun uninstallToolsApp() {
        appinfoHelper.uninstallAppInfoService()
    }

    /**
     * 录屏并导出
     */
    var isRecording = false
    fun startScreenRecord(recordTime: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            LogUtils.printLog("startScreenRecord")
            isRecording = true
            val androidFilePath = "/sdcard/Record_${getDateString()}.mp4"
            LogUtils.printLog("recordTime: $recordTime")
            // 先打开显示触摸点
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell settings put system show_touches 1")
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell screenrecord --time-limit $recordTime $androidFilePath")
            // 多余1s，等文件生成完毕，再pull
            delay((recordTime + 1) * 1000L)
            LogUtils.printLog("startScreenRecord time up")
            // 关闭显示触摸点
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell settings put system show_touches 0")
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} pull $androidFilePath ${PlatformAdapter.desktopTempFolder}")
            delay(recordTime * 1000L)
            isRecording = false
        }
    }

    /**
     * 清空录屏缓存文件
     */
    fun clearRecordCache() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell rm /sdcard/Record_*.mp4")
        }
    }

    /**
     * recovery模式
     */
    fun rebootRecovery() {
        CoroutineScope(Dispatchers.IO).launch {
            LogUtils.printLog("rebootRecovery")
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} reboot recovery")
        }
    }

    /**
     * fastboot模式
     */
    fun rebootFastboot() {
        CoroutineScope(Dispatchers.IO).launch {
            LogUtils.printLog("rebootFastboot")
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} reboot bootloader")
        }
    }

    /**
     * 开始抓取trace文件
     */
    fun startCollectTrace() {
        CoroutineScope(Dispatchers.IO).launch {
            LogUtils.printLog("startCollectTrace")
            runCatching {
                val traceLogName =
                    "trace_log_" + SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(System.currentTimeMillis())
                platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell setprop persist.traced.enable 1")
                platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell perfetto -o /data/misc/perfetto-traces/$traceLogName -t 10s -b 100mb -s 150mb sched freq idle am wm gfx view input")
                delay(10_000L)
                platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} pull /data/misc/perfetto-traces/$traceLogName ${PlatformAdapter.desktopTempFolder}")
                platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell rm /data/misc/perfetto-traces/$traceLogName")
            }.onFailure { e ->
                LogUtils.printLog("抓取trace出错：${e.message}", LogUtils.LogLevel.ERROR)
            }
        }
    }

    /**
     * 打开原生系统设置
     */
    fun openAndroidSettings() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell am start -a android.settings.SETTINGS")
        }
    }

    /**
     * 重启设备
     */
    fun rebootDevice() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} reboot")
        }
    }

    /**
     * 关机
     */
    fun powerOff() {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} reboot -p")
        }
    }

    /**
     * 安装apk
     */
    fun installApp(filePath: String, installParams: String = "") {
        CoroutineScope(Dispatchers.IO).launch {
            LogUtils.printLog("${platformAdapter.localAdbPath} ${adbClient.serial} install $installParams $filePath")
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} install $installParams $filePath")
        }
    }

    /**
     * 获取当前目录，和目录下的文件列表
     */
    fun getFileList(path: String = FileManager.ROOT_DIR) {
        CoroutineScope(Dispatchers.IO).launch {
            fileManager.prepareDirPath(path)
            runCatching {
                val deviceCode =
                    adbClient.getExecuteResult(adbClient.choosedDevicePosition, "getprop ro.product.device")
                val subdirectories =
                    adbClient.getAdbClient(adbClient.choosedDevicePosition)
                        ?.list(fileManager.getDirPath().joinToString("/"))
                        ?: listOf()
                _fileState.update {
                    it.copy(
                        deviceCode = deviceCode,
                        currentdirectory = fileManager.getDirPath().joinToString("/"),
                        subdirectories = subdirectories
                    )
                }
                _fileState.value = _fileState.value.toUiState()
            }.onFailure { e ->
                LogUtils.printLog("获取文件列表失败:${e.message}", LogUtils.LogLevel.ERROR)
            }
        }
    }

    /**
     * 获取安装的app列表
     */
    @OptIn(ExperimentalResourceApi::class)
    fun getPackageList(filterParams: String = PackageFilter.SIMPLE.param) {
        LogUtils.printLog("getPackageList $filterParams")
        CoroutineScope(Dispatchers.IO).launch {
            appinfoHelper.pullAppInfoToComputer()
            // 把label和packageName的数据读取到map中，挂起方法，读取完毕再进行下一步
            val packageLabelMap = appinfoHelper.analyzeAppLabel()
            val installedApps = adbClient.getExecuteResult(
                adbClient.choosedDevicePosition,
                if (filterParams == PackageFilter.SIMPLE.param) "pm list package -3"
                else "pm list package"
            )
            val tempList = mutableListOf<AppItemData>()
            installedApps.split("package:")
                .filter { it.isNotEmpty() }
                .sortedBy { (packageLabelMap[it] ?: "default") }.forEach {
                    runCatching {
                        // suspend方法，只有读取完成后才会往下走，否则会阻塞
                        val iconFile = appinfoHelper.getIconFile(it)
                        val imageBitmap: ImageBitmap = iconFile.inputStream().readAllBytes().decodeToImageBitmap()
                        // 读取label
                        val label = packageLabelMap[it] ?: "default"
                        // 读取版本
                        val version =
                            adbClient.getExecuteResult(
                                adbClient.choosedDevicePosition,
                                "dumpsys package $it | grep versionName"
                            ).split("=").last()
                        // 上次1更新时间
                        val lastUpdateTime =
                            adbClient.getExecuteResult(
                                adbClient.choosedDevicePosition,
                                "dumpsys package $it | grep lastUpdateTime"
                            ).split("=").last()
                        tempList.add(
                            AppItemData(
                                packageName = it,
                                appLabel = label,
                                version = version,
                                icon = imageBitmap,
                                lastUpdateTime = lastUpdateTime
                            )
                        )
                        _appListState.update {
                            it.copy(appList = tempList, tempList.size)
                        }
                        _appListState.value = _appListState.value.toUiState()
                    }.onFailure { e ->
                        LogUtils.printLog("获取app信息失败:${e.message}", LogUtils.LogLevel.ERROR)
                    }
                }
        }
    }

    fun executeAdbCommand(command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            platformAdapter.executeTerminalCommand(command)
        }
    }

    /**
     * 打开应用主界面
     */
    fun startMainActivity(packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            LogUtils.printLog("startMainActivity: $packageName")
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        }
    }

    /**
     * 卸载app
     */
    fun uninstallApp(packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            LogUtils.printLog("uninstallApp: $packageName")
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} uninstall $packageName")
        }
    }

    /**
     * push置换应用apk
     */
    fun pushApk(packageName: String, apkPath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val installedApkPath =
                adbClient.getExecuteResult(adbClient.choosedDevicePosition, "pm path $packageName")
                    .split("package:").last()
            val installedApkFolderPath = installedApkPath.split("/").dropLast(1).joinToString("/")
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} shell rm $installedApkPath")
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} push $apkPath $installedApkFolderPath")
        }
    }

    /**
     * 提取安装包
     */
    fun pullInstalledApk(packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val installedApkPath =
                adbClient.getExecuteResult(adbClient.choosedDevicePosition, "pm path $packageName")
                    .split("package:").last()
            platformAdapter.executeTerminalCommand("${platformAdapter.localAdbPath} ${adbClient.serial} pull $installedApkPath ${PlatformAdapter.desktopTempFolder}")
        }
    }

    /**
     * 循环检查adb连接状态
     */
    private var isConnected = false
    private fun recycleCheckConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(2000L)
                runCatching {
                    // 通过系统命令，检索连接设备的数量是否变化
                    val deviceCount =
                        platformAdapter.executeCommandWithResult("${platformAdapter.localAdbPath} devices").split("\n")
                            .filter {
                                // 筛掉无用行
                                it.contains("device") && !it.contains("devices")
                            }.size
                    if (deviceCount != _deviceMapState.value.deviceMap.size) {
                        LogUtils.printLog("DEVICE_COUNT_CHANGED! current device count: $deviceCount")
                        // 刷新列表之后，刷新一次当前设备信息
                        getDeviceMap()
                        MainScope().launch {
                            delay(800L)
                            getCurrentDeviceInfo()
                        }
                    }
                    // 检索当前设备连接状态
                    val result =
                        platformAdapter.executeCommandWithResult("${platformAdapter.localAdbPath} ${adbClient.serial} shell echo \"connect_test\"")
                    if (!result.contains("connect_test")) throw Exception("Current Device is offline!")
                    // 从断开到成功连接，主动刷新一次设备信息
                    if (!isConnected) {
                        getCurrentDeviceInfo()
                    }
                    isConnected = true
                    _deviceState.update {
                        it.copy(
                            isConnected = true,
                        )
                    }
                    _deviceState.value = _deviceState.value.toUiState()
                }.onFailure { error ->
                    LogUtils.printLog("${error.message}", LogUtils.LogLevel.ERROR)
                    isConnected = false
                    _deviceState.update {
                        it.copy(
                            isConnected = false,
                        )
                    }
                    _deviceState.value = _deviceState.value.toUiState()
                }
            }
        }
    }

    @Deprecated("Unused")
    private fun getMemInfo() {
        val totalMem =
            (adbClient.getExecuteResult(adbClient.choosedDevicePosition, "cat /proc/meminfo | grep MemTotal")
                .split(":").last().dropLast(3)
                .replace(" ", "").toLong() * 1024).size()
        val freeMem = (adbClient.getExecuteResult(adbClient.choosedDevicePosition, "cat /proc/meminfo | grep MemFree")
            .split(":").last().dropLast(3)
            .replace(" ", "").toLong() * 1024).size()
        LogUtils.printLog("totalMem:$totalMem freeMem:$freeMem")
    }

    /**
     * 刷新文件列表
     */
    fun updateFileList(path: String) {
        CoroutineScope(Dispatchers.IO).launch {
            delay(1000L)
            runCatching {
                val subdirectories = adbClient.getAdbClient(adbClient.choosedDevicePosition)?.list(path) ?: listOf()
                val deviceCode =
                    adbClient.getExecuteResult(adbClient.choosedDevicePosition, "getprop ro.product.device")
                _fileState.update {
                    it.copy(deviceCode = deviceCode, currentdirectory = path, subdirectories = subdirectories)
                }
                _fileState.value = _fileState.value.toUiState()
            }.onFailure { e ->
                LogUtils.printLog("刷新文件列表失败:${e.message}", LogUtils.LogLevel.ERROR)
            }
        }
    }

    fun setSelectedFilePath(path: String) {
        fileManager.setSelectedFilePath(path)
    }

    fun getSelectedPath() = fileManager.selectedFilePath

    /**
     * 删除文件或文件夹
     */
    fun deleteFileOrFolder(path: String) {
        fileManager.deleteFileOrFolder(path)
    }

    /**
     * 推送文件到Android设备
     */
    fun pushFileToAndroid(windowsPath: String, androidPath: String) {
        fileManager.pushFileToAndroid(windowsPath, androidPath)
    }

    /**
     * 拉取文件到Windows设备
     */
    fun pullFileFromAndroid(androidPath: String) {
        fileManager.pullFileFromAndroid(androidPath)
    }

    /**
     * 创建文件夹
     */
    fun createDirectory(path: String) {
        fileManager.createDirectory(path)
    }

    /**
     * 创建文件
     */
    fun createFile(content: String, path: String) {
        fileManager.createFile(content, path)
    }

    fun pasteFileOrFolder() {
        fileManager.pasteFileOrFolder()
    }

    fun setFileOperationState(operationType: FileOperationType) {
        fileManager.setFileOperationState(operationType)
    }

    fun getDebugManagetVersion() = PlatformAdapter.appVersion

    fun openFolder(path: String) {
        platformAdapter.openFolder(path)
    }

    fun getUserTempFilePath() = platformAdapter.getUserTempFilePath()

    fun getDesktopTempFolder() = PlatformAdapter.desktopTempFolder
}