/**
 * DVA 自动化测试脚本 - AIGame
 * 
 * 功能：
 * 1. 截图分析当前界面
 * 2. 识别 UI 元素位置
 * 3. 自动执行点击/滑动操作
 * 4. 发送截图到 AI 服务器分析
 * 5. 循环直到测试完成
 */

let CONFIG = {
    SERVER_URL: "http://100.91.119.94:18791/dva-test",
    DEBUG: true,
    AUTO_MODE: true,  // 自动模式开关
    LOOP_INTERVAL: 3000,  // 循环间隔(ms)
};

function log() {
    if (CONFIG.DEBUG) {
        var args = Array.prototype.slice.call(arguments);
        console.log("[DVA-Test] " + args.join(" "));
    }
}

// ============== 截图 ==============
function takeScreenshot() {
    let img = $屏幕.获取截屏();
    if (!img) {
        log("截图失败");
        return null;
    }
    log("截图成功:", img.width, "x", img.height);
    return img;
}

function saveScreenshot(img, path) {
    if (!img) return false;
    try {
        $img.save(img, path);
        return true;
    } catch (e) {
        log("保存失败:", e);
        return false;
    }
}

// ============== UI 分析 ==============
/**
 * 分析当前界面，返回可执行的操作
 */
function analyzeUI(img, screenInfo) {
    // 截图保存用于分析
    let path = "/sdcard/dva_ui.png";
    $img.save(img, path);
    
    // 发送到服务器分析
    let result = sendToServer({
        type: "ui_analysis",
        screen: screenInfo,
        img_path: path
    });
    
    return result;
}

// ============== 服务器通信 ==============
function sendToServer(data) {
    try {
        let url = CONFIG.SERVER_URL + "/dva_test?action=" + (data.action || "analyze");
        let response = $http.get(url);
        
        if (response && response.code === 200) {
            return response.json();
        }
    } catch (e) {
        log("发送失败:", e);
    }
    return null;
}

// ============== HID 操作 ==============
function click(x, y) {
    log("点击:", x, y);
    return $hid.点击(x, y);
}

function swipe(x1, y1, x2, y2) {
    log("滑动:", x1, y1, "->", x2, y2);
    return $hid.增强滑动(x1, y1, x2, y2);
}

function pressBack() {
    log("按返回键");
    return $hid.返回();
}

function pressHome() {
    log("按主页键");
    return $hid.主页();
}

// ============== 常用坐标 ==============
/**
 * DVA 常用坐标（基于 1440x3168 屏幕）
 */
let DVA_COORDS = {
    // 首页
    HOME_SELECT_VIDEO: {x: 720, y: 1200},  // 选择视频按钮
    HOME_MODEL_BTN: {x: 720, y: 1800},  // 模型管理按钮
    
    // 分析页
    ANALYSIS_START: {x: 720, y: 2800},  // 开始分析按钮
    
    // 系统
    BACK: {x: 100, y: 150},  // 返回按钮
};

// ============== 测试场景 ==============
/**
 * 测试场景 1: 打开 DVA，选择视频
 */
function testSelectVideo() {
    log("=== 测试: 选择视频 ===");
    
    let img = takeScreenshot();
    if (!img) return false;
    
    // 点击选择视频
    let coord = DVA_COORDS.HOME_SELECT_VIDEO;
    click(coord.x, coord.y);
    
    // 等待系统 picker 打开
    $thread.线程睡眠(2000);
    
    // 再截一张看看
    img = takeScreenshot();
    log("选择视频后截图已保存");
    
    return true;
}

/**
 * 测试场景 2: 等待分析完成
 */
function testWaitAnalysis() {
    log("=== 测试: 等待分析 ===");
    
    // 等待一段时间
    $thread.线程睡眠(5000);
    
    let img = takeScreenshot();
    if (!img) return false;
    
    // 保存截图
    $img.save(img, "/sdcard/dva_analysis.png");
    log("分析过程截图已保存");
    
    return true;
}

/**
 * 测试场景 3: 检查分析结果
 */
function testCheckResult() {
    log("=== 测试: 检查结果 ===");
    
    let img = takeScreenshot();
    if (!img) return false;
    
    $img.save(img, "/sdcard/dva_result.png");
    log("结果截图已保存");
    
    // 发送到服务器
    let result = sendToServer({
        type: "check_result",
        screen: {w: img.width, h: img.height}
    });
    
    if (result && result.action) {
        executeAction(result.action);
    }
    
    return true;
}

// ============== 主测试流程 ==============
function runTestScenario(scenario) {
    log("执行测试场景:", scenario);
    
    switch(scenario) {
        case "open_dva":
            // 确保在 DVA 首页
            pressHome();
            $thread.线程睡眠(1000);
            // 启动 DVA（需要知道包名）
            // $app.launchApp("com.dva.app");
            return true;
            
        case "select_video":
            return testSelectVideo();
            
        case "wait_analysis":
            return testWaitAnalysis();
            
        case "check_result":
            return testCheckResult();
            
        case "back":
            pressBack();
            return true;
            
        default:
            log("未知场景:", scenario);
            return false;
    }
}

// ============== 自动化测试循环 ==============
function autoTestLoop() {
    if (!CONFIG.AUTO_MODE) {
        log("自动模式已关闭");
        return;
    }
    
    log("========== 自动测试循环启动 ==========");
    
    // 场景列表，按顺序执行
    let scenarios = [
        "select_video",   // 1. 选择视频
        // "wait_analysis", // 2. 等待分析（需要视频）
        // "check_result",   // 3. 检查结果
    ];
    
    let currentIndex = 0;
    
    function runNext() {
        if (currentIndex >= scenarios.length) {
            log("========== 所有测试场景完成 ==========");
            log("可在 /sdcard/ 查看截图");
            return;
        }
        
        let scenario = scenarios[currentIndex];
        log("场景", (currentIndex + 1), "/", scenarios.length, ":", scenario);
        
        let success = runTestScenario(scenario);
        if (success) {
            currentIndex++;
            // 等待后执行下一个
            setTimeout(runNext, CONFIG.LOOP_INTERVAL);
        } else {
            log("场景执行失败，停止");
        }
    }
    
    runNext();
}

// ============== 启动 ==============
function main() {
    log("========== DVA 自动化测试启动 ==========");
    log("屏幕:", $屏幕.信息().w, "x", $屏幕.信息().h);
    
    // 先截一张看看当前界面
    let img = takeScreenshot();
    if (img) {
        $img.save(img, "/sdcard/dva_start.png");
        log("初始截图已保存到 /sdcard/dva_start.png");
    }
    
    // 开启自动测试
    autoTestLoop();
}

main();
