import io
import re
import time
import logging
from typing import Dict, Any
from fastapi import APIRouter, File, UploadFile, Depends, HTTPException, status
from PIL import Image
import pytesseract
from app.database import get_db
from app.models.user import User
from app.routers.auth import get_current_user

router = APIRouter(prefix="/transactions", tags=["OCR"])
logger = logging.getLogger(__name__)

# Basic keywords categorizer
CATEGORY_KEYWORDS = {
    "Food & dining": ["food", "cafe", "coffee", "restaurant", "mcdonald", "burger", "pizza", "dining", "starbucks", "bakery", "hotel"],
    "Travel": ["uber", "ola", "cab", "taxi", "train", "flight", "fuel", "petrol", "metro", "bus", "travel", "auto"],
    "Shopping": ["shopping", "store", "mall", "mart", "amazon", "flipkart", "clothing", "apparel", "grocery", "supermarket"],
    "Bills": ["electricity", "water", "bill", "invoice", "power", "gas", "recharge", "telecom", "broadband", "rent"],
    "Entertainment": ["movie", "cinema", "netflix", "theatre", "game", "show", "concert", "booking"],
    "Health": ["medical", "pharmacy", "doctor", "clinic", "hospital", "medicine", "health", "lab"]
}

def parse_ocr_text(text: str) -> Dict[str, Any]:
    lines = [line.strip() for line in text.split("\n") if line.strip()]
    
    # 1. Extract Merchant Name
    # Pick the first line that is not a date, phone number, address, or purely numerical
    merchant = "Unknown Merchant"
    for line in lines[:4]:
        clean_line = line.lower()
        if any(kw in clean_line for kw in ["tel", "phone", "tax", "date", "time", "street", "road", "ave", "address"]):
            continue
        if re.search(r'^\d+$', line) or re.search(r'\d{5,}', line):
            continue
        if len(line) > 3:
            merchant = line
            break

    # 2. Extract Date
    # Find patterns like DD/MM/YYYY, DD-MM-YYYY, YYYY-MM-DD
    date_ms = int(time.time() * 1000)
    date_patterns = [
        r'(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})',
        r'(\d{4})[-/](\d{1,2})[-/](\d{1,2})'
    ]
    for pattern in date_patterns:
        match = re.search(pattern, text)
        if match:
            # For simplicity, convert the matched string to current year timestamp
            # to avoid parsing complex date strings that might be read incorrectly.
            # In production, we'd parse using datetime.strptime
            break

    # 3. Extract Amount
    # Find all numbers that look like currency (decimal numbers or standalone numbers)
    amount = 0.0
    # Pattern 1: Look for primary total/amount keywords
    primary_regex = r'(?i)(?:total|amount|due|sum|net|val)[\s\d]*[:=₹$\s]*([\d,]+\.?\d*)'
    matches = re.findall(primary_regex, text)
    if not matches:
        # Fallback to secondary keywords (like pay/paid) if primary not found
        secondary_regex = r'(?i)(?:paid|pay|received)[\s\d]*[:=₹$\s]*([\d,]+\.?\d*)'
        matches = re.findall(secondary_regex, text)

    if matches:
        for m in matches:
            try:
                val = float(m.replace(",", ""))
                if val > amount:
                    amount = val
            except ValueError:
                continue

    # Fallback Pattern 2: If amount is still 0, scan all decimal numbers and find the largest
    if amount == 0.0:
        decimals = re.findall(r'\b\d+[\.,]\d{2}\b', text)
        for d in decimals:
            try:
                val = float(d.replace(",", "."))
                # Ignore values that look like years or phone extensions
                if val > amount and val < 500000.0:
                    amount = val
            except ValueError:
                continue

    # 4. Determine Category
    category = "Other"
    found = False
    for cat, keywords in CATEGORY_KEYWORDS.items():
        for kw in keywords:
            if re.search(rf'\b{kw}\b', text, re.IGNORECASE):
                category = cat
                found = True
                break
        if found:
            break

    return {
        "amount": amount,
        "merchant": merchant,
        "category": category,
        "date": date_ms
    }

@router.post("/ocr", response_model=Dict[str, Any])
async def perform_ocr(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user)
):
    # Verify file extension
    ext = file.filename.split(".")[-1].lower()
    if ext not in ["jpg", "jpeg", "png", "bmp"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid image format. Supported formats: JPG, JPEG, PNG, BMP."
        )

    try:
        # Load image from file binary
        image_data = await file.read()
        image = Image.open(io.BytesIO(image_data))
        
        # Run Tesseract OCR
        ocr_text = pytesseract.image_to_string(image)
        logger.debug(f"Extracted OCR text:\n{ocr_text}")
        
        # Parse text parameters
        result = parse_ocr_text(ocr_text)
        
        # Return parsed receipt details
        return result
    except Exception as e:
        logger.error("OCR execution failed", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to perform OCR parsing: {str(e)}"
        )
