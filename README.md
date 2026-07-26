# Job Application Tracker

A full-stack web application for tracking job applications throughout your job search. Built with Spring Boot and Next.js.

## Features

- **JWT Authentication** — Register and log in securely; all data is scoped to your account
- **Kanban Board** — Visual pipeline view with columns for APPLIED, PHONE_SCREEN, INTERVIEW, TECHNICAL_TEST, OFFER, and REJECTED
- **Dashboard** — Stats cards showing total applications, active applications, this month's count, and a full status breakdown with progress bars
- **Applications Table** — Sortable table with color-coded status badges and quick inline status updates
- **Interview Tracking** — Schedule interviews per application (PHONE, VIDEO, ON_SITE, TECHNICAL) with notes and completion status
- **Full CRUD** — Create, view, edit, and delete applications; add and remove interviews
- **Status Quick-Patch** — Change application status from any view without opening the full edit form
- **Discover Jobs (live scanner)** — Fetches openings straight from ~265 SA companies' & recruiters' applicant-tracking systems (Greenhouse, Lever, Recruitee, Workable, SmartRecruiters, Ashby, Breezy), stores them in Postgres, and shows a searchable table of **only the links you haven't applied to yet**. Clicking **Apply** opens the company's page and records the application (so it leaves "new" and appears on your board). A **deep probe** mode auto-discovers each company's ATS by guessing tokens.

## Tech Stack

| Layer     | Technology                                      |
|-----------|-------------------------------------------------|
| Backend   | Java 17, Spring Boot 3.2.5, Spring Security, JJWT 0.12.3 |
| Database  | PostgreSQL 16 (H2 for tests)                    |
| Migrations| Flyway                                          |
| Frontend  | Next.js 15 (App Router), React 19, TypeScript   |
| Styling   | Tailwind CSS 4                                  |
| State     | Zustand                                         |
| HTTP      | Axios                                           |

## Getting Started

### Option 1 — Docker Compose (recommended)

```bash
git clone <repo-url>
cd job-application-tracker
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Database: localhost:5432 (user: postgres / pass: postgres)

### Option 2 — Local Development

**Backend** — no manual database setup needed:

```bash
cd backend
mvn spring-boot:run
# API available at http://localhost:8080
```

On startup the backend **automatically starts its own Postgres container** (and
stops it again when you stop the backend), so a fresh clone just runs. The only
prerequisite is Docker Desktop — if it's missing, the app prints install steps, or
run `./install-docker.ps1` (Windows) once. To manage Postgres yourself instead,
set `JOBTRACKER_AUTO_DB=false`.

**Frontend**

```bash
cd frontend
cp .env.local.example .env.local
npm install
npm run dev
# App available at http://localhost:3000
```

## 🔒 Configuration & Secrets

**This repository contains no secrets.** It is deliberately designed so that anyone
can clone it and run it on their own machine — without needing, or ever seeing, my
API keys.

- **Runs with zero secrets.** Every credential has a safe local-development default,
  and the optional Adzuna job feed simply stays off until it's configured. A plain
  `git clone` → `docker compose up` just works.
- **Real secrets live only in gitignored files.** Values go in `backend/secrets.properties`
  and/or a root `.env` — both gitignored. The repo only ever tracks the `*.example`
  templates, which hold placeholders, never real values.
- **Nothing is hardcoded.** `application.yml` and `docker-compose.yml` reference every
  secret as `${ENV_VAR}` with a local default; real values are injected from the
  environment or the gitignored files at runtime.
- **Bring your own keys.** Optional integrations use *your* free key, not mine.

### Enabling the optional Adzuna job feed

```bash
# 1. Copy the template — the real file is gitignored
cp backend/secrets.properties.example backend/secrets.properties

# 2. Get a free key (no card) at https://developer.adzuna.com/ and paste it in:
#    ADZUNA_APP_ID=your-id
#    ADZUNA_APP_KEY=your-key

