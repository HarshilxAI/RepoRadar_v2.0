# RepoRadar v2.0 — Project Handoff

## 1. Project overview

RepoRadar is a GitHub repository analysis application with AI-generated repository insights.

- Active workspace: `E:\RepoRadar v2.0`
- Original project: `https://github.com/HarshilxAI/RepoRadar`
- V2 repository: `https://github.com/HarshilxAI/RepoRadar_v2.0`
- Original live application: `https://repo-radar-23.vercel.app/`

**Important:** RepoRadar v2.0 is a separate working copy. Do not modify the original project. Do not change Git history, remotes, commit, or push without explicit approval.

## 2. Technology stack

- Frontend: React, Vite, JavaScript, Tailwind CSS.
- Backend: Java, Spring Boot 3.5.0, Maven.
- Repository data: GitHub REST API.
- AI provider: OpenRouter chat completions API.
- Current AI model: `nvidia/nemotron-3.5-lightning:free`.
- Frontend development server: Vite, usually port 5173.
- Backend development server: Spring Boot, port 8080.
- Intended deployment: Vercel frontend and Render backend.

## 3. Main architecture

Frontend API service:
`frontend/src/services/api.js`

AI Insights UI:
`frontend/src/pages/DashboardPage.jsx`

Backend controller:
`backend/src/main/java/com/reporadar/controller/AiInsightsController.java`

AI service:
`backend/src/main/java/com/reporadar/service/GemmaInsightsService.java`

Repository analysis:
`backend/src/main/java/com/reporadar/service/RepositoryAnalysisService.java`

Global exception handling:
`backend/src/main/java/com/reporadar/exception/GlobalExceptionHandler.java`

Configuration:
`backend/src/main/resources/application.properties`

## 4. Current local setup

Frontend `.env`:

`frontend/.env`

Current setting:

`VITE_API_BASE_URL=http://localhost:8080`

Backend secrets are stored locally in:

`backend/.env`

Expected variable names:

- `GITHUB_TOKEN`
- `OPENROUTER_API_KEY`

The backend startup script is:

`backend/start-backend.ps1`

Normal startup:

Terminal 1:
```powershell
cd "E:\RepoRadar v2.0\backend"
.\start-backend.ps1
```

Terminal 2:
```powershell
cd "E:\RepoRadar v2.0\frontend"
npm run dev
```

The startup script loads credentials into the PowerShell environment before launching Spring Boot. Do not print, expose, or commit secrets. Verify `.gitignore` excludes `backend/.env` and all actual secret files.

## 5. Working features

- Public GitHub repository analysis works.
- GitHub authentication was verified successfully.
- OpenRouter authentication was independently verified successfully.
- AI Insights has successfully generated output for at least some repositories.
- The AI service has diagnostic logging and a bounded corrective retry for malformed model output.

Do not rewrite these working features unnecessarily.

## 6. AI Insights issues encountered

### Issue A: OpenRouter authentication

Previously, AI Insights returned HTTP 401.

Root cause: The Java backend was not using the valid OpenRouter credential from the PowerShell session.

Resolution: A valid `OPENROUTER_API_KEY` was loaded before starting the backend. AI generation subsequently worked.

### Issue B: Malformed AI JSON

Some repositories caused:

`AI_OUTPUT_JSON_PARSE_ERROR`

Jackson reported:

`JsonParseException: Unexpected character ('}'): was expecting a colon to separate field name and value`

The provider response was not valid JSON. Other repositories succeeded.

Current service code includes one corrective retry. The code comment indicates the selected free endpoint cannot enforce `response_format`. A request was later observed to succeed after a previous malformed-output failure.

Do not implement the corrective retry again without inspecting the current source.

### Issue C: Frontend timeout

The frontend has shown:

`timeout of 120000ms exceeded`

This means the frontend request exceeded 120 seconds. Retrying the same repository sometimes resulted in another timeout, while other requests succeeded.

The most recent known state: AI Insights can succeed for some repositories but still times out for others.

The backend log has previously shown:

`finishReason=stop correctiveAttempt=false`

along with `AI_OUTPUT_JSON_PARSE_ERROR`.

This is evidence of malformed initial model output, but it does not establish the cause of every timeout.

## 7. Immediate next task

**Prioritize the remaining AI Insights timeout. Do not restart the entire integration.**

Inspect these files first:

1. `frontend/src/services/api.js`
2. `backend/src/main/java/com/reporadar/service/GemmaInsightsService.java`
3. `backend/src/main/resources/application.properties`

Investigate:

- The frontend's current request timeout.
- The backend's HTTP read timeout.
- The time consumed by initial generation and the corrective retry.
- Whether retry requests have independent bounded timeouts.
- OpenRouter response latency and `finish_reason`.
- Whether an actual provider timeout or malformed response is causing the delay.

The previous proposed direction was to consider a frontend timeout of up to 180 seconds, but no increase should be assumed to fix the underlying problem. Choose coordinated, bounded timeouts based on the actual code.

Do not automatically increase the timeout to 6–10 minutes. Avoid uncontrolled repeated calls to the free provider.

## 8. Rules for the new Codex account

1. Read this handoff and inspect the actual source before editing.
2. Treat the current source code as authoritative if it differs from this document.
3. Do not reimplement completed functionality.
4. Conserve usage: make one focused diagnosis and a minimal, targeted fix.
5. Preserve working repository analysis, AI authentication, the existing DTO contract, model configuration, and frontend design unless evidence requires a change.
6. Never expose or log API keys, full prompts, or repository contents.
7. Run the existing Maven build and relevant tests after changes.
8. Test both a previously successful repository and one that timed out, if feasible.
9. Report exact files changed, root cause, tests, and remaining limitations.
10. Do not claim success without evidence from an actual request.
11. Do not commit, push, rewrite history, change remotes, or modify the original RepoRadar project without explicit approval.

## 9. Deployment plan

- Deploy Spring Boot backend to Render.
- Configure `GITHUB_TOKEN`, `OPENROUTER_API_KEY`, and the production CORS origin in Render's environment settings.
- Deploy the React/Vite frontend to Vercel.
- Configure `VITE_API_BASE_URL` to the deployed Render backend URL in Vercel.
- Never put secret API keys into frontend environment variables or Git.

## 10. Current status summary

Repository analysis: Working.

GitHub credentials: Verified.

OpenRouter credentials: Verified in the correct local environment.

AI Insights: Partially working; malformed output can be recovered through a corrective retry, but intermittent 120-second frontend timeouts remain unresolved.

Next priority: Diagnose and fix AI Insights timeouts without destabilizing the working implementation.
