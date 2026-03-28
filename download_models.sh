#!/bin/bash
# DVA 模型下载脚本
# 下载模型到当前目录或指定目录

set -e

OUTPUT_DIR="${1:-.}"

echo "==========================================="
echo "  DVA 模型下载脚本"
echo "==========================================="
echo ""
echo "输出目录: $OUTPUT_DIR"
echo ""

mkdir -p "$OUTPUT_DIR"

# 下载函数
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
        if curl -L --connect-timeout 30 --max-time 300 -o "$dest" "$url" 2>/dev/null; then
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

# 1. YOLOv8n
download_model \
    "YOLOv8n 车辆检测" \
    "yolov8n-vehicle.onnx" \
    "https://github.com/ultralytics/ultralytics/releases/download/v8.2.0/yolov8n.onnx"

echo ""

# 2. LaneNet
download_model \
    "LaneNet 车道线检测" \
    "lanenet.onnx" \
    "https://github.com/harryhanYu/LaneNet_Deep_Learning_Studio/releases/download/v1.0/lanenet.onnx"

echo ""

# 3. LPRNet
download_model \
    "LPRNet 车牌识别" \
    "lprnet_chinese.onnx" \
    "https://raw.githubusercontent.com/myyrRO/le-cheng-shu/main/lprnet.onnx"

echo ""
echo "==========================================="
echo "下载完成"
echo "==========================================="
echo ""
echo "已下载的模型:"
ls -lh "$OUTPUT_DIR"/*.onnx 2>/dev/null || echo "  无"
echo ""
echo "如需手动安装到应用目录:"
echo "  adb push *.onnx /sdcard/Android/data/com.dva.app/files/models/"
