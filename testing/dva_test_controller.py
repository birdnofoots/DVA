#!/usr/bin/env python3
"""
DVA 测试控制器 - Python 服务器
运行在我（zizi）的服务器上

功能：
1. 接收来自 AIGame 的截图和分析请求
2. 分析截图，生成操作指令
3. 支持 YOLO 检测（如果本地有模型）
4. 支持 OCR（使用本地 OCR 服务）

通信方式：
- AIGame 通过 HTTP POST 发送截图到本服务器
- 服务器返回操作指令
"""

import base64
import json
import os
import sys
import time
import traceback
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from datetime import datetime

# 配置
PORT = 18791
HOST = "0.0.0.0"
DEBUG = True

# 截图存储目录
SCREENSHOT_DIR = "/tmp/dva_test/screenshots"
os.makedirs(SCREENSHOT_DIR, exist_ok=True)

# 日志
def log(*args, **kwargs):
    if DEBUG:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] [DVA-Controller]", *args, **kwargs)

class DVAHandler(BaseHTTPRequestHandler):
    """处理 DVA 测试请求"""
    
    def log_message(self, format, *args):
        """自定义日志格式"""
        log(format % args)
    
    def do_POST(self):
        """处理 POST 请求"""
        try:
            if self.path == "/dva-test/analyze":
                self.handle_analyze()
            else:
                self.send_json_response({"error": "Unknown endpoint"}, 404)
        except Exception as e:
            log("处理请求失败:", e)
            traceback.print_exc()
            self.send_json_response({"error": str(e)}, 500)
    
    def do_GET(self):
        """处理 GET 请求"""
        from urllib.parse import urlparse, parse_qs
        
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)
        
        if path == "/dva-test/status":
            self.send_json_response({
                "status": "running",
                "uptime": time.time() - START_TIME,
                "screenshots_received": SCREENSHOTS_RECEIVED
            })
        elif path == "/dva-test/analyze":
            # GET 方式接收截图分析（通过 URL 参数）
            img_len = query.get('img_len', [0])[0]
            w = query.get('w', [0])[0]
            h = query.get('h', [0])[0]
            
            log(f"收到 GET 分析请求: {w}x{h}, img_len={img_len}")
            
            self.send_json_response({
                "status": "ok",
                "message": "收到截图分析请求",
                "img_len": img_len,
                "w": w,
                "h": h,
                "continueLoop": True
            })
        
        elif path == "/dva-test/dva_test":
            # DVA 自动化测试接口
            action = query.get('action', ['analyze'])[0]
            
            log(f"DVA 测试请求: action={action}")
            
            # 返回测试指令
            result = {
                "status": "ok",
                "action": action,
                "message": "等待执行",
                "screen": {
                    "width": int(query.get('w', [1440])[0]),
                    "height": int(query.get('h', [3168])[0])
                }
            }
            
            # 根据不同 action 返回不同指令
            if action == "ui_analysis":
                result["message"] = "UI 分析模式，请发送截图"
                result["continueLoop"] = True
            elif action == "analyze":
                result["message"] = "开始分析"
                result["continueLoop"] = True
            elif action == "check_result":
                result["message"] = "检查结果"
                result["continueLoop"] = False
            else:
                result["message"] = f"未知 action: {action}"
                result["continueLoop"] = False
            
            self.send_json_response(result)
        else:
            self.send_json_response({"error": "Not found"}, 404)
    
    def handle_analyze(self):
        """处理截图分析请求"""
        global SCREENSHOTS_RECEIVED
        
        # 读取请求体
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length)
        
        try:
            data = json.loads(body)
        except json.JSONDecodeError:
            self.send_json_response({"error": "Invalid JSON"}, 400)
            return
        
        # 提取数据
        screenshot_b64 = data.get("screenshot")
        screen_info = data.get("screen", {})
        timestamp = data.get("timestamp", 0)
        
        if not screenshot_b64:
            self.send_json_response({"error": "No screenshot provided"}, 400)
            return
        
        log(f"收到截图分析请求: {screen_info.get('width', 0)}x{screen_info.get('height', 0)}")
        SCREENSHOTS_RECEIVED += 1
        
        # 保存截图
        screenshot_path = None
        try:
            screenshot_bytes = base64.b64decode(screenshot_b64)
            screenshot_path = os.path.join(SCREENSHOT_DIR, f"screenshot_{timestamp}.png")
            with open(screenshot_path, "wb") as f:
                f.write(screenshot_bytes)
            log(f"截图已保存: {screenshot_path}")
        except Exception as e:
            log("保存截图失败:", e)
        
        # 分析截图并生成指令
        command = analyze_screenshot(screenshot_path, screen_info, data)
        
        # 返回指令
        self.send_json_response(command)
    
    def send_json_response(self, data, status=200):
        """发送 JSON 响应"""
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())


def analyze_screenshot(screenshot_path, screen_info, request_data):
    """
    分析截图并生成操作指令
    
    这是一个简化版的分析器
    实际应用中可以：
    1. 使用本地 YOLO 模型检测目标
    2. 使用 OCR 识别文字
    3. 调用 AI 模型分析界面
    """
    width = screen_info.get("width", 1080)
    height = screen_info.get("height", 2400)
    
    log("分析截图...")
    
    # 这里可以添加实际的分析逻辑
    # 目前返回一个默认的待命指令
    
    # 示例：返回不同的测试指令
    instruction = request_data.get("instruction", "analyze")
    
    if instruction == "test_click":
        # 测试点击（屏幕中央）
        return {
            "action": {
                "type": "click",
                "params": {"x": width // 2, "y": height // 2}
            },
            "message": "点击屏幕中央",
            "continueLoop": True
        }
    
    elif instruction == "test_swipe":
        # 测试滑动（向上滑）
        return {
            "action": {
                "type": "swipe",
                "params": {
                    "x1": width // 2, "y1": height * 3 // 4,
                    "x2": width // 2, "y2": height // 4
                }
            },
            "message": "向上滑动",
            "continueLoop": True
        }
    
    elif instruction == "test_back":
        return {
            "action": {
                "type": "pressBack",
                "params": {}
            },
            "message": "按返回键",
            "continueLoop": True
        }
    
    elif instruction == "test_home":
        return {
            "action": {
                "type": "pressHome",
                "params": {}
            },
            "message": "按主页键",
            "continueLoop": True
        }
    
    else:
        # 默认：继续等待
        return {
            "action": None,
            "message": "等待进一步指令",
            "continueLoop": True
        }


# 全局统计
START_TIME = time.time()
SCREENSHOTS_RECEIVED = 0


def main():
    """启动服务器"""
    server = HTTPServer((HOST, PORT), DVAHandler)
    log(f"========== DVA 测试控制器启动 ==========")
    log(f"监听地址: http://{HOST}:{PORT}")
    log(f"等待 AIGame 连接...")
    log(f"按 Ctrl+C 停止")
    log(f"==========================================")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        log("服务器停止")
        server.shutdown()


if __name__ == "__main__":
    main()
