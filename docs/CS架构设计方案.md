# DVA CS 架构设计方案

> 分支：cs-branch  
> 创建时间：2026-03-29  
> 目标：手机端 AIGame/JavaScript + VPS 服务端 YOLO 推理

---

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        手机端 (AIGame)                        │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐  │
│  │  录屏   │──>│  抽帧   │──>│ 压缩编码 │──>│ HTTP上传 │  │
│  │  模块   │   │ (2fps)  │   │ (JPEG)  │   │  模块    │  │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘  │
└────────────────────────┬──────────────────────────────────┘
                         │ HTTP POST /api/frame
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                     VPS 服务端 (Python)                      │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐  │
│  │  接收    │──>│ YOLOv8  │──>│  车牌    │──>│ 结果     │  │
│  │  帧数据  │   │ 车道推理 │   │  OCR    │   │ 存储     │  │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘  │
│                      │                                    │
│                      │ WebSocket push                     │
│                      ▼                                    │
│  ┌──────────┐   ┌──────────┐                              │
│  │ 实时推送 │<──│ 违章    │                              │
│  │ 结果     │   │ 检测    │                              │
│  └──────────┘   └──────────┘                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、手机端模块 (AIGame/JavaScript)

### 2.1 核心流程

```
1. 启动录屏采集（屏幕录制）
2. 每500ms抽一帧 → JPEG压缩
3. 上传到 VPS（REST API）
4. 接收 VPS 返回结果（WebSocket）
5. 本地显示 / 通知用户
```

### 2.2 流量优化策略

| 策略 | 说明 | 省流量比例 |
|------|------|-----------|
| 抽帧降频 | 30fps → 2fps | ~85% |
| JPEG 压缩 | 质量 60-70% | ~50-70% |
| ROI 裁剪 | 只发车道区域 | ~60% |
| 端侧预过滤 | 明显无车/无违章时不上报 | ~40% |

### 2.3 API 接口设计

```
POST /api/frame
  Body: { frame: base64(jpeg), timestamp: int, region: [x,y,w,h] }
  Response: { detected: bool, type: "lane" | "plate" | "both", confidence: float }

POST /api/violation/confirm
  Body: { frame: base64, violation_data: object }
  Response: { saved: bool, id: string }

GET /api/history?page=1&limit=20
  Response: { items: [...], total: int }

WebSocket /ws/realtime
  Server → Client: { type: "result", data: {...} }
```

---

## 三、服务端模块 (Python/FastAPI)

### 3.1 技术栈

| 组件 | 选择 | 原因 |
|------|------|------|
| Web框架 | FastAPI | 高性能、自动化文档 |
| 推理引擎 | ONNX Runtime | CPU高效、跨平台 |
| YOLO | YOLOv8 | 最新、稳定 |
| OCR | PaddleOCR (轻量) | 支持多语言车牌 |
| 数据库 | SQLite | 轻量、免部署 |
| WebSocket | FastAPI WebSocket | 集成在一起 |

### 3.2 模型清单

| 模型 | 用途 | 模型文件 | 状态 |
|------|------|---------|------|
| YOLOv8n | 车辆检测 | yolov8n.onnx | 待下载 |
| 车道检测 | 车道线检测 | 需替换 | ⚠️ 原链接失效 |
| 车牌识别 | OCR 识别 | 需替换 | ⚠️ 原链接失效 |

### 3.3 推理流程

```
收到帧 → 预处理(Resize) → YOLO车辆检测 
  → 提取车辆ROI → 车道分析 
  → 如有违章 → 车牌OCR → 存储结果 → WebSocket推送
```

---

## 四、部署方案

### 4.1 VPS 要求

- 系统：Ubuntu 22.04
- 内存：4GB+ (推荐 8GB)
- 磁盘：20GB+
- 网络：固定 IP / 域名

### 4.2 Docker 部署

```dockerfile
FROM python:3.11-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY models/ ./models/
COPY app/ ./app/

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### 4.3 域名与 HTTPS

- 使用 Nginx + Let's Encrypt
- API 域名：`dva-api.yourdomain.com`
- WebSocket：`wss://dva-api.yourdomain.com/ws`

---

## 五、待解决问题

| 问题 | 优先级 | 负责人 | 状态 |
|------|--------|--------|------|
| 车道检测模型替代 | 高 | 没脚鸟 | 🔍 搜索中 |
| 车牌识别模型替代 | 高 | 没脚鸟 | 🔍 搜索中 |
| VPS 购买/配置 | 中 | Justin | ⏳ 待确认 |
| AIGame 工程创建 | 中 | Justin | ⏳ 待开始 |
| 服务端 API 开发 | 低 | 没脚鸟 | 🔜 待开发 |

---

## 六、模型替代方案

### 6.1 车道检测模型

| 模型 | 来源 | 下载命令/地址 |
|------|------|-------------|
| YOLOv8n-lane | HuggingFace | `yolov8n.pt` 微调 |
| LaneNet | GitHub | 需要单独训练 |
| ResNet + HNet | 学术模型 | 需转换 ONNX |

**推荐方案：** 使用 YOLOv8 + 车道线数据集微调

```bash
# 安装 ultralytics
pip install ultralytics

# 下载预训练模型
yolo detect train model=yolov8n.pt  # 基础模型需微调
```

### 6.2 车牌识别模型

| 模型 | 来源 | 特点 |
|------|------|------|
| **EasyOCR** | PyPI | 开源、支持多语言、包括中文 |
| **PaddleOCR 轻量版** | PaddlePaddle | 中文识别强 |
| **Tesseract + LSTM** | 系统包 | 传统方案、需调优 |

**推荐方案：EasyOCR（简单易用）**

```bash
pip install easyocr

# 使用示例
import easyocr
reader = easyocr.Reader(['ch_sim', 'en'])
result = reader.readtext('plate.jpg')
```

### 6.3 模型下载脚本（待实现）

```python
# models/download_models.py
import urllib.request
import os

MODELS = {
    "yolov8n": "https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8n.pt",
    "easyocr": "pip install easyocr",  # 通过 pip 安装
}

def download_all():
    os.makedirs("models", exist_ok=True)
    # 下载 YOLOv8
    urllib.request.urlretrieve(MODELS["yolov8n"], "models/yolov8n.pt")
    print("All models downloaded!")
```

---

## 七、分支管理

```bash
# 查看分支
git branch -a

# 切换到 main（端侧）
git checkout main

# 切换到 cs-branch（云端）
git checkout cs-branch

# 提交 cs-branch 规划文档
git add docs/CS架构设计方案.md
git commit -m "docs: add CS architecture design for cloud inference"
git push -u origin cs-branch
```

---

*文档版本：v1.0*  
*最后更新：2026-03-29*
