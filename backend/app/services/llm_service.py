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
    DigestSchema,
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
        Извлекает структурированные действия (привычки, задачи, заметки) или дневниковую выжимку из текста речи.
        Возвращает (SemanticExtractionResult, duration_ms, model_name).
        """
        start_time = time.time()
        
        # Формируем контекст для промпта
        habits_json = json.dumps([h.model_dump() for h in context.active_habits], ensure_ascii=False)
        tasks_json = json.dumps([t.model_dump() for t in context.open_tasks], ensure_ascii=False)

        system_instruction = f"""
Ты — персональный AI-ассистент продуктивности, организации жизни и трекинга привычек Duro.
Твоя задача — проанализировать голосовую заметку пользователя, классифицировать намерение и извлечь структуру.

Контекст пользователя:
- Текущее локальное время: {context.client_current_time}
- Таймзона: {context.timezone}
- Активные привычки пользователя (id, title, target_value): {habits_json}
- Открытые незавершенные задачи (id, title, due_date): {tasks_json}

ШАГ 0. ОПРЕДЕЛЕНИЕ РЕЖИМА (поле mode):
- "LOG": пользователь отчитывается о сделанном («выпил 2л воды», «сделал тренировку») или дает прямое поручение («напомни отправить отчет завтра в 14:00»).
- "JOURNAL": пользователь наговаривает монолог, рассуждает, делится переживаниями или идеями без команд «напомни/сделай». Запись в дневник мыслей. Задачи НЕ создаем! Обязательно заполняем поле digest.
- "MIXED": длинное рассуждение, внутри которого есть конкретное поручение («...и надо бы заехать в банк»). Заполняем и digest, и tasks_to_add.
- "QUERY": вопрос к приложению («что у меня сегодня»).

СТРУКТУРА ДНЕВНИКА (поле digest для JOURNAL и MIXED):
- title: ёмкая и точная тема мысли (например: 'Размышления о смене фокуса в работе')
- gist: 1–2 главных предложения с сутью мысли (Core Insight)
- key_points: ключевые структурированные тезисы рассуждения
- tone: эмоциональный тон / настроение ('Спокойное', 'Вдохновленное', 'Тревожное', 'Аналитическое')
- decisions: принятые решения или выводы
- next_steps: шаги-кандидаты в действия
- people: упомянутые люди
- numbers: упомянутые цифры с единицами

ПРАВИЛА ИЗВЛЕЧЕНИЯ СУЩНОСТЕЙ (для LOG и MIXED):
1. habits_completed:
   - Если пользователь говорит, что сделал привычку, сопоставь с id из списка активных привычек.
   - Если привычки нет в списке, заполни habit_title начальной формой и оставь habit_id=null.
   - Если названы цифры (например: 'выпил 600 мл', 'пробежал 5 км'), запиши в increment_value число.
2. tasks_to_add:
   - Название должно быть в инфинитиве (например: 'Купить продукты', 'Отправить презентацию').
   - Вычисли дедлайн в ISO-8601 относительно {context.client_current_time}.
   - Приоритет: HIGH, MEDIUM, LOW.
3. tasks_to_complete:
   - Сопоставь выполненную задачу с id из списка открытых задач.
4. quick_notes:
   - Краткие идеи или мысли, если не подходят под большой дневник.
5. summary:
   - 1-2 кратких предложения с главным смыслом сказанного.
"""

        # 1. Если задан Gemini API Key — вызываем gemini со схемой
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

        lower = raw_transcript.lower()
        is_journal_query = any(k in lower for k in ["дневник", "размышл", "чувствую", "мысли", "инсайт", "рефлекс"]) or (
            len(raw_transcript.split()) >= 12 and not any(k in lower for k in ["напомни", "купи", "сделай", "задача"])
        )

        if is_journal_query:
            mock_result = SemanticExtractionResult(
                mode="JOURNAL",
                mode_confidence=0.95,
                mode_reason="Поток мыслей и рефлексия без прямых команд поручения",
                summary="Размышления о фокусе в работе и приоритетах на текущий период.",
                digest=DigestSchema(
                    title="Размышления о смене фокуса в работе",
                    gist="Пора перестроить рабочий ритм и сконцентрироваться на глубокой работе без постоянных переключений.",
                    key_points=[
                        "Перегруженность мелкими задачами снижает общую отдачу от проектов",
                        "Нужно выделить 2-3 часа утром исключительно под сложную архитектуру",
                        "Командные созвоны стоит сгруппировать во второй половине дня"
                    ],
                    tone="Аналитическое",
                    decisions=[
                        "Перенести все командные синки на время после обеда",
                        "Установить режим 'Не беспокоить' до 12:00"
                    ],
                    open_questions=[
                        "Как экологично предупредить коллег о новых утренних слотах тишины?"
                    ],
                    next_steps=[
                        "Обновить календарные слоты для фокус-часов",
                        "Согласовать новое расписание созвонов с лидом"
                    ],
                    people=["Лид", "Команда"],
                    numbers=["2-3 часа", "12:00"]
                ),
                habits_completed=[],
                tasks_to_add=[],
                tasks_to_complete=[],
                quick_notes=[],
                insights=[
                    "Классифицировано как дневниковая запись",
                    "Выделены ключевые тезисы и принятые решения",
                    "Список задач не затронут"
                ]
            )
        else:
            mock_result = SemanticExtractionResult(
                mode="LOG",
                mode_confidence=0.92,
                mode_reason="Распознано прямое выполнение привычек и постановка задач",
                summary="Выполнена силовая тренировка и норма воды. Запланированы встреча по архитектуре, сдача отчета и бытовые дела.",
                digest=None,
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
                ],
                insights=[
                    "Распознаны 2 выполненные привычки",
                    "Выделено 3 задачи с вычисленными сроками"
                ]
            )
        return mock_result, duration_ms, "mock:gemini-simulation"
