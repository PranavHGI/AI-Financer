from pydantic import BaseModel
from typing import List, Optional

class ChatMessage(BaseModel):
    role: str  # "user" or "model"
    content: str

class AdvisorChatRequest(BaseModel):
    message: str
    history: Optional[List[ChatMessage]] = []

class AdvisorChatResponse(BaseModel):
    response: str