# 3. Restart the backend. Done — the app now pulls live SA jobs.
```

For Docker Compose, copy `.env.example` → `.env` instead; the same values flow in automatically.

> **Why not just hide the keys inside the repo?** You can't — anyone who can *run*
> code that uses a key can *extract* it. The professional standard (12-factor config)
> is exactly what's implemented here: no secrets in source control, each user supplies
> their own, and the application runs fine without them.

## API Reference

| Method | Endpoint                              | Description                        |
|--------|---------------------------------------|------------------------------------|
| POST   | /api/auth/register                    | Register a new user                |
| POST   | /api/auth/login                       | Login and receive JWT token        |
| GET    | /api/applications                     | List all applications (filter ?status=) |
| POST   | /api/applications                     | Create a new application           |
| GET    | /api/applications/{id}                | Get application details            |
| PUT    | /api/applications/{id}                | Update an application              |
| DELETE | /api/applications/{id}                | Delete an application              |
| PATCH  | /api/applications/{id}/status         | Quick status update                |
| GET    | /api/applications/{id}/interviews     | List interviews for application    |
| POST   | /api/applications/{id}/interviews     | Add interview to application       |
| DELETE | /api/interviews/{id}                  | Delete an interview                |
| GET    | /api/dashboard                        | Get dashboard stats                |
| GET    | /api/discover                         | New openings you haven't applied to (`?onlyNew`, `?q`, `?junior`, `?segment`) |
| POST   | /api/discover/scan                    | Re-run the live ATS scan (`?probe=true` for the deep sweep) |
| POST   | /api/discover/{id}/apply              | Record an application from a scanned job (drops it from "new", adds to board) |
| GET    | /api/discover/companies               | The bundled ~265-company / recruiter directory |
| GET    | /api/discover/stats                   | Counts: new-for-you / total in DB / companies tracked |

All endpoints except `/api/auth/*` require a `Authorization: Bearer <token>` header.

### Discover data flow

`companies.json` (bundled, ~265 SA employers + recruiters) → `JobScanService` fetches each one's ATS board via `AtsClient` → filters to SA + software roles → upserts into the `scanned_jobs` table (deduped on URL). The `GET /api/discover` query then subtracts any URL already in your `job_applications`, so you only ever see links you haven't applied to.

## Running Tests

```bash
cd backend
mvn test
```

The test suite includes 8 unit tests for `JobApplicationService` using Mockito and an H2 in-memory database.

## Project Structure

```
job-application-tracker/
├── backend/
│   ├── src/main/java/com/sanele/jobtracker/
│   │   ├── entity/         # JPA entities + enums
│   │   ├── repository/     # Spring Data repositories
│   │   ├── dto/            # Request/Response records
│   │   ├── service/        # Business logic
│   │   ├── controller/     # REST controllers
│   │   ├── security/       # JWT + Spring Security config
│   │   └── exception/      # Global error handling
│   ├── src/main/resources/
│   │   └── db/migration/   # Flyway SQL migrations
│   └── Dockerfile
├── frontend/
│   └── src/
│       ├── app/            # Next.js App Router pages
│       │   ├── dashboard/
│       │   ├── applications/
│       │   ├── board/      # Kanban board
│       │   ├── login/
│       │   └── register/
│       ├── components/     # Shared UI components
│       ├── lib/            # Axios API client
│       ├── store/          # Zustand state
│       └── types/          # TypeScript types
└── docker-compose.yml
```

## Environment Variables

Set these via a gitignored `backend/secrets.properties` (loaded automatically),
a root `.env` (for Docker Compose), or your shell environment. **All have working
local defaults — none are required to run the app locally.**

### Backend

| Variable         | Default                                    | Secret? | Description              |
|------------------|--------------------------------------------|:-------:|--------------------------|
| DB_URL           | jdbc:postgresql://localhost:5433/jobtracker|         | PostgreSQL JDBC URL      |
| DB_USERNAME      | postgres                                   |         | Database username        |
| DB_PASSWORD      | postgres                                   |   🔑    | Database password        |
| JWT_SECRET       | local-dev default (override in prod)       |   🔑    | JWT signing secret (256+ bits) |
| JWT_EXPIRATION_MS| 86400000                                   |         | Token TTL (default 24h)  |
| ADZUNA_APP_ID    | *(empty — feed off)*                       |   🔑    | Adzuna app id — free at developer.adzuna.com |
| ADZUNA_APP_KEY   | *(empty — feed off)*                       |   🔑    | Adzuna app key           |

🔑 = keep out of source control. Put these in `backend/secrets.properties` (gitignored),
never in `application.yml`.

### Frontend

| Variable              | Default                  | Description         |
|-----------------------|--------------------------|---------------------|
| NEXT_PUBLIC_API_URL   | http://localhost:8080    | Backend base URL    |
