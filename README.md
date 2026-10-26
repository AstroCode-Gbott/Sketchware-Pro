<p align="center">
  <img src="assets/Sketchware-Pro.png" style="width: 24%;" alt="Sketchware Pro AI Agent Studio Logo" />
</p>

<h1 align="center">Sketchware Pro — AI Agent Studio Edition</h1>

<p align="center">
  <b>Open-Source Autonomous Android App Development IDE with Embedded AI Agent Harness, Linux/Debian Terminal, MCP Server & Client, and Self-Healing Compiler</b>
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

## 📥 Download APK (Siap Pakai)

File APK dikompilasi secara otomatis oleh **GitHub Actions** setiap kali ada pembaruan kode dan dipublikasikan langsung ke **GitHub Releases**:

<p align="center">
  <a href="https://github.com/GeekJaack/Sketchware-Pro/releases/download/v7.0.0-ai-agent/Sketchware_Pro_AI_Agent.apk">
    <img src="https://img.shields.io/badge/⬇️_Download_APK_Terbaru-Sketchware__Pro__AI__Agent.apk-2ea44f?style=for-the-badge&logo=android&logoColor=white" alt="Download Sketchware_Pro_AI_Agent.apk" />
  </a>
</p>

- **Direct Download (Latest Release):** [`Sketchware_Pro_AI_Agent.apk`](https://github.com/GeekJaack/Sketchware-Pro/releases/download/v7.0.0-ai-agent/Sketchware_Pro_AI_Agent.apk)
- **Halaman Semua Rilis:** [GitHub Releases](https://github.com/GeekJaack/Sketchware-Pro/releases)
- **Build Artifacts (CI/CD):** [GitHub Actions Runs](https://github.com/GeekJaack/Sketchware-Pro/actions/workflows/android.yml)

---

## ✨ Fitur Utama AI Agent Studio

Edisi ini mengintegrasikan **AI Agent Studio** setara *Claude Code / Antigravity / OpenCode* langsung ke dalam IDE **Sketchware Pro** di perangkat Android Anda:

### 1. 🤖 5 Mode Otonomi Agen
- **Ask**: Tanya jawab & inspeksi kode/arsitektur proyek (*Read-only*).
- **Copilot**: Bantu mengedit tampilan (*View*) dan *Event* pada layar yang sedang dibuka.
- **Agent**: Eksekusi *multi-step* lintas layar, komponen, *Custom View*, dan logika blok.
- **Autopilot `0 → APK`**: Otonom penuh — merancang UI, menyusun logika blok/Java, mengunduh *library* Maven, membangun APK, dan melakukan *self-healing* jika ada error kompilasi.
- **Tutor**: Membimbing cara kerja blok dan arsitektur aplikasi langkah demi langkah.

### 2. 🌐 Multi-Provider LLM & Resilient Streaming Parser
Mendukung berbagai penyedia model AI dengan manajemen *profile* dan penyimpanan API Key terenkripsi (**Android Keystore AES-GCM** via `SecretVault`):
- **Google Gemini API** (`gemini-2.5-pro`, `gemini-2.5-flash`, dll.)
- **OpenAI API** (`gpt-4.1`, `o3`, dll.)
- **Anthropic Claude API** (`claude-sonnet-4`, dll.)
- **OpenRouter API**
- **Custom OpenAI-Compatible Gateway** (Ollama, Groq, DeepSeek, GLM, vLLM, LM Studio, dll.) — dilengkapi parser JSON *lenient* multi-objek (`{...}{...}`), penggabungan *chunk* SSE (`data: {...}`), dan *fallback* otomatis untuk *inline tool-calling* (`<tool_call>`).

### 3. 🐧 Terminal Linux & Debian PRoot Bawaan (*Zero-Setup*)
- **Bundel Alpine Linux + PRoot Bawaan**: Langsung aktif di dalam APK tanpa perlu mengunduh apapun saat pertama kali dijalankan.
- **Auto-Bootstrap Debian (`bookworm`)**: Ketika agen membutuhkan ekosistem penuh (`apt-get`, `python3`, `nodejs`, `git`, `curl`, `jq`), agen dapat mengunduh dan mengekstrak *rootfs* Debian secara otomatis di latar belakang.
- **Direct Mount Proyek**: Direktori proyek aktif otomatis di-*mount* ke `/workspace` (kode sumber `.sketchware/mysc/list/<sc_id>`) dan `/project_data` (data metadata `.sketchware/data/<sc_id>`).

### 4. 🛠️ 20+ Built-in Agent Tools
| Kategori | Nama Tool | Fungsi Utama |
| :--- | :--- | :--- |
| **Inspeksi Proyek** | `get_project_overview`, `list_project_files`, `read_project_file`, `search_project_code` | Membaca ringkasan proyek, daftar file, isi file, dan pencarian *regex* lintas layar, *event*, logika blok, XML, & Java. |
| **Desain UI & Layar** | `create_screen`, `write_layout_views`, `write_layout_xml` | Membuat *Activity* + *Layout* baru, menyusun pohon *View* Sketchware, atau menulis XML langsung. |
| **Logika, Event & Blok** | `search_block_catalog`, `write_event_logic`, `add_project_component`, `add_project_variable`, `add_custom_block` | Mencari katalog 250+ *opCode* blok Sketchware, menyusun logika event (`SketchIrCompiler`), menambah *Component*, variabel, & *MoreBlock*. |
| **Kode Java & Manifest** | `write_custom_java`, `patch_activity_java`, `manage_permissions`, `configure_project_metadata` | Menulis kelas Java kustom, memodifikasi kode Activity, mengatur *permission* AndroidManifest, mengonfigurasi `package_name` / `minSdk` / `targetSdk` / palet warna, atau menghapus layar/event. |
| **Library & Resource** | `manage_local_libraries`, `manage_project_resources` | Mengaktifkan *local library*, mengunduh artefak Maven/AAR/JAR beserta dependensi transitif + kompilasi `classes.jar` → `classes.dex` via R8/D8, membuat Vector Drawable XML, serta mengimpor gambar/font/sound/asset. |
| **Keamanan & Build** | `manage_checkpoints`, `build_and_verify_project`, `capture_runtime_logs` | Membuat & memulihkan *snapshot checkpoint* sebelum perubahan besar, menjalankan kompilasi penuh (`aapt2` → `ecj/kotlinc` → `d8` → `apkbuilder`), dan menangkap `logcat` runtime. |
| **Sistem & Web** | `run_terminal_command`, `web_search`, `web_fetch` | Menjalankan perintah shell di Android / Alpine / Debian, mencari referensi di web, dan mengambil dokumentasi/API eksternal. |
| **DeerFlow 2.0 Sub-Agent & Artifacts** | `delegate_subagent`, `manage_artifacts` | Mendelegasikan tugas investigasi/audit ke *Sub-Agent* terisolasi (`researcher`, `ui_architect`, `block_auditor`, `compile_debugger`) dan mengelola *deliverable* di panel **📦 Artifacts**. |

### 5. 🔌 Ekosistem MCP Server, MCP Client, Skills & Plugins
- **Internal MCP Server (`127.0.0.1:8765`)**: Mengekspos seluruh tool Sketchware melalui protokol JSON-RPC 2.0 (`initialize`, `tools/list`, `tools/call`) sehingga agen eksternal via ADB atau terminal lokal dapat mengontrol proyek secara *real-time*.
- **External MCP Client Hub**: Menghubungkan AI Agent ke server MCP eksternal berbasis HTTP/JSON-RPC.
- **Skills (`SKILL.md`) & Dynamic Plugins**: Mendukung pemasangan instruksi keahlian khusus dan *custom tool* berbasis HTTP webhook atau *shell template*.

### 6. 🌍 Dukungan Multi-Bahasa Global (17 Bahasa + Auto System Locale)
Mendukung pengguna dari seluruh negara dengan pengaturan bahasa instan (tersedia melalui *Workspace Sidebar* maupun di dalam **Pengaturan AI Agent Studio**) yang mengubah bahasa antarmuka **dan** menginstruksikan AI Agent untuk selalu merespons dalam bahasa pilihan pengguna:
- **🌐 Auto (System Default)** — Otomatis mengikuti bahasa bawaan perangkat Android atau bahasa pesan pengguna.
- **🇺🇸 English** · **🇮🇩 Bahasa Indonesia** · **🇪🇸 Español** · **🇧🇷 Português** · **🇸🇦 العربية** · **🇮🇳 हिन्दी** · **🇷🇺 Русский** · **🇨🇳 中文 (简体)** · **🇯🇵 日本語** · **🇰🇷 한국어** · **🇫🇷 Français** · **🇩🇪 Deutsch** · **🇹🇷 Türkçe** · **🇻🇳 Tiếng Việt** · **🇹🇭 ไทย** · **🇲🇾 Bahasa Melayu**

---

## 🏗️ Arsitektur Kode Sumber

```text
app/src/main/
├── cpp/                                # Native PTY bridge (libsketch_pty.so) untuk sesi terminal interaktif
├── assets/linux/                       # Bundel proot (aarch64/armv7/x86_64) & rootfs Alpine Linux bawaan
└── java/
    ├── a/a/a/                          # Compiler inti Sketchware Pro (ProjectBuilder, Jx, Lx, Ox, Ix, yq)
    ├── com/besome/sketch/              # Activity utama Sketchware (DesignActivity, LogicEditorActivity, dll.)
    ├── mod/                            # Modifikasi komunitas Sketchware Pro (Local Library, Code Editor, dll.)
    └── pro/sketchware/ai/              # Modul AI Agent Studio
        ├── agent/                      # AgentOrchestrator, AgentMemoryStore, AgentForegroundService
        ├── config/                     # AiConfigManager (Multi-provider & profil model)
        ├── extensions/                 # McpClientHub, SkillManager, PluginManager
        ├── ir/                         # BlockCatalog & SketchIrCompiler (JSON IR -> Sketchware BlockBean)
        ├── mcp/                        # SketchwareMcpServer (JSON-RPC 2.0 Server di port 8765)
        ├── observe/                    # HeadlessProjectBuilder & RuntimeLogHarness
        ├── project/                    # ProjectGateway (akses langsung ke struktur data proyek Sketchware)
        ├── provider/                   # LlmProvider (Gemini, OpenAI, Claude, OpenRouter, Custom Resilient Parser)
        ├── security/                   # SecretVault (Enkripsi API Key berbasis Android Keystore AES-GCM)
        ├── shell/                      # LinuxSubsystem, ShellRunner, TerminalSession, LinuxDownloader
        ├── tools/                      # SketchwareToolRegistry & WebTools
        └── ui/                         # Jetpack Compose UI (AiAgentStudioSheet, AgentChatScreen, TerminalScreen, dll.)
```

---

## ⚙️ Cara Compile Sendiri (Local & GitHub Actions)

### A. Otomatis via GitHub Actions (Tanpa Install Android Studio)
1. **Fork** repositori ini ke akun GitHub Anda.
2. Buka tab **Actions** pada repositori hasil fork Anda, lalu pilih workflow **Build Sketchware Pro AI Agent APK**.
3. Klik **Run workflow** (atau lakukan *push* ke branch `main`).
4. Setelah selesai, unduh file **`Sketchware_Pro_AI_Agent.apk`** langsung dari halaman **Releases** atau dari bagian **Artifacts** pada workflow run tersebut.

### B. Build secara Lokal via Gradle
Pastikan Anda memiliki **JDK 21** dan **Android SDK (API 35 + NDK)**:
```bash
git clone https://github.com/GeekJaack/Sketchware-Pro.git
cd Sketchware-Pro

# Jalankan Unit Tests & Build Debug APK
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
File APK hasil build akan tersedia di:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🤝 Kontribusi & Lisensi Komunitas

Kontribusi sangat terbuka! Silakan buat *Issue* atau *Pull Request* untuk menambahkan fitur baru, memperbaiki bug, atau memperkaya katalog *Skill* dan *Plugin* AI Agent Studio.
