# 설치 문서

아무것도 없는 PC에서 시작해 **메모 → 회의록 초안 → 확정**까지 돌리는 방법이다. 명령은 Windows PowerShell 기준이고, macOS/Linux가 다른 곳만 따로 적었다.

> **검증 범위 (2026-11-23, Windows 11, 빈 폴더에 새로 clone)**
> - 실제로 해 봄: 새 가상환경에 `requirements.txt` 설치, AI 모듈 테스트 555개, 백엔드 단위 테스트, 프론트 설치·빌드, `VITE_API_BASE_URL` 적용, DB 생성, 회의록 엔진을 가짜 메모로 호출 (`gemma4:e4b`, `qwen2.5:3b`, Ollama 꺼짐 3가지)
> - **아직 못 해 봄: 세 서버를 함께 띄운 실행과 브라우저 흐름(5~7장의 실행·확인 단계)**, Google 연결(8장), macOS/Linux, 다른 PC에서 접속(6장 끝)
>
> 5~7장을 처음 따라 하는 사람은 막힌 곳을 이 문서에 고쳐 주세요.

## 1. 준비물

| 프로그램 | 버전 | 비고 |
| --- | --- | --- |
| Git | 아무 최신 버전 | |
| JDK | **17** | 백엔드가 17로 고정되어 있고 자동 다운로드 설정이 없다. 다른 버전이 있어도 17은 따로 설치한다 |
| Node.js | **22** 권장 (최소 20.19 또는 22.12) | CI가 22 |
| Python | **3.13** | CI가 3.13 |
| MySQL | **8.0** | 8.0.21에서 확인 |
| Ollama | 최신 | 0.40.17에서 확인 |

**메모리와 디스크**

| 모델 | 용도 | 메모리 |
| --- | --- | --- |
| `qwen2.5:7b` | 메일 분석·할 일 등 기본 | 약 4.7GB |
| `gemma4:e4b` | 회의록 (D-029) | 약 6.9GB |
| `qwen2.5:3b` | 메모리가 부족할 때 대신 사용 | 약 2.0GB |

- 두 모델이 동시에 올라가면 약 11.6GB를 쓴다. **RAM 16GB 이상**을 권장하고, 그보다 적으면 4장의 "메모리가 부족하면"을 따른다.
- 디스크는 **15GB 이상** 비워 둔다. 모델 약 11.3GB, Python 패키지 약 1.1GB(다운로드 약 240MB, torch 포함)에 Gradle·npm 캐시가 더해진다.

## 2. 코드 받기

**경로를 짧게** 둔다. 긴 폴더에 받으면 `pip install`이 torch의 긴 파일 경로에서 실패한다 (Windows 260자 제한).

```powershell
mkdir C:\dev
git clone https://github.com/CSID-DGU/2026-1-CECD1-5-CZC-02.git C:\dev\salesmap-agent
cd C:\dev\salesmap-agent
```

아래 명령은 모두 이 폴더를 기준으로 한다. 서버마다 **PowerShell 창을 따로** 연다 (AI 모듈, 백엔드, 프론트 3개).

## 3. MySQL

DB를 만든다. 테이블은 백엔드가 처음 뜰 때 자동으로 만든다 (`ddl-auto=update`).

```powershell
mysql -u root -p
```

```sql
CREATE DATABASE IF NOT EXISTS salesmap
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
EXIT;
```

`mysql` 명령을 못 찾으면 `& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p`로 실행한다.

**이미 쓰던 DB만** 아래 한 줄이 필요하다. 새로 만든 DB에는 실행하지 않는다. `ddl-auto=update`가 NOT NULL을 풀어 주지 않아서 수정 기록 저장이 실패하기 때문이다.

```sql
ALTER TABLE analysis_revisions MODIFY analysis_id BIGINT NULL;
```

## 4. Ollama와 AI 모듈 (포트 8000)

**모델 받기** (처음 한 번, 약 11GB)

```powershell
ollama pull qwen2.5:7b
ollama pull gemma4:e4b
ollama list
```

**설치**

```powershell
cd C:\dev\salesmap-agent\ai-module
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
notepad .env
```

macOS/Linux는 `python3 -m venv .venv`, `.venv/bin/python -m pip install -r requirements.txt`, `cp .env.example .env`.

메모장에서 한 줄만 바꾸고 저장한다.

```text
AI_ENGINE=ollama
```

> **`AI_ENGINE=rule`의 뜻:** 이 값은 **메일 분석(`/analyze`)에만** 영향을 준다. `rule`이면 메일 분석이 규칙만 쓴다. **회의록(`/minutes`)은 이 값과 상관없이 항상 Ollama를 부른다** (`AI_ENGINE=rule`로 실행해 `gemma4:e4b`가 호출되는 것을 확인했다). 그래서 `rule`로 둬도 회의록은 나오지만, 메일 분석까지 LLM을 쓰려면 `ollama`로 바꾼다.

