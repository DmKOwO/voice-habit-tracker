import json
import time
from datetime import datetime, timedelta
from typing import Tuple
from google import genai
from google.genai import types

from app.config import Settings
from app.schemas import (
    ClientContext,
    SemanticExtractionResult,
    HabitCompletion,
    TaskCreate,
    TaskComplete,
    QuickNote,
)


class LLMService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.gemini_client = genai.Client(api_key=settings.gemini_api_key) if settings.is_gemini_ready else None

    async def extract_actions(
        self,
        raw_transcript: str,
        context: ClientContext
    ) -> Tuple[SemanticExtractionResult, int, str]:
        """
        Извлекает структурированные действия (привычки, задачи, заметки) из текста речи.
        Возвращает (SemanticExtractionResult, duration_ms, model_name).
        """
        start_time = time.time()
        
        # Формируем контекст для промпта
        habits_json = json.dumps([h.model_dump() for h in context.active_habits], ensure_ascii=False)
        tasks_json = json.dumps([t.model_dump() for t in context.open_tasks], ensure_ascii=False)

        system_instruction = f"""
Ты — персональный AI-ассистент продуктивности и трекинга привычек.
Твоя задача — проанализировать неструктурированный поток мыслей пользователя из голосовой заметки и извлечь действия.

Контекст пользователя:
- Текущее локальное время: {context.client_current_time}
- Таймзона: {context.timezone}
- Активные привычки пользователя (id, title, target_value): {habits_json}
- Открытые незавершенные задачи (id, title, due_date): {tasks_json}

Правила извлечения:
1. habits_completed:
   - Если пользователь говорит, что сделал привычку, сопоставь с id из списка активных привычек.
   - Если привычки нет в списке, заполни habit_title начальной формой (например: 'Контрастный душ') и оставь habit_id=null.
   - Если названы цифры (например: 'выпил 600 мл', 'пробежал 5 км'), запиши в increment_value число.
2. tasks_to_add:
   - Выдели разовые дела. Название должно быть в инфинитиве (например: 'Купить продукты', 'Отправить презентацию').
   - Если упомянуты даты ('сегодня вечером', 'завтра в 14:00', 'в пятницу', 'через 3 дня'), вычисли ТОЧНЫЙ дедлайн в формате ISO-8601 (YYYY-MM-DDTHH:MM:SS) относительно текущего времени {context.client_current_time}.
   - Определи приоритет: HIGH (если 'срочно', 'обязательно', 'важно'), LOW (если 'когда-нибудь', 'мелочь'), иначе MEDIUM.
3. tasks_to_complete:
   - Если пользователь сказал, что выполнил ранее запланированную задачу, сопоставь её с id из списка открытых задач.
4. quick_notes:
   - Мысли, идеи, наблюдения или рефлексии, которые не являются задачами к выполнению. Снабди краткими тегами.
5. summary:
   - 1-2 кратких предложения с главным смыслом сказанного.
"""

        # 1. Если задан Gemini API Key — вызываем gemini-3.5-flash-lite со схемой
        if self.gemini_client:
            try:
                response = self.gemini_client.models.generate_content(
                    model=self.settings.gemini_model,
                    contents=f"Транскрибированная речь пользователя:\n\"\"\"{raw_transcript}\"\"\"",
                    config=types.GenerateContentConfig(
                        system_instruction=system_instruction,
                        response_mime_type="application/json",
                        response_schema=SemanticExtractionResult,
                        temperature=0.1
                    )
                )
                duration_ms = int((time.time() - start_time) * 1000)
                result = SemanticExtractionResult.model_validate_json(response.text)
                return result, duration_ms, f"google:{self.settings.gemini_model}"
            except Exception as e:
                print(f"[LLM Error in Gemini] {e}. Falling back to smart mock.")

        # 2. Мок-режим с умным расчетом дат относительно реального клиентского времени
        duration_ms = int((time.time() - start_time) * 1000) + 150
        
        # Парсим время клиента для расчета реалистичных дат в моке
        try:
            now = datetime.fromisoformat(context.client_current_time.replace("Z", "+00:00"))
        except Exception:
            now = datetime.now()

        tomorrow_14 = (now + timedelta(days=1)).replace(hour=14, minute=0, second=0).strftime("%Y-%m-%dT%H:%M:%S")
        friday = (now + timedelta(days=(4 - now.weekday()) % 7 or 7)).replace(hour=18, minute=0, second=0).strftime("%Y-%m-%dT%H:%M:%S")
        tonight = now.replace(hour=20, minute=0, second=0).strftime("%Y-%m-%dT%H:%M:%S")

        mock_result = SemanticExtractionResult(
            summary="Выполнена силовая тренировка и норма воды. Запланированы встреча по архитектуре, сдача отчета и бытовые дела.",
            habits_completed=[
                HabitCompletion(
                    habit_id="habit_gym" if any(h.id == "habit_gym" for h in context.active_habits) else None,
                    habit_title="Спортзал / Тренировка",
                    comment="отлично покачал спину"
                ),
                HabitCompletion(
                    habit_id="habit_water" if any(h.id == "habit_water" for h in context.active_habits) else None,
                    habit_title="Пить норму воды",
                    increment_value=2000.0,
                    comment="выпил 2 литра к обеду"
                )
            ],
            tasks_to_add=[
                TaskCreate(
                    title="Заехать к ребятам обсудить архитектуру",
                    due_date=tomorrow_14,
                    priority="HIGH",
                    category="Работа/Проект"
                ),
                TaskCreate(
                    title="Отправить отчет по практике научному руководителю",
                    due_date=friday,
                    priority="HIGH",
                    category="Учеба"
                ),
                TaskCreate(
                    title="Разморозить курицу на ужин",
                    due_date=tonight,
                    priority="MEDIUM",
                    category="Дом"
                )
            ],
            tasks_to_complete=[],
            quick_notes=[
                QuickNote(
                    text="Идея для игры: прикольная игровая механика с постепенно затухающим фонариком",
                    tags=["игры", "геймдев", "идеи"]
                )
            ]
        )
        return mock_result, duration_ms, "mock:gemini-simulation"
