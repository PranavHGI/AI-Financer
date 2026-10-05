import os
import httpx
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import Annotated, List
from app.database import get_db
from app.routers.auth import get_current_user
from app.models.user import User
from app.models.transaction import Transaction
from app.models.budget import Budget
from app.schemas.advisor import AdvisorChatRequest, AdvisorChatResponse, ChatMessage
from app.ml.analytics_models import predict_next_month_expense, detect_anomalies
from collections import defaultdict
import datetime

router = APIRouter(prefix="/advisor", tags=["Advisor"])

def parse_amount(amt_str: str) -> float:
    cleaned = amt_str.replace("₹", "").replace("+", "").replace("-", "").replace(",", "").strip()
    try:
        return float(cleaned)
    except:
        return 0.0

@router.post("/chat", response_model=AdvisorChatResponse)
async def chat_with_advisor(
    request: AdvisorChatRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    # Fetch live transaction history and budget configurations for this user
    transactions = db.query(Transaction).filter(Transaction.user_id == current_user.id).all()
    budgets = db.query(Budget).filter(Budget.user_id == current_user.id).all()

    # Calculate financial aggregates
    total_income = 0.0
    total_spent = 0.0
    category_spending = {}
    monthly_totals_map = defaultdict(float)

    for tx in transactions:
        amt = parse_amount(tx.amount)
        if tx.type == "income":
            total_income += amt
        else:
            total_spent += amt
            category_spending[tx.category] = category_spending.get(tx.category, 0.0) + amt
            
            # Extract month for regression forecasting
            try:
                tx_date = datetime.datetime.utcfromtimestamp(tx.timestamp)
                month_key = f"{tx_date.year}-{tx_date.month:02d}"
                monthly_totals_map[month_key] += amt
            except:
                pass

    safe_to_spend = total_income - total_spent

    # Calculate expense prediction
    sorted_monthly_keys = sorted(monthly_totals_map.keys())
    historical_spends = [monthly_totals_map[key] for key in sorted_monthly_keys]
    prediction = predict_next_month_expense(historical_spends)

    # Detect anomaly spending outliers (Isolation Forest)
    expenses = [tx for tx in transactions if tx.type == "expense"]
    anomaly_list = []
    if len(expenses) >= 5:
        amounts = [parse_amount(tx.amount) for tx in expenses]
        flags = detect_anomalies(amounts)
        for idx, flag in enumerate(flags):
            if flag == -1:
                tx = expenses[idx]
                anomaly_list.append(f"- Unusual transaction: ₹{amounts[idx]:.2f} spent at {tx.merchant or 'Unknown'} in category {tx.category}")
    anomaly_summary = "\n".join(anomaly_list) if anomaly_list else "No unusual spending anomalies detected."

    # Budget limit vs spending breakdown
    budget_details = []
    for b in budgets:
        spent = category_spending.get(b.category, 0.0)
        status_label = "OK"
        if spent > b.amount:
            status_label = "OVER BUDGET"
        budget_details.append(
            f"- {b.category}: Spent ₹{spent:.2f} of ₹{b.amount:.2f} Limit ({status_label})"
        )
    budget_summary = "\n".join(budget_details) if budget_details else "No specific budget limits configured."

    # Recent transactions listing
    recent_txs = sorted(transactions, key=lambda x: x.timestamp, reverse=True)[:5]
    recent_details = []
    for tx in recent_txs:
        recent_details.append(
            f"- {tx.merchant} ({tx.category}): {tx.type.upper()} {tx.amount} on Source: {tx.source}"
        )
    recent_summary = "\n".join(recent_details) if recent_details else "No recent transactions found."

    # Build system instructions
    system_prompt = (
        f"You are an expert personal financial advisor and budgeting coach named AI Advisor, designed to assist {current_user.username}.\n"
        f"Here is the user's live financial data for the current month:\n"
        f"- Total Income: ₹{total_income:.2f}\n"
        f"- Total Expenses: ₹{total_spent:.2f}\n"
        f"- Remaining Balance (Safe-to-Spend): ₹{safe_to_spend:.2f}\n\n"
        f"ML Analytics Predictions:\n"
        f"- Next Month Forecast Expense (Linear Regression): ₹{prediction:.2f}\n"
        f"- Detected Unusual Spending (Outliers):\n{anomaly_summary}\n\n"
        f"Monthly Budgets Configurations:\n{budget_summary}\n\n"
        f"Recent Transactions:\n{recent_summary}\n\n"
        f"Provide personalized, constructive, and actionable budgeting advice based strictly on local cashflow and goals. "
        f"You are strictly prohibited from offering regulated investment advice, stock or cryptocurrency trading recommendations, tax filings legal counsel, or guaranteeing any future financial returns or outcomes. "
        f"Format currency in Indian Rupees (₹). Keep answers structured, encouraging, and easy to read. "
        f"Be conversational but brief, avoiding long generic essays. Maintain a pure budgeting advisor focus."
    )

    api_key = os.getenv("GEMINI_API_KEY")

    if not api_key:
        query = request.message.lower()
        
        # 1. Budget Keywords
        if "budget" in query or "limit" in query:
            overrun_list = []
            ok_list = []
            for b in budgets:
                spent = category_spending.get(b.category, 0.0)
                if spent > b.amount:
                    overrun_list.append(f"🔴 **{b.category}**: Spent ₹{spent:,.2f} of ₹{b.amount:,.2f} limit (Over budget by ₹{spent - b.amount:,.2f})")
                else:
                    ok_list.append(f"🟢 **{b.category}**: Spent ₹{spent:,.2f} of ₹{b.amount:,.2f} limit (Remaining: ₹{b.amount - spent:,.2f})")
            
            response = f"**[Demo Advisor (Offline) - Budget Analysis]**\n\n"
            if overrun_list:
                response += "🚨 **Alert:** You have exceeded your limits in the following categories:\n" + "\n".join(overrun_list) + "\n\n"
            if ok_list:
                response += "✅ **On Track:** You are within limits for the following categories:\n" + "\n".join(ok_list) + "\n"
            if not budgets:
                response += "No budgets configured yet. Create a budget in the Goals screen to track limits.\n"
                
        # 2. Spend / Expense Keywords
        elif any(k in query for k in ["spend", "expense", "cost"]):
            response = f"**[Demo Advisor (Offline) - Expense Analysis]**\n\n"
            response += f"You have spent a total of **₹{total_spent:,.2f}** this month.\n\n"
            if category_spending:
                response += "Category-wise Breakdown:\n"
                for cat, amt in category_spending.items():
                    percentage = (amt / total_spent * 100) if total_spent > 0 else 0
                    response += f"- **{cat}**: ₹{amt:,.2f} ({percentage:.1f}% of total)\n"
            else:
                response += "No expenses logged this month.\n"
                
        # 3. Income Keywords
        elif any(k in query for k in ["income", "earn", "salary", "freelance"]):
            response = f"**[Demo Advisor (Offline) - Income Analysis]**\n\n"
            response += f"You have earned a total of **₹{total_income:,.2f}** this month.\n\n"
            income_txs = [tx for tx in transactions if tx.type == "income"]
            if income_txs:
                response += "Income logs:\n"
                for tx in income_txs:
                    response += f"- **{tx.merchant}** ({tx.category}): {tx.amount}\n"
            else:
                response += "No income logs found for this month.\n"
                
        # 4. Saving / Tips Keywords
        elif "save" in query or "saving" in query:
            response = f"**[Demo Advisor (Offline) - Saving Advisor]**\n\n"
            response += "Here are customized suggestions to increase your savings based on your data:\n\n"
            if total_spent > total_income:
                response += "1. ⚠️ **Immediate Action Required:** Your monthly spending is higher than your income. Trim non-essential categories (like Shopping or Entertainment) immediately.\n"
            else:
                savings_rate = ((total_income - total_spent) / total_income * 100) if total_income > 0 else 0
                response += f"1. 📈 **Savings Rate:** You saved **{savings_rate:.1f}%** of your earnings this month. Aim to maintain this above 20%.\n"
            
            top_categories = sorted(category_spending.items(), key=lambda x: x[1], reverse=True)
            if top_categories:
                main_cat, main_amt = top_categories[0]
                response += f"2. 🔍 **Highest Expense:** Your top expense category is **{main_cat}** at **₹{main_amt:,.2f}**. Trimming this by 15% would save you **₹{main_amt * 0.15:,.2f}**.\n"
            response += "3. 🎯 **Set Budget Limits:** Setting a strict monthly budget in the Goals screen is the most effective way to control impulsive spending.\n"
            
        # 5. Default General diagnostics
        else:
            response = (
                f"**[Demo Advisor (Offline)]**\n\n"
                f"Hello {current_user.username}! Live Gemini AI is currently offline because the `GEMINI_API_KEY` is not set in the server's `.env` file.\n\n"
                f"**Local Financial Diagnostics:**\n"
                f"- **Cashflow Summary:** You earned **₹{total_income:,.2f}** and spent **₹{total_spent:,.2f}** this month. "
                f"Your net cashflow is **₹{safe_to_spend:,.2f}**.\n"
            )
            if total_spent > total_income:
                response += "- ⚠️ **Warning:** Your spending exceeds your income this month. Consider reviewing your top categories.\n"
            else:
                response += "- ✅ **Healthy Saving:** You are spending within your income. Keep saving!\n"

            overrun_list = []
            for b in budgets:
                spent = category_spending.get(b.category, 0.0)
                if spent > b.amount:
                    overrun_list.append(f"**{b.category}** (Spent ₹{spent:,.2f} vs Limit ₹{b.amount:,.2f})")
            
            if overrun_list:
                response += f"- 🚨 **Budget Overrun:** You have exceeded your budget limits in: {', '.join(overrun_list)}.\n"
            else:
                response += "- 🎯 **Budget Tracking:** You are within all configured category budget limits.\n"

        response += "\n\n*To enable smart interactive chat advisor responses, please set `GEMINI_API_KEY` inside `backend/.env` and restart the backend.*"
        return AdvisorChatResponse(response=response)

    # Format history and request payload for Gemini API
    contents = []
    for msg in request.history:
        contents.append({
            "role": msg.role,
            "parts": [{"text": msg.content}]
        })
    
    # Append current user query
    contents.append({
        "role": "user",
        "parts": [{"text": request.message}]
    })

    payload = {
        "systemInstruction": {
            "parts": [{"text": system_prompt}]
        },
        "contents": contents
    }

    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key={api_key}"

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            res = await client.post(url, json=payload)
            if res.status_code != 200:
                raise HTTPException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    detail=f"Gemini API returned error code {res.status_code}: {res.text}"
                )
            
            data = res.json()
            try:
                ai_text = data["candidates"][0]["content"]["parts"][0]["text"]
                return AdvisorChatResponse(response=ai_text)
            except KeyError:
                raise HTTPException(
                    status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                    detail="Malformed Gemini response payload."
                )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Network error communicating with Gemini AI: {str(e)}"
        )
