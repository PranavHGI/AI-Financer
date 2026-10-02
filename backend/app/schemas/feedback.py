from pydantic import BaseModel, ConfigDict
from datetime import datetime

class FeedbackBase(BaseModel):
    transaction_id: str
    original_category: str
    corrected_category: str

class FeedbackCreate(FeedbackBase):
    pass

class FeedbackResponse(FeedbackBase):
    id: str
    user_id: str
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)
