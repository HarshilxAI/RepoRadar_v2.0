# Deployment Notes

## Render backend

1. Push the project to GitHub and create a Render account.
2. Create a **Web Service** from the repository, or use the root `render.yaml` Blueprint.
3. If configuring manually, use `backend` as the root directory, Java 25, build command `./mvnw clean package -DskipTests`, and start command `java -Dserver.port=$PORT -jar target/reporadar-api-1.0.0.jar`.
4. Set `GITHUB_API_URL=https://api.github.com`, `GITHUB_TOKEN` (recommended), and `CORS_ALLOWED_ORIGINS` to the final Vercel origin exactly, for example `https://reporadar.vercel.app`.
5. Set health check path to `/health` and verify it reports `UP`.

Render provides `PORT`; the application consumes it automatically. Keep `GITHUB_TOKEN` secret and never place it in frontend variables.

## Vercel frontend

1. Import the same GitHub repository into Vercel.
2. Select `frontend` as the root directory; Vercel detects Vite.
3. Set `VITE_API_BASE_URL` to the HTTPS Render API URL, for example `https://reporadar-api.onrender.com`.
4. Deploy using `npm run build`. The included `vercel.json` keeps client routes working on refresh.
5. Open the deployment, analyze a public repository, and confirm the PDF export works.

If the Render URL changes, update `VITE_API_BASE_URL` in Vercel, update backend `CORS_ALLOWED_ORIGINS` if the frontend origin changed, then redeploy the frontend. No source-code change is required.
