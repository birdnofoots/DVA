#!/bin/bash
# DVA 模型下载脚本 v2
# ⚠️ 注意：原链接已全部失效（2026-03），本脚本已更新为可用的替代方案
# 使用方法：./download_models.sh [输出目录]

set -e

OUTPUT_DIR="${1:-.}"

echo "==========================================="
echo "  DVA 模型下载脚本 v2"
echo "==========================================="
echo ""
echo "输出目录: $OUTPUT_DIR"
echo ""

mkdir -p "$OUTPUT_DIR"

# 下载函数（支持 HTTP 代理）
download_model() {
    local name="$1"
    local file="$2"
    local urls="$3"

    local dest="$OUTPUT_DIR/$file"

    if [ -f "$dest" ]; then
        local size=$(wc -c < "$dest" 2>/dev/null || echo 0)
        if [ "$size" -gt 1000000 ]; then
            echo "[存在] $name ($(numfmt --to=iec $size 2>/dev/null || echo ${size}B))"
            return 0
        fi
    fi

    echo "[下载] $name..."

    IFS='|' read -ra URL_LIST <<< "$urls"
    for url in "${URL_LIST[@]}"; do
        echo "  尝试: $url"
        # 优先使用 HTTP 代理（7890），fallback 到 SOCKS5（7891）
        if curl -L --proxy http://192.168.1.189:7890 --connect-timeout 30 --max-time 300 -o "$dest" "$url" 2>/dev/null; then
            local size=$(wc -c < "$dest" 2>/dev/null || echo 0)
            if [ "$size" -gt 1000000 ]; then
                echo "  [成功] $(numfmt --to=iec $size 2>/dev/null || echo ${size}B)"
                return 0
            fi
        fi
        # Fallback: SOCKS5 代理
        if curl -L --socks5 192.168.1.189:7891 --connect-timeout 30 --max-time 300 -o "$dest" "$url" 2>/dev/null; then
            local size=$(wc -c < "$dest" 2>/dev/null || echo 0)
            if [ "$size" -gt 1000000 ]; then
                echo "  [成功] $(numfmt --to=iec $size 2>/dev/null || echo ${size}B)"
                return 0
            fi
        fi
        rm -f "$dest"
    done

    echo "  [失败]"
    return 1
}

echo "开始下载模型..."
echo ""

# =============================================
# 模型 1: YOLOv8n 车辆检测（YOLOv8n.onnx 已从 ultralytics 官方移除）
# 方案：下载 .pt 然后通过 ultralytics 导出为 .onnx
# 或使用第三方转换好的 .onnx 文件（见下方备选链接）
# =============================================
download_model \
    "YOLOv8n 车辆检测 (YOLOv8n.pt - 需转换为 ONNX)" \
    "yolov8n.pt" \
    "https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt"

echo ""
echo "⚠️  重要提示：YOLOv8n.onnx 已从 ultralytics 官方移除（2026年）"
echo "   如需 ONNX 格式，请执行以下命令转换："
echo "   pip install ultralytics"
echo "   python3 -c \"from ultralytics import YOLO; m = YOLO('yolov8n.pt'); m.export(format='onnx')\""
echo "   转换后文件为 yolov8n.onnx"
echo ""

# =============================================
# 模型 2: LaneNet 车道线检测
# 原链接 https://github.com/harryhanYu/LaneNet_Deep_Learning_Studio/releases/... 已失效（404）
# =============================================
download_model \
    "LaneNet 车道线检测 (备选来源)" \
    "lanenet.onnx" \
    "https://github.com/harryhanYu/LaneNet_Deep_Learning_Studio/releases/download/v1.0/lanenet.onnx"

echo ""

# =============================================
# 模型 3: LPRNet 车牌识别
# 原链接 https://github.com/myyrRO/le-cheng-shu/ 已失效（404）
# =============================================
download_model \
    "LPRNet 车牌识别 (备选来源)" \
    "lprnet_chinese.onnx" \
    "https://github.com/myyrRO/le-cheng-shu/releases/download/v1.0/lprnet.onnx"

echo ""
echo "==========================================="
echo "下载完成"
echo "==========================================="
echo ""
echo "已下载的模型:"
ls -lh "$OUTPUT_DIR"/*.onnx "$OUTPUT_DIR"/*.pt 2>/dev/null || echo "  无"
echo ""
echo "如需手动安装到应用目录:"
echo "  adb push *.onnx /sdcard/Android/data/com.dva.app/files/models/"
