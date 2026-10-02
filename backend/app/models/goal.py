from sqlalchemy import Column, String, Float, Integer, ForeignKey
from app.database import Base

class Goal(Base):
    __tablename__ = "goals"

    id = Column(String, primary_key=True, index=True)
    user_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    title = Column(String, nullable=False)
    saved_amount = Column(Float, nullable=False)
    target_amount = Column(Float, nullable=False)
    monthly_saving_estimate = Column(Float, nullable=False)
    months_remaining = Column(Integer, nullable=False)
