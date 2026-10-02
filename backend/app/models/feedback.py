from sqlalchemy import Column, String, DateTime, ForeignKey
from datetime import datetime
from app.database import Base

class CategorizationFeedback(Base):
    __tablename__ = "categorization_feedback"

    id = Column(String, primary_key=True, index=True)
    transaction_id = Column(String, ForeignKey("transactions.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    original_category = Column(String, nullable=False)
    corrected_category = Column(String, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
