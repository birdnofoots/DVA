# DVA 模型说明

本目录用于存放 ONNX 模型文件。

## 重要更新

**从 v2.0 开始，模型不再打包进 APK！**

模型将在首次运行时按需下载到应用目录。

## 模型列表

| 文件名 | 用途 | 大小 | 说明 |
|--------|------|------|------|
| `yolov8n-vehicle.onnx` | 车辆检测 | ~6MB | YOLOv8n |
| `lanenet.onnx` | 车道线检测 | ~15MB | LaneNet |
| `lprnet_chinese.onnx` | 车牌识别 | ~10MB | LPRNet |

**总大小：约 31MB**

## 工作流程

1. **首次启动** → 检测到模型缺失 → 提示下载
2. **下载模型** → 保存到 `应用目录/files/models/`
3. **后续启动** → 检测到模型已存在 → 直接使用

## 手动下载（可选）

如果网络受限，可以手动下载模型：

```bash
# 下载模型到应用目录
adb push yolov8n-vehicle.onnx /sdcard/Android/data/com.dva.app/files/models/
adb push lanenet.onnx /sdcard/Android/data/com.dva.app/files/models/
adb push lprnet_chinese.onnx /sdcard/Android/data/com.dva.app/files/models/
```

## 模型下载链接

### YOLOv8n 车辆检测
```bash
curl -L -o yolov8n-vehicle.onnx \\
  https://github.com/ultralytics/ultralytics/releases/download/v8.2.0/yolov8n.onnx
```

### LaneNet 车道线检测
```bash
curl -L -o lanenet.onnx \\
  https://github.com/harryhanYu/LaneNet_Deep_Learning_Studio/releases/download/v1.0/lanenet.onnx
```

### LPRNet 车牌识别
```bash
curl -L -o lprnet_chinese.onnx \\
  https://raw.githubusercontent.com/myyrRO/le-cheng-shu/main/lprnet.onnx
```

## 国内镜像

如果 GitHub 下载慢，使用镜像：
```bash
# ghproxy
curl -L -o model.onnx \\
  https://ghproxy.com/https://github.com/xxx/xxx/releases/download/v1.0/model.onnx
```

## 验证模型

下载后验证：
```bash
ls -lh *.onnx
# 检查文件大小应 > 1MB
```

---
更新时间: 2024年
