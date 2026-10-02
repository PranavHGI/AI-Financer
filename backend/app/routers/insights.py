from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from datetime import datetime, timedelta
from typing import Annotated, List, Dict, Any
from collections import defaultdict
from pydantic import BaseModel
from app.database import get_db
from app.routers.auth import get_current_user
from app.models.user import User
from app.models.transaction import Transaction
from app.ml.analytics_models import predict_next_month_expense, detect_anomalies

router = APIRouter(prefix="/insights", tags=["Analytics & Insights"])

# Schemas
class RecurringBillResponse(BaseModel):
    merchant: str
    amount: float
    frequency_days: int

class AnomalyAlertResponse(BaseModel):
    transaction_id: str
    merchant: str
    amount: float
    category: str
    timestamp: float

class DuplicateAlertResponse(BaseModel):
    merchant: str
    amount: float
    count: int

class InsightsResponse(BaseModel):
    top_category: str
    monthly_change_percent: float
    next_month_prediction: float
    recurring_bills: List[RecurringBillResponse]
    duplicates: List[DuplicateAlertResponse]
    anomalies: List[AnomalyAlertResponse]
    disclaimer: str

def parse_amount(amt_str: str) -> float:
    if not amt_str:
        return 0.0
    cleaned = amt_str.replace("₹", "").replace("+", "").replace("-", "").replace(",", "").strip()
    try:
        return float(cleaned)
    except:
        return 0.0

@router.get("/summary", response_model=InsightsResponse)
def get_insights(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    # Fetch all transactions for this user
    txs = db.query(Transaction).filter(Transaction.user_id == current_user.id).order_by(Transaction.timestamp.asc()).all()
    
    if not txs:
        return InsightsResponse(
            top_category="None",
            monthly_change_percent=0.0,
            next_month_prediction=0.0,
            recurring_bills=[],
            duplicates=[],
            anomalies=[],
            disclaimer="Educational information only. No trading or investment advice is provided."
        )

    # 1. Monthly totals and current month stats
    now = datetime.utcnow()
    current_month = now.month
    current_year = now.year
    
    prev_month = current_month - 1 if current_month > 1 else 12
    prev_year = current_year if current_month > 1 else current_year - 1

    current_month_spent = 0.0
    prev_month_spent = 0.0
    category_spends = defaultdict(float)

    # Historical monthly totals (sorted chronologically)
    monthly_totals_map = defaultdict(float)

    for tx in txs:
        if tx.type == "expense":
            tx_date = datetime.utcfromtimestamp(tx.timestamp)
            month_key = f"{tx_date.year}-{tx_date.month:02d}"
            amt = parse_amount(tx.amount)
            monthly_totals_map[month_key] += amt

            # Current month stats
            if tx_date.month == current_month and tx_date.year == current_year:
                current_month_spent += amt
                category_spends[tx.category] += amt
            
            # Previous month stats
            elif tx_date.month == prev_month and tx_date.year == prev_year:
                prev_month_spent += amt

    # Top spending category
    top_cat = "None"
    if category_spends:
        top_cat = max(category_spends, key=category_spends.get)

    # Monthly spent change percentage
    change_percent = 0.0
    if prev_month_spent > 0.0:
        change_percent = ((current_month_spent - prev_month_spent) / prev_month_spent) * 100.0

    # Next month predicted spend (Linear Regression)
    sorted_monthly_keys = sorted(monthly_totals_map.keys())
    historical_spends = [monthly_totals_map[key] for key in sorted_monthly_keys]
    prediction = predict_next_month_expense(historical_spends)

    # 2. Duplicate Detection (merchant + amount matching within 10 minutes)
    duplicates_alerts = []
    # Group transactions by (merchant, amount)
    grouped_by_amount = defaultdict(list)
    for tx in txs:
        amt = parse_amount(tx.amount)
        if tx.merchant and amt > 0.0:
            grouped_by_amount[(tx.merchant.lower(), amt)].append(tx)
            
    for (merch, amt), group in grouped_by_amount.items():
        # Sort by timestamp
        group.sort(key=lambda t: t.timestamp)
        dup_count = 0
        for i in range(len(group) - 1):
            time_diff = group[i+1].timestamp - group[i].timestamp
            if time_diff <= 600: # 10 minutes
                dup_count += 1
        if dup_count > 0:
            duplicates_alerts.append(
                DuplicateAlertResponse(merchant=merch.capitalize(), amount=amt, count=dup_count + 1)
            )

    # 3. Recurring Bills (merchant + amount matching at ~30 day intervals)
    recurring_bills = []
    for (merch, amt), group in grouped_by_amount.items():
        if len(group) >= 3: # Need at least 3 occurrences to verify recurring nature
            group.sort(key=lambda t: t.timestamp)
            consecutive_diffs = []
            for i in range(len(group) - 1):
                days_diff = (group[i+1].timestamp - group[i].timestamp) / 86400.0 # convert to days
                consecutive_diffs.append(days_diff)
                
            # If average spacing is between 25 and 35 days, mark as recurring
            avg_diff = sum(consecutive_diffs) / len(consecutive_diffs)
            if 25.0 <= avg_diff <= 35.0:
                recurring_bills.append(
                    RecurringBillResponse(merchant=merch.capitalize(), amount=amt, frequency_days=int(avg_diff))
                )

    # 4. Anomaly Detection (Isolation Forest outliers)
    anomaly_alerts = []
    expenses = [tx for tx in txs if tx.type == "expense"]
    if len(expenses) >= 5:
        amounts = [parse_amount(tx.amount) for tx in expenses]
        flags = detect_anomalies(amounts)
        for idx, flag in enumerate(flags):
            if flag == -1: # Outlier detected!
                tx = expenses[idx]
                anomaly_alerts.append(
                    AnomalyAlertResponse(
                        transaction_id=tx.id,
                        merchant=tx.merchant or "Unknown",
                        amount=parse_amount(tx.amount),
                        category=tx.category,
                        timestamp=tx.timestamp
                    )
                )

    return InsightsResponse(
        top_category=top_cat,
        monthly_change_percent=change_percent,
        next_month_prediction=prediction,
        recurring_bills=recurring_bills,
        duplicates=duplicates_alerts,
        anomalies=anomaly_alerts,
        disclaimer="Disclaimer: This tool provides educational budget planning recommendations based on statistics. We explicitly do NOT offer financial product trading recommendations, legal tax planning advice, or guaranteed monetary outcomes."
    )
