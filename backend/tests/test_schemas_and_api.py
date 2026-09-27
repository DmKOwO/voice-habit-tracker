import io
import json
import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.schemas import (
    ClientContext,
    HabitContextItem,
    TaskContextItem,
    SemanticExtractionResult,
    HabitCompletion,
    TaskCreate
)


@pytest.mark.asyncio
async def test_health_check():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/api/v1/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert "whisper_model" in data
        assert "llm_model" in data


def test_schema_serialization():
    context = ClientContext(
        client_current_time="2026-09-25T14:00:00+05:00",
        timezone="Asia/Yekaterinburg",
        active_habits=[
            HabitContextItem(id="habit_water", title="Пить воду 2л", target_value=2000, unit="мл")
        ],
        open_tasks=[
            TaskContextItem(id="task_report", title="Сдать отчет")
        ]
    )
    assert len(context.active_habits) == 1
    assert context.active_habits[0].id == "habit_water"

    extraction = SemanticExtractionResult(
        summary="Тренировка выполнена, запланирована встреча",
        habits_completed=[
            HabitCompletion(habit_id="habit_gym", habit_title="Спортзал", comment="жим лежа")
        ],
        tasks_to_add=[
            TaskCreate(title="Купить протеин", due_date="2026-09-26T12:00:00", priority="HIGH")
        ],
        tasks_to_complete=[],
        quick_notes=[]
    )
    json_str = extraction.model_dump_json()
    assert "habit_gym" in json_str
    assert "Купить протеин" in json_str


@pytest.mark.asyncio
async def test_voice_process_endpoint():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Create a mock 100-byte audio file
        dummy_audio = io.BytesIO(b"RIFFmockaudiobytescontentform4atesting")
        
        habits_json = json.dumps([
            {"id": "habit_water", "title": "Пить норму воды", "target_value": 2000, "unit": "мл"}
        ])
        tasks_json = json.dumps([
            {"id": "task_1", "title": "Купить продукты"}
        ])

        response = await client.post(
            "/api/v1/voice/process",
            files={"audio": ("voice.m4a", dummy_audio, "audio/m4a")},
            data={
                "client_current_time": "2026-09-25T13:30:00+05:00",
                "timezone": "Asia/Yekaterinburg",
                "active_habits_context": habits_json,
                "open_tasks_context": tasks_json,
            }
        )

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert len(data["raw_transcript"]) > 0
        assert "summary" in data
        assert isinstance(data["habits_completed"], list)
        assert isinstance(data["tasks_to_add"], list)
        assert data["duration_seconds"] >= 0
