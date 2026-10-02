import numpy as np
from typing import List
from sklearn.linear_model import LinearRegression
from sklearn.ensemble import IsolationForest

def predict_next_month_expense(monthly_totals: List[float]) -> float:
    """
    Fits a linear trend line on historical monthly expenses to predict next month's spending.
    monthly_totals: list of expense sums sorted chronologically.
    """
    if not monthly_totals:
        return 0.0
    if len(monthly_totals) < 2:
        return float(monthly_totals[0])

    # Convert months indices to format [[1], [2]...]
    X = np.arange(len(monthly_totals)).reshape(-1, 1)
    y = np.array(monthly_totals)

    try:
        model = LinearRegression()
        model.fit(X, y)
        next_month_idx = np.array([[len(monthly_totals)]])
        prediction = float(model.predict(next_month_idx)[0])
        return max(0.0, prediction)
    except Exception:
        # Fallback to standard average
        return float(np.mean(monthly_totals))

def detect_anomalies(amounts: List[float]) -> List[int]:
    """
    Uses Isolation Forest to detect outlier spending amounts.
    amounts: list of transaction amounts.
    Returns: list of flags where 1 is normal, -1 is anomaly.
    """
    if len(amounts) < 5:
        # Not enough samples to construct an Isolation Forest
        return [1] * len(amounts)

    try:
        X = np.array(amounts).reshape(-1, 1)
        # contamination sets percentage of expected anomalies (e.g. 10%)
        clf = IsolationForest(contamination=0.1, random_state=42)
        predictions = clf.fit_predict(X)
        return predictions.tolist()
    except Exception:
        # Fallback to no anomalies on model exception
        return [1] * len(amounts)
