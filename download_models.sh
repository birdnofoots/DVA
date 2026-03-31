#!/bin/bash
# DVA 模型下载脚本 v3
# ⚠️ 注意：原链接已全部失效（2026-03），本脚本已更新为可用的替代方案
# 使用方法：./download_models.sh [输出目录]
#
# 问题说明（2026-03-30）：
# - LaneNet 和 LPRNet 的原下载链接已失效（GitHub releases 404）
# - YOLOv8n 已有本地副本，无需下载
# - 替代方案正在寻找中，部分模型需要手动下载

set -e

# 代理设置（更新：Mihomo 已迁移到 192.168.1.81）
PROXY_HTTP="http://192.168.1.81:7890"
PROXY_SOCKS="socks5://192.168.1.81:7891"

OUTPUT_DIR="${1:-.}"

echo "==========================================="
echo "  DVA 模型下载脚本 v3"
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
        # 优先使用 HTTP 代理
        if curl -L --proxy "$PROXY_HTTP" --connect-timeout 30 --max-time 300 -o "$dest" "$url" 2>/dev/null; then
            local size=$(wc -c < "$dest" 2>/dev/null || echo 0)
            if [ "$size" -gt 1000000 ]; then
                echo "  [成功] $(numfmt --to=iec $size 2>/dev/null || echo ${size}B)"
                return 0
            fi
        fi
        # Fallback: SOCKS5 代理
        if curl -L --proxy "$PROXY_SOCKS" --connect-timeout 30 --max-time 300 -o "$dest" "$url" 2>/dev/null; then
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
# 模型 1: YOLOv8n 车辆检测
# 状态：✅ 已本地存储在 app/src/main/assets/models/yolov8n.onnx
# 如需重新下载，请使用 ultralytics 导出：
#   pip install ultralytics
#   python3 -c "from ultralytics import YOLO; m = YOLO('yolov8n.pt'); m.export(format='onnx')"
# =============================================
YOLOV8N_LOCAL="app/src/main/assets/models/yolov8n.onnx"
if [ -f "$YOLOV8N_LOCAL" ]; then
    echo "[存在] YOLOv8n 车辆检测 ($YOLOV8N_LOCAL)"
else
    echo "[提示] YOLOv8n.onnx 不在预期位置，请检查 app/src/main/assets/models/"
fi
echo ""

# =============================================
# 模型 2: LaneNet 车道线检测
# 状态：❌ 原链接 https://github.com/harryhanYu/LaneNet_Deep_Learning_Studio/releases 已失效（404）
#
# 替代方案（需要手动下载）：
#   方案A: Ultra-Fast-Lane-Detection-v2 ONNX 模型
#   - 来源: https://github.com/hpc203/Ultra-Fast-Lane-Detection-v2-opencv-onnxrun
#   - 模型在百度云盘: https://pan.baidu.com/s/1b68-q_NX2PQPHZQn2h-x2A (提取码: jfwn)
#   - 需要将模型转换为 app/src/main/assets/models/lanenet.onnx
#
#   方案B: 从 voldemortX/pytorch-auto-drive 训练并导出
#   - https://github.com/voldemortX/pytorch-auto-drive
# =============================================
echo "⚠️  LaneNet 模型需要手动下载"
echo "   替代方案: Ultra-Fast-Lane-Detection-v2"
echo "   百度云盘: https://pan.baidu.com/s/1b68-q_NX2PQPHZQn2h-x2A (提取码: jfwn)"
echo ""

# =============================================
# 模型 3: LPRNet 中文车牌识别
# 状态：❌ 原链接 https://github.com/myyrRO/le-cheng-shu/releases 已失效（404）
#
# 替代方案（需要手动下载）：
#   方案A: PyTorch 权重 + 转换为 ONNX
#   - 来源: https://github.com/xiaofuqing13/chinese-license-plate-recognition
#   - weights/plate_detect.pt (1.1MB) + plate_rec_color.pth (750KB)
#   - 需要运行 export.py 转换为 ONNX
#
#   方案B: 寻找其他预转换的 ONNX 模型
# =============================================
echo "⚠️  LPRNet 模型需要手动下载或从 PyTorch 转换"
echo "   替代方案: https://github.com/xiaofuqing13/chinese-license-plate-recognition"
echo "   下载 weights/plate_detect.pt 和 weights/plate_rec_color.pth"
echo "   然后运行 export.py 转换为 ONNX"
echo ""

# =============================================
# 尝试下载（目前会失败，仅作记录）
# =============================================
download_model \
    "LaneNet 车道线检测 (备选来源 - 会失败)" \
    "lanenet.onnx" \
    "https://github.com/harryhanYu/LaneNet_Deep_Learning_Studio/releases/download/v1.0/lanenet.onnx" || true

download_model \
    "LPRNet 车牌识别 (备选来源 - 会失败)" \
    "lprnet_chinese.onnx" \
    "https://github.com/myyrRO/le-cheng-shu/releases/download/v1.0/lprnet.onnx" || true

echo ""
echo "==========================================="
echo "下载完成"
echo "==========================================="
echo ""
echo "当前模型状态:"
for f in "$OUTPUT_DIR"/*.onnx "$OUTPUT_DIR"/*.pt; do
    if [ -f "$f" ]; then
        echo "  $(basename $f) - $(wc -c < "$f" | numfmt --to=iec 2>/dev/null || echo $(wc -c < "$f")B)"
    fi
done
echo ""
echo "如需手动安装模型到应用目录:"
echo "  adb push *.onnx /sdcard/Android/data/com.dva.app/files/models/"
echo ""
echo "==========================================="
echo "待解决：需要找到 LaneNet 和 LPRNet 的可用下载链接"
echo "==========================================="
