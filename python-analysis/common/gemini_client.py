"""
Gemini API HTTP 클라이언트.
Java GeminiClient.java 패턴을 Python으로 포팅.
"""
import time
import requests

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import GEMINI_API_KEY

GEMINI_API_URL = (
    "https://generativelanguage.googleapis.com/v1beta/"
    "models/gemini-2.5-flash:generateContent"
)


class GeminiClient:
    def __init__(self, api_key: str = None):
        self.api_key = api_key or GEMINI_API_KEY

    def call(self, prompt: str, max_retries: int = 2) -> str | None:
        """
        Gemini API 호출. 실패 시 None 반환.
        429(할당량 초과) 시 15초 대기 후 재시도.
        """
        url = f"{GEMINI_API_URL}?key={self.api_key}"
        payload = {
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": 0.7,
                "maxOutputTokens": 16384,
                "thinkingConfig": {"thinkingBudget": 2048},
            },
        }

        for attempt in range(max_retries + 1):
            try:
                resp = requests.post(url, json=payload, timeout=60)

                if resp.status_code == 429:
                    if attempt < max_retries:
                        print(f"    [Gemini] 할당량 초과, 15초 대기 후 재시도 "
                              f"({attempt + 1}/{max_retries})")
                        time.sleep(15)
                        continue
                    print("    [Gemini] 할당량 초과 — 재시도 한도 도달")
                    return None

                if resp.status_code != 200:
                    print(f"    [Gemini] HTTP {resp.status_code}: "
                          f"{resp.text[:200]}")
                    return None

                data = resp.json()
                candidates = data.get("candidates", [])
                if not candidates:
                    print("    [Gemini] 응답에 candidates 없음")
                    return None

                # 잘림 감지
                finish_reason = candidates[0].get("finishReason", "")
                if finish_reason == "MAX_TOKENS":
                    print("    [Gemini] 출력 토큰 한도 도달 — 응답 잘림")

                parts = candidates[0].get("content", {}).get("parts", [])
                if not parts:
                    print("    [Gemini] 응답에 parts 없음")
                    return None

                text = parts[0].get("text", "")

                # 잘린 응답이면 None 반환 (fallback 유도)
                if finish_reason == "MAX_TOKENS" and len(text) < 800:
                    print("    [Gemini] 잘린 응답이 너무 짧음 — 폐기")
                    return None

                return text

            except requests.exceptions.Timeout:
                print(f"    [Gemini] 타임아웃 ({attempt + 1}/{max_retries + 1})")
                if attempt < max_retries:
                    time.sleep(5)
                    continue
                return None
            except Exception as e:
                print(f"    [Gemini] 오류: {e}")
                return None

        return None


# 모듈 레벨 싱글턴
gemini_client = GeminiClient()
