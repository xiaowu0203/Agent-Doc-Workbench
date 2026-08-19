# Agent-Doc-Workbench

> An open-source, lightweight web workbench for AI-agent-powered document collaboration, built for individuals and small teams.
> Documents as the single collaboration vehicle for AI Agent tasks.

[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE) ![Status](https://img.shields.io/badge/Status-Planning-orange)

English | [简体中文](./README.md)

**Repository**
- Gitee: https://gitee.com/wu_hai123/agent-doc-workbench
- GitHub: https://github.com/xiaowu0203/Agent-Doc-Workbench
- Branches: main (stable) · phase-0 (Phase 0 foundation, under development)

---

## Why this project?

Existing AI document tools (Notion AI, WPS AI) let AI rewrite documents directly — uncontrolled and untraceable.
Orchestration frameworks (LangGraph, CrewAI) keep task results in memory only, with nothing persisted.
Agent-Doc-Workbench treats **documents** as the collaboration carrier for Agent tasks, so every AI change is reviewable, revertible, and auditable.

## Core Features

- **Draft / Formal dual-document mode**: Agents may freely edit drafts for fast experimentation; formal documents are write-protected from Agents, preventing content tampering
- **Diff-based change approval**: all Agent modifications become structured change requests supporting accept-all / partial accept / reject / comment-and-return; merging automatically creates a version snapshot
- **Token budget circuit breaker**: per-task budgets plus a workspace-wide budget; automatic shutdown when limits are exceeded, keeping Agent costs under control
- **Native MCP protocol**: compatible with any external MCP server, local or remote Agents, with no model lock-in
- **Agent permission control**: workspace + document scope + tool whitelist; Agents never inherit user permissions
- **End-to-end audit log**: operator (human/Agent), operation type, linked task — immutable and traceable
- **Version snapshots & rollback**: every merged change generates a version; one-click rollback to any historical version

## UI Mockups

| Login | Workspace Home | Diff Review (Core) |
| --- | --- | --- |
| ![01-login](docs/ui-mockups/01-login.png) | ![02-workspace](docs/ui-mockups/02-workspace.png) | ![06-diff-review](docs/ui-mockups/06-diff-review.png) |

All 8 screens: [docs/ui-mockups/README.md](docs/ui-mockups/README.md).

## Tech Stack

| Layer | Choice |
| --- | --- |
| Backend | Spring Boot 3.5 · Java 21 · Spring Cloud 2025 · MyBatis-Plus · MySQL 5.7 |
| Messaging/Cache | RabbitMQ · Redis 7 · Redisson |
| Storage | MinIO (object storage) |
| Registry/Config | Nacos 3.2.2 |
| Agent integration | Spring AI MCP Client + official MCP Java SDK (custom business orchestration) |
| Frontend | Vue 3 · TypeScript · Vite · Pinia · Element Plus · ProseMirror |
| Auth | Spring Authorization Server · OAuth2 · JWT (RS256) |

Details and rationale: [docs/tech/README.md](docs/tech/README.md).

## Architecture Overview

```
Frontend (Vue 3 + ProseMirror)
   │  OAuth2 / JWT
   ▼
Gateway (Spring Cloud Gateway · WebFlux)
   │
   ├── auth-service       Users, OAuth2, JWT
   ├── document-service   Spaces, directories, documents, versions, Diff approval
   ├── task-service       Agent tasks, Token budgets, RabbitMQ consumers
   └── (agent / audit merged initially)
         │
         ▼
   External MCP Agents (read document fragments, submit changes via MCP)
```

## Getting Started

> ⚠️ Currently in the **planning/skeleton phase**; code is not implemented yet. The following is the v0.1 startup plan.

```bash
# 1. Start infrastructure (MySQL / Redis / RabbitMQ / MinIO / Nacos)
docker compose up -d

# 2. Start backend (Maven multi-module)
cd backend
./mvnw spring-boot:run -pl auth-service

# 3. Start frontend
cd frontend
pnpm install
pnpm dev
```

Environment variable templates: `.env.example` (MCP credentials, JWT keys and other secrets must go through environment variables — never commit them).

## Development Roadmap

| Phase | Scope | Status |
| --- | --- | --- |
| Phase 0 | Engineering foundation: Git, Docker Compose, frontend/backend scaffolding | Planned |
| Phase 1 | Backend foundation: common, auth, gateway | Planned |
| Phase 2 | Document core: spaces/documents/versions/Diff | Planned |
| Phase 3 | Agents & tasks: MCP integration, Token circuit breaker | Planned |
| Phase 4 | Frontend: 8 core pages | Planned |
| Phase 5 | Integration & testing of the full loop | Planned |
| Phase 6 | Open-source release preparation | Planned |

See [docs/development-plan.md](docs/development-plan.md).

## Documentation

| Doc | Description |
| --- | --- |
| [docs/Agent-Doc-Workbench 项目完整开发规划文档.md](docs/Agent-Doc-Workbench%20项目完整开发规划文档.md) | Product planning: feature list, MVP scope, milestones (Chinese) |
| [docs/development-plan.md](docs/development-plan.md) | Development roadmap (Phase 0-6) |
| [docs/tech/README.md](docs/tech/README.md) | Finalized tech stack: backend, frontend, auth |
| [docs/ui-mockups/README.md](docs/ui-mockups/README.md) | UI mockups for all v0.1 pages |
| [CLAUDE.md](CLAUDE.md) | Project memory & collaboration conventions (incl. ADRs) |

## Open Source Plans

- License: Apache-2.0
- Goal: v0.1 should be clone-and-run; welcome individual developers and small teams to try and contribute
- CONTRIBUTING and security notes will be completed before the Phase 6 release

## License

Licensed under the [Apache-2.0](LICENSE) License.
