package pro.sketchware.ai.config

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pro.sketchware.utility.SketchwareUtil

data class LocalizedAiUi(
    val languageSettingTitle: String,
    val emptyTitle: String,
    val emptySubtitle: String,
    val suggestionBuildZeroTitle: String,
    val suggestionFixErrorTitle: String,
    val suggestionRedesignUiTitle: String,
    val suggestionExplainProjectTitle: String,
    val composerHint: String,
    val thinkingText: String,
    val disclaimerText: String,
    val noApiKeyText: String,
    val configureModelText: String,
    val workingStepsFormat: String,
    val completedStepsFormat: String,
    val failedStepsFormat: String,
    val planTitle: String,
    val allTasksCompleted: String,
    val copyText: String,
    val copiedToast: String,
    val closeText: String,
    val cancelText: String,
    val saveAndCloseText: String,
    val newChatTitle: String,
    val newChatConfirmMsg: String,
    val newChatStartBtn: String,
    val menuSettings: String,
    val menuLanguage: String,
    val menuExtensions: String,
    val menuTerminal: String,
    val menuCheckpoints: String,
    val menuCopyTranscript: String,
    val settingsDialogTitle: String,
    val settingsProviderLabel: String,
    val settingsLanguageLabel: String,
    val settingsSaveKeyBtn: String,
    val settingsDeleteKeyBtn: String,
    val settingsSavedProfilesTitle: String,
    val continueLastStepText: String
)

object AiStrings {

    @JvmStatic
    fun current(context: Context): LocalizedAiUi {
        val lang = AiConfigManager(context).agentLanguage.resolved()
        return forLanguage(lang)
    }

    @JvmStatic
    fun modeSubtitle(mode: AgentAutonomyMode, lang: AgentLanguage): String {
        val eff = lang.resolved()
        if (eff == AgentLanguage.ID) return mode.subtitle
        return when (mode) {
            AgentAutonomyMode.ASK -> when (eff) {
                AgentLanguage.ES -> "Preguntas y lectura del proyecto (Solo lectura)"
                AgentLanguage.PT -> "Perguntas e leitura do projeto (Somente leitura)"
                AgentLanguage.AR -> "الأسئلة وقراءة المشروع (للقراءة فقط)"
                AgentLanguage.HI -> "प्रश्नोत्तर और प्रोजेक्ट निरीक्षण (केवल पढ़ने के लिए)"
                AgentLanguage.RU -> "Вопросы и чтение проекта (Только чтение)"
                AgentLanguage.ZH -> "问答与项目检查（只读模式）"
                AgentLanguage.JA -> "質問とプロジェクトの読み取り（読み取り専用）"
                AgentLanguage.KO -> "질문 및 프로젝트 탐색 (읽기 전용)"
                AgentLanguage.FR -> "Questions et lecture du projet (Lecture seule)"
                AgentLanguage.DE -> "Fragen & Projekt lesen (Nur Lesen)"
                AgentLanguage.TR -> "Soru-Cevap ve proje inceleme (Salt okunur)"
                AgentLanguage.VI -> "Hỏi đáp & đọc dự án (Chỉ đọc)"
                AgentLanguage.TH -> "ถาม-ตอบและอ่านโปรเจ็กต์ (อ่านอย่างเดียว)"
                AgentLanguage.MS -> "Soal jawab & baca projek (Baca sahaja)"
                else -> "Q&A and inspect project (Read-only)"
            }
            AgentAutonomyMode.COPILOT -> when (eff) {
                AgentLanguage.ES -> "Ayuda a editar la pantalla o evento actual"
                AgentLanguage.PT -> "Ajuda a editar a tela ou evento aberto"
                AgentLanguage.AR -> "المساعدة في تعديل الشاشة أو الحدث المفتوح"
                AgentLanguage.HI -> "वर्तमान स्क्रीन/इवेंट संपादित करने में सहायता"
                AgentLanguage.RU -> "Помощь в редактировании текущего экрана/события"
                AgentLanguage.ZH -> "辅助编辑当前打开的界面与事件"
                AgentLanguage.JA -> "現在開いている画面やイベントの編集を支援"
                AgentLanguage.KO -> "현재 열린 화면/이벤트 편집 지원"
                AgentLanguage.FR -> "Aide à modifier l'écran ou l'événement ouvert"
                AgentLanguage.DE -> "Hilft beim Bearbeiten des aktuellen Bildschirms/Events"
                AgentLanguage.TR -> "Açık ekranı veya etkinliği düzenlemeye yardımcı olur"
                AgentLanguage.VI -> "Hỗ trợ chỉnh sửa màn hình/sự kiện đang mở"
                AgentLanguage.TH -> "ช่วยแก้ไขหน้าจอหรืออีเวนต์ที่เปิดอยู่"
                AgentLanguage.MS -> "Bantu sunting skrin/acara yang sedang dibuka"
                else -> "Assist editing the currently open screen/event"
            }
            AgentAutonomyMode.AGENT -> when (eff) {
                AgentLanguage.ES -> "Ejecución multi-paso en pantallas, componentes y lógica"
                AgentLanguage.PT -> "Execução multi-etapas entre telas, componentes e lógica"
                AgentLanguage.AR -> "تنفيذ متعدد الخطوات عبر الشاشات والمكونات والمنطق"
                AgentLanguage.HI -> "स्क्रीन, कॉम्पोनेंट और लॉजिक में मल्टी-स्टेप निष्पादन"
                AgentLanguage.RU -> "Многошаговое выполнение по экранам, компонентам и логике"
                AgentLanguage.ZH -> "跨界面、组件与逻辑块的多步智能执行"
                AgentLanguage.JA -> "画面・コンポーネント・ロジックを横断するマルチステップ実行"
                AgentLanguage.KO -> "화면, 컴포넌트 및 로직 전반의 다단계 실행"
                AgentLanguage.FR -> "Exécution multi-étapes sur les écrans, composants et logiques"
                AgentLanguage.DE -> "Mehrstufige Ausführung über Bildschirme, Komponenten & Logik"
                AgentLanguage.TR -> "Ekranlar, bileşenler ve mantıkta çok adımlı yürütme"
                AgentLanguage.VI -> "Thực thi đa bước trên các màn hình, thành phần & logic"
                AgentLanguage.TH -> "ทำงานหลายขั้นตอนข้ามหน้าจอ คอมโพเนนต์ และลอจิก"
                AgentLanguage.MS -> "Pelaksanaan berbilang langkah merentas skrin, komponen & logik"
                else -> "Multi-step execution across screens, components & logic"
            }
            AgentAutonomyMode.AUTOPILOT -> when (eff) {
                AgentLanguage.ES -> "Autónomo total: Diseñar, Construir, Compilar APK y Auto-Reparar"
                AgentLanguage.PT -> "Autônomo total: Projetar, Construir, Compilar APK e Auto-Corrigir"
                AgentLanguage.AR -> "مستقل بالكامل: تصميم وبناء وتجميع APK والإصلاح الذاتي"
                AgentLanguage.HI -> "पूर्ण स्वायत्त: डिज़ाइन, बिल्ड, APK कंपाइल और सेल्फ-हील"
                AgentLanguage.RU -> "Полный автопилот: дизайн, сборка APK и самоисправление"
                AgentLanguage.ZH -> "全自动自治：设计、构建、编译 APK 与自动修复"
                AgentLanguage.JA -> "完全自律：設計・構築・APKコンパイル・自己修復"
                AgentLanguage.KO -> "완전 자율: 설계, 빌드, APK 컴파일 및 자가 복구"
                AgentLanguage.FR -> "Autonome complet : Concevoir, Construire, Compiler APK & Auto-Réparer"
                AgentLanguage.DE -> "Vollautonom: Entwerfen, Bauen, APK kompilieren & Selbstheilung"
                AgentLanguage.TR -> "Tam Otonom: Tasarla, Oluştur, APK Derle ve Kendi Kendini Onar"
                AgentLanguage.VI -> "Tự động hoàn toàn: Thiết kế, Xây dựng, Biên dịch APK & Tự sửa lỗi"
                AgentLanguage.TH -> "อัตโนมัติเต็มรูปแบบ: ออกแบบ สร้าง คอมไพล์ APK และซ่อมแซมตัวเอง"
                AgentLanguage.MS -> "Autonomi penuh: Reka, Bina, Kompil APK & Baiki Sendiri"
                else -> "Full Autonomy: Design, Build, Compile APK & Self-Heal"
            }
            AgentAutonomyMode.TUTOR -> when (eff) {
                AgentLanguage.ES -> "Explica cómo funcionan los bloques paso a paso"
                AgentLanguage.PT -> "Explica como os blocos funcionam passo a passo"
                AgentLanguage.AR -> "شرح عمل اللبنات البرمجية خطوة بخطوة"
                AgentLanguage.HI -> "ब्लॉक कैसे काम करते हैं, चरण-दर-चरण समझाएं"
                AgentLanguage.RU -> "Пошаговое объяснение работы блоков и кода"
                AgentLanguage.ZH -> "循序渐进讲解积木逻辑与代码原理"
                AgentLanguage.JA -> "ブロックの仕組みをステップバイステップで解説"
                AgentLanguage.KO -> "블록 작동 방식을 단계별로 안내 및 설명"
                AgentLanguage.FR -> "Explique le fonctionnement des blocs étape par étape"
                AgentLanguage.DE -> "Erklärt Schritt für Schritt, wie Blöcke funktionieren"
                AgentLanguage.TR -> "Blokların nasıl çalıştığını adım adım açıklar"
                AgentLanguage.VI -> "Giải thích cách hoạt động của khối từng bước"
                AgentLanguage.TH -> "อธิบายการทำงานของบล็อกทีละขั้นตอน"
                AgentLanguage.MS -> "Terangkan cara blok berfungsi langkah demi langkah"
                else -> "Explain how blocks work and guide step-by-step"
            }
        }
    }

