from sqlalchemy import Column, String, DateTime, ForeignKey, Float, UniqueConstraint
from datetime import datetime
from app.database import Base

class SMSImport(Base):
    __tablename__ = "sms_imports"
    __table_args__ = (
        UniqueConstraint('user_id', 'sms_hash', name='uq_user_sms_hash'),
    )

    id = Column(String, primary_key=True, index=True)
    user_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    sms_hash = Column(String, index=True, nullable=False)
    sender = Column(String, nullable=False)
    timestamp = Column(Float, nullable=False) # SMS timestamp from Android device
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
