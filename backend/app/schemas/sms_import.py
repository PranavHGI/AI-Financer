from pydantic import BaseModel, ConfigDict
from datetime import datetime

class SMSImportBase(BaseModel):
    sms_hash: str
    sender: str
    timestamp: float

class SMSImportCreate(SMSImportBase):
    pass

class SMSImportResponse(SMSImportBase):
    id: str
    user_id: str
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)
