from typing import List, Optional
from pydantic import BaseModel, Field


# ----------------------------------------------------
# 1. Incoming Context from Client
# ----------------------------------------------------
class HabitContextItem(BaseModel):
    id: str = Field(..., description="ID привычки в локальной базе Room")
    title: str = Field(..., description="Название привычки (например: 'Пить 2л воды', 'Спортзал')")
    target_value: Optional[float] = Field(None, description="Целевое значение за день, если есть")
    unit: Optional[str] = Field(None, description="Единица измерения ('мл', 'страниц', 'раз')")


class TaskContextItem(BaseModel):
    id: str = Field(..., description="ID открытой задачи в базе")
    title: str = Field(..., description="Название задачи")
    due_date: Optional[str] = Field(None, description="Дедлайн в ISO 8601, если задан")


class ClientContext(BaseModel):
    client_current_time: str = Field(
        ...,
        description="Текущее локальное время на клиенте в ISO-8601 (например: 2026-09-25T13:45:00+05:00)"
    )
    timezone: str = Field(
        default="UTC",
        description="Название таймзоны клиента, например: Europe/Moscow или Asia/Yekaterinburg"
    )
    active_habits: List[HabitContextItem] = Field(
        default_factory=list,
        description="Список активных привычек пользователя для контекстного сопоставления"
    )
    open_tasks: List[TaskContextItem] = Field(
        default_factory=list,
        description="Список открытых незавершенных задач пользователя"
    )


# ----------------------------------------------------
# 2. Extracted Entities (Structured Outputs for LLM)
# ----------------------------------------------------
class HabitCompletion(BaseModel):
    habit_id: Optional[str] = Field(
        None,
        description="ID из active_habits, если пользователь явно упомянул существующую привычку. Если привычка новая — оставить null"
    )
    habit_title: str = Field(
        ...,
        description="Название привычки в начальной форме (например: 'Выпить норму воды', 'Силовая тренировка')"
    )
    increment_value: Optional[float] = Field(
        None,
        description="Числовое значение, если пользователь указал точное число (например: 500 для 500 мл воды, 20 для 20 страниц)"
    )
    comment: Optional[str] = Field(
        None,
        description="Дополнительный контекст или детали, упомянутые пользователем (например: 'тренировка спины и пресса')"
    )


class TaskCreate(BaseModel):
    title: str = Field(
        ...,
        description="Четкая формулировка задачи в инфинитиве (например: 'Отправить отчет научному руководителю')"
    )
    due_date: Optional[str] = Field(
        None,
        description="Вычисленный точный дедлайн в формате ISO-8601 (YYYY-MM-DDTHH:MM:SS) на основе client_current_time"
    )
    priority: str = Field(
        "MEDIUM",
        description="Приоритет: LOW (низкий), MEDIUM (обычный), HIGH (срочно/важно)"
    )
    category: str = Field(
        "General",
        description="Категория: Работа, Учеба, Дом, Здоровье, Финансы, Покупки"
    )
    subtasks: List[str] = Field(
        default_factory=list,
        description="Список подзадач, если пользователь разбил дело на шаги"
    )


class TaskComplete(BaseModel):
    task_id: Optional[str] = Field(
        None,
        description="ID существующей задачи из open_tasks, которую пользователь сообщил как выполненную"
    )
    task_title: str = Field(
        ...,
        description="Название закрытой задачи"
    )


class QuickNote(BaseModel):
    text: str = Field(
        ...,
        description="Мысль, идея, рефлексия или инсайт из речи, не являющийся прямой задачей или привычкой"
    )
    tags: List[str] = Field(
        default_factory=list,
        description="1-3 тематических тега (например: ['стартап', 'идея', 'игры'])"
    )


# ----------------------------------------------------
# 3. Overall Structured Output Schema for Gemini
# ----------------------------------------------------
class SemanticExtractionResult(BaseModel):
    summary: str = Field(
        ...,
        description="Лаконичное резюме аудиозаписи в 1-2 предложениях на русском языке"
    )
    habits_completed: List[HabitCompletion] = Field(
        default_factory=list,
        description="Список привычек, которые пользователь выполнил или отметил"
    )
    tasks_to_add: List[TaskCreate] = Field(
        default_factory=list,
        description="Список новых задач к созданию"
    )
    tasks_to_complete: List[TaskComplete] = Field(
        default_factory=list,
        description="Список существующих задач, которые были завершены"
    )
    quick_notes: List[QuickNote] = Field(
        default_factory=list,
        description="Свободные мысли, идеи и заметки"
    )


# ----------------------------------------------------
# 4. Final Response Payload to Mobile App
# ----------------------------------------------------
class VoiceProcessResponse(BaseModel):
    success: bool = True
    raw_transcript: str = Field(..., description="Полный распознанный текст речи от Whisper")
    duration_seconds: float = Field(0.0, description="Длительность обработки")
    stt_duration_ms: int = Field(0, description="Время транскрипции в мс")
    llm_duration_ms: int = Field(0, description="Время семантического разбора в мс")
    model_used: str = Field(..., description="Использованные модели (например: groq:whisper-turbo + gemini-3.5-flash-lite)")
    summary: str = Field(..., description="Краткое резюме")
    habits_completed: List[HabitCompletion] = Field(default_factory=list)
    tasks_to_add: List[TaskCreate] = Field(default_factory=list)
    tasks_to_complete: List[TaskComplete] = Field(default_factory=list)
    quick_notes: List[QuickNote] = Field(default_factory=list)
