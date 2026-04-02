/**
 * DVA 测试 Agent - 极简版
 */

let CONFIG = {
    SERVER_URL: "http://100.91.119.94:18791/dva-test",
    DEBUG: true,
};

function log() {
    if (CONFIG.DEBUG) {
        var args = Array.prototype.slice.call(arguments);
        console.log("[DVA-Agent] " + args.join(" "));
    }
}

function main() {
    log("========== DVA 测试 Agent 启动 ==========");
    
    // 获取屏幕信息
    let screenInfo = $屏幕.信息();
    log("屏幕信息:", JSON.stringify(screenInfo));
    
    // 测试截图
    log("开始截图...");
    let screenshot = $屏幕.获取截屏();
    if (screenshot) {
        log("截图成功:", screenshot.width, "x", screenshot.height);
        
        // 保存截图
        let path = "/sdcard/dva_test.png";
        $img.save(screenshot, path);
        log("截图已保存到:", path);
        
        // 获取截图的 base64
        let base64 = $img.toBase64(screenshot, "png", 50);
        log("Base64 长度:", base64 ? base64.length : "null");
        
        // 用 GET 发送数据（因为 POST 有问题）
        if (base64 && base64.length > 100) {
            let url = CONFIG.SERVER_URL + "/analyze?img_len=" + base64.length + "&w=" + screenshot.width + "&h=" + screenshot.height;
            log("发送请求...");
            let response = $http.get(url);
            log("响应码:", response ? response.code : "null");
            if (response && response.body) {
                log("响应:", JSON.stringify(response.body).substring(0, 200));
            }
        }
    } else {
        log("截图失败");
    }
    
    log("========== 测试结束 ==========");
}

main();
