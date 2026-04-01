# DVA 自动化测试框架

基于 AIGame + AI Agent 的 DVA 应用自动化测试方案。

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                     AI Agent (zizi)                         │
│                   运行在我的服务器上                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  dva_test_controller.py                             │   │
│  │  - 接收截图                                          │   │
│  │  - 分析图像 (YOLO + OCR)                             │   │
│  │  - 生成操作指令                                       │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP / WebSocket
                       │ (通过局域网)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   AIGame (手机端)                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  dva_test_agent.js                                  │   │
│  │  - 截图                                              │   │
│  │  - 发送到 AI 服务器                                  │   │
│  │  - 执行操作指令 ($hid)                                │   │
│  │  - YOLO 检测 ($yolo)                                 │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │ HID 协议
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    DVA App (待测试)                        │
│                  运行在被测手机上                            │
└─────────────────────────────────────────────────────────────┘
```

## 文件说明

| 文件 | 说明 |
|------|------|
| `dva_test_agent.js` | AIGame 脚本，运行在手机端 |
| `dva_test_controller.py` | Python 服务器，运行在我的服务器上 |
| `dva_test_controller_advanced.py` | 高级版，带 YOLO 和 OCR 支持 |

## 使用方法

### 1. 启动 AI 服务器（我这边）

```bash
cd /home/justin/.openclaw/workspace/DVA/testing

# 基础版
python3 dva_test_controller.py

# 高级版（需要安装依赖）
pip3 install ultralytics opencv-python numpy pillow
python3 dva_test_controller_advanced.py
```

服务器会监听 `http://0.0.0.0:18789`

### 2. 配置 AIGame 脚本

编辑 `dva_test_agent.js`，修改 `CONFIG.SERVER_URL` 为我的服务器地址：

```javascript
let CONFIG = {
    SERVER_URL: "http://192.168.1.81:18789/dva-test",  // 修改为我的 IP
    DEBUG: true,
    // ...
};
```

### 3. 在 AIGame 中运行脚本

1. 打开 AIGame
2. 加载 `dva_test_agent.js`
3. 运行脚本

### 4. 查看日志

服务器端会显示：
```
[10:30:45] [DVA-Controller] 收到截图: 1080x2400
[10:30:45] [DVA-Controller] 截图已保存: /tmp/dva_test/screenshots/screenshot_1234567890.png
[10:30:45] [DVA-Controller] 分析截图...
```

## 支持的操作指令

| 指令 | 说明 | 参数 |
|------|------|------|
| `click` | 点击 | `x`, `y` |
| `swipe` | 滑动 | `x1`, `y1`, `x2`, `y2`, `duration`, `swipeDuration` |
| `longPress` | 长按 | `x`, `y`, `duration` |
| `input` | 输入文本 | `text` |
| `pressBack` | 按返回键 | - |
| `pressHome` | 按主页键 | - |

## 测试场景

### 场景 1：基本导航测试
```
1. 打开 DVA
2. 点击"选择视频"
3. 选择视频文件
4. 点击"开始分析"
5. 等待分析完成
6. 查看报告
```

### 场景 2：YOLO 目标检测
```javascript
// 在 AIGame 中执行
let screenshot = $screen.capture();
let result = $yolo.detect(screenshot, {confidence: 0.5});
log("检测到:", JSON.stringify(result));
```

### 场景 3：连续操作自动化
```javascript
// 完整的测试流程
testMainFlow();
```

## API 接口

### POST /dva-test/analyze
发送截图到服务器分析

请求：
```json
{
    "screenshot": "base64编码的图片",
    "screen": {"width": 1080, "height": 2400},
    "timestamp": 1234567890,
    "instruction": "analyze"
}
```

响应：
```json
{
    "action": {
        "type": "click",
        "params": {"x": 540, "y": 1200}
    },
    "message": "点击屏幕中央",
    "continueLoop": true
}
```

### GET /dva-test/status
查看服务器状态

## 依赖安装

```bash
# 服务器端（我这边）
pip3 install ultralytics opencv-python numpy pillow

# 如果要使用 YOLO 模型（可选）
# 模型会在首次运行时自动下载
```

## 注意事项

1. **网络互通**：手机和我的服务器需要在同一局域网内
2. **防火墙**：确保 18789 端口开放
3. **HID 连接**：AIGame 的 `$hid` 需要连接键鼠硬件
4. **截图质量**：可根据网络情况调整 `SCREENSHOT_QUALITY`

## 故障排除

### 服务器无法连接
```bash
# 检查端口是否监听
netstat -tlnp | grep 18789

# 检查防火墙
sudo ufw allow 18789
```

### AIGame 发送失败
1. 检查 `CONFIG.SERVER_URL` 是否正确
2. 确保手机和服务器网络互通
3. 查看 AIGame 日志中的错误信息

### YOLO 检测失败
```bash
# 检查模型文件
ls -la /tmp/dva_test/yolov8n-vehicle.onnx

# 重新下载模型（如果损坏）
rm /tmp/dva_test/yolov8n-vehicle.onnx
python3 dva_test_controller_advanced.py
```

## 下一步

- [ ] 添加 WebSocket 支持实现实时通信
- [ ] 集成更多 OCR 服务（百度 OCR、腾讯 OCR）
- [ ] 添加视频帧提取和对比功能
- [ ] 实现完整的测试报告生成
