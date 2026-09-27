import json
import os
import time
from pathlib import Path
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, FileResponse
from fastapi.staticfiles import StaticFiles

from app.config import get_settings
from app.schemas import (
    ClientContext,
    HabitContextItem,
    TaskContextItem,
    VoiceProcessResponse
)
from app.services.stt_service import STTService
from app.services.llm_service import LLMService

settings = get_settings()

app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
    description="FastAPI service for voice habits and task tracking using Groq Whisper and Gemini 3.5 Flash-Lite"
)

# Enable CORS for local Android development and web playground
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

stt_service = STTService(settings)
llm_service = LLMService(settings)

STATIC_DIR = Path(__file__).parent / "static"


@app.get("/", include_in_schema=False)
@app.get("/playground", response_class=HTMLResponse)
async def playground():
    """Интерактивный веб-тестбенч для проверки микрофона и разбора речи."""
    index_file = STATIC_DIR / "index.html"
    if index_file.exists():
        return FileResponse(index_file)
    return HTMLResponse("<h1>Voice Habit Tracker Testbench</h1><p>Static index.html not found</p>")


@app.get(f"{settings.api_v1_prefix}/health")
async def health_check():
    """Проверка доступности сервера и конфигурации API-ключей."""
    return {
        "status": "healthy",
        "timestamp": time.time(),
        "groq_configured": settings.is_groq_ready,
        "gemini_configured": settings.is_gemini_ready,
        "whisper_model": settings.groq_whisper_model,
        "llm_model": settings.gemini_model,
        "mode": "production" if (settings.is_groq_ready and settings.is_gemini_ready) else "simulation_fallback"
    }


@app.post(f"{settings.api_v1_prefix}/voice/process", response_model=VoiceProcessResponse)
async def process_voice(
    audio: UploadFile = File(..., description="Аудиофайл (.m4a, .mp3, .wav, .opus)"),
    client_current_time: str = Form(..., description="ISO 8601 текущее время на клиенте"),
    timezone: str = Form("UTC", description="Таймзона клиента"),
    active_habits_context: str = Form("[]", description="JSON-список активных привычек"),
    open_tasks_context: str = Form("[]", description="JSON-список открытых задач")
):
    """
    Основной пайплайн:
    1. Принимает сжатое аудио речи.
    2. Выполняет быструю транскрипцию через Whisper Large v3 Turbo (Groq).
    3. Выполняет семантическое извлечение действий через Gemini 3.5 Flash-Lite (Google GenAI).
    4. Возвращает строго типизированный ответ для отображения в ReviewSheet и сохранения в Room DB.
    """
    start_total = time.time()
    
    # 1. Читаем байты аудио
    try:
        audio_bytes = await audio.read()
        if not audio_bytes:
            raise HTTPException(status_code=400, detail="Аудиофайл пуст")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Ошибка чтения аудио: {str(e)}")

    # 2. Валидация контекста пользователя
    try:
        habits_raw = json.loads(active_habits_context)
        tasks_raw = json.loads(open_tasks_context)
        context = ClientContext(
            client_current_time=client_current_time,
            timezone=timezone,
            active_habits=[HabitContextItem(**h) for h in habits_raw],
            open_tasks=[TaskContextItem(**t) for t in tasks_raw]
        )
    except Exception as e:
        # Если клиент передал некорректный JSON контекста, создаем пустой безопасный контекст
        context = ClientContext(
            client_current_time=client_current_time,
            timezone=timezone,
            active_habits=[],
            open_tasks=[]
        )

    # 3. Шаг 1: STT
    raw_transcript, stt_ms, stt_model = await stt_service.transcribe(
        audio_bytes=audio_bytes,
        filename=audio.filename or "recording.m4a"
    )

    # 4. Шаг 2: LLM
    extraction, llm_ms, llm_model = await llm_service.extract_actions(
        raw_transcript=raw_transcript,
        context=context
    )

    total_seconds = time.time() - start_total

    return VoiceProcessResponse(
        success=True,
        raw_transcript=raw_transcript,
        duration_seconds=total_seconds,
        stt_duration_ms=stt_ms,
        llm_duration_ms=llm_ms,
        model_used=f"{stt_model} + {llm_model}",
        summary=extraction.summary,
        habits_completed=extraction.habits_completed,
        tasks_to_add=extraction.tasks_to_add,
        tasks_to_complete=extraction.tasks_to_complete,
        quick_notes=extraction.quick_notes
    )
