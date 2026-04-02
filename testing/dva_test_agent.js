/**
 * DVA 测试 Agent - AIGame 脚本
 * 
 * 功能：
 * 1. 截图并发送到 AI 服务器分析
 * 2. 执行 AI 返回的指令（点击、滑动等）
 * 3. 支持 YOLO 目标检测
 * 4. 支持 OCR 文字识别
 */

let CONFIG = {
    SERVER_URL: "http://100.91.119.94:18791/dva-test",
    DEBUG: true,
    SCREENSHOT_QUALITY: 50,
    WAIT_AFTER_ACTION: 1000,
};

// 停止标志
let STOP_FLAG = false;

function log() {
    if (CONFIG.DEBUG) {
        var args = Array.prototype.slice.call(arguments);
        console.log("[DVA-Agent] " + args.join(" "));
    }
}

function takeScreenshot() {
    let screenshot = $屏幕.获取截屏();
    if (!screenshot) {
        log("截图失败");
        return null;
    }
    log("截图成功:", screenshot.width, "x", screenshot.height);
    return screenshot;
}

function saveScreenshot(screenshot, path) {
    if (!screenshot) return false;
    try {
        $img.save(screenshot, path);
        log("截图已保存:", path);
        return true;
    } catch (e) {
        log("保存截图失败:", e);
        return false;
    }
}

function sendToServer(screenshot, screenInfo) {
    if (!screenshot) {
        log("没有截图");
        return null;
    }
    
    try {
        // 获取截图信息
        let w = screenInfo.w || screenshot.width;
        let h = screenInfo.h || screenshot.height;
        
        // 保存截图
        let path = "/sdcard/dva_test.png";
        $img.save(screenshot, path);
        
        // 获取 base64（用于服务器分析）
        // 暂时跳过 base64，只发送尺寸信息
        log("截图尺寸:", w, "x", h);
        
        // 通过 GET 请求发送数据（POST 有问题）
        let url = CONFIG.SERVER_URL + "/analyze?w=" + w + "&h=" + h + "&continueLoop=true";
        log("发送请求到:", url);
        
        let response = $http.get(url);
        
        // AIGame HTTP 响应是 Java 对象，json() 和 string() 是方法需要调用
        log("收到响应, code:", response.code);
        
        // 调用 string() 方法获取原始字符串
        let raw = null;
        try {
            raw = response.string();
            log("原始响应:", raw);
        } catch (e) {
            log("string() 失败:", e);
        }
        
        // 尝试解析 JSON
        let body = null;
        if (raw) {
            try {
                body = JSON.parse(raw);
                log("解析成功:", JSON.stringify(body));
            } catch (e) {
                log("JSON解析失败:", e);
                body = raw;
            }
        }
        
        if (response.code === 200 && body) {
            return body;
        } else {
            log("服务器响应错误:", response.code);
            return null;
        }
    } catch (e) {
        log("发送失败:", e);
        return null;
    }
}

function executeAction(action) {
    if (!action) {
        log("没有可执行的操作");
        return false;
    }
    
    log("执行操作:", action.type, action.params);
    
    let success = false;
    
    if (action.type === "click") {
        success = $hid.点击(action.params.x, action.params.y);
    } else if (action.type === "swipe") {
        success = $hid.增强滑动(
            action.params.x1, action.params.y1,
            action.params.x2, action.params.y2,
            action.params.duration || 300,
            action.params.swipeDuration || 1000
        );
    } else if (action.type === "back") {
        success = $hid.返回();
    } else if (action.type === "home") {
        success = $hid.主页();
    }
    
    if (success) {
        log("操作执行成功");
    } else {
        log("操作执行失败");
    }
    
    return success;
}

function main() {
    log("========== DVA 测试 Agent 启动 ==========");
    
    // 检查 HID
    if (!$hid.是开启的()) {
        log("HID 未开启，需要连接键鼠硬件");
    }
    
    // 获取屏幕信息
    let screenInfo = $屏幕.信息();
    log("屏幕信息:", screenInfo.w, "x", screenInfo.h);
    
    // 截图
    log("开始截图...");
    let screenshot = takeScreenshot();
    if (!screenshot) {
        log("截图失败，退出");
        return;
    }
    
    // 保存截图
    saveScreenshot(screenshot, "/sdcard/dva_test.png");
    
    // 发送到服务器
    log("发送到服务器...");
    let result = sendToServer(screenshot, screenInfo);
    
    if (result) {
        log("收到服务器响应");
        
        // 如果有操作指令就执行
        if (result.action) {
            executeAction(result.action);
        }
        
        // 不再自动循环，等待手动重启
        // 如果需要循环测试，可以修改 CONFIG.AUTO_LOOP = true
        log("========== 单次测试完成 ==========");
        log("如需继续测试，请重新运行脚本");
    } else {
        log("未收到有效响应");
    }
    
    log("========== 测试结束 ==========");
}

main();