**메모리가 부족하면** `ollama pull qwen2.5:3b`를 받고 `.env`를 이렇게 고친다. 회의록 줄은 값을 비워 둔다.

```text
OLLAMA_MODEL=qwen2.5:3b
OLLAMA_MODEL_MINUTES=
```

**실행**

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8000
```

브라우저에서 <http://localhost:8000/health>가 열리면 된다. `OPENAI_API_KEY`는 비워 둔다 (비어 있으면 유료 API를 부르지 않는다).

## 5. 백엔드 (포트 8080)

새 PowerShell 창에서 실행한다. 여기서 넣은 환경변수는 **이 창을 닫으면 사라지므로** 다시 띄울 때 또 넣는다.

```powershell
cd C:\dev\salesmap-agent\backend

# DB 비밀번호 (화면에 보이지 않게 입력)
$p = Read-Host "MySQL 비밀번호" -AsSecureString
$env:SPRING_DATASOURCE_PASSWORD = [Net.NetworkCredential]::new('', $p).Password

# JWT 서명 키 (임의 값 생성)
$b = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
$env:JWT_SECRET = [Convert]::ToBase64String($b)

# 실제 AI 모듈 호출, Google 없이 실행
$env:AI_MODULE_MODE = "http"
$env:GOOGLE_CALENDAR_ENABLED = "false"

.\gradlew.bat bootRun
```

macOS/Linux는 `export 이름=값`으로 넣고 `./gradlew bootRun`을 실행한다.

브라우저에서 <http://localhost:8080/api/health>가 응답하면 된다.

| 변수 | 기본값 | 꼭 알아야 할 점 |
| --- | --- | --- |
| `AI_MODULE_MODE` | `mock` | **기본값은 가짜 응답이다.** 실제 AI를 쓰려면 반드시 `http` |
| `JWT_SECRET` | 저장소에 공개된 개발용 값 | 시범 운영에서는 반드시 직접 만든 값을 쓴다. 키가 바뀌면 다시 로그인해야 하므로 계속 쓸 PC에서는 값을 안전한 곳에 보관한다. **커밋 금지** |
| `SPRING_DATASOURCE_URL` | `salesmap` DB | 다른 DB 이름을 쓸 때만 넣는다 |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `root` / 빈 값 | |
| `AI_MODULE_BASE_URL` | `http://localhost:8000` | AI 모듈이 다른 곳에 있을 때만 |
| `AI_MODULE_READ_TIMEOUT_MS` | `330000` (5분 30초) | **AI 모듈의 `OLLAMA_TIMEOUT_SECONDS`(300초)보다 길게** 둔다. 그래야 LLM이 늦을 때 AI 모듈이 먼저 빈 초안을 돌려준다 |
| `CORS_ALLOWED_ORIGINS` | `localhost`·`127.0.0.1`의 5173, 5174 | 프론트를 다른 주소·포트로 열면 여기에 넣는다. 값을 넣으면 기본값을 **대체**하므로 필요한 주소를 모두 적는다 |
| `GOOGLE_CALENDAR_ENABLED` | `true` | Google 없이 쓰면 `false` |

## 6. 프론트엔드 (포트 5174)

새 PowerShell 창에서 실행한다. 저장소 맨 위가 아니라 **`frontend` 폴더 안에서** 설치한다.

```powershell
cd C:\dev\salesmap-agent\frontend
npm ci
npm run dev -- --port 5174 --strictPort
```

<http://localhost:5174>를 연다. CORS 기본값이 5173과 5174만 허용하므로 **다른 포트로 띄우면 로그인이 실패한다.**

**백엔드가 다른 PC에 있으면** `frontend/.env.local` 파일을 만들고 (커밋되지 않음) 프론트를 다시 띄운다.

```text
VITE_API_BASE_URL=http://백엔드주소:8080
```

비워 두면 `http://localhost:8080`을 쓴다. 이때 백엔드의 `CORS_ALLOWED_ORIGINS`에 브라우저가 여는 프론트 주소를 넣어야 한다.

## 7. 잘 됐는지 확인

가짜 데이터만 쓴다.

1. <http://localhost:5174>에서 **계정이 없으신가요? 회원가입**을 눌러 가짜 이메일(예: `install-check@example.test`)로 가입한다.
2. <http://localhost:5174/meeting-notes>를 연다.
3. 만난 사람에 `이가상`을 쓰고 Enter, 메모에 아래를 붙여 넣는다.

   ```text
   예시전자 이가상 과장과 미팅. 신규 장비 도입 일정을 논의했다.
   고객 요청: 견적서를 보내 달라고 함.
   [내가 할 일] 다음 주 금요일까지 견적서 전달.
   ```

4. **AI로 정리하기**를 누른다. CPU만 쓰면 1~4분 걸린다 (이 예시는 약 50초).
5. **확인하고 확정하기** 화면에 원문·회의록·할 일이 보이면 초안 생성 성공이다.
6. 할 일을 하나씩 확정하거나 취소한 뒤 회의록을 **확정하기**로 확정한다.

