"""
DVA Supervision Backend - FastAPI 主服务
针对无GPU VPS优化版本

优化策略:
1. YOLOv8n (nano) 模型 - 最小的模型
2. 跳帧处理 - 每隔N帧分析一次
3. 图像缩放 - 降低分辨率加速
4. FP16半精度 - 减少内存和计算量
"""

import os
import uuid
import asyncio
from pathlib import Path
from typing import Optional, List
from datetime import datetime

from fastapi import FastAPI, File, UploadFile, HTTPException, BackgroundTasks
from fastapi.responses import JSONResponse
from pydantic import BaseModel
import cv2
import numpy as np

# 配置
UPLOAD_DIR = Path("./uploads")
OUTPUT_DIR = Path("./output")
UPLOAD_DIR.mkdir(exist_ok=True)
OUTPUT_DIR.mkdir(exist_ok=True)

app = FastAPI(
    title="DVA Supervision Backend (Optimized)",
    description="行车记录仪违章分析后端服务 - 无GPU优化版",
    version="1.1.0"
)

# 全局检测器实例
detector = None
detector_config = {
    "frame_skip": 5,      # 每隔5帧处理一次
    "resize_scale": 0.5,  # 图像缩小到50%
    "confidence": 0.5,
}

# 任务存储
tasks: dict = {}


class TaskStatus(BaseModel):
    task_id: str
    status: str
    progress: float
    result: Optional[List[dict]] = None
    error: Optional[str] = None


def init_detector():
    """初始化检测器（延迟加载）"""
    global detector
    if detector is None:
        from detector import create_optimized_detector
        detector = create_optimized_detector(
            frame_skip=detector_config["frame_skip"],
            resize_scale=detector_config["resize_scale"],
            confidence=detector_config["confidence"]
        )


@app.get("/health")
async def health_check():
    """健康检查"""
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "detector_ready": detector is not None,
        "optimization": detector_config
    }


@app.post("/analyze")
async def analyze_video(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    frame_skip: int = 5,
    resize_scale: float = 0.5
):
    """
    上传视频并分析违章
    
    - **file**: 视频文件 (mp4, avi, mov 等)
    - **frame_skip**: 跳帧数（1=逐帧分析, 5=每5帧分析1次）
    - **resize_scale**: 图像缩放比例 (0.1-1.0)
    """
    if not file.filename.lower().endswith(('.mp4', '.avi', '.mov', '.mkv')):
        raise HTTPException(status_code=400, detail="Unsupported video format")
    
    if not (0.1 <= resize_scale <= 1.0):
        raise HTTPException(status_code=400, detail="resize_scale must be between 0.1 and 1.0")
    
    task_id = str(uuid.uuid4())[:8]
    video_path = UPLOAD_DIR / f"{task_id}_{file.filename}"
    
    with open(video_path, "wb") as f:
        content = await file.read()
        f.write(content)
    
    tasks[task_id] = {
        "status": "pending",
        "progress": 0.0,
        "result": None,
        "error": None,
        "video_path": str(video_path)
    }
    
    background_tasks.add_task(
        process_video_task, 
        task_id, 
        video_path, 
        frame_skip,
        resize_scale
    )
    
    return {
        "task_id": task_id,
        "status": "pending",
        "message": f"Video uploaded, processing started (frame_skip={frame_skip}, resize={resize_scale})"
    }


async def process_video_task(task_id: str, video_path: Path, frame_skip: int, resize_scale: float):
    """后台视频处理任务"""
    tasks[task_id]["status"] = "processing"
    
    try:
        init_detector()
        
        cap = cv2.VideoCapture(str(video_path))
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        fps = cap.get(cv2.CAP_PROP_FPS)
        
        violations = []
        frame_number = 0
        processed_count = 0
        
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break
            
            # 跳帧处理
            if frame_skip > 1 and frame_number % frame_skip != 0:
                frame_number += 1
                continue
            
            # 缩放图像
            if resize_scale < 1.0:
                h, w = frame.shape[:2]
                frame = cv2.resize(frame, (int(w * resize_scale), int(h * resize_scale)))
            
            # YOLOv8n 推理
            results = detector.model(frame, verbose=False)[0]
            
            # 转换检测结果
            import supervision as sv
            detections = sv.Detections.from_ultralytics(results)
            
            # 更新轨迹
            if len(detections) > 0:
                detector.update_history(detections, frame_number)
                
                # 检测变道
                lane_changes = detector.detect_violations(frame, frame_number)
                violations.extend(lane_changes)
            
            frame_number += 1
            processed_count += 1
            
            # 更新进度
            if processed_count % 30 == 0:
                tasks[task_id]["progress"] = processed_count / total_frames
        
        cap.release()
        video_path.unlink(missing_ok=True)
        
        tasks[task_id]["status"] = "completed"
        tasks[task_id]["progress"] = 1.0
        tasks[task_id]["result"] = violations
        
    except Exception as e:
        tasks[task_id]["status"] = "failed"
        tasks[task_id]["error"] = str(e)


@app.get("/result/{task_id}")
async def get_result(task_id: str):
    """获取分析结果"""
    if task_id not in tasks:
        raise HTTPException(status_code=404, detail="Task not found")
    
    task = tasks[task_id]
    return TaskStatus(
        task_id=task_id,
        status=task["status"],
        progress=task["progress"],
        result=task["result"],
        error=task["error"]
    )


@app.get("/config")
async def get_config():
    """获取当前配置"""
    return detector_config


@app.post("/config")
async def update_config(frame_skip: int = 5, resize_scale: float = 0.5, confidence: float = 0.5):
    """更新检测器配置"""
    global detector_config, detector
    
    if not (1 <= frame_skip <= 30):
        raise HTTPException(status_code=400, detail="frame_skip must be between 1 and 30")
    if not (0.1 <= resize_scale <= 1.0):
        raise HTTPException(status_code=400, detail="resize_scale must be between 0.1 and 1.0")
    if not (0.1 <= confidence <= 1.0):
        raise HTTPException(status_code=400, detail="confidence must be between 0.1 and 1.0")
    
    detector_config = {
        "frame_skip": frame_skip,
        "resize_scale": resize_scale,
        "confidence": confidence,
    }
    
    # 重置检测器以应用新配置
    detector = None
    
    return {"message": "Config updated", "config": detector_config}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
