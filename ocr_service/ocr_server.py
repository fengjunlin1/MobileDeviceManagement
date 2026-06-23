"""
简易 OCR 服务 - 供 MDM 后端调用
启动：python ocr_server.py
"""

import base64
import io
import logging
from fastapi import FastAPI, Request
import uvicorn

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(message)s')
log = logging.getLogger(__name__)

app = FastAPI()

# 延迟加载 PaddleOCR
ocr = None

def get_ocr():
    global ocr
    if ocr is None:
        from paddleocr import PaddleOCR
        log.info("正在加载 PaddleOCR 模型（首次加载可能需要几分钟）...")
        ocr = PaddleOCR(use_angle_cls=True, lang='ch', use_gpu=
        False)
        log.info("PaddleOCR 模型加载完成")
    return ocr

@app.get("/health")
async def health():
    return {"status": "ok"}

@app.post("/ocr")
async def ocr_recognize(request: Request):
    try:
        body = await request.json()
        image_b64 = body.get("image", "")
        if not image_b64:
            log.warning("请求体中缺少 image 字段")
            from fastapi import HTTPException
            raise HTTPException(status_code=400, detail="缺少 image 字段")

        log.info("收到 OCR 请求: imageSize=%d 字符", len(image_b64))

        engine = get_ocr()

        # base64 解码
        image_data = base64.b64decode(image_b64)
        image_bytes = io.BytesIO(image_data)
        log.info("图片解码完成: %d 字节", len(image_data))

        # 执行 OCR
        result = engine.ocr(image_bytes, cls=True)

        # 提取文字
        texts = []
        if result and len(result) > 0 and result[0]:
            for line in result[0]:
                texts.append(line[1][0])
            log.info("OCR 识别完成: %d 段文字", len(texts))
        else:
            log.info("OCR 未识别到文字")

        output = "\n".join(texts) if texts else ""
        log.info("OCR 输出: %s", output[:500])
        return output

    except Exception as e:
        log.error("OCR 识别失败: %s", str(e), exc_info=True)
        from fastapi import HTTPException
        raise HTTPException(status_code=500, detail=f"OCR 识别失败: {str(e)}")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001, timeout_keep_alive=300)
