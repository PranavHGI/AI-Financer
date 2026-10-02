from sqlalchemy import Column, String, Float, ForeignKey
from app.database import Base

class Budget(Base):
    __tablename__ = "budgets"

    id = Column(String, primary_key=True, index=True)
    user_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    category = Column(String, nullable=False)
    amount = Column(Float, nullable=False)
