# Northgate Toll Plaza

Full-stack toll plaza portfolio application with **Java / Spring Boot**, **Angular**, **PostgreSQL** and **MongoDB**.

Operators use a lane console to record vehicle passes and handle exceptions. Managers use a dashboard to review plaza activity. The backend separates transactional toll processing from audit observations.

## What to review

- **Backend:** two Spring Boot services, JWT authentication, PostgreSQL transactions and MongoDB audit events.
- **Frontend:** Angular operator and manager workflows.
- **Design decision:** toll processing is transactional; audit delivery is best effort. The backend guide explains this trade-off.

## Documentation

| Guide | Contents |
| --- | --- |
| [Backend README](northgate-backend/README.md) | Setup, services, databases, configuration and tests |
| [Frontend README](northgate-frontend/README.md) | Angular setup, screens and client checks |
| [Architecture](ARCHITECTURE.md) | Service boundaries, data model and design decisions |

## Run locally

```bash
git clone https://github.com/full-stack-dev-johncastrosanabria/NorthgateTollPlaza.git
cd NorthgateTollPlaza
cp .env.example .env
```

Configure the environment and local PostgreSQL/MongoDB services using the backend guide, then start the stack with:

```bash
bash run.sh
```

The documented local ports are Angular `4200`, toll-service `8080` and audit-service `8081`. The launcher expects local database tools; use the component guides for prerequisites.

## Verification

```bash
bash test.sh
```

The test script runs backend and frontend checks and requires the documented database services. This README describes the existing scripts; it does not publish a new test-run result.
