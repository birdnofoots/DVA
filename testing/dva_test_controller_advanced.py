#!/usr/bin/env python3
"""
DVA 测试控制器 - 高级版
运行在我（zizi）的服务器上

功能：
1. 接收来自 AIGame 的截图和分析请求  
2. 使用 YOLO 模型检测车辆、车道线等目标
3. 使用 OCR 识别车牌号
4. 生成操作指令让 AIGame 执行
5. 支持 WebSocket 实时通信（可选）

依赖：
- pip install ultralytics pillow opencv-python numpy
"""

import base64
import json
import os
import sys
import time
import traceback
import io
import numpy as np
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from datetime import datetime

# 尝试导入 YOLO（如果没有安装则跳过）
try:
    from ultralytics import YOLO
    YOLO_AVAILABLE = True
except ImportError:
    YOLO_AVAILABLE = False
    print("[WARNING] YOLO not installed. Run: pip install ultralytics")

# 配置
PORT = 18790
HOST = "0.0.0.0"
DEBUG = True

# 截图存储目录
SCREENSHOT_DIR = "/tmp/dva_test/screenshots"
RESULTS_DIR = "/tmp/dva_test/results"
os.makedirs(SCREENSHOT_DIR, exist_ok=True)
os.makedirs(RESULTS_DIR, exist_ok=True)

# YOLO 模型路径
YOLO_MODEL_PATH = "/tmp/dva_test/yolov8n-vehicle.onnx"
if not os.path.exists(YOLO_MODEL_PATH):
    # 下载模型（如果不存在）
    if YOLO_AVAILABLE:
        print("[INFO] Downloading YOLO model...")
        from ultralytics import YOLO
        model = YOLO("yolov8n.pt")  # 会自动下载
        # 保存为 ONNX
        model.export(format="onnx", imgsz=640)
        print(f"[INFO] Model exported to {YOLO_MODEL_PATH}")

# 全局统计
START_TIME = time.time()
SCREENSHOTS_RECEIVED = 0
yolo_model = None

def load_yolo_model():
    """加载 YOLO 模型"""
    global yolo_model
    if not YOLO_AVAILABLE:
        return None
    try:
        if os.path.exists(YOLO_MODEL_PATH):
            yolo_model = YOLO(YOLO_MODEL_PATH)
            print("[INFO] YOLO model loaded successfully")
            return yolo_model
        else:
            print("[WARNING] YOLO model not found at", YOLO_MODEL_PATH)
            return None
    except Exception as e:
        print("[ERROR] Failed to load YOLO model:", e)
        return None

def log(*args, **kwargs):
    """日志输出"""
    if DEBUG:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] [DVA-Controller]", *args, **kwargs)

class DVAHandler(BaseHTTPRequestHandler):
    """处理 DVA 测试请求"""
    
    def log_message(self, format, *args):
        log(format % args)
    
    def do_POST(self):
        """处理 POST 请求"""
        try:
            if self.path == "/dva-test/analyze":
                self.handle_analyze()
            elif self.path == "/dva-test/yolo":
                self.handle_yolo_detect()
            elif self.path == "/dva-test/ocr":
                self.handle_ocr()
            else:
                self.send_json_response({"error": "Unknown endpoint"}, 404)
        except Exception as e:
            log("处理请求失败:", e)
            traceback.print_exc()
            self.send_json_response({"error": str(e)}, 500)
    
    def do_GET(self):
        """处理 GET 请求"""
        if self.path == "/dva-test/status":
            self.send_json_response({
                "status": "running",
                "uptime": time.time() - START_TIME,
                "screenshots_received": SCREENSHOTS_RECEIVED,
                "yolo_available": YOLO_AVAILABLE,
                "yolo_loaded": yolo_model is not None
            })
        elif self.path == "/dva-test/results":
            # 返回检测结果列表
            results = []
            for f in sorted(os.listdir(RESULTS_DIR))[-10:]:
                results.append(f)
            self.send_json_response({"results": results})
        else:
            self.send_json_response({"error": "Not found"}, 404)
    
    def handle_analyze(self):
        """处理截图分析请求"""
        global SCREENSHOTS_RECEIVED
        
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length)
        
        try:
            data = json.loads(body)
        except json.JSONDecodeError:
            self.send_json_response({"error": "Invalid JSON"}, 400)
            return
        
        screenshot_b64 = data.get("screenshot")
        screen_info = data.get("screen", {})
        timestamp = data.get("timestamp", 0)
        
        if not screenshot_b64:
            self.send_json_response({"error": "No screenshot provided"}, 400)
            return
        
        log(f"收到截图: {screen_info.get('width', 0)}x{screen_info.get('height', 0)}")
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
        
        # 分析截图
        command = analyze_screenshot(screenshot_path, screen_info, data)
        
        self.send_json_response(command)
    
    def handle_yolo_detect(self):
        """处理 YOLO 检测请求"""
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length)
        
        try:
            data = json.loads(body)
        except json.JSONDecodeError:
            self.send_json_response({"error": "Invalid JSON"}, 400)
            return
        
        screenshot_b64 = data.get("screenshot")
        confidence = data.get("confidence", 0.5)
        
        if not screenshot_b64:
            self.send_json_response({"error": "No screenshot provided"}, 400)
            return
        
        # 解码图片
        try:
            img_bytes = base64.b64decode(screenshot_b64)
            img = np.frombuffer(img_bytes, dtype=np.uint8)
            img = cv2.imdecode(img, cv2.IMREAD_COLOR)
        except Exception as e:
            log("图片解码失败:", e)
            self.send_json_response({"error": str(e)}, 500)
            return
        
        # YOLO 检测
        if yolo_model is None:
            self.send_json_response({"error": "YOLO model not loaded"}, 500)
            return
        
        try:
            results = yolo_model(img, conf=confidence)
            detections = []
            for r in results:
                boxes = r.boxes
                for box in boxes:
                    detections.append({
                        "class": r.names[int(box.cls[0])],
                        "confidence": float(box.conf[0]),
                        "bbox": box.xyxy[0].tolist()
                    })
            
            # 保存结果图片
            timestamp = data.get("timestamp", 0)
            result_img_path = os.path.join(RESULTS_DIR, f"result_{timestamp}.jpg")
            cv2.imwrite(result_img_path, results[0].plot())
            
            self.send_json_response({
                "detections": detections,
                "image_path": result_img_path,
                "count": len(detections)
            })
        except Exception as e:
            log("YOLO 检测失败:", e)
            self.send_json_response({"error": str(e)}, 500)
    
    def handle_ocr(self):
        """处理 OCR 请求"""
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length)
        
        try:
            data = json.loads(body)
        except json.JSONDecodeError:
            self.send_json_response({"error": "Invalid JSON"}, 400)
            return
        
        screenshot_b64 = data.get("screenshot")
        
        if not screenshot_b64:
            self.send_json_response({"error": "No screenshot provided"}, 400)
            return
        
        # 这里需要 OCR 库，暂时返回模拟数据
        self.send_json_response({
            "text": "OCR not implemented yet",
            "regions": []
        })
    
    def send_json_response(self, data, status=200):
        """发送 JSON 响应"""
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())


