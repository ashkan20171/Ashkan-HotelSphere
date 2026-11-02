# Ashkan HotelSphere

### Enterprise Hotel Management & Hospitality Platform

**Ashkan HotelSphere** is a modular hotel management platform designed to demonstrate enterprise-level software engineering practices, backend architecture, financial workflows, guest management, and hospitality operations.

Built with **Java 17**, the project combines a desktop-oriented hotel management experience with a service-oriented backend architecture and a growing set of business capabilities.

---

## Project Highlights

- **Modular Architecture** – Separation of business logic, persistence, and application services.
- **Microservices-Oriented Design** – Independent service boundaries for selected business domains.
- **Hotel Operations** – Booking, guest, front-desk, and operational management capabilities.
- **Financial Management** – Payment workflows, refunds, financial reporting, and audit records.
- **Double-Entry Accounting** – Journal entries, trial balances, income statements, and profit-centre reporting.
- **Guest Portal** – Guest-facing booking and account workflows.
- **Security** – JWT-based authorization and signed payment webhook validation.
- **Data Persistence** – SQLite and PostgreSQL integrations across different application components.
- **Messaging & Infrastructure** – Redis, RabbitMQ, and Docker Compose integration.
- **Internationalization** – Persian (RTL) and English (LTR) interface support.

## Technology Stack

| Area | Technologies |
|---|---|
| Language | Java 17 |
| Desktop UI | Java Swing |
| Database | SQLite, PostgreSQL |
| Database Migrations | Flyway |
| Connection Pooling | HikariCP |
| Authentication | JWT |
| Messaging | RabbitMQ |
| Caching | Redis |
| API | HTTP / REST-style services |
| Containerization | Docker Compose |
| Testing | Java Smoke Tests |

## Architecture & Engineering

The project focuses on maintainable software design and practical engineering principles:

- Separation of concerns
- Domain-oriented service boundaries
- Repository pattern
- Transactional database operations
- Input validation and business-rule enforcement
- Secure authentication and authorization
- Financial auditability
- Currency-aware accounting
- Error handling and resilience
- Incremental and test-driven development practices

## Financial & Accounting Capabilities

The financial module includes:

- Payment capture and refund workflows
- Financial expense management
- Revenue and expense reporting
- Currency-separated financial analytics
- Signed webhook verification
- Financial audit records
- Double-entry journal posting
- Trial balance reporting
- Income statement generation
- Profit-centre profitability analysis

**Important:** The accounting module is an engineering implementation and is not presented as certified accounting, tax, or regulatory software.

## Project Structure

The solution is organized around application services, infrastructure, persistence, migrations, and automated smoke tests.

```text
AshkanHotelSphere/
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   │       └── db/
│   │           └── migration/
│   └── test/
│       └── java/
├── infra/
│   └── docker-compose.yml
├── pom.xml
└── README.md
```

## Getting Started

### Requirements

- JDK 17 or later
- Maven (for Maven-based builds)
- Docker Desktop (for containerized infrastructure, where required)

### Build

```bash
mvn clean package
```

For a direct Java compilation:

```bash
javac --release 17 -encoding UTF-8 \
  -d out \
  $(find src/main/java src/test/java -name '*.java')
```

### Run Tests

The repository includes standalone Java smoke tests for validating important application services, business rules, and schema expectations.

Use the project's documented test instructions and ensure the required database infrastructure is available for integration testing.

## Current Development Status

Ashkan HotelSphere is developed incrementally, with a focus on expanding its business capabilities, improving architecture, strengthening validation, and increasing test coverage.

Some infrastructure and integration scenarios require external services such as PostgreSQL, Redis, RabbitMQ, and Docker.

## Roadmap

- Deeper integration between hotel operations and accounting
- Automated financial journal generation
- Advanced financial reconciliation
- Accounting period management
- Improved operational analytics
- Expanded integration and end-to-end testing
- Further security and observability improvements

## Localization

The application is designed to support:

- Persian – Right-to-Left (RTL)
- English – Left-to-Right (LTR)

## Author

**Ashkan Motaei**

Software Engineer | Java | Backend Development | Software Architecture

GitHub: [ashkan20171](https://github.com/ashkan20171)

---

*Ashkan HotelSphere – Engineering a smarter hospitality management experience.*
