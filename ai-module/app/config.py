import os
from dotenv import load_dotenv

load_dotenv()

# Server
SERVER_HOST = os.getenv("SERVER_HOST", "0.0.0.0")
SERVER_PORT = int(os.getenv("SERVER_PORT", 8000))

# Backend URL (for testing/integration)
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")

# Timezone
TIMEZONE = "Asia/Seoul"
