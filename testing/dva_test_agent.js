/**
 * DVA 测试 Agent - AIGame 脚本
 * 
 * 功能：
 * 1. 截图并发送到 AI 服务器分析
 * 2. 执行 AI 返回的指令（点击、滑动、输入等）
 * 3. 支持 YOLO 目标检测
 * 4. 支持 OCR 文字识别
 * 
 * 使用方式：
 * 1. 在 AIGame 中加载此脚本
 * 2. 配置 SERVER_URL 为你的 AI 服务器地址
 * 3. 运行脚本
 */

// ============== 配置 ==============
let CONFIG = {
    // AI 服务器地址（修改为你的服务器 IP）
    SERVER_URL: "http://192.168.1.81:18790/dva-test",
    
    // 是否显示调试信息
    DEBUG: true,
    
    // 截图质量 (1-100)
    SCREENSHOT_QUALITY: 80,
    
    // 操作完成后等待时间(ms)
    WAIT_AFTER_ACTION: 1000,
};

// ============== 日志 ==============
function log(...args) {
    if (CONFIG.DEBUG) {
        console.log("[DVA-Agent]", ...args);
    }
}

// ============== 截图 ==============
/**
 * 截取当前屏幕
 */
function takeScreenshot() {
    let screenshot = $screen.capture();
    if (!screenshot) {
        log("截图失败");
        return null;
    }
    log("截图成功:", screenshot.width, "x", screenshot.height);
    return screenshot;
}

/**
 * 保存截图到指定路径
 */
function saveScreenshot(screenshot, path) {
    if (!screenshot) return false;
    try {
        $file.write(screenshot, path);
        log("截图已保存:", path);
        return true;
    } catch (e) {
        log("保存截图失败:", e);
        return false;
    }
}

// ============== 网络通信 ==============
/**
 * 发送截图到服务器并获取指令
 */
function sendScreenshotForAnalysis(screenshot, screenInfo) {
    if (!screenshot) {
        log("没有截图可发送");
        return null;
    }
    
    try {
        // 将截图转为 base64
        let base64 = $img.toBase64(screenshot, CONFIG.SCREENSHOT_QUALITY);
        
        // 构造请求数据
        let data = {
            screenshot: base64,
            screen: screenInfo,
            timestamp: Date.now(),
            availableActions: ["click", "swipe", "longPress", "input", "pressBack", "pressHome", "yoloDetect", "ocr"]
        };
        
        // 发送请求
        let response = $http.post(CONFIG.SERVER_URL + "/analyze", {
            json: data,
            timeout: 30000
        });
        
        if (response && response.code === 200) {
            let result = response.body;
            log("服务器响应:", JSON.stringify(result));
            return result;
        } else {
            log("服务器响应错误:", response);
            return null;
        }
    } catch (e) {
        log("发送截图失败:", e);
        return null;
    }
}

/**
 * 执行操作
 */
function executeAction(action) {
    if (!action) {
        log("没有可执行的操作");
        return false;
    }
    
    log("执行操作:", action.type, action.params);
    
    let success = false;
    
    switch (action.type) {
        case "click":
            success = $hid.点击(action.params.x, action.params.y);
            break;
            
        case "swipe":
            success = $hid.增强滑动(
                action.params.x1, action.params.y1,
                action.params.x2, action.params.y2,
                action.params.duration || 300,
                action.params.swipeDuration || 1000
            );
            break;
            
        case "longPress":
            success = $hid.长按(action.params.x, action.params.y, action.params.duration || 1500);
            break;
            
        case "input":
            success = $hid.输入(action.params.text);
            break;
            
        case "pressBack":
            success = $hid.返回();
            break;
            
        case "pressHome":
            success = $hid.主页();
            break;
            
        case "pressRecent":
            success = $hid.最近();
            break;
            
        default:
            log("未知操作类型:", action.type);
            return false;
    }
    
    if (success) {
        log("操作执行成功");
    } else {
        log("操作执行失败");
    }
    
    // 等待操作完成
    $thread.sleep(CONFIG.WAIT_AFTER_ACTION);
    
    return success;
}

// ============== YOLO 检测 ==============
/**
 * 使用 YOLO 检测图像中的目标
 */
function detectWithYOLO(image, options) {
    try {
        let result = $yolo.detect(image, {
            confidence: options.confidence || 0.5,
            classes: options.classes || null  // null 表示检测所有类
        });
        
        log("YOLO 检测结果:", JSON.stringify(result));
        return result;
    } catch (e) {
        log("YOLO 检测失败:", e);
        return null;
    }
}

// ============== OCR ==============
/**
 * 使用 OCR 识别图片中的文字
 */
function recognizeText(image) {
    try {
        let result = $ocr.recognize(image);
        log("OCR 结果:", result);
        return result;
    } catch (e) {
        log("OCR 失败:", e);
        return null;
    }
}

// ============== 主循环 ==============
/**
 * 测试主流程
 */
function testMainFlow() {
    log("========== DVA 测试 Agent 启动 ==========");
    
    // 获取屏幕信息
    let screenInfo = $screen.info();
    log("屏幕信息:", JSON.stringify(screenInfo));
    
    // 初始化 HID
    let hidResult = $hid.初始化();
    log("HID 初始化:", hidResult);
    
    if (hidResult !== "true") {
        log("HID 初始化失败，请检查键鼠连接");
        toast("HID 初始化失败");
        return;
    }
    
    // 测试截图
    log("测试截图...");
    let screenshot = takeScreenshot();
    if (!screenshot) {
        log("截图测试失败");
        return;
    }
    
    // 保存测试截图
    let testPath = "/sdcard/dva_test_screenshot.png";
    saveScreenshot(screenshot, testPath);
    
    // 发送截图到服务器
    log("发送截图到服务器分析...");
    let command = sendScreenshotForAnalysis(screenshot, screenInfo);
    
    if (command) {
        log("收到服务器指令:", JSON.stringify(command));
        
        // 执行指令
        if (command.action) {
            executeAction(command.action);
        }
        
        // 如果有后续指令
        if (command.continueLoop) {
            log("继续执行主循环...");
            $thread.run(() => {
                testMainFlow();
            });
        }
    } else {
        log("未收到服务器指令");
    }
    
    log("========== DVA 测试 Agent 结束 ==========");
}

/**
 * 简单的点击测试
 */
function testClick(x, y) {
    log("测试点击:", x, y);
    
    if (!$hid.是开启的()) {
        let result = $hid.初始化();
        if (result !== "true") {
            log("HID 初始化失败:", result);
            return false;
        }
    }
    
    return $hid.点击(x, y);
}

/**
 * 简单的滑动测试
 */
function testSwipe(x1, y1, x2, y2) {
    log("测试滑动:", x1, y1, "->", x2, y2);
    
    if (!$hid.是开启的()) {
        let result = $hid.初始化();
        if (result !== "true") {
            log("HID 初始化失败:", result);
            return false;
        }
    }
    
    return $hid.增强滑动(x1, y1, x2, y2);
}

// ============== 启动 ==============
// 运行主测试流程
testMainFlow();

// 导出函数供外部调用
module.exports = {
    testClick,
    testSwipe,
    takeScreenshot,
    executeAction,
    detectWithYOLO,
    recognizeText,
    CONFIG
};
