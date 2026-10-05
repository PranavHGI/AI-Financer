from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.database import engine, Base, SessionLocal
from app.routers import auth, finance, ocr, advisor, ml, insights
from app.models.category import Category
from uuid import uuid4

# Create database tables on startup if they don't exist
Base.metadata.create_all(bind=engine)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: pre-populate categories
    db = SessionLocal()
    try:
        count = db.query(Category).count()
        if count == 0:
            defaults = [
                "Food & dining", "Travel", "Shopping", "Education", "Bills", 
                "Entertainment", "Health", "Salary", "Freelance", "Transfer", "Other"
            ]
            for cat in defaults:
                db.add(Category(id=str(uuid4()), name=cat))
            db.commit()
    finally:
        db.close()
    yield

app = FastAPI(
    title="AI Financer Backend API",
    description="Local server API powering authentication, transaction storage, budgets, goals, and financial advisor flows.",
    version="1.0.0",
    lifespan=lifespan
)

# CORS middleware for mobile and web clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(finance.router)
app.include_router(ocr.router)
app.include_router(advisor.router)
app.include_router(ml.router)
app.include_router(insights.router)

@app.get("/")
def read_root():
    return {
        "status": "online",
        "message": "Welcome to AI Financer API. Go to /docs for interactive Swagger API docs."
    }

@app.get("/health")
def health_check():
    return {"status": "healthy"}

