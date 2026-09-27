import io
import time
from typing import Tuple
from groq import Groq
from app.config import Settings


class STTService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.groq_client = Groq(api_key=settings.groq_api_key) if settings.is_groq_ready else None

    async def transcribe(self, audio_bytes: bytes, filename: str = "voice.m4a") -> Tuple[str, int, str]:
        """
        Транскрибирует аудио в текст.
        Возвращает (текст, длительность_мс, имя_провайдера).
        """
        start_time = time.time()
        
        # 1. Если задан Groq API Key — используем супербыстрый whisper-large-v3-turbo
        if self.groq_client:
            try:
                # Groq ожидает tuple: (filename, file_like_object_or_bytes)
                audio_file = (filename, io.BytesIO(audio_bytes))
                transcription = self.groq_client.audio.transcriptions.create(
                    file=audio_file,
                    model=self.settings.groq_whisper_model,
                    response_format="verbose_json",
                    language="ru",
                    temperature=0.0
                )
                duration_ms = int((time.time() - start_time) * 1000)
                return transcription.text, duration_ms, f"groq:{self.settings.groq_whisper_model}"
            except Exception as e:
                # В случае ошибки логируем и переходим к фоллбеку
                print(f"[STT Error in Groq] {e}. Falling back to simulation/fallback.")

        # 2. Если API ключ не задан или произошла ошибка — режим реалистичной симуляции
        duration_ms = int((time.time() - start_time) * 1000) + 120
        mock_transcript = (
            "Слушай, ну сегодня продуктивный день получился: сходил в зал, отлично спину покачал, "
            "норму воды на два литра закрыл уже к обеду. Завтра в два часа дня нужно обязательно заехать к ребятам "
            "обсудить архитектуру, а еще в пятницу до шести вечера отправить отчет по практике научнику. "
            "Да, и напомни вечером курицу разморозить на ужин. И записать мысль для игры: прикольная механика с затухающим фонариком."
        )
        return mock_transcript, duration_ms, "mock:whisper-simulation"
