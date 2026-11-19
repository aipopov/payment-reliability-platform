# Payment Reliability Platform

Backend-платформа для оркестрации массовых выплат, управления финансовыми операциями и восстановления платежей при сбоях внешних платёжных провайдеров.

## Overview

Payment Reliability Platform — тренировочный проект, моделирующий backend-архитектуру платёжной системы с использованием микросервисного подхода.

Основная задача платформы — обеспечить надёжную обработку платежей, согласованность финансовых операций и контролируемое восстановление после сбоев.

Система позволяет:
- Принимать и обрабатывать массовые платёжные поручения.
- Управлять жизненным циклом платежей.
- Резервировать средства и выполнять финансовые проводки.
- Маршрутизировать платежи через внешних провайдеров.
- Обрабатывать повторные запросы и предотвращать дублирование операций.
- Выявлять расхождения между внутренними и внешними статусами.
- Восстанавливать платежи с неопределённым результатом.
- Вести аудит финансовых операций и решений системы.

## Architecture

Проект построен на основе четырёх независимо развёртываемых микросервисов.

| Service | Responsibility |
|---|---|
| Payment Service | Приём платежей, управление жизненным циклом, оркестрация операций |
| Ledger Service | Управление счетами, резервирование средств, двойная бухгалтерская запись |
| Provider Service | Интеграция с платёжными провайдерами и обработка их ответов |
| Reconciliation Service | Сверка операций, выявление расхождений и управление восстановлением |

Каждый сервис имеет собственную базу данных и отвечает за собственную бизнес-область.

Взаимодействие между сервисами осуществляется через REST API и Apache Kafka.

## Technology Stack

**Backend**
- Java 25
- Spring Boot
- Spring Security
- Spring Data JPA / Hibernate
- Maven

**Data & Messaging**
- PostgreSQL
- Apache Kafka
- Redis
- Flyway

**Security**
- Keycloak
- HashiCorp Vault
- JWT
- Mutual TLS (mTLS)
- Role-Based Access Control (RBAC)

**Infrastructure & Observability**
- Docker
- Kubernetes
- Prometheus
- Grafana
- OpenTelemetry

## Engineering Concepts
- Microservices Architecture
- Domain-Driven Design (DDD)
- Saga Pattern
- Transactional Outbox / Inbox
- Idempotent Message Processing
- Double-Entry Ledger
- Distributed Transaction Management
- Fault Tolerance and Recovery
- Reconciliation
- Centralized Secrets Management
- Distributed Tracing and Monitoring