from pydantic import BaseModel, ConfigDict
from datetime import datetime

class InsightBase(BaseModel):
    title: str
    description: str

class InsightCreate(InsightBase):
    pass

class InsightResponse(InsightBase):
    id: str
    user_id: str
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)
