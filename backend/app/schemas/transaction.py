from pydantic import BaseModel, ConfigDict
from datetime import datetime

class TransactionBase(BaseModel):
    type: str # "income" or "expense"
    amount: str
    category: str
    merchant: str
    timestamp: float
    source: str
    description: str = ""
    masked_account_ref: str = ""

class TransactionCreate(TransactionBase):
    pass

class TransactionResponse(TransactionBase):
    id: str
    user_id: str
    confidence: float
    duplicate_fingerprint: str
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)
