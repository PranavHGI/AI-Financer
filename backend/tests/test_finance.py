import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from datetime import datetime, timedelta

from app.main import app
from app.database import Base, get_db
from app.models.category import Category
from app.models.transaction import Transaction

SQLALCHEMY_DATABASE_URL = "sqlite:///:memory:"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Create tables in memory
Base.metadata.create_all(bind=engine)

# Populate standard categories in memory database
db = TestingSessionLocal()
defaults = [
    "Food & dining", "Travel", "Shopping", "Education", "Bills", 
    "Entertainment", "Health", "Salary", "Freelance", "Transfer", "Other"
]
from uuid import uuid4
for cat in defaults:
    db.add(Category(id=str(uuid4()), name=cat))
db.commit()
db.close()

def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()

app.dependency_overrides[get_db] = override_get_db

client = TestClient(app)

# Helper function to get valid headers
def get_auth_headers(username="testfinanceuser"):
    # Register
    client.post(
        "/auth/register",
        json={"username": username, "email": f"{username}@example.com", "password": "securepassword123"},
    )
    # Login
    response = client.post(
        "/auth/token",
        data={"username": username, "password": "securepassword123"},
    )
    token_data = response.json()
    token = token_data["access_token"]
    refresh_token = token_data["refresh_token"]
    return {"Authorization": f"Bearer {token}"}, refresh_token

def test_get_categories():
    headers, _ = get_auth_headers("user1")
    response = client.get("/categories", headers=headers)
    assert response.status_code == 200
    cats = response.json()
    assert len(cats) == 11
    names = [c["name"] for c in cats]
    assert "Food & dining" in names

def test_refresh_and_logout():
    headers, refresh_token = get_auth_headers("user2")
    
    # Refresh token rotation
    response = client.post("/auth/refresh", json={"refresh_token": refresh_token})
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert "refresh_token" in data
    new_refresh_token = data["refresh_token"]
    
    # Old refresh token should be invalid/revoked now
    failed_refresh = client.post("/auth/refresh", json={"refresh_token": refresh_token})
    assert failed_refresh.status_code == 401
    
    # Logout
    logout_resp = client.post("/auth/logout", json={"refresh_token": new_refresh_token})
    assert logout_resp.status_code == 200
    
    # Refreshed token should be invalid/revoked after logout
    failed_refresh_2 = client.post("/auth/refresh", json={"refresh_token": new_refresh_token})
    assert failed_refresh_2.status_code == 401

def test_sms_import():
    headers, _ = get_auth_headers("user3")
    
    # First import
    response = client.post(
        "/sms/import",
        json={"sms_hash": "sms_hash_123", "sender": "HDFC-BANK", "timestamp": 1690000000.0},
        headers=headers
    )
    assert response.status_code == 201
    data = response.json()
    assert data["sms_hash"] == "sms_hash_123"
    
    # Duplicate import should fail
    dup_response = client.post(
        "/sms/import",
        json={"sms_hash": "sms_hash_123", "sender": "HDFC-BANK", "timestamp": 1690000000.0},
        headers=headers
    )
    assert dup_response.status_code == 400
    assert dup_response.json()["detail"] == "SMS already imported"

def test_insights():
    headers, _ = get_auth_headers("user4")
    
    # Fetch insights (should seed defaults since they don't exist yet)
    response = client.get("/insights", headers=headers)
    assert response.status_code == 200
    insights = response.json()
    assert len(insights) == 2
    assert insights[0]["title"] == "Top spending category"

def test_categorization_feedback():
    headers, _ = get_auth_headers("user5")
    
    # Create a transaction
    tx_resp = client.post(
        "/transactions",
        json={
            "type": "expense",
            "amount": "₹500.00",
            "category": "Food & dining",
            "merchant": "Swiggy",
            "timestamp": 1690000000.0,
            "source": "manual"
        },
        headers=headers
    )
    tx_id = tx_resp.json()["id"]
    
    # Create feedback
    fb_resp = client.post(
        "/transactions/feedback",
        json={
            "transaction_id": tx_id,
            "original_category": "Food & dining",
            "corrected_category": "Other"
        },
        headers=headers
    )
    assert fb_resp.status_code == 201
    fb_data = fb_resp.json()
    assert fb_data["transaction_id"] == tx_id
    assert fb_data["corrected_category"] == "Other"

def test_advisor_flow():
    headers, _ = get_auth_headers("user6")
    
    # Ask a question
    response = client.post(
        "/advisor",
        json={"question": "What is my budget status?"},
        headers=headers
    )
    assert response.status_code == 200
    data = response.json()
    assert "response" in data
    assert "Disclaimer:" in data["response"]
    
    # Get advisor history
    hist_resp = client.get("/advisor", headers=headers)
    assert hist_resp.status_code == 200
    history = hist_resp.json()
    # Should contain 2 messages: 1 user, 1 assistant
    assert len(history) == 2
    assert history[0]["role"] == "user"
    assert history[0]["message"] == "What is my budget status?"
    assert history[1]["role"] == "assistant"
