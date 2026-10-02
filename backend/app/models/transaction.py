from sqlalchemy import Column, String, Float, DateTime, ForeignKey
from sqlalchemy.sql import func
from app.database import Base

class Transaction(Base):
    __tablename__ = "transactions"

    id = Column(String, primary_key=True, index=True)
    user_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    type = Column(String, nullable=False) # "income" or "expense"
    amount = Column(String, nullable=False) # e.g. "₹250", "+₹8,000"
    category = Column(String, nullable=False)
    merchant = Column(String, nullable=False)
    timestamp = Column(Float, nullable=False)
    source = Column(String, nullable=False) # "manual", "sms", "ocr", "voice"
    description = Column(String, default="")
    masked_account_ref = Column(String, default="")
    confidence = Column(Float, default=1.0)
    duplicate_fingerprint = Column(String, default="")
    created_at = Column(DateTime, server_default=func.now(), nullable=False)