    @JvmStatic
    fun forLanguage(lang: AgentLanguage): LocalizedAiUi = when (lang.resolved()) {
        AgentLanguage.ID -> LocalizedAiUi(
            languageSettingTitle = "🌐 Bahasa / Language",
            emptyTitle = "Apa yang ingin kita bangun?",
            emptySubtitle = "Agent dapat merancang layar, menyusun blok logika, mengelola file & library, lalu compile dan memperbaiki error secara otomatis.",
            suggestionBuildZeroTitle = "Bangun aplikasi dari 0",
            suggestionFixErrorTitle = "Cek & perbaiki error",
            suggestionRedesignUiTitle = "Percantik UI layar ini",
            suggestionExplainProjectTitle = "Jelaskan proyek",
            composerHint = "Ketik / untuk prompt cepat, atau tulis perintah…",
            thinkingText = "Berpikir…",
            disclaimerText = "AI dapat keliru. Selalu periksa hasil build & logika.",
            noApiKeyText = "Belum ada API key",
            configureModelText = "Atur model",
            workingStepsFormat = "Mengerjakan · %d langkah",
            completedStepsFormat = "%d langkah dijalankan",
            failedStepsFormat = "%d langkah dijalankan · %d gagal",
            planTitle = "Rencana",
            allTasksCompleted = "Semua tugas selesai",
            copyText = "Salin",
            copiedToast = "Disalin ke clipboard",
            closeText = "Tutup",
            cancelText = "Batal",
            saveAndCloseText = "Simpan & Tutup",
            newChatTitle = "Mulai percakapan baru?",
            newChatConfirmMsg = "Riwayat chat dan rencana tugas proyek ini akan dihapus. Isi proyek tidak berubah.",
            newChatStartBtn = "Mulai baru",
            menuSettings = "Pengaturan API & MCP",
            menuLanguage = "Bahasa / Language",
            menuExtensions = "Ekosistem MCP, Plugin & Skill",
            menuTerminal = "Terminal (Linux / Debian)",
            menuCheckpoints = "Riwayat Checkpoint & Rollback",
            menuCopyTranscript = "Salin seluruh percakapan",
            settingsDialogTitle = "Pengaturan AI Agent Studio",
            settingsProviderLabel = "1. Pilih AI Provider (BYOK)",
            settingsLanguageLabel = "🌐 Bahasa Antarmuka & Respons AI (Language)",
            settingsSaveKeyBtn = "Simpan Key & Model",
            settingsDeleteKeyBtn = "Hapus API Key",
            settingsSavedProfilesTitle = "Daftar API Key & Model Tersimpan",
            continueLastStepText = "Lanjutkan dari langkah terakhir"
        )

        AgentLanguage.ES -> LocalizedAiUi(
            languageSettingTitle = "🌐 Idioma / Language",
            emptyTitle = "¿Qué quieres construir hoy?",
            emptySubtitle = "El agente puede diseñar pantallas, armar bloques lógicos, gestionar archivos y librerías, compilar y reparar errores automáticamente.",
            suggestionBuildZeroTitle = "Construir app desde cero",
            suggestionFixErrorTitle = "Verificar y reparar errores",
            suggestionRedesignUiTitle = "Mejorar el diseño UI actual",
            suggestionExplainProjectTitle = "Explicar este proyecto",
            composerHint = "Escribe / para comandos rápidos o envía una instrucción…",
            thinkingText = "Pensando…",
            disclaimerText = "La IA puede cometer errores. Verifica siempre la compilación.",
            noApiKeyText = "Sin clave API",
            configureModelText = "Configurar modelo",
            workingStepsFormat = "Trabajando · %d pasos",
            completedStepsFormat = "%d pasos ejecutados",
            failedStepsFormat = "%d pasos ejecutados · %d fallidos",
            planTitle = "Plan de tareas",
            allTasksCompleted = "Todas las tareas completadas",
            copyText = "Copiar",
            copiedToast = "Copiado al portapapeles",
            closeText = "Cerrar",
            cancelText = "Cancelar",
            saveAndCloseText = "Guardar y cerrar",
            newChatTitle = "¿Iniciar nueva conversación?",
            newChatConfirmMsg = "Se borrará el historial de chat de este proyecto. Los archivos del proyecto no cambiarán.",
            newChatStartBtn = "Iniciar nuevo",
            menuSettings = "Ajustes de API y MCP",
            menuLanguage = "Idioma / Language",
            menuExtensions = "Ecosistema MCP, Plugins y Skills",
            menuTerminal = "Terminal (Linux / Debian)",
            menuCheckpoints = "Puntos de control y restauración",
            menuCopyTranscript = "Copiar toda la conversación",
            settingsDialogTitle = "Ajustes de AI Agent Studio",
            settingsProviderLabel = "1. Seleccionar Proveedor IA (BYOK)",
            settingsLanguageLabel = "🌐 Idioma de la Interfaz y Respuestas IA",
            settingsSaveKeyBtn = "Guardar Clave y Modelo",
            settingsDeleteKeyBtn = "Eliminar Clave API",
            settingsSavedProfilesTitle = "Perfiles de Claves y Modelos Guardados",
            continueLastStepText = "Continuar desde el último paso"
        )

        AgentLanguage.PT -> LocalizedAiUi(
            languageSettingTitle = "🌐 Idioma / Language",
            emptyTitle = "O que vamos construir hoje?",
            emptySubtitle = "O agente pode criar telas, montar blocos lógicos, gerenciar arquivos e bibliotecas, compilar e corrigir erros automaticamente.",
            suggestionBuildZeroTitle = "Criar aplicativo do zero",
            suggestionFixErrorTitle = "Verificar e corrigir erros",
            suggestionRedesignUiTitle = "Modernizar a UI desta tela",
            suggestionExplainProjectTitle = "Explicar o projeto",
            composerHint = "Digite / para comandos rápidos ou escreva uma instrução…",
            thinkingText = "Pensando…",
            disclaimerText = "A IA pode cometer erros. Verifique sempre a compilação.",
            noApiKeyText = "Sem chave de API",
            configureModelText = "Configurar modelo",
            workingStepsFormat = "Trabalhando · %d etapas",
            completedStepsFormat = "%d etapas concluídas",
            failedStepsFormat = "%d etapas concluídas · %d falharam",
            planTitle = "Plano",
            allTasksCompleted = "Todas as tarefas concluídas",
            copyText = "Copiar",
            copiedToast = "Copiado para a área de transferência",
            closeText = "Fechar",
            cancelText = "Cancelar",
            saveAndCloseText = "Salvar e Fechar",
            newChatTitle = "Iniciar nova conversa?",
            newChatConfirmMsg = "O histórico de chat deste projeto será limpo. Os arquivos do projeto não serão alterados.",
            newChatStartBtn = "Iniciar nova",
            menuSettings = "Configurações de API e MCP",
            menuLanguage = "Idioma / Language",
            menuExtensions = "Ecossistema MCP, Plugins e Skills",
            menuTerminal = "Terminal (Linux / Debian)",
            menuCheckpoints = "Checkpoints e Restauração",
            menuCopyTranscript = "Copiar toda a conversa",
            settingsDialogTitle = "Configurações do AI Agent Studio",
            settingsProviderLabel = "1. Escolha o Provedor de IA (BYOK)",
            settingsLanguageLabel = "🌐 Idioma da Interface e Respostas da IA",
            settingsSaveKeyBtn = "Salvar Chave e Modelo",
            settingsDeleteKeyBtn = "Excluir Chave de API",
            settingsSavedProfilesTitle = "Perfis de Chaves e Modelos Salvos",
            continueLastStepText = "Continuar da última etapa"
        )

        AgentLanguage.AR -> LocalizedAiUi(
            languageSettingTitle = "🌐 اللغة / Language",
            emptyTitle = "ماذا تريد أن نبني اليوم؟",
            emptySubtitle = "يمكن للوكيل الذكي تصميم الشاشات، وبناء اللبنات البرمجية، وإدارة الملفات والمكتبات، وتجميع التطبيق وإصلاح الأخطاء تلقائياً.",
            suggestionBuildZeroTitle = "بناء تطبيق كامل من الصفر",
            suggestionFixErrorTitle = "فحص وإصلاح الأخطاء",
            suggestionRedesignUiTitle = "تحسين واجهة هذه الشاشة",
            suggestionExplainProjectTitle = "شرح بنية المشروع",
            composerHint = "اكتب / للأوامر السريعة، أو اكتب طلبك هنا…",
            thinkingText = "جارٍ التفكير…",
            disclaimerText = "قد يخطئ الذكاء الاصطناعي. تحقق دائماً من نتائج البناء.",
            noApiKeyText = "لا يوجد مفتاح API",
            configureModelText = "إعداد النموذج",
            workingStepsFormat = "جارٍ العمل · %d خطوات",
            completedStepsFormat = "تم تنفيذ %d خطوات",
            failedStepsFormat = "تم تنفيذ %d خطوات · فشل %d",
            planTitle = "خطة العمل",
            allTasksCompleted = "اكتملت جميع المهام",
            copyText = "نسخ",
            copiedToast = "تم النسخ إلى الحافظة",
            closeText = "إغلاق",
            cancelText = "إلغاء",
            saveAndCloseText = "حفظ وإغلاق",
            newChatTitle = "بدء محادثة جديدة؟",
            newChatConfirmMsg = "سيتم مسح سجل المحادثة لهذا المشروع دون المساس بملفات المشروع.",
            newChatStartBtn = "بدء جديد",
            menuSettings = "إعدادات API و MCP",
            menuLanguage = "اللغة / Language",
            menuExtensions = "إضافات MCP والمهارات (Skills)",
            menuTerminal = "الطرفية (Linux / Debian)",
            menuCheckpoints = "نقاط الاستعادة (Checkpoints)",
            menuCopyTranscript = "نسخ المحادثة بالكامل",
            settingsDialogTitle = "إعدادات AI Agent Studio",
            settingsProviderLabel = "1. اختر مزود الذكاء الاصطناعي (BYOK)",
            settingsLanguageLabel = "🌐 لغة الواجهة واستجابة الوكيل الذكي",
            settingsSaveKeyBtn = "حفظ المفتاح والنموذج",
            settingsDeleteKeyBtn = "حذف مفتاح API",
            settingsSavedProfilesTitle = "المفاتيح والنماذج المحفوظة",
            continueLastStepText = "المتابعة من الخطوة الأخيرة"
        )

        AgentLanguage.HI -> LocalizedAiUi(
            languageSettingTitle = "🌐 भाषा / Language",
            emptyTitle = "आज हम क्या बनाना चाहते हैं?",
            emptySubtitle = "एजेंट स्क्रीन डिज़ाइन कर सकता है, लॉजिक ब्लॉक बना सकता है, फ़ाइलें व लाइब्रेरी प्रबंधित कर सकता है, और ऑटो-कंपाइल व एरर ठीक कर सकता है।",
            suggestionBuildZeroTitle = "शून्य से ऐप बनाएं",
            suggestionFixErrorTitle = "एरर जांचें और ठीक करें",
            suggestionRedesignUiTitle = "इस स्क्रीन का UI सुधारें",
            suggestionExplainProjectTitle = "प्रोजेक्ट समझाएं",
            composerHint = "त्वरित प्रॉम्प्ट के लिए / टाइप करें, या निर्देश लिखें…",
            thinkingText = "सोच रहा है…",
            disclaimerText = "AI से गलती हो सकती है। हमेशा बिल्ड और लॉजिक की जांच करें।",
            noApiKeyText = "कोई API कुंजी नहीं",
            configureModelText = "मॉडल सेट करें",
            workingStepsFormat = "कार्य प्रगति पर · %d चरण",
            completedStepsFormat = "%d चरण पूर्ण",
            failedStepsFormat = "%d चरण पूर्ण · %d विफल",
            planTitle = "कार्य योजना",
            allTasksCompleted = "सभी कार्य पूर्ण हुए",
            copyText = "कॉपी करें",
            copiedToast = "क्लिपबोर्ड पर कॉपी किया गया",
            closeText = "बंद करें",
            cancelText = "रद्द करें",
            saveAndCloseText = "सहेजें और बंद करें",
            newChatTitle = "नई चैट शुरू करें?",
            newChatConfirmMsg = "इस प्रोजेक्ट का चैट इतिहास साफ़ हो जाएगा। प्रोजेक्ट की फ़ाइलें सुरक्षित रहेंगी।",
            newChatStartBtn = "नया शुरू करें",
            menuSettings = "API और MCP सेटिंग्स",
            menuLanguage = "भाषा / Language",
            menuExtensions = "MCP, प्लगइन और स्किल हब",
            menuTerminal = "टर्मिनल (Linux / Debian)",
            menuCheckpoints = "चेकपॉइंट और रोलबैक",
            menuCopyTranscript = "पूरी बातचीत कॉपी करें",
            settingsDialogTitle = "AI Agent Studio सेटिंग्स",
            settingsProviderLabel = "1. AI प्रदाता चुनें (BYOK)",
            settingsLanguageLabel = "🌐 इंटरफ़ेस और AI प्रतिक्रिया भाषा",
            settingsSaveKeyBtn = "Key और Model सहेजें",
            settingsDeleteKeyBtn = "API Key हटाएं",
            settingsSavedProfilesTitle = "सहेजे गए API Key और मॉडल प्रोफ़ाइल",
            continueLastStepText = "अंतिम चरण से जारी रखें"
        )

        AgentLanguage.RU -> LocalizedAiUi(
            languageSettingTitle = "🌐 Язык / Language",
            emptyTitle = "Что мы будем создавать?",
            emptySubtitle = "Агент умеет проектировать экраны, собирать блоки логики, управлять библиотеками и автоматически исправлять ошибки сборки.",
            suggestionBuildZeroTitle = "Создать приложение с нуля",
            suggestionFixErrorTitle = "Проверить и исправить ошибки",
            suggestionRedesignUiTitle = "Улучшить UI этого экрана",
            suggestionExplainProjectTitle = "Объяснить архитектуру проекта",
            composerHint = "Введите / для быстрых команд или напишите задачу…",
            thinkingText = "Думаю…",
            disclaimerText = "ИИ может ошибаться. Всегда проверяйте сборку и логику.",
            noApiKeyText = "Нет API-ключа",
            configureModelText = "Выбрать модель",
            workingStepsFormat = "Выполняется · %d шаг.",
            completedStepsFormat = "Выполнено шагов: %d",
            failedStepsFormat = "Выполнено: %d · Ошибок: %d",
            planTitle = "План задач",
            allTasksCompleted = "Все задачи выполнены",
            copyText = "Копировать",
            copiedToast = "Скопировано в буфер обмена",
            closeText = "Закрыть",
            cancelText = "Отмена",
            saveAndCloseText = "Сохранить и закрыть",
            newChatTitle = "Начать новый диалог?",
            newChatConfirmMsg = "История чата этого проекта будет очищена. Файлы проекта не изменятся.",
            newChatStartBtn = "Начать заново",
            menuSettings = "Настройки API и MCP",
            menuLanguage = "Язык / Language",
            menuExtensions = "MCP, Плагины и Навыки (Skills)",
            menuTerminal = "Терминал (Linux / Debian)",
            menuCheckpoints = "Контрольные точки (Rollback)",
            menuCopyTranscript = "Скопировать весь чат",
            settingsDialogTitle = "Настройки AI Agent Studio",
            settingsProviderLabel = "1. Выберите AI провайдера (BYOK)",
            settingsLanguageLabel = "🌐 Язык интерфейса и ответов ИИ",
            settingsSaveKeyBtn = "Сохранить ключ и модель",
            settingsDeleteKeyBtn = "Удалить API-ключ",
            settingsSavedProfilesTitle = "Сохранённые ключи и модели",
            continueLastStepText = "Продолжить с последнего шага"
        )

        AgentLanguage.ZH -> LocalizedAiUi(
            languageSettingTitle = "🌐 语言 / Language",
            emptyTitle = "今天想构建什么应用？",
            emptySubtitle = "AI 智能体可自动设计界面、编写积木逻辑、管理文件与依赖库，并自动编译与修复错误。",
            suggestionBuildZeroTitle = "从零构建完整应用",
            suggestionFixErrorTitle = "检查并自动修复错误",
            suggestionRedesignUiTitle = "美化当前界面 UI",
            suggestionExplainProjectTitle = "分析并讲解项目架构",
            composerHint = "输入 / 查看快捷指令，或直接输入任务描述…",
            thinkingText = "思考中…",
            disclaimerText = "AI 可能会出错，请始终检查编译结果与逻辑。",
            noApiKeyText = "未配置 API 密钥",
            configureModelText = "配置模型",
            workingStepsFormat = "正在执行 · %d 个步骤",
            completedStepsFormat = "已执行 %d 个步骤",
            failedStepsFormat = "已执行 %d 步 · %d 步失败",
            planTitle = "任务计划",
            allTasksCompleted = "所有任务已完成",
            copyText = "复制",
            copiedToast = "已复制到剪贴板",
            closeText = "关闭",
            cancelText = "取消",
            saveAndCloseText = "保存并关闭",
            newChatTitle = "开始新对话？",
            newChatConfirmMsg = "当前项目的聊天记录与任务计划将被清空，项目文件不受影响。",
            newChatStartBtn = "新对话",
            menuSettings = "API 与 MCP 设置",
            menuLanguage = "语言 / Language",
            menuExtensions = "MCP、插件与技能中心",
            menuTerminal = "Linux / Debian 终端",
            menuCheckpoints = "项目快照与回滚 (Checkpoints)",
            menuCopyTranscript = "复制完整对话记录",
            settingsDialogTitle = "AI Agent Studio 设置",
            settingsProviderLabel = "1. 选择 AI 服务商 (BYOK)",
            settingsLanguageLabel = "🌐 界面与 AI 回复语言 (Language)",
            settingsSaveKeyBtn = "保存密钥与模型",
            settingsDeleteKeyBtn = "删除 API 密钥",
            settingsSavedProfilesTitle = "已保存的密钥与模型配置",
            continueLastStepText = "从上一步继续执行"
        )

        AgentLanguage.JA -> LocalizedAiUi(
            languageSettingTitle = "🌐 言語 / Language",
            emptyTitle = "今日は何を作りましょうか？",
            emptySubtitle = "AIエージェントが画面設計、ロジックブロック構築、ファイル・ライブラリ管理、APKコンパイルとエラー修復を自動で行います。",
            suggestionBuildZeroTitle = "ゼロからアプリを構築",
            suggestionFixErrorTitle = "エラーを検査・自動修復",
            suggestionRedesignUiTitle = "この画面のUIを洗練する",
            suggestionExplainProjectTitle = "プロジェクト構造を解説",
            composerHint = "/ でクイックコマンド、または指示を入力…",
            thinkingText = "思考中…",
            disclaimerText = "AIは誤る場合があります。ビルド結果とロジックを確認してください。",
            noApiKeyText = "APIキー未設定",
            configureModelText = "モデル設定",
            workingStepsFormat = "実行中 · %d ステップ",
            completedStepsFormat = "%d ステップ完了",
            failedStepsFormat = "%d ステップ実行 · %d 失敗",
            planTitle = "タスク計画",
            allTasksCompleted = "すべてのタスクが完了しました",
            copyText = "コピー",
            copiedToast = "クリップボードにコピーしました",
            closeText = "閉じる",
            cancelText = "キャンセル",
            saveAndCloseText = "保存して閉じる",
            newChatTitle = "新しい会話を開始しますか？",
            newChatConfirmMsg = "このプロジェクトのチャット履歴を消去します。プロジェクトファイルは変更されません。",
            newChatStartBtn = "新規開始",
            menuSettings = "API & MCP 設定",
            menuLanguage = "言語 / Language",
            menuExtensions = "MCP・プラグイン・スキル管理",
            menuTerminal = "ターミナル (Linux / Debian)",
            menuCheckpoints = "チェックポイントと復元",
            menuCopyTranscript = "会話全体をコピー",
            settingsDialogTitle = "AI Agent Studio 設定",
            settingsProviderLabel = "1. AIプロバイダーを選択 (BYOK)",
            settingsLanguageLabel = "🌐 UIとAI応答の言語 (Language)",
            settingsSaveKeyBtn = "キーとモデルを保存",
            settingsDeleteKeyBtn = "APIキーを削除",
            settingsSavedProfilesTitle = "保存済みのAPIキーとモデル",
            continueLastStepText = "最後のステップから続行"
        )

        AgentLanguage.KO -> LocalizedAiUi(
            languageSettingTitle = "🌐 언어 / Language",
            emptyTitle = "무엇을 만들어 볼까요?",
            emptySubtitle = "에이전트가 화면 설계, 로직 블록 구성, 파일 및 라이브러리 관리, APK 컴파일 및 오류 자동 수정을 수행합니다.",
            suggestionBuildZeroTitle = "처음부터 앱 만들기",
            suggestionFixErrorTitle = "오류 확인 및 자동 수정",
            suggestionRedesignUiTitle = "현재 화면 UI 개선",
            suggestionExplainProjectTitle = "프로젝트 구조 설명",
            composerHint = "/ 를 입력해 빠른 명령어 보기, 또는 지시사항 입력…",
            thinkingText = "생각 중…",
            disclaimerText = "AI는 실수할 수 있습니다. 항상 빌드 결과와 로직을 확인하세요.",
            noApiKeyText = "API 키 없음",
            configureModelText = "모델 설정",
            workingStepsFormat = "진행 중 · %d단계",
            completedStepsFormat = "%d단계 완료",
            failedStepsFormat = "%d단계 실행 · %d 실패",
            planTitle = "작업 계획",
            allTasksCompleted = "모든 작업 완료",
            copyText = "복사",
            copiedToast = "클립보드에 복사되었습니다",
            closeText = "닫기",
            cancelText = "취소",
            saveAndCloseText = "저장 후 닫기",
            newChatTitle = "새 대화를 시작할까요?",
            newChatConfirmMsg = "이 프로젝트의 대화 기록이 초기화됩니다. 프로젝트 파일은 유지됩니다.",
            newChatStartBtn = "새로 시작",
            menuSettings = "API 및 MCP 설정",
            menuLanguage = "언어 / Language",
            menuExtensions = "MCP, 플러그인 및 스킬 허브",
            menuTerminal = "터미널 (Linux / Debian)",
            menuCheckpoints = "체크포인트 및 복원",
            menuCopyTranscript = "전체 대화 복사",
            settingsDialogTitle = "AI Agent Studio 설정",
            settingsProviderLabel = "1. AI 제공자 선택 (BYOK)",
            settingsLanguageLabel = "🌐 인터페이스 및 AI 응답 언어",
            settingsSaveKeyBtn = "키 및 모델 저장",
            settingsDeleteKeyBtn = "API 키 삭제",
            settingsSavedProfilesTitle = "저장된 API 키 및 모델 프로필",
            continueLastStepText = "마지막 단계에서 계속"
        )

        AgentLanguage.FR -> LocalizedAiUi(
            languageSettingTitle = "🌐 Langue / Language",
            emptyTitle = "Que voulez-vous créer ?",
            emptySubtitle = "L'agent peut concevoir des écrans, assembler des blocs logiques, gérer les fichiers et bibliothèques, compiler et corriger les erreurs automatiquement.",
            suggestionBuildZeroTitle = "Créer une application de zéro",
            suggestionFixErrorTitle = "Vérifier et corriger les erreurs",
            suggestionRedesignUiTitle = "Embellir l'interface de cet écran",
            suggestionExplainProjectTitle = "Expliquer le projet",
            composerHint = "Tapez / pour les commandes rapides, ou écrivez une instruction…",
            thinkingText = "Réflexion…",
            disclaimerText = "L'IA peut se tromper. Vérifiez toujours la compilation.",
            noApiKeyText = "Aucune clé API",
            configureModelText = "Configurer le modèle",
            workingStepsFormat = "En cours · %d étapes",
            completedStepsFormat = "%d étapes exécutées",
            failedStepsFormat = "%d étapes exécutées · %d échecs",
            planTitle = "Plan",
            allTasksCompleted = "Toutes les tâches sont terminées",
            copyText = "Copier",
            copiedToast = "Copié dans le presse-papiers",
            closeText = "Fermer",
            cancelText = "Annuler",
            saveAndCloseText = "Enregistrer et fermer",
            newChatTitle = "Nouvelle conversation ?",
            newChatConfirmMsg = "L'historique de discussion de ce projet sera effacé. Les fichiers du projet restent intacts.",
            newChatStartBtn = "Nouveau",
            menuSettings = "Paramètres API et MCP",
            menuLanguage = "Langue / Language",
            menuExtensions = "Écosystème MCP, Plugins et Skills",
            menuTerminal = "Terminal (Linux / Debian)",
            menuCheckpoints = "Points de contrôle et restauration",
            menuCopyTranscript = "Copier toute la conversation",
            settingsDialogTitle = "Paramètres AI Agent Studio",
            settingsProviderLabel = "1. Choisir le fournisseur IA (BYOK)",
            settingsLanguageLabel = "🌐 Langue de l'interface et des réponses IA",
            settingsSaveKeyBtn = "Enregistrer Clé & Modèle",
            settingsDeleteKeyBtn = "Supprimer la clé API",
            settingsSavedProfilesTitle = "Profils de clés et modèles enregistrés",
            continueLastStepText = "Continuer depuis la dernière étape"
        )

        AgentLanguage.DE -> LocalizedAiUi(
            languageSettingTitle = "🌐 Sprache / Language",
            emptyTitle = "Was möchten wir heute bauen?",
            emptySubtitle = "Der Agent kann Bildschirme entwerfen, Logikblöcke erstellen, Dateien & Bibliotheken verwalten sowie automatisch kompilieren und Fehler beheben.",
            suggestionBuildZeroTitle = "App von Grund auf erstellen",
            suggestionFixErrorTitle = "Fehler prüfen & beheben",
            suggestionRedesignUiTitle = "UI dieses Bildschirms verschönern",
            suggestionExplainProjectTitle = "Projekt erklären",
            composerHint = "Tippe / für Schnellbefehle oder gib eine Anweisung ein…",
            thinkingText = "Denkt nach…",
            disclaimerText = "KI kann Fehler machen. Prüfe immer Build & Logik.",
            noApiKeyText = "Kein API-Schlüssel",
            configureModelText = "Modell einrichten",
            workingStepsFormat = "Arbeitet · %d Schritte",
            completedStepsFormat = "%d Schritte ausgeführt",
            failedStepsFormat = "%d Schritte · %d fehlgeschlagen",
            planTitle = "Aufgabenplan",
            allTasksCompleted = "Alle Aufgaben erledigt",
            copyText = "Kopieren",
            copiedToast = "In die Zwischenablage kopiert",
            closeText = "Schließen",
            cancelText = "Abbrechen",
            saveAndCloseText = "Speichern & Schließen",
            newChatTitle = "Neues Gespräch starten?",
            newChatConfirmMsg = "Der Chatverlauf dieses Projekts wird gelöscht. Projektdateien bleiben unverändert.",
            newChatStartBtn = "Neu starten",
            menuSettings = "API- & MCP-Einstellungen",
            menuLanguage = "Sprache / Language",
            menuExtensions = "MCP, Plugins & Skills",
            menuTerminal = "Terminal (Linux / Debian)",
            menuCheckpoints = "Checkpoints & Wiederherstellung",
            menuCopyTranscript = "Gesamten Chat kopieren",
            settingsDialogTitle = "AI Agent Studio Einstellungen",
            settingsProviderLabel = "1. KI-Anbieter wählen (BYOK)",
            settingsLanguageLabel = "🌐 Sprache für Oberfläche & KI-Antworten",
            settingsSaveKeyBtn = "Schlüssel & Modell speichern",
            settingsDeleteKeyBtn = "API-Schlüssel löschen",
            settingsSavedProfilesTitle = "Gespeicherte Schlüssel- & Modellprofile",
            continueLastStepText = "Ab dem letzten Schritt fortfahren"
        )

        AgentLanguage.TR -> LocalizedAiUi(
            languageSettingTitle = "🌐 Dil / Language",
            emptyTitle = "Bugün ne inşa etmekطرح edersiniz?",
            emptySubtitle = "Ajan ekranlar tasarlayabilir, mantık blokları oluşturabilir, dosya ve kütüphaneleri yönetebilir, APK derleyip hataları otomatik onarabilir.",
            suggestionBuildZeroTitle = "Sıfırdan uygulama oluştur",
            suggestionFixErrorTitle = "Hataları kontrol et ve düzelt",
            suggestionRedesignUiTitle = "Bu ekranın arayüzünü güzelleştir",
            suggestionExplainProjectTitle = "Projeyi açıkla",
            composerHint = "Hızlı komutlar için / yazın veya talimatınızı girin…",
            thinkingText = "Düşünüyor…",
            disclaimerText = "Yapay zeka hata yapabilir. Derleme ve mantığı her zaman kontrol edin.",
            noApiKeyText = "API anahtarı yok",
            configureModelText = "Modeli ayarla",
            workingStepsFormat = "Çalışıyor · %d adım",
            completedStepsFormat = "%d adım tamamlandı",
            failedStepsFormat = "%d adım · %d başarısız",
            planTitle = "Görev Planı",
            allTasksCompleted = "Tüm görevler tamamlandı",
            copyText = "Kopyala",
            copiedToast = "Panoya kopyalandı",
            closeText = "Kapat",
            cancelText = "İptal",
            saveAndCloseText = "Kaydet ve Kapat",
            newChatTitle = "Yeni sohbet başlatılsın mı?",
            newChatConfirmMsg = "Bu projenin sohbet geçmişi silinecek. Proje dosyaları değişmez.",
            newChatStartBtn = "Yeni başlat",
            menuSettings = "API ve MCP Ayarları",
            menuLanguage = "Dil / Language",
            menuExtensions = "MCP, Eklenti ve Beceri Merkezi",
            menuTerminal = "Terminal (Linux / Debian)",
            menuCheckpoints = "Kontrol Noktaları ve Geri Yükleme",
            menuCopyTranscript = "Tüm sohbeti kopyala",
            settingsDialogTitle = "AI Agent Studio Ayarları",
            settingsProviderLabel = "1. Yapay Zeka Sağlayıcısını Seçin (BYOK)",
            settingsLanguageLabel = "🌐 Arayüz ve Yapay Zeka Yanıt Dili",
            settingsSaveKeyBtn = "Anahtar ve Modeli Kaydet",
            settingsDeleteKeyBtn = "API Anahtarını Sil",
            settingsSavedProfilesTitle = "Kayıtlı API Anahtarı ve Model Profilleri",
            continueLastStepText = "Son adımdan devam et"
        )

        AgentLanguage.VI -> LocalizedAiUi(
            languageSettingTitle = "🌐 Ngôn ngữ / Language",
            emptyTitle = "Bạn muốn xây dựng ứng dụng gì?",
            emptySubtitle = "AI Agent có thể thiết kế màn hình, lắp ghép khối logic, quản lý tệp & thư viện, tự động biên dịch APK và sửa lỗi.",
            suggestionBuildZeroTitle = "Xây dựng ứng dụng từ đầu",
            suggestionFixErrorTitle = "Kiểm tra & sửa lỗi tự động",
            suggestionRedesignUiTitle = "Làm đẹp giao diện màn hình này",
            suggestionExplainProjectTitle = "Giải thích cấu trúc dự án",
            composerHint = "Nhập / để mở lệnh nhanh, hoặc viết yêu cầu của bạn…",
            thinkingText = "Đang suy nghĩ…",
            disclaimerText = "AI có thể nhầm lẫn. Hãy luôn kiểm tra kết quả build & logic.",
            noApiKeyText = "Chưa có API key",
            configureModelText = "Chọn mô hình",
            workingStepsFormat = "Đang thực hiện · %d bước",
            completedStepsFormat = "Đã chạy %d bước",
            failedStepsFormat = "Đã chạy %d bước · %d lỗi",
            planTitle = "Kế hoạch",
            allTasksCompleted = "Đã hoàn thành mọi tác vụ",
            copyText = "Sao chép",
            copiedToast = "Đã sao chép vào bộ nhớ tạm",
            closeText = "Đóng",
            cancelText = "Hủy",
            saveAndCloseText = "Lưu & Đóng",
            newChatTitle = "Bắt đầu cuộc trò chuyện mới?",
            newChatConfirmMsg = "Lịch sử trò chuyện của dự án này sẽ bị xóa. Dữ liệu dự án không bị ảnh hưởng.",
            newChatStartBtn = "Bắt đầu mới",
            menuSettings = "Cài đặt API & MCP",
            menuLanguage = "Ngôn ngữ / Language",
            menuExtensions = "Hệ sinh thái MCP, Plugin & Skill",
            menuTerminal = "Terminal (Linux / Debian)",
            menuCheckpoints = "Điểm sao lưu & Khôi phục",
            menuCopyTranscript = "Sao chép toàn bộ trò chuyện",
            settingsDialogTitle = "Cài đặt AI Agent Studio",
            settingsProviderLabel = "1. Chọn nhà cung cấp AI (BYOK)",
            settingsLanguageLabel = "🌐 Ngôn ngữ Giao diện & Phản hồi AI",
            settingsSaveKeyBtn = "Lưu Key & Model",
            settingsDeleteKeyBtn = "Xóa API Key",
            settingsSavedProfilesTitle = "Danh sách API Key & Model đã lưu",
            continueLastStepText = "Tiếp tục từ bước cuối"
        )

        AgentLanguage.TH -> LocalizedAiUi(
            languageSettingTitle = "🌐 ภาษา / Language",
            emptyTitle = "วันนี้เราอยากสร้างแอปอะไร?",
            emptySubtitle = "เอเจนต์สามารถออกแบบหน้าจอ สร้างบล็อกลอจิก จัดการไฟล์และไลบรารี พร้อมคอมไพล์และแก้ไขข้อผิดพลาดโดยอัตโนมัติ",
            suggestionBuildZeroTitle = "สร้างแอปใหม่ตั้งแต่ศูนย์",
            suggestionFixErrorTitle = "ตรวจสอบและแก้ไขข้อผิดพลาด",
            suggestionRedesignUiTitle = "ปรับแต่ง UI หน้าจอนี้ให้สวยงาม",
            suggestionExplainProjectTitle = "อธิบายโครงสร้างโปรเจ็กต์",
            composerHint = "พิมพ์ / เพื่อดูคำสั่งด่วน หรือพิมพ์คำสั่งของคุณ…",
            thinkingText = "กำลังคิด…",
            disclaimerText = "AI อาจผิดพลาดได้ โปรดตรวจสอบผลลัพธ์การบิลด์เสมอ",
            noApiKeyText = "ยังไม่มี API key",
            configureModelText = "ตั้งค่าโมเดล",
            workingStepsFormat = "กำลังทำงาน · %d ขั้นตอน",
            completedStepsFormat = "ดำเนินการแล้ว %d ขั้นตอน",
            failedStepsFormat = "ดำเนินการแล้ว %d ขั้นตอน · ล้มเหลว %d",
            planTitle = "แผนงาน",
            allTasksCompleted = "งานทั้งหมดเสร็จสมบูรณ์",
            copyText = "คัดลอก",
            copiedToast = "คัดลอกไปยังคลิปบอร์ดแล้ว",
            closeText = "ปิด",
            cancelText = "ยกเลิก",
            saveAndCloseText = "บันทึกและปิด",
            newChatTitle = "เริ่มการสนทนาใหม่หรือไม่?",
            newChatConfirmMsg = "ประวัติแชทของโปรเจ็กต์นี้จะถูกล้าง โดยไฟล์ในโปรเจ็กต์จะไม่เปลี่ยนแปลง",
            newChatStartBtn = "เริ่มใหม่",
            menuSettings = "ตั้งค่า API และ MCP",
            menuLanguage = "ภาษา / Language",
            menuExtensions = "ศูนย์รวม MCP, ปลั๊กอิน และสกิล",
            menuTerminal = "เทอร์มินัล (Linux / Debian)",
            menuCheckpoints = "จุดบันทึกและย้อนกลับ (Checkpoints)",
            menuCopyTranscript = "คัดลอกบทสนทนาทั้งหมด",
            settingsDialogTitle = "การตั้งค่า AI Agent Studio",
            settingsProviderLabel = "1. เลือกผู้ให้บริการ AI (BYOK)",
            settingsLanguageLabel = "🌐 ภาษาของอินเทอร์เฟซและการตอบกลับของ AI",
            settingsSaveKeyBtn = "บันทึกคีย์และโมเดล",
            settingsDeleteKeyBtn = "ลบ API Key",
            settingsSavedProfilesTitle = "รายการ API Key และโมเดลที่บันทึกไว้",
            continueLastStepText = "ทำต่อจากขั้นตอนล่าสุด"
        )

        AgentLanguage.MS -> LocalizedAiUi(
            languageSettingTitle = "🌐 Bahasa / Language",
            emptyTitle = "Apa yang ingin kita bina hari ini?",
            emptySubtitle = "Ejen boleh mereka bentuk skrin, menyusun blok logik, mengurus fail & pustaka, serta mengompil dan membaiki ralat secara automatik.",
            suggestionBuildZeroTitle = "Bina aplikasi dari 0",
            suggestionFixErrorTitle = "Semak & baiki ralat",
            suggestionRedesignUiTitle = "Cantikkan UI skrin ini",
            suggestionExplainProjectTitle = "Terangkan projek ini",
            composerHint = "Taip / untuk arahan pantas, atau tulis arahan anda…",
            thinkingText = "Sedang berfikir…",
            disclaimerText = "AI mungkin melakukan kesilapan. Sentiasa semak hasil binaan & logik.",
            noApiKeyText = "Tiada kunci API",
            configureModelText = "Tetapkan model",
            workingStepsFormat = "Sedang bekerja · %d langkah",
            completedStepsFormat = "%d langkah dijalankan",
            failedStepsFormat = "%d langkah dijalankan · %d gagal",
            planTitle = "Pelan Tugasan",
            allTasksCompleted = "Semua tugasan selesai",
            copyText = "Salin",
            copiedToast = "Disalin ke papan keratan",
            closeText = "Tutup",
            cancelText = "Batal",
            saveAndCloseText = "Simpan & Tutup",
            newChatTitle = "Mulakan perbualan baharu?",
            newChatConfirmMsg = "Sejarah sembang projek ini akan dipadamkan. Fail projek tidak berubah.",
            newChatStartBtn = "Mula baharu",
            menuSettings = "Tetapan API & MCP",
            menuLanguage = "Bahasa / Language",
            menuExtensions = "Ekosistem MCP, Plugin & Kemahiran",
            menuTerminal = "Terminal (Linux / Debian)",
            menuCheckpoints = "Titik Semak & Pulihkan (Rollback)",
            menuCopyTranscript = "Salin seluruh perbualan",
            settingsDialogTitle = "Tetapan AI Agent Studio",
            settingsProviderLabel = "1. Pilih Penyedia AI (BYOK)",
            settingsLanguageLabel = "🌐 Bahasa Antara Muka & Respons AI",
            settingsSaveKeyBtn = "Simpan Kunci & Model",
            settingsDeleteKeyBtn = "Padam Kunci API",
            settingsSavedProfilesTitle = "Senarai Kunci API & Model Tersimpan",
            continueLastStepText = "Teruskan dari langkah terakhir"
        )

        else -> LocalizedAiUi(
            languageSettingTitle = "🌐 Language / Bahasa",
            emptyTitle = "What shall we build today?",
            emptySubtitle = "The agent can design screens, assemble logic blocks, manage files & libraries, and compile & self-heal errors automatically.",
            suggestionBuildZeroTitle = "Build an app from scratch",
            suggestionFixErrorTitle = "Check & fix project errors",
            suggestionRedesignUiTitle = "Redesign this screen's UI",
            suggestionExplainProjectTitle = "Explain project architecture",
            composerHint = "Type / for quick commands, or ask the agent…",
            thinkingText = "Thinking…",
            disclaimerText = "AI can make mistakes. Always verify build results & logic.",
            noApiKeyText = "No API key configured",
            configureModelText = "Set model",
            workingStepsFormat = "Working · %d steps",
            completedStepsFormat = "%d steps executed",
            failedStepsFormat = "%d steps executed · %d failed",
            planTitle = "Task Plan",
            allTasksCompleted = "All tasks completed",
            copyText = "Copy",
            copiedToast = "Copied to clipboard",
            closeText = "Close",
            cancelText = "Cancel",
            saveAndCloseText = "Save & Close",
            newChatTitle = "Start a new conversation?",
            newChatConfirmMsg = "Chat history and task plan for this project will be cleared. Project files remain untouched.",
            newChatStartBtn = "Start new",
            menuSettings = "API & MCP Settings",
            menuLanguage = "Language / Bahasa",
            menuExtensions = "MCP, Plugins & Skills Hub",
            menuTerminal = "Terminal (Linux / Debian)",
            menuCheckpoints = "Checkpoints & Rollback",
            menuCopyTranscript = "Copy full transcript",
            settingsDialogTitle = "AI Agent Studio Settings",
            settingsProviderLabel = "1. Select AI Provider (BYOK)",
            settingsLanguageLabel = "🌐 UI & AI Response Language",
            settingsSaveKeyBtn = "Save Key & Model",
            settingsDeleteKeyBtn = "Delete API Key",
            settingsSavedProfilesTitle = "Saved API Key & Model Profiles",
            continueLastStepText = "Continue from last step"
        )
    }

    @JvmStatic
    fun showLanguagePickerDialog(
        context: Context,
        onChanged: ((AgentLanguage) -> Unit)? = null
    ) {
        val config = AiConfigManager(context)
        val entries = AgentLanguage.entries
        val currentLang = config.agentLanguage
        val checkedIndex = entries.indexOf(currentLang).coerceAtLeast(0)
        val labels = entries.map { lang ->
            if (lang == AgentLanguage.AUTO) {
                "${lang.displayLabel} → ${lang.resolved().displayLabel}"
            } else {
                lang.displayLabel
            }
        }.toTypedArray()

        val ui = current(context)
        MaterialAlertDialogBuilder(context)
            .setTitle(ui.languageSettingTitle)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val chosen = entries[which]
                config.agentLanguage = chosen
                val newUi = forLanguage(chosen)
                SketchwareUtil.toast("${newUi.languageSettingTitle}: ${chosen.displayLabel}")
                onChanged?.invoke(chosen)
                dialog.dismiss()
            }
            .setNegativeButton(ui.cancelText, null)
            .show()
    }
}
