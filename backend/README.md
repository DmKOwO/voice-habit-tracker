# Voice Habit & Task Tracker Backend (2026)

FastAPI microservice utilizing **Groq Whisper-large-v3-turbo** for sub-second speech-to-text and **Gemini 3.5 Flash-Lite** with native Structured Outputs for semantic parsing.

## Setup & Running

```bash
# 1. Install dependencies using uv
uv sync --extra dev

# 2. Configure environment variables (.env)
export GROQ_API_KEY="your-groq-key"
export GEMINI_API_KEY="your-gemini-key"

# 3. Run development server
uv run uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## Features
- Fast STT with Whisper Large v3 Turbo on Groq LPUs (~1.2s for 5 min speech)
- Semantic extraction of habit completions, tasks, and notes with Gemini 3.5 Flash-Lite (~0.4s)
- Fully offline-friendly mock simulation mode when API keys are not provided
- Built-in Interactive Web Testbench at `http://localhost:8000/playground`
