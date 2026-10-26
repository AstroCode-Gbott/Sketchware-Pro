<p align="center">
  <img src="assets/Sketchware-Pro.png" style="width: 24%;" alt="Sketchware Pro AI Agent Studio Logo" />
</p>

<h1 align="center">Sketchware Pro — AI Agent Studio Edition</h1>

<p align="center">
  <b>Open-source autonomous Android application development IDE featuring an embedded AI agent harness, Linux PRoot terminal, MCP server and client, and self-healing compiler pipeline.</b>
</p>

<p align="center">
  <a href="https://github.com/GeekJaack/Sketchware-Pro/actions/workflows/android.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/GeekJaack/Sketchware-Pro/android.yml?branch=main&label=Auto%20Compile%20APK&logo=githubactions" alt="Auto Compile APK Status" />
  </a>
  <a href="https://github.com/GeekJaack/Sketchware-Pro/releases/latest">
    <img src="https://img.shields.io/github/v/release/GeekJaack/Sketchware-Pro?label=Latest%20APK&logo=android&color=2ea44f" alt="Latest Release" />
  </a>
  <a href="https://github.com/GeekJaack/Sketchware-Pro/releases">
    <img src="https://img.shields.io/github/downloads/GeekJaack/Sketchware-Pro/total?label=APK%20Downloads&logo=github" alt="Total Downloads" />
  </a>
  <a href="https://github.com/GeekJaack/Sketchware-Pro/commits/main">
    <img src="https://img.shields.io/github/last-commit/GeekJaack/Sketchware-Pro?logo=git" alt="Last Commit" />
  </a>
</p>

---

## Download Prebuilt APK

Application packages are compiled automatically by GitHub Actions on every commit to `main` and published to GitHub Releases:

<p align="center">
  <a href="https://github.com/GeekJaack/Sketchware-Pro/releases/download/v7.0.0-ai-agent/Sketchware_Pro_AI_Agent.apk">
    <img src="https://img.shields.io/badge/Download_APK-v7.0.0--ai--agent-brightgreen?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" />
  </a>
</p>

