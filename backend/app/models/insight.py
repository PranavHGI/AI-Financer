from sqlalchemy import Column, String, DateTime, ForeignKey
from datetime import datetime
from app.database import Base

class Insight(Base):
    __tablename__ = "insights"

    id = Column(String, primary_key=True, index=True)
    user_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    title = Column(String, nullable=False)
    description = Column(String, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
