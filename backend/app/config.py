from functools import lru_cache
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Voice Habit & Task Tracker Backend"
    api_v1_prefix: str = "/api/v1"
    host: str = "0.0.0.0"
    port: int = 8000
    
    # API Keys (optional for mock/simulation mode)
    groq_api_key: Optional[str] = None
    gemini_api_key: Optional[str] = None
    
    # Model selections
    groq_whisper_model: str = "whisper-large-v3-turbo"
    gemini_model: str = "gemini-3.5-flash-lite"
    
    # Debug / Mock mode
    mock_mode: bool = False

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )

    @property
    def is_groq_ready(self) -> bool:
        return bool(self.groq_api_key and self.groq_api_key.strip())

    @property
    def is_gemini_ready(self) -> bool:
        return bool(self.gemini_api_key and self.gemini_api_key.strip())


@lru_cache()
def get_settings() -> Settings:
    return Settings()
