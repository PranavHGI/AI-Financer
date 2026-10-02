# 💰 AI Financer

**AI Financer** is an AI-powered personal finance management application designed to help users track, manage, and understand their finances through a modern Android application and a FastAPI backend.

The application combines **personal finance tracking, transaction management, financial goals, budgets, analytics, OCR-based transaction extraction, SMS transaction detection, and an AI financial advisor** into a single platform.

---

## 🚀 Features

### 📊 Personal Finance Management

* Track income and expenses
* Add and manage transactions
* Categorize financial transactions
* View financial summaries
* Monitor spending patterns

### 🤖 AI Financial Advisor

* AI-powered financial assistance
* Ask questions about personal finances
* Get financial insights and suggestions
* Conversation-based financial guidance

### 📱 SMS Transaction Detection

* Detect financial transactions from SMS messages
* Extract transaction information automatically
* Import detected transactions into the application
* Background SMS processing

### 🧾 OCR Receipt/Document Processing

* Extract transaction information from images
* OCR-based text extraction using Tesseract
* Convert extracted information into structured transaction data

### 🎯 Financial Goals

* Create financial goals
* Track goal progress
* Monitor savings targets
* Manage multiple goals

### 💳 Budget Management

* Create budgets
* Track spending against budgets
* Monitor financial limits

### 📈 Financial Analytics

* Analyze income and expenses
* Identify spending patterns
* Generate financial insights
* Machine-learning-based financial analysis

### 🔐 Authentication & Security

* User registration and login
* Authentication tokens
* Secure API communication
* Local token management
* User consent management

---

## 🏗️ Architecture

The project follows a **client-server architecture**:

```text
                    ┌─────────────────────┐
                    │   Android Client    │
                    │                     │
                    │   Kotlin + Compose  │
                    └──────────┬──────────┘
                               │
                               │ REST API
                               ▼
                    ┌─────────────────────┐
                    │    FastAPI Backend  │
                    │                     │
                    │ Authentication      │
                    │ Transactions        │
                    │ Budgets             │
                    │ Goals               │
                    │ Analytics           │
                    │ OCR                 │
                    │ AI Advisor          │
                    └──────────┬──────────┘
                               │
                    ┌──────────┴──────────┐
                    ▼                     ▼
             ┌─────────────┐       ┌─────────────┐
             │ PostgreSQL  │       │ ML / AI     │
             │  Database   │       │ Components  │
             └─────────────┘       └─────────────┘
```

---

## 🛠️ Technology Stack

### Android Application

* **Kotlin**
* **Jetpack Compose**
* Android SDK
* Gradle
* Material Design
* ViewModel
* Navigation
* Repository pattern

### Backend

* **Python**
* **FastAPI**
* **SQLAlchemy**
* **PostgreSQL**
* **Pydantic**
* **psycopg2**
* JWT-based authentication
* Tesseract OCR

### AI / Machine Learning

* Python
* Machine Learning models
* Financial analytics
* Transaction classification
* AI financial advisor

### DevOps

* Docker
* Docker Compose
* Git
* GitHub

---

## 📁 Project Structure

```text
AI-Financer/
│
├── app/
│   └── src/
│       ├── androidTest/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/example/aifinancerfree/
│       │   │       ├── data/
│       │   │       │   ├── local/
│       │   │       │   ├── model/
│       │   │       │   ├── network/
│       │   │       │   ├── repository/
│       │   │       │   └── sms/
│       │   │       │
│       │   │       ├── ui/
│       │   │       │   ├── navigation/
│       │   │       │   ├── screens/
│       │   │       │   ├── theme/
│       │   │       │   └── viewmodel/
│       │   │       │
│       │   │       └── MainActivity.kt
│       │   │
│       │   └── res/
│       │
│       └── test/
│
├── backend/
│   ├── app/
│   │   ├── ml/
│   │   ├── models/
│   │   ├── routers/
│   │   ├── schemas/
│   │   ├── config.py
│   │   ├── database.py
│   │   ├── main.py
│   │   └── security.py
│   │
│   ├── tests/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── requirements.txt
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── .gitignore
└── README.md
```

---

## ⚙️ Requirements

Before running the project, install:

### Android

* Android Studio
* Android SDK
* JDK
* Android device or emulator

### Backend

* Python 3.10+
* Docker Desktop
* PostgreSQL / Supabase database

### Recommended

* Git
* GitHub account
  
---

# 🔐 Security

AI Financer handles financial information, so security is an important part of the project.

Security considerations include:

* JWT authentication
* Password protection
* Secure API communication
* Environment-based secrets
* Database access control
* User consent management
* Local token management
* Input validation
* Authentication and authorization

---

# 📸 Application Modules

The application includes:

* Welcome / onboarding
* User registration
* Login
* Dashboard
* Transactions
* Add Transaction
* Budgets
* Financial Goals
* AI Advisor
* Financial Insights
* Profile
* SMS transaction synchronization
* OCR transaction extraction

---

# 🎯 Project Objectives

The main objectives of AI Financer are to:

1. Simplify personal finance management.
2. Automate transaction recording.
3. Provide useful financial insights.
4. Help users track financial goals.
5. Reduce manual transaction entry.
6. Provide AI-assisted financial guidance.
7. Combine mobile finance management with backend analytics.

---

# 🔮 Future Improvements

Potential future improvements include:

* Advanced AI financial recommendations
* Improved spending prediction
* Investment portfolio tracking
* Financial forecasting
* Expense anomaly detection
* Personalized financial planning
* Multi-language support
* Cloud-based analytics
* Improved OCR accuracy
* Advanced financial dashboards
* Notification-based financial alerts

---

# 📄 License

This project is currently intended for educational and development purposes.

Add an appropriate open-source license if you decide to distribute the project under one.

---

# 👨‍💻 Author

**Pranav Agalgave**

B.Tech Computer Engineering

GitHub: [PranavHGI](https://github.com/PranavHGI)

---

## ⭐ Support

If you find this project useful, consider giving the repository a ⭐ on GitHub.

---

**AI Financer — Smarter personal finance management with AI.**
