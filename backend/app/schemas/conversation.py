from pydantic import BaseModel, ConfigDict
from datetime import datetime

class ConversationMessageBase(BaseModel):
    role: str # "user" or "assistant"
    message: str

class ConversationMessageCreate(ConversationMessageBase):
    pass

class ConversationMessageResponse(ConversationMessageBase):
    id: str
    user_id: str
    timestamp: datetime

    model_config = ConfigDict(from_attributes=True)

class AskAdvisorRequest(BaseModel):
    question: str

