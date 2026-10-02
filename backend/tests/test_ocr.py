from app.routers.ocr import parse_ocr_text

def test_parse_ocr_food_receipt():
    sample_text = """
    STARBUCKS COFFEE
    Store #12345
    Date: 2026-08-25
    1x Caffe Latte - 180.00
    1x Chocolate Muffin - 120.00
    TOTAL: 300.00
    Cash Paid: 500.00
    Change: 200.00
    Thank you!
    """
    result = parse_ocr_text(sample_text)
    assert result["amount"] == 300.0
    assert "Starbucks" in result["merchant"] or "STARBUCKS" in result["merchant"]
    assert result["category"] == "Food & dining"

def test_parse_ocr_travel_receipt():
    sample_text = """
    UBER RIDE INVOICE
    Trip ID: 9876543
    Fare: 150.00
    Tolls: 0.00
    Total Paid: 150.00
    Payment: Credit Card
    """
    result = parse_ocr_text(sample_text)
    assert result["amount"] == 150.0
    assert "Uber" in result["merchant"] or "UBER" in result["merchant"]
    assert result["category"] == "Travel"