def analyze_screenshot(screenshot_path, screen_info, request_data):
    """
    分析截图并生成操作指令
    """
    width = screen_info.get("width", 1080)
    height = screen_info.get("height", 2400)
    
    log("分析截图...")
    
    instruction = request_data.get("instruction", "analyze")
    
    # 根据不同指令返回不同的操作
    if instruction == "test_click":
        return {
            "action": {
                "type": "click",
                "params": {"x": width // 2, "y": height // 2}
            },
            "message": "点击屏幕中央",
            "continueLoop": True
        }
    
    elif instruction == "test_swipe_up":
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
    
    elif instruction == "test_swipe_down":
        return {
            "action": {
                "type": "swipe", 
                "params": {
                    "x1": width // 2, "y1": height // 4,
                    "x2": width // 2, "y2": height * 3 // 4
                }
            },
            "message": "向下滑动",
            "continueLoop": True
        }
    
    elif instruction == "test_back":
        return {
            "action": {"type": "pressBack", "params": {}},
            "message": "按返回键",
            "continueLoop": True
        }
    
    elif instruction == "test_home":
        return {
            "action": {"type": "pressHome", "params": {}},
            "message": "按主页键",
            "continueLoop": True
        }
    
    elif instruction == "open_dva":
        # 打开 DVA 应用
        return {
            "action": {"type": "launchApp", "params": {"package": "com.dva.app"}},
            "message": "打开 DVA 应用",
            "continueLoop": True
        }
    
    elif instruction == "start_analysis":
        # 开始分析（点击分析按钮）
        # 假设分析按钮在屏幕下方 1/3 处中央
        return {
            "action": {
                "type": "click",
                "params": {"x": width // 2, "y": height * 2 // 3}
            },
            "message": "点击开始分析",
            "continueLoop": True
        }
    
    elif instruction == "select_video":
        # 选择视频（假设在屏幕上半部分）
        return {
            "action": {
                "type": "click",
                "params": {"x": width // 2, "y": height // 3}
            },
            "message": "点击选择视频",
            "continueLoop": True
        }
    
    else:
        # 默认：返回分析状态
        return {
            "action": None,
            "message": "等待进一步指令",
            "continueLoop": True,
            "availableCommands": [
                "test_click", "test_swipe_up", "test_swipe_down",
                "test_back", "test_home", 
                "open_dva", "select_video", "start_analysis"
            ]
        }


def main():
    """启动服务器"""
    # 预加载 YOLO 模型
    if YOLO_AVAILABLE:
        load_yolo_model()
    
    server = HTTPServer((HOST, PORT), DVAHandler)
    log("========== DVA 测试控制器启动 ==========")
    log(f"监听地址: http://{HOST}:{PORT}")
    log(f"YOLO 可用: {YOLO_AVAILABLE}")
    log(f"YOLO 已加载: {yolo_model is not None}")
    log("等待 AIGame 连接...")
    log("==========================================")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        log("服务器停止")
        server.shutdown()


if __name__ == "__main__":
    main()
