import os
import joblib
from typing import List, Tuple
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.naive_bayes import MultinomialNB
from sklearn.pipeline import Pipeline
from sklearn.model_selection import train_test_split

MODEL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "data")
MODEL_PATH = os.path.join(MODEL_DIR, "category_model.pkl")

# Rule-based categorization keywords dictionary mapping
RULE_KEYWORDS = {
    "Food & dining": ["mcdonald", "starbuck", "burger", "pizza", "kfc", "restaurant", "food", "cafe", "baker", "swiggy", "zomato", "dining", "canteen"],
    "Travel": ["uber", "ola", "taxi", "metro", "railway", "irctc", "flight", "indigo", "travel", "auto", "cab", "petrol", "diesel", "fuel"],
    "Shopping": ["amazon", "flipkart", "zara", "myntra", "shopping", "clothes", "store", "mall", "grocery", "mart", "supermarket"],
    "Education": ["school", "college", "university", "tutor", "book", "course", "udemy", "education", "fees", "coaching"],
    "Bills": ["electricity", "power", "water", "gas", "recharge", "airtel", "jio", "bill", "utility", "postpaid", "broadband"],
    "Entertainment": ["netflix", "prime", "spotify", "cinema", "theater", "movie", "game", "steam", "playstation", "entertainment", "club", "pub"],
    "Health": ["hospital", "doctor", "pharmacy", "medical", "clinic", "health", "medicine", "diagnostic", "gym", "fitness"],
    "Salary": ["salary", "paycheck", "wages", "employer", "stipend"],
    "Freelance": ["freelance", "upwork", "fiverr", "project", "consulting"]
}

def rule_based_categorize(merchant: str) -> str:
    merchant_lower = merchant.lower()
    for category, keywords in RULE_KEYWORDS.items():
        if any(keyword in merchant_lower for keyword in keywords):
            return category
    return "Other"

def predict_category(merchant: str) -> Tuple[str, float]:
    """
    Predict the financial category of a merchant.
    Returns a tuple containing the predicted category string and the confidence score (float).
    """
    if not merchant or not merchant.strip():
        return "Other", 1.0

    if not os.path.exists(MODEL_PATH):
        # Fall back to robust rule-based match if model not trained
        return rule_based_categorize(merchant), 1.0

    try:
        pipeline = joblib.load(MODEL_PATH)
        predictions = pipeline.predict([merchant])
        probabilities = pipeline.predict_proba([merchant])
        
        predicted_category = predictions[0]
        max_prob = float(max(probabilities[0]))
        return predicted_category, max_prob
    except Exception:
        # Fallback to rule-based on any load failure
        return rule_based_categorize(merchant), 1.0

def train_model(dataset: List[Tuple[str, str]]) -> Tuple[float, int]:
    """
    Train a new MultinomialNB + TF-IDF classification pipeline from a dataset.
    dataset: List of (merchant_name, category) tuples.
    Returns: Tuple of (accuracy score, samples count).
    """
    if len(dataset) < 5:
        # Insufficient data to train split, just train on what is there and save
        X = [item[0] for item in dataset]
        y = [item[1] for item in dataset]
        
        pipeline = Pipeline([
            ('vectorizer', TfidfVectorizer(ngram_range=(1, 2), analyzer='char_wb')),
            ('classifier', MultinomialNB(alpha=0.1))
        ])
        pipeline.fit(X, y)
        
        os.makedirs(MODEL_DIR, exist_ok=True)
        joblib.dump(pipeline, MODEL_PATH)
        return 1.0, len(dataset)

    X = [item[0] for item in dataset]
    y = [item[1] for item in dataset]

    # Split dataset for model accuracy evaluation
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    pipeline = Pipeline([
        ('vectorizer', TfidfVectorizer(ngram_range=(1, 2), analyzer='char_wb')),
        ('classifier', MultinomialNB(alpha=0.1))
    ])
    pipeline.fit(X_train, y_train)

    accuracy = float(pipeline.score(X_test, y_test))

    # Fit again on all data before saving to use the full dataset
    pipeline.fit(X, y)
    os.makedirs(MODEL_DIR, exist_ok=True)
    joblib.dump(pipeline, MODEL_PATH)

    return accuracy, len(dataset)
