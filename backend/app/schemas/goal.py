from pydantic import BaseModel, ConfigDict

class GoalBase(BaseModel):
    title: str
    saved_amount: float
    target_amount: float
    monthly_saving_estimate: float = 0.0
    months_remaining: int

class GoalCreate(GoalBase):
    pass

class GoalResponse(GoalBase):
    id: str
    user_id: str

    model_config = ConfigDict(from_attributes=True)
