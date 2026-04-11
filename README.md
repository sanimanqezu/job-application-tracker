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

**Backend**

```bash
cd backend

# Start PostgreSQL (or update application.yml to point to your instance)
docker run -d \
  --name jobtracker-postgres \
  -e POSTGRES_DB=jobtracker \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine

mvn spring-boot:run
# API available at http://localhost:8080
```

**Frontend**

```bash
cd frontend
cp .env.local.example .env.local
npm install
npm run dev
# App available at http://localhost:3000
```

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

All endpoints except `/api/auth/*` require a `Authorization: Bearer <token>` header.

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

### Backend

| Variable         | Default                                    | Description              |
|------------------|--------------------------------------------|--------------------------|
| DB_URL           | jdbc:postgresql://localhost:5432/jobtracker| PostgreSQL JDBC URL      |
| DB_USERNAME      | postgres                                   | Database username        |
| DB_PASSWORD      | postgres                                   | Database password        |
| JWT_SECRET       | (required — change in production)          | JWT signing secret (256+ bits) |
| JWT_EXPIRATION_MS| 86400000                                   | Token TTL (default 24h)  |

### Frontend

| Variable              | Default                  | Description         |
|-----------------------|--------------------------|---------------------|
| NEXT_PUBLIC_API_URL   | http://localhost:8080    | Backend base URL    |
