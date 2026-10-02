from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List, Annotated
from uuid import uuid4
from datetime import datetime
from pydantic import BaseModel

from app.database import get_db
from app.routers.auth import get_current_user
from app.models.user import User
from app.models.transaction import Transaction
from app.models.goal import Goal
from app.models.budget import Budget
from app.models.category import Category
from app.models.feedback import CategorizationFeedback
from app.models.sms_import import SMSImport
from app.models.insight import Insight
from app.models.conversation import ConversationMessage

from app.schemas.transaction import TransactionCreate, TransactionResponse
from app.schemas.goal import GoalCreate, GoalResponse
from app.schemas.category import CategoryResponse
from app.schemas.feedback import FeedbackCreate, FeedbackResponse
from app.schemas.sms_import import SMSImportCreate, SMSImportResponse
from app.schemas.insight import InsightResponse
from app.schemas.conversation import ConversationMessageResponse, AskAdvisorRequest

router = APIRouter(tags=["Finance"])

# Profile
@router.get("/profile", response_model=dict)
def get_profile(current_user: Annotated[User, Depends(get_current_user)]):
    return {
        "username": current_user.username,
        "email": current_user.email,
        "created_at": current_user.created_at
    }

# Transactions
@router.get("/transactions", response_model=List[TransactionResponse])
def get_transactions(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    txs = db.query(Transaction).filter(Transaction.user_id == current_user.id).all()
    return txs

@router.post("/transactions", response_model=TransactionResponse, status_code=status.HTTP_201_CREATED)
def create_transaction(
    tx_in: TransactionCreate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    new_tx = Transaction(
        id=str(uuid4()),
        user_id=current_user.id,
        type=tx_in.type,
        amount=tx_in.amount,
        category=tx_in.category,
        merchant=tx_in.merchant,
        timestamp=tx_in.timestamp,
        source=tx_in.source,
        description=tx_in.description,
        masked_account_ref=tx_in.masked_account_ref,
        confidence=1.0,
        duplicate_fingerprint=str(uuid4())
    )
    db.add(new_tx)
    db.commit()
    db.refresh(new_tx)
    return new_tx

# Categories
@router.get("/categories", response_model=List[CategoryResponse])
def get_categories(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    cats = db.query(Category).all()
    return cats

# Budgets
@router.get("/budgets", response_model=List[dict])
def get_budgets(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    budgets = db.query(Budget).filter(Budget.user_id == current_user.id).all()
    return [{"category": b.category, "amount": b.amount} for b in budgets]


# Dashboard
@router.get("/dashboard", response_model=dict)
def get_dashboard(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    txs = db.query(Transaction).filter(Transaction.user_id == current_user.id).all()
    income_val = 0.0
    spent_val = 0.0
    for t in txs:
        cleaned = t.amount.replace("₹", "").replace("+", "").replace(",", "").strip()
        try:
            val = float(cleaned)
            if t.type == "income":
                income_val += val
            else:
                spent_val += val
        except ValueError:
            pass
    
    balance_val = income_val - spent_val
    return {
        "available_balance": f"₹{balance_val:,.2f}",
        "monthly_income": f"₹{income_val:,.2f}",
        "monthly_spent": f"₹{spent_val:,.2f}"
    }

# Categorization Feedback
@router.post("/transactions/feedback", response_model=FeedbackResponse, status_code=status.HTTP_201_CREATED)
def create_feedback(
    fb_in: FeedbackCreate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    tx = db.query(Transaction).filter(Transaction.id == fb_in.transaction_id, Transaction.user_id == current_user.id).first()
    if not tx:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Transaction not found")
        
    new_fb = CategorizationFeedback(
        id=str(uuid4()),
        transaction_id=fb_in.transaction_id,
        user_id=current_user.id,
        original_category=fb_in.original_category,
        corrected_category=fb_in.corrected_category
    )
    db.add(new_fb)
    db.commit()
    db.refresh(new_fb)
    return new_fb

# SMS Imports
@router.post("/sms/import", response_model=SMSImportResponse, status_code=status.HTTP_201_CREATED)
def import_sms(
    sms_in: SMSImportCreate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    existing = db.query(SMSImport).filter(SMSImport.sms_hash == sms_in.sms_hash).first()
    if existing:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="SMS already imported")
        
    new_import = SMSImport(
        id=str(uuid4()),
        user_id=current_user.id,
        sms_hash=sms_in.sms_hash,
        sender=sms_in.sender,
        timestamp=sms_in.timestamp
    )
    db.add(new_import)
    db.commit()
    db.refresh(new_import)
    return new_import

# Insights
@router.get("/insights", response_model=List[InsightResponse])
def get_insights(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    insights = db.query(Insight).filter(Insight.user_id == current_user.id).all()
    if not insights:
        default_insights = [
            ("Top spending category", "Your top category this month was Food & dining."),
            ("Spending increase warning", "You spent 35% higher than last month on dining out.")
        ]
        for title, desc in default_insights:
            db.add(Insight(id=str(uuid4()), user_id=current_user.id, title=title, description=desc))
        db.commit()
        insights = db.query(Insight).filter(Insight.user_id == current_user.id).all()
    return insights

# Advisor
@router.get("/advisor", response_model=List[ConversationMessageResponse])
def get_advisor_messages(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    messages = db.query(ConversationMessage).filter(ConversationMessage.user_id == current_user.id).order_by(ConversationMessage.timestamp.asc()).all()
    return messages

@router.post("/advisor", response_model=dict)
def ask_advisor(
    req: AskAdvisorRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    user_msg = ConversationMessage(
        id=str(uuid4()),
        user_id=current_user.id,
        role="user",
        message=req.question,
        timestamp=datetime.utcnow()
    )
    db.add(user_msg)
    
    question = req.question.lower()
    
    txs = db.query(Transaction).filter(Transaction.user_id == current_user.id).all()
    budgets = db.query(Budget).filter(Budget.user_id == current_user.id).all()
    
    total_spent = 0.0
    category_spending = {}
    for t in txs:
        if t.type == "expense":
            try:
                cleaned = t.amount.replace("₹", "").replace("+", "").replace(",", "").strip()
                val = float(cleaned)
                total_spent += val
                category_spending[t.category] = category_spending.get(t.category, 0.0) + val
            except ValueError:
                pass
                
    overspent_categories = []
    for b in budgets:
        spent = category_spending.get(b.category, 0.0)
        if spent > b.amount:
            overspent_categories.append(f"{b.category} (Spent: ₹{spent:,.2f} / Budget: ₹{b.amount:,.2f})")
            
    if "budget" in question or "spend" in question or "expense" in question:
        if overspent_categories:
            advisor_response = "You have exceeded your budget in the following categories:\n" + "\n".join(overspent_categories)
        elif budgets:
            advisor_response = "Great job! You are currently within budget for all set categories."
        else:
            advisor_response = "You haven't set up any budgets yet. I recommend setting up budgets for your top categories like Food & dining or Travel."
    elif "save" in question or "goal" in question:
        goals = db.query(Goal).filter(Goal.user_id == current_user.id).all()
        if goals:
            goal_details = [f"'{g.title}' (Saved: ₹{g.saved_amount:,.2f} / Target: ₹{g.target_amount:,.2f})" for g in goals]
            advisor_response = "Here are your savings goals:\n" + "\n".join(goal_details) + "\nTo reach them faster, try cutting down on non-essential categories."
        else:
            advisor_response = "Setting savings goals is a great way to build wealth. You can add goals in the Goals tab."
    else:
        advisor_response = f"Based on your transaction history, you have spent a total of ₹{total_spent:,.2f}. I recommend reviewing your spending patterns weekly."
        
    disclaimer = "\n\nDisclaimer: As an educational budgeting advisor, I provide general spending guidance. I do not provide regulated investment advice, trading recommendations, or guaranteed financial outcomes."
    full_response = advisor_response + disclaimer
    
    assistant_msg = ConversationMessage(
        id=str(uuid4()),
        user_id=current_user.id,
        role="assistant",
        message=full_response,
        timestamp=datetime.utcnow()
    )
    db.add(assistant_msg)
    db.commit()
    
    return {
        "response": full_response
    }

class BudgetCreate(BaseModel):
    category: str
    amount: float

@router.post("/budgets", status_code=status.HTTP_201_CREATED)
def create_budget(
    budget_in: BudgetCreate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    budget = db.query(Budget).filter(Budget.category == budget_in.category, Budget.user_id == current_user.id).first()
    if budget:
        budget.amount = budget_in.amount
    else:
        budget = Budget(
            id=str(uuid4()),
            user_id=current_user.id,
            category=budget_in.category,
            amount=budget_in.amount
        )
        db.add(budget)
    db.commit()
    db.refresh(budget)
    return {"category": budget.category, "amount": budget.amount}

@router.delete("/transactions/{tx_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_transaction(
    tx_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    tx = db.query(Transaction).filter(Transaction.id == tx_id, Transaction.user_id == current_user.id).first()
    if not tx:
        raise HTTPException(status_code=404, detail="Transaction not found")
    db.delete(tx)
    db.commit()
    return

@router.get("/goals", response_model=List[GoalResponse])
def get_goals(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    return db.query(Goal).filter(Goal.user_id == current_user.id).all()

@router.post("/goals", response_model=GoalResponse, status_code=status.HTTP_201_CREATED)
def create_goal(
    goal_in: GoalCreate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    remaining = goal_in.target_amount - goal_in.saved_amount
    est = remaining / goal_in.months_remaining if goal_in.months_remaining > 0 else 0.0
    
    goal = Goal(
        id=str(uuid4()),
        user_id=current_user.id,
        title=goal_in.title,
        saved_amount=goal_in.saved_amount,
        target_amount=goal_in.target_amount,
        monthly_saving_estimate=est,
        months_remaining=goal_in.months_remaining
    )
    db.add(goal)
    db.commit()
    db.refresh(goal)
    return goal

class AddSavingsRequest(BaseModel):
    amount: float

@router.post("/goals/{goal_id}/save", response_model=GoalResponse)
def add_goal_savings(
    goal_id: str,
    savings_in: AddSavingsRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    goal = db.query(Goal).filter(Goal.id == goal_id, Goal.user_id == current_user.id).first()
    if not goal:
        raise HTTPException(status_code=404, detail="Goal not found")
    
    goal.saved_amount += savings_in.amount
    remaining = goal.target_amount - goal.saved_amount
    goal.monthly_saving_estimate = remaining / goal.months_remaining if goal.months_remaining > 0 else 0.0
    
    db.commit()
    db.refresh(goal)
    return goal

@router.delete("/goals/{goal_id}", status_code=status.HTTP_200_OK)
def delete_goal(
    goal_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    goal = db.query(Goal).filter(Goal.id == goal_id, Goal.user_id == current_user.id).first()
    if not goal:
        raise HTTPException(status_code=404, detail="Goal not found")
    db.delete(goal)
    db.commit()
    return {"message": "Goal deleted successfully"}


