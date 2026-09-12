# Agent-Doc-Workbench

> **Let agents work autonomously --- without letting them silently
> change your canonical content.**

An open-source web workbench for practical Agent engineering.\
Agents can read documents, load versioned Skills, call built-in or
external MCP tools, and execute tasks. Changes to formal documents are
never applied silently: they become reviewable Change Requests first,
and humans decide what gets merged.

**Autonomous execution · Reviewable changes · Scoped permissions ·
Controlled cost · Traceable runs**

**English** \| [简体中文](./README.md)

[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-21-orange.svg) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg) ![Vue](https://img.shields.io/badge/Vue-3-42b883.svg) ![A2A](https://img.shields.io/badge/A2A-1.0-blueviolet.svg) ![MCP](https://img.shields.io/badge/MCP-enabled-6f42c1.svg)

**Mirrors:** [GitHub](https://github.com/xiaowu0203/Agent-Doc-Workbench)
· [Gitee](https://gitee.com/wu_hai123/agent-doc-workbench)

## At a Glance

![Space Overview](docs/ui-mockups/01-space-overview.png)

Many Agent demos ask one question:

> **Can the Agent finish the task?**

Agent-Doc-Workbench also asks:

> What exactly did the Agent do?\
> Why was it allowed to do it?\
> Which Agent, prompt, model, Skill, and MCP configuration did it use?\
> Which tools were called, and how many tokens were spent?\
> Who approves changes to canonical content?\
> Can a historical execution still be explained and traced?

The project explores how to move Agents beyond "chatbots that can call
tools" toward **governed engineering execution units**.

## AI Can Propose Changes. It Cannot Silently Rewrite Formal Documents.

![Diff Review](docs/ui-mockups/08-diff-review.png)

Documents have two modes:

- **Draft documents** --- Agents may edit directly for fast iteration.
- **Formal documents** --- Agents cannot overwrite them directly.
    Every modification becomes a Change Request.

The formal-document flow is intentionally explicit:

``` text
Agent Execution
      │
      ▼
ChangeRequest / Structured Diff
      │
      ▼
Human Review
      │
      ├── Accept All
      ├── Partial Accept
      ├── Edit & Accept
      ├── Reject
      └── Comment & Return
      │
      ▼
New Document Version
```

Merges and rollbacks create new versions instead of destroying
historical snapshots.

## An Agent Run Is More Than a Chat Transcript

![Task Execution](docs/ui-mockups/07-task-execution.png)

In v0.1, users explicitly choose one Agent for each task. When execution
starts, the system freezes the runtime configuration used by that run,
including:

- Agent configuration version
- System prompt
- Model and model parameters
- Bound and selected Skills
- Effective tool definitions
- MCP configuration and tool allowlists
- Task Capability
- Token budget

Later configuration changes do not mutate an already-running execution.

The execution view records Skill routing, instruction loading, tool
calls, token usage, status, and key execution events so failures, cost,
and capability sources can be investigated.

## Core Capabilities

### 🧠 Let Agents Work

- **Independent Agent Runtime** --- tasks are dispatched to Agent
    Server over A2A.
- **Versioned Skills** --- Skills are immutable, versioned capability
    packages with publishing and Agent bindings.
- **Progressive Skill Loading** --- models first see lightweight Skill
    metadata and load full instructions/resources only when needed.
- **Skill Router** --- supports `ALL_BOUND` and `ROUTER` selection
    modes.
- **Built-in + External MCP** --- every Agent has Workbench MCP and
    may bind multiple space-scoped external MCP servers.

### 🔐 Keep Agents Bounded

- **Task Capability** --- every task scopes allowed Space, documents,
    and actions.
- **Agents do not inherit user permissions** --- human RBAC and Agent
    Capability are separate authorization boundaries.
- **Layered tool restrictions** --- Skills, Agent configuration, MCP
    bindings, and Task Capability constrain effective tools.
- **Token Budget Circuit Breaker** --- task-level and space-level
    budgets stop runaway executions.
- **External MCP security** --- tool namespaces, allowlists, encrypted
    credentials, HTTPS and network-address validation.

### 📝 Review What They Change

- **Draft / Formal document model**
- **Structured ChangeRequest / Diff**
- **Accept all / partial accept / edit & accept / reject / comment &
    return**
- **Version snapshots and non-destructive rollback**
- **Traceable links between task, execution, review, and document
    version**

### 🔎 Know Exactly What Happened

- **Execution snapshots**
- **Skill / MCP / tool provenance**
- **Token and usage ledger**
- **Redacted tool-call auditing**
- **Human / Agent actor auditing**
- **Platform roles + Space RBAC**

![Usage & Audit](docs/ui-mockups/09-usage-audit.png)

## Agent / Skill / MCP

An Agent is not just a prompt. It is a governed collection of executable
capabilities.

![Agent Management](docs/ui-mockups/03-agent-management-card.png)

A configured Agent may include:

``` text
Agent
├── Main Model
├── System Prompt
├── Skill Selection Mode
├── Versioned Skills
├── Built-in Workbench MCP
├── External MCP Servers
├── Tool Whitelist
└── Execution Limits
```

Skills and MCP solve different problems:

- **Skill** --- describes how the Agent should perform a class of
    work, with versioned instructions, resources, and allowed tool
    scope.
- **MCP** --- exposes actual Workbench or external capabilities the
    Agent can invoke.
| Skills | MCP Servers |
| --- | --- |
| ![Skill Management](docs/ui-mockups/04-skill-management-card.png) | ![MCP Management](docs/ui-mockups/05-mcp-management-card.png) |
See the complete [UI gallery](docs/ui-mockups/README.md).

## A2A and MCP Have Deliberately Different Roles

Agent-Doc-Workbench does not treat A2A and MCP as interchangeable RPC
mechanisms.

``` text
                         A2A
┌──────────────┐  ─────────────────►  ┌───────────────┐
│ task-service │                      │ agent-service │
│ Orchestration│  ◄─────────────────  │ Agent Runtime │
└──────────────┘   Task / Callback    └───────┬───────┘
                                             │
                                             │ MCP
                                             ▼
                                    ┌──────────────────┐
                                    │ Workbench Tools  │
                                    │ External MCPs    │
                                    └──────────────────┘
```

**A2A moves the work. MCP gives Agents capabilities.**

- **A2A** is the Agent task protocol for send, query, cancel, status
    synchronization, and callbacks.
- **MCP** is the capability protocol for reading documents and Skills,
    submitting changes, and accessing external tools.

`agent-service` does not directly own or access Workbench
document/task/change-request tables. It uses Workbench capabilities
through MCP.

## Permission Model

Humans and Agents intentionally use different authorization models:

``` text
Human User
    │
    ▼
Space RBAC
OWNER / EDITOR / VIEWER / Custom Role

Agent Execution
    │
    ▼
Task Capability
Space + Document + Actions + Budget
```

An Agent does not automatically receive all permissions of the user on
whose behalf it runs.

Effective tool access is constrained by multiple layers:

``` text
Effective Tools =
Skill Allowed Tools
∩ Agent Tool Whitelist
∩ MCP Binding Whitelist
∩ Task Capability
```

The Skill Router may reduce the Skill set needed for a task. It cannot
expand authorization.

## Architecture

``` text
Frontend (Vue 3 + TypeScript)
          │
          │ OAuth2 / JWT
          ▼
┌─────────────────────────────┐
│ Spring Cloud Gateway :9090  │
└──────────────┬──────────────┘
               │
     ┌─────────┼───────────┬─────────────┐
     ▼         ▼           ▼             ▼
 auth-service  document    task-service  agent-service
    :8081      service        :8083         :8084
               :8082           │              ▲
                 │             │ A2A          │
                 │             └──────────────┘
                 │                            │
                 └──── Workbench Domain ─────┤ MCP
                                              ▼
                                      External MCPs
```
| Service | Responsibility |
| --- | --- |
| `auth-service` | Users, OAuth2, JWT, platform identity |
| `document-service` | Spaces, directories, documents, versions, ChangeRequest / Diff |
| `task-service` | Agent tasks, A2A Client, Workbench MCP Server, Token Ledger, Audit |
| `agent-service` | Agent / Model / Skill / MCP configuration, A2A Server, Spring AI Runtime |
| `gateway-service` | API gateway and unified entry point |

## Tech Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21 · Spring Boot 3.5 · Spring Cloud 2025 · MyBatis-Plus |
| Database | MySQL 5.7 |
| Messaging / Cache | RabbitMQ · Redis 7 · Redisson |
| Object Storage | MinIO |
| Registry / Config | Nacos 3.2.2 |
| Agent | Spring AI · Official A2A Java SDK · MCP Java SDK |
| Frontend | Vue 3 · TypeScript · Vite · Pinia · Element Plus · Markdown |
| Auth | Spring Authorization Server · OAuth2 · JWT RS256 |
See [docs/tech/README.md](docs/tech/README.md) for technical choices and
constraints.

## Quick Start

### Requirements

Recommended local environment:

- Java 21
- Node.js / pnpm
- Docker / Docker Compose
- Maven Wrapper (included)

### 1. Start Infrastructure

``` bash
docker compose up -d
```

The default infrastructure includes:

``` text
MySQL
Redis
RabbitMQ
MinIO
Nacos
```

If MySQL and Redis already run locally:

``` bash
docker compose up -d rabbitmq minio nacos
```

### 2. Start Backend Services

The backend is a Maven multi-module project:

``` bash
cd backend

./mvnw spring-boot:run -pl auth-service -am
./mvnw spring-boot:run -pl gateway-service -am
./mvnw spring-boot:run -pl document-service -am
./mvnw spring-boot:run -pl task-service -am
./mvnw spring-boot:run -pl agent-service -am
```

Default ports:

| Service | Port |
| --- | ---: |
| Gateway | `9090` |
| Auth | `8081` |
| Document | `8082` |
| Task | `8083` |
| Agent | `8084` |

### 3. Start Frontend

``` bash
cd frontend
pnpm install
pnpm dev
```

Frontend variables are documented in
[`frontend/.env.example`](frontend/.env.example). Infrastructure and
secret templates are in [`.env.example`](.env.example).

> For LLM provider and external MCP configuration, follow the current
> configuration templates and documentation under `docs/`.

## Who Is This For?

This project may be useful if you are exploring:

- Java / Spring AI Agent engineering
- Practical A2A / MCP protocol boundaries
- Agent Skills and progressive loading
- External MCP and tool governance
- Human-in-the-loop workflows
- Agent permissions and task-scoped capabilities
- Execution snapshots, auditing, and token-cost control
- AI-assisted document collaboration
- Governed and traceable Agent runtimes

It is intentionally more than a "chat UI + tool calling" demo. The
project focuses on **how Agents should be executed and governed inside a
real engineering system**.

## Project Status

### v0.1 --- Available

The main v0.1 loop is implemented:

- Space / Document / Version
- Draft / Formal documents
- ChangeRequest / Diff Review
- Agent / Model configuration
- Skill / Skill Router / Progressive Loading
- Built-in Workbench MCP
- External Multi-MCP
- A2A task execution
- Task Capability
- Token Budget
- Audit
- Platform roles / Space RBAC
- Vue Web UI

v0.1 intentionally uses:

> **One user-selected Agent per Task.**

The project does not add free-form Multi-Agent orchestration merely for
demo value.

### Next: v0.2 --- Agent Engineering Foundation

Planned areas include:

``` text
Execution Model / Run
OpenTelemetry
Evaluation
Dataset / Replay
Experiment / A-B
Evidence / Context / Memory
Skill Sandbox
OAuth2 / OIDC
Goal / Plan / Workflow v1
```

Multi-Agent orchestration is a later step, after the execution and
evaluation foundations are solid.

See the repository Roadmap / Architecture documents for the long-term
direction.

## Documentation

| Document | What it covers |
| --- | --- |
| [Full Development Plan](docs/Agent-Doc-Workbench%20项目完整开发规划文档.md) | Product scope, MVP, and iteration background (Chinese) |
| [Tech Stack](docs/tech/README.md) | Backend / frontend / auth choices |
| [Common Modules](docs/common-modules.md) | Common modules and infrastructure |
| [Database Design](docs/database-design.md) | Database design and migration constraints |
| [A2A / MCP Design](docs/agent-server-a2a-mcp-design.md) | Agent Server, A2A, and MCP architecture |
| [Agent Task Execution](docs/agent-task-execution-guide.md) | End-to-end Agent task execution |
| [External MCP](docs/external-mcp-architecture-design.md) | Multi-MCP architecture, permissions, and security |
| [Skill Selection](docs/skill-selection-and-progressive-loading-design.md) | Skill routing and progressive loading |
| [UI Gallery](docs/ui-mockups/README.md) | Complete UI mockups and interaction constraints |

## Open Source Direction

Agent-Doc-Workbench is currently driven primarily by **technical
exploration, engineering practice, knowledge sharing, and community
discussion**, and is open by default.

The long-term architecture is gradually evolving toward:

``` text
Agent Platform Core
        +
Document Workbench
        +
Extensible Runtime / Evaluator / Sandbox / Memory
```

Document Workbench remains the first real-world domain and reference
workbench for the platform core.

Core Agent capabilities are not intentionally held back for a
hypothetical closed edition.

The project is ultimately interested in questions such as:

> Why did the Agent act this way?\
> Which capabilities did it use?\
> What evidence supports the result?\
> Were its permissions bounded?\
> Can the execution be traced and reproduced?\
> How good was the result?\
> After changing the Agent, can we prove that it actually improved?

## Contributing

Issues, architecture discussions, bug reports, documentation
improvements, and code contributions are welcome.

Topics especially worth discussing include:

- A2A vs MCP
- Agent Capability
- Skill / Tool boundaries
- Human-in-the-loop
- Execution snapshots
- Agent Memory
- Evaluation / Experiment
- Sandbox execution
- Multi-Agent governance

For significant architectural changes, opening an Issue first to discuss
the problem, proposed design, and trade-offs is recommended.

## License

Licensed under the [Apache License 2.0](LICENSE).
<p align="center">
<strong>Open by default. Extensible by design. Governed in execution.</strong><br/>
<sub>Let agents act while keeping every action bounded, reviewable, and traceable.</sub>
</p>
