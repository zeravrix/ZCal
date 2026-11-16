package app.zcal.domain.model

enum class AppLanguage(
    val code: String,
    val title: String,
    val nativeTitle: String,
    val isRtl: Boolean = false
) {
    ENGLISH("en", "English", "English", false),
    ARABIC("ar", "Arabic", "العربية", true),
    FRENCH("fr", "French", "Français", false),
    SPANISH("es", "Spanish", "Español", false),
    JAPANESE("ja", "Japanese", "日本語", false),
    CHINESE("zh", "Chinese", "中文", false),
    KOREAN("ko", "Korean", "한국어", false),
    GERMAN("de", "German", "Deutsch", false);

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}