- **Direct Download (Latest Release):** [`Sketchware_Pro_AI_Agent.apk`](https://github.com/GeekJaack/Sketchware-Pro/releases/download/v7.0.0-ai-agent/Sketchware_Pro_AI_Agent.apk)
- **All Releases:** [GitHub Releases](https://github.com/GeekJaack/Sketchware-Pro/releases)
- **Build Artifacts (CI/CD):** [GitHub Actions Workflow Runs](https://github.com/GeekJaack/Sketchware-Pro/actions/workflows/android.yml)

---

## Key Capabilities

This release embeds a complete autonomous agent development environment directly inside the Sketchware Pro Android IDE:

### 1. Five Agent Autonomy Modes
- **Ask**: Read-only inspection and analysis of project architecture, views, events, and source code.
- **Copilot**: Interactive design and coding assistant scoped to the currently opened screen or component.
- **Agent**: Multi-turn execution across screens, components, custom views, and visual block logic.
- **Autopilot (0 to APK)**: Fully autonomous execution from an initial prompt to a verified APK. Designs layouts, writes block and Java logic, resolves Maven dependencies, runs build pipelines, and self-heals compiler diagnostics.
- **Tutor**: Guided walk-through explaining block structures, Android internals, and architecture best practices.

### 2. Multi-Provider LLM Integration and Resilient Streaming
Supports major AI providers with profile management and encrypted credential storage backed by Android Keystore AES-GCM (`SecretVault`):
- **Google Gemini API** (`gemini-2.5-pro`, `gemini-2.5-flash`, etc.)
- **OpenAI API** (`gpt-4.1`, `o3`, etc.)
- **Anthropic Claude API** (`claude-sonnet-4`, etc.)
- **OpenRouter API**
- **Custom OpenAI-Compatible Endpoints** (Ollama, Groq, DeepSeek, GLM, vLLM, LM Studio) featuring resilient multi-object JSON stream parsing (`{...}{...}`), SSE chunk aggregation, and automatic fallback for inline tool invocations (`<tool_call>`).

### 3. Embedded Linux Terminal and Debian PRoot
- **Prebundled Alpine Linux and PRoot**: Operational upon installation without additional runtime downloads.
- **Debian (bookworm) Auto-Bootstrap**: Automates background download and rootfs extraction when comprehensive package management (`apt-get`, `python3`, `nodejs`, `git`, `curl`, `jq`) is required.
- **Automated Workspace Mounting**: Mounts the active project's source tree to `/workspace` (`.sketchware/mysc/list/<sc_id>`) and project metadata to `/project_data` (`.sketchware/data/<sc_id>`).

### 4. Over 20 Built-in Agent Tools

| Category | Tool Name | Description |
| :--- | :--- | :--- |
| **Project Inspection** | `get_project_overview`, `list_project_files`, `read_project_file`, `search_project_code` | Inspect project summary, file listings, source code, and regex search across layouts, events, blocks, XML, and Java. |
| **UI and Screens** | `create_screen`, `write_layout_views`, `write_layout_xml` | Create Activities and CustomViews, generate visual View hierarchies, and write standard Android XML layouts. |
| **Logic, Events, and Blocks** | `search_block_catalog`, `write_event_logic`, `add_project_component`, `add_project_variable`, `add_custom_block` | Search 250+ Sketchware opCodes, compile event logic via `SketchIrCompiler`, and manage Components, variables, and MoreBlocks. |
| **Java Code and Manifest** | `write_custom_java`, `patch_activity_java`, `manage_permissions`, `configure_project_metadata` | Manage custom Java/Kotlin classes, patch Activity lifecycles, configure AndroidManifest permissions, and tune SDK targets. |
| **Libraries and Resources** | `manage_local_libraries`, `manage_project_resources` | Enable local libraries, resolve Maven/AAR/JAR artifacts with transitive D8 compilation, and manage Drawables, fonts, and assets. |
| **Diagnostics and Build** | `manage_checkpoints`, `build_and_verify_project`, `capture_runtime_logs` | Create pre-mutation checkpoints, execute headless build pipelines (`aapt2` -> `ecj/kotlinc` -> `d8` -> `apkbuilder`), and read runtime logcat. |
| **System and Web** | `run_terminal_command`, `web_search`, `web_fetch` | Execute shell commands in Android/Alpine/Debian environments, search technical documentation, and fetch web resources. |
| **Sub-Agents and Artifacts** | `delegate_subagent`, `manage_artifacts` | Delegate domain-specific tasks to isolated worker agents (`researcher`, `ui_architect`, `block_auditor`, `compile_debugger`) and maintain structured deliverables. |

### 5. Extensible MCP Server, MCP Client, Skills, and Plugins
- **Built-in MCP Server (`127.0.0.1:8765`)**: Exposes project inspection and manipulation tools via standard JSON-RPC 2.0 (`initialize`, `tools/list`, `tools/call`) over ADB port forwarding or Wi-Fi LAN.
- **External MCP Client Hub**: Connects the internal agent to external HTTP/JSON-RPC MCP servers.
- **Skills (`SKILL.md`) and Plugins**: Extensible modular instruction definitions and external tool extensions.

### 6. Internationalization Support (17 Languages + System Locale)
Runtime language switching via the Workspace Sidebar and AI Settings dialog. Configures both the user interface and system prompts:
- **Auto (System Default)** — Matches the host device language or incoming prompt language.
- Supported translations: English, Indonesian, Spanish, Portuguese, Arabic, Hindi, Russian, Simplified Chinese, Japanese, Korean, French, German, Turkish, Vietnamese, Thai, and Malay.

### 7. Modern Workspace Interface and Context Integration
- **Workspace Navigation Sidebar**: Collapsible navigation drawer covering New Chat, History, Artifacts, Agent Modes, Skills, Plugins, MCP Connectors, Terminal Sessions, and Checkpoints.
- **Centered Input and Quick Actions**: Interactive prompt interface with dedicated shortcut chips for Build, Repair, Design, Learn, and Create actions.
- **Structured Artifacts and To-Dos**: Stacked task-tracking cards and an artifact drawer with real-time counters for generated APKs, layouts, Java files, and specification docs.
- **Unified "Add Context" Menu**: Bottom-left context launcher combining File Selection, Directory Attachment, APK Inspection (manifest, DEX, resources, native libraries), Image Import (with one-click import into `@drawable`), Quick Slash Commands (`/`), and Mode Selectors.

---

## Repository Architecture

```text
app/src/main/
├── cpp/                                # Native PTY bridge (libsketch_pty.so) for interactive terminal sessions
├── assets/linux/                       # Embedded proot binaries (aarch64/armv7/x86_64) and Alpine rootfs
└── java/
    ├── a/a/a/                          # Core Sketchware Pro compiler pipeline (ProjectBuilder, Jx, Lx, Ox, Ix, yq)
    ├── com/besome/sketch/              # Core Activities (DesignActivity, LogicEditorActivity, etc.)
    ├── mod/                            # Community additions (Local Libraries, Code Editor, etc.)
    └── pro/sketchware/ai/              # AI Agent Studio Subsystem
        ├── agent/                      # AgentOrchestrator, AgentMemoryStore, AgentForegroundService
        ├── config/                     # AiConfigManager (Multi-provider settings and model configurations)
        ├── extensions/                 # McpClientHub, SkillManager, PluginManager
        ├── ir/                         # BlockCatalog and SketchIrCompiler (IR to Sketchware BlockBeans)
        ├── mcp/                        # SketchwareMcpServer (JSON-RPC 2.0 server on port 8765)
        ├── observe/                    # HeadlessProjectBuilder and RuntimeLogHarness
        ├── project/                    # ProjectGateway (direct access to Sketchware data store)
        ├── provider/                   # LlmProvider (Gemini, OpenAI, Claude, OpenRouter, Custom Endpoints)
        ├── security/                   # SecretVault (Android Keystore AES-GCM credential encryption)
        ├── shell/                      # LinuxSubsystem, ShellRunner, TerminalSession, LinuxDownloader
        ├── tools/                      # SketchwareToolRegistry and WebTools
        └── ui/                         # Jetpack Compose UI (AiAgentStudioSheet, AgentChatScreen, TerminalScreen)
```

---

## Building from Source

### Option A: GitHub Actions (Automated Cloud Build)
1. Fork this repository to your GitHub account.
2. In your fork, navigate to the **Actions** tab and select the **Build Sketchware Pro AI Agent APK** workflow.
3. Click **Run workflow** (or push a commit to `main`).
4. Once completed, download `Sketchware_Pro_AI_Agent.apk` from the **Releases** tab or from the workflow run artifacts.

### Option B: Local Gradle Build
Prerequisites: **JDK 21** and **Android SDK (API 35 with NDK)**.

```bash
git clone https://github.com/GeekJaack/Sketchware-Pro.git
cd Sketchware-Pro

# Run unit tests and assemble debug APK
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The output package will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Contributing and Community

Contributions are welcome. Submit an Issue or Pull Request to propose features, report defects, or contribute to the Skill and Plugin catalogs.
