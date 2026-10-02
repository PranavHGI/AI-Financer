# AI Financer - Installation and Quick-Start Guide

Welcome to the **AI Financer** project! This guide provides step-by-step instructions to set up, install, and run the local backend server (via Docker) and the Jetpack Compose Android application.

---

## Prerequisites

Ensure the following are installed and running on your laptop:
1. **[Docker Desktop](https://www.docker.com/products/docker-desktop/)** (to run the backend server).
2. **Android Studio** (Koala or newer recommended, to run the mobile app).
3. **Java Development Kit (JDK 17)** (usually bundled inside Android Studio).
4. A physical **Android phone** connected to the laptop via USB with **USB Debugging** enabled (required to test SMS auto-tracking).
5. Both your laptop and phone must be connected to the **same Wi-Fi network**.

---

## Part 1: Running the Backend Server (Via Docker)

Docker runs the server in an isolated container with zero manual setup. You do not need to install Python or packages manually.

1. Open **Docker Desktop** on your laptop.
2. Open a **Command Prompt (cmd)** or PowerShell window, and navigate to the `backend` folder:
   ```cmd
   cd backend
   ```
3. Launch the server by running:
   ```cmd
   docker compose up --build
   ```
   *(Keep this terminal window open. The server will download dependencies, map the database, and start listening on `http://localhost:8000`.)*
   
> [!TIP]
> The database will automatically persist inside a folder named `data` in your local `backend` directory. You can open `backend/data/aifinancer.db` with any SQLite viewer (like DB Browser) to inspect tables directly.

---

## Part 2: Allowing Network Access (Windows Firewall Rule)

To allow the phone to talk to your laptop's backend over local Wi-Fi, you must open port 8000:

1. Click your laptop's **Start Menu**, search for **PowerShell**, right-click it, and select **Run as Administrator**.
2. Copy, paste, and run this command:
   ```powershell
   New-NetFirewallRule -DisplayName "FastAPI Dev" -Direction Inbound -LocalPort 8000 -Protocol TCP -Action Allow
   ```
3. Close the Administrator PowerShell window.

---

## Part 3: Setting up the Android Mobile Application

1. Open **Android Studio** on your laptop.
2. Click **Open** and select the root project directory (`AIFinancerFree`).
3. Wait for the project sync to finish.
4. **Link the app to your laptop's Wi-Fi IP address:**
   * Open a command prompt and run `ipconfig`.
   * Find your active Wi-Fi connection's **IPv4 Address** (e.g., `192.168.1.15`).
   * In Android Studio, open the file:
     `app/src/main/java/com/example/aifinancerfree/data/network/ApiClient.kt`
   * Replace `192.168.8.82` in the `BASE_URL` line with **your laptop's Wi-Fi IP address** and save:
     ```kotlin
     private val BASE_URL = "http://YOUR_LAPTOP_IP:8000/"
     ```
5. Connect your physical phone via USB.
6. Click the green **Run** (Play) button in Android Studio to install the app on your phone.

---

## Part 4: Step-by-Step E2E Guide to Demo the App to Your Teacher

Once the app is running on your phone, demonstrate these core features to show project progress:

### **1. Registration & Login Flow**
* On the app's Welcome Screen, tap **Register**.
* Enter a Username (e.g., `Yash`), Email, and Password. Tap **Register**.
* Look at your laptop's Docker terminal window. You will see logs printing in real-time (`POST /auth/register`, `POST /auth/token`, `GET /auth/me`), verifying successful communication!

### **2. Add Transaction Manually**
* Go to the **Transactions** tab -> tap **+** (Add) in the top-right corner.
* Enter an amount (e.g., `₹450`), choose **Expense**, select **Food**, type **"McDonald's"** as the merchant, select **Cash** as the payment method, and tap **Save Transaction**.
* Check the transaction history list and the dashboard. The Available Balance and Monthly Spent progress bar will instantly update!

### **3. Setting Category Budget Limits**
* Go to the **Goals** tab (which displays Budgets).
* Tap the edit pencil icon next to the **Food** category.
* Change the limit to `₹1000` and tap **Save**.
* Notice the spent progress bar updates. If you spend more than ₹1000 on Food, a red **"Overdraft Warning"** banner will display, showing how much you exceeded the budget.

### **4. SMS Automated Tracking (The Core Feature)**
* Go to **Profile** -> **SMS Ingestion & Privacy**.
* Tap **Set as Default SMS App** (or toggle Enable Ingestion) and click **Yes** to make AI Financer your default SMS app.
* Go back to the **Dashboard** and tap **"Scan Historical SMS"**.
* Click **Allow** when the system asks for **SMS Read Permission**.
* The app will securely scan your inbox, parse transaction alerts locally on your device, register transaction logs, and automatically update your dashboard without typing anything!
