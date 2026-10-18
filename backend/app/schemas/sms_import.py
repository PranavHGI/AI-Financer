from pydantic import BaseModel, ConfigDict
from datetime import datetime
from typing import List, Optional

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

class SmsImportItem(BaseModel):
    sender: str
    body: Optional[str] = None
    sms_hash: Optional[str] = None
    timestamp: float

class SmsBatchImportRequest(BaseModel):
    sms_list: List[SmsImportItem]

class SmsBatchImportResponse(BaseModel):
    imported_count: int
    skipped_count: int
