from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import Annotated, List, Tuple
from uuid import uuid4
from pydantic import BaseModel
from app.database import get_db
from app.routers.auth import get_current_user
from app.models.user import User
from app.models.transaction import Transaction
from app.models.feedback import CategorizationFeedback
from app.ml.ml_classifier import predict_category, train_model

router = APIRouter(prefix="/ml", tags=["Machine Learning"])

# Schemas
class PredictRequest(BaseModel):
    merchant: str

class PredictResponse(BaseModel):
    category: str
    confidence: float

class FeedbackRequest(BaseModel):
    transaction_id: str
    original_category: str
    corrected_category: str

class RetrainResponse(BaseModel):
    status: str
    accuracy: float
    samples_count: int

@router.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest):
    category, confidence = predict_category(request.merchant)
    return PredictResponse(category=category, confidence=confidence)

@router.post("/feedback", status_code=status.HTTP_201_CREATED)
def submit_feedback(
    request: FeedbackRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    # Verify the target transaction exists and belongs to the user
    tx = db.query(Transaction).filter(
        Transaction.id == request.transaction_id,
        Transaction.user_id == current_user.id
    ).first()
    
    if not tx:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Transaction not found or unauthorized access."
        )

    # Save the categorization feedback entry
    feedback = CategorizationFeedback(
        id=str(uuid4()),
        transaction_id=request.transaction_id,
        user_id=current_user.id,
        original_category=request.original_category,
        corrected_category=request.corrected_category
    )
    db.add(feedback)
    
    # Update the transaction's category in the DB
    tx.category = request.corrected_category
    db.commit()

    return {"message": "Feedback submitted successfully, transaction category updated."}

@router.post("/retrain", response_model=RetrainResponse)
def trigger_retrain(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    # Gather dataset to train the classifier
    # We query all transactions to build the baseline model
    transactions = db.query(Transaction).all()
    feedbacks = db.query(CategorizationFeedback).all()

    # Create mapping of merchant -> category, where feedbacks take precedence
    data_map = {}
    for tx in transactions:
        if tx.merchant and tx.category:
            data_map[tx.merchant.lower()] = tx.category

    # Overwrite with user corrected categories
    # Match feedback to its transaction merchant name
    tx_map = {tx.id: tx.merchant for tx in transactions if tx.merchant}
    for fb in feedbacks:
        merch = tx_map.get(fb.transaction_id)
        if merch:
            data_map[merch.lower()] = fb.corrected_category

    # If dataset is too small, inject a small set of rule-based keywords to bootstrap training
    # This prevents the sklearn TF-IDF vectorizer from failing on empty inputs
    if len(data_map) < 3:
        # Bootstrap default samples
        data_map["mcdonalds"] = "Food & dining"
        data_map["starbucks"] = "Food & dining"
        data_map["uber ride"] = "Travel"
        data_map["amazon store"] = "Shopping"
        data_map["airtel bills"] = "Bills"
        data_map["netflix media"] = "Entertainment"
        data_map["medical clinic"] = "Health"
        data_map["salary credits"] = "Salary"
        data_map["freelance fiverr"] = "Freelance"

    dataset = [(merch, cat) for merch, cat in data_map.items()]

    try:
        accuracy, count = train_model(dataset)
        return RetrainResponse(status="success", accuracy=accuracy, samples_count=count)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Model retraining failed: {str(e)}"
        )
