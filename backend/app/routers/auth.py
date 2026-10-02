from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from uuid import uuid4
from datetime import timedelta
from typing import Annotated

from app.database import get_db
from app.models.user import User
from app.schemas.user import UserCreate, UserResponse, Token, RefreshTokenRequest
from app.security import hash_password, verify_password, create_access_token, verify_token
from app.models.session import Session as UserSession
from datetime import datetime, timedelta

router = APIRouter(prefix="/auth", tags=["Authentication"])

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="auth/token")

def get_current_user(token: Annotated[str, Depends(oauth2_scheme)], db: Session = Depends(get_db)) -> User:
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    payload = verify_token(token)
    if payload is None:
        raise credentials_exception
    username: str = payload.get("sub")
    if username is None:
        raise credentials_exception
    user = db.query(User).filter(User.username == username).first()
    if user is None:
        raise credentials_exception
    return user

@router.post("/register", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
def register(user_in: UserCreate, db: Session = Depends(get_db)):
    existing_user = db.query(User).filter(User.username == user_in.username).first()
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Username already registered"
        )
    existing_email = db.query(User).filter(User.email == user_in.email).first()
    if existing_email:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already registered"
        )
    
    new_user = User(
        id=str(uuid4()),
        username=user_in.username,
        email=user_in.email,
        hashed_password=hash_password(user_in.password)
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    return new_user

@router.post("/token", response_model=Token)
def login(form_data: Annotated[OAuth2PasswordRequestForm, Depends()], db: Session = Depends(get_db)):
    user = db.query(User).filter(User.username == form_data.username).first()
    if not user or not verify_password(user.hashed_password, form_data.password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    access_token = create_access_token(data={"sub": user.username})
    refresh_token = str(uuid4())
    
    db_sess = UserSession(
        id=str(uuid4()),
        user_id=user.id,
        refresh_token=refresh_token,
        expires_at=datetime.utcnow() + timedelta(days=7),
        is_revoked=False
    )
    db.add(db_sess)
    db.commit()
    
    return {
        "access_token": access_token,
        "token_type": "bearer",
        "refresh_token": refresh_token
    }

@router.post("/refresh", response_model=Token)
def refresh(refresh_in: RefreshTokenRequest, db: Session = Depends(get_db)):
    db_session = db.query(UserSession).filter(
        UserSession.refresh_token == refresh_in.refresh_token,
        UserSession.is_revoked == False
    ).first()
    
    if not db_session:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or revoked refresh token"
        )
        
    if db_session.expires_at < datetime.utcnow():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Expired refresh token"
        )
        
    db_session.is_revoked = True
    db.commit()
    
    user = db.query(User).filter(User.id == db_session.user_id).first()
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found"
        )
        
    new_access_token = create_access_token(data={"sub": user.username})
    new_refresh_token = str(uuid4())
    
    new_db_session = UserSession(
        id=str(uuid4()),
        user_id=user.id,
        refresh_token=new_refresh_token,
        expires_at=datetime.utcnow() + timedelta(days=7),
        is_revoked=False
    )
    db.add(new_db_session)
    db.commit()
    
    return {
        "access_token": new_access_token,
        "token_type": "bearer",
        "refresh_token": new_refresh_token
    }

@router.post("/logout", status_code=status.HTTP_200_OK)
def logout(refresh_in: RefreshTokenRequest, db: Session = Depends(get_db)):
    db_session = db.query(UserSession).filter(
        UserSession.refresh_token == refresh_in.refresh_token
    ).first()
    
    if db_session:
        db_session.is_revoked = True
        db.commit()
        
    return {"message": "Successfully logged out"}

@router.get("/me", response_model=UserResponse)
def read_users_me(current_user: Annotated[User, Depends(get_current_user)]):
    return current_user