메모와 상관없는 내용이 나오면 `AI_MODULE_MODE`가 `mock`인 상태다.

## 8. Google 연결 (선택)

**Google 없이도 메모 → 회의록 → 할 일 확정은 동작한다.** `GOOGLE_CALENDAR_ENABLED=false`이면 Google 캘린더를 호출하지 않는다. Gmail·캘린더를 쓸 때만 아래를 설정하고, 테스트에는 실제 고객 메일이 없는 계정을 쓴다.

1. Google Cloud Console에서 프로젝트를 만들고 **Gmail API**와 **Google Calendar API**를 켠다.
2. OAuth 동의 화면을 설정하고, 테스트 중이면 쓸 계정을 테스트 사용자로 추가한다.
3. OAuth 클라이언트(웹 애플리케이션)를 만들고 **승인된 리디렉션 URI**를 등록한다. 포트 5174로 띄운다면:

   ```text
   http://localhost:5174/settings/gmail/callback
   ```

4. 백엔드를 띄우기 전에 넣는다. 값은 커밋하지 않는다.

   ```powershell
   $env:GMAIL_CLIENT_ID = "발급받은 클라이언트 ID"
   $env:GMAIL_CLIENT_SECRET = "발급받은 시크릿"
   $env:GMAIL_REDIRECT_URI = "http://localhost:5174/settings/gmail/callback"
   $env:GOOGLE_CALENDAR_ENABLED = "true"
   ```

> `GMAIL_REDIRECT_URI`의 기본값은 **5173**이다. 5174로 띄우면서 이 값을 넣지 않으면 Google 로그인 뒤 돌아올 주소가 맞지 않는다.

권한 범위와 Salesmap 쪽 설정은 [`backend/README.md`](../../backend/README.md)를 본다.

## 9. 새 회사에 도입할 때

메일 계정, 캘린더, CRM, 회의록 양식, 할 일 부류 등 회사별 설정은 [`scope.md` 4장](scope.md#4-새-회사에-도입할-때-설정할-것)을 본다.

## 10. 문제 해결

"확인"은 이 문서를 만들 때 실제로 겪었으면 **재현**, 코드와 설정을 읽고 적었으면 **코드 확인**이다.

| 증상 | 원인 | 해결 | 확인 |
| --- | --- | --- | --- |
| `pip install`이 `OSError: No such file or directory`, `Long Path` 안내로 실패 | 폴더 경로가 길어 torch 파일이 260자를 넘음 | `C:\dev\salesmap-agent`처럼 짧은 경로에 다시 clone | 재현 |
| 회의록 초안이 비어 있고 실패 이유가 `Ollama request failed` | Ollama가 꺼져 있음 | Ollama를 켜고 `ollama list` 확인 후 다시 생성 | 재현 |
| `.env`를 고쳤는데 값이 기본값으로 동작 | `.env`가 비었거나 다른 폴더에 있음 | `ai-module\.env`를 메모장으로 열어 내용 확인. 스크립트로 고치지 말고 메모장으로 고친다 | 재현 |
| 메모와 상관없는 회의록이 나옴 | `AI_MODULE_MODE`가 기본값 `mock` | `$env:AI_MODULE_MODE = "http"` 후 백엔드 재시작 | 코드 확인 |
| 회원가입·로그인이 CORS 오류로 실패 | 프론트 포트가 5173·5174가 아님 | `--port 5174 --strictPort`로 띄우거나 `CORS_ALLOWED_ORIGINS` 설정 | 코드 확인 |
| 5174 포트가 이미 사용 중 | 다른 프로그램이 사용 | 그 프로그램을 끄거나 5173으로 실행 (Google 리디렉션 주소도 함께 맞춤) | 코드 확인 |
| 백엔드가 DB 접속 오류로 시작하지 않음 | 비밀번호·DB 이름이 틀렸거나 MySQL이 꺼짐 | 3장의 DB 생성과 `SPRING_DATASOURCE_PASSWORD` 확인 | 코드 확인 |
| 백엔드 빌드가 Java 17을 찾지 못함 | JDK 17 미설치 | JDK 17 설치 후 새 창에서 다시 실행 | 코드 확인 |
| 기존 DB에서 수정 기록 저장 실패 | `analysis_id`가 NOT NULL로 남음 | 3장의 `ALTER TABLE` 실행 (기존 DB만) | 코드 확인 |
| 회의록 생성이 5분 넘게 걸리고 빈 초안이 나옴 | 메모리 부족·CPU 처리 지연 | 다른 프로그램을 닫거나 4장의 `qwen2.5:3b` 사용 | 코드 확인 |
| Google 연결 뒤 돌아오는 화면에서 오류 | 리디렉션 URI 불일치 | Console 등록값과 `GMAIL_REDIRECT_URI`, 실제 프론트 포트를 똑같이 맞춤 | 코드 확인 |
