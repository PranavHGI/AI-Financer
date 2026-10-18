import logging
import os
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger("aifinancer.config")

class Settings(BaseSettings):
    ENVIRONMENT: str = "development"
    DATABASE_URL: str = "sqlite:///./aifinancer.db"
    SECRET_KEY: str = "9a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440
    GEMINI_API_KEY: Optional[str] = None

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

settings = Settings()

if settings.ENVIRONMENT == "production" and settings.SECRET_KEY.startswith("9a1b2c3d"):
    logger.warning("[SECURITY ALERT] Default hardcoded SECRET_KEY is active in production environment! Please configure a cryptographically random SECRET_KEY in your deployment environment.")

