from pydantic import BaseModel, ConfigDict
from datetime import datetime

class SessionBase(BaseModel):
    refresh_token: str
    expires_at: datetime

class SessionCreate(SessionBase):
    user_id: str

class SessionResponse(SessionBase):
    id: str
    user_id: str
    is_revoked: bool
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)
