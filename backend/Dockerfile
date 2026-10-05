FROM python:3.10-slim

# Set working directory inside the container
WORKDIR /app

# Install basic system build dependencies and Tesseract OCR
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    tesseract-ocr \
    tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

# Copy and install python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the backend files
COPY . .

# Set default port (Render will override PORT with its assigned port, e.g. 10000)
ENV PORT=8000
EXPOSE 8000

# Start the uvicorn server with dynamic port support
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
