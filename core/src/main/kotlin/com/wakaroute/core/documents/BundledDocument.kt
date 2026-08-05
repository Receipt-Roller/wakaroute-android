package com.wakaroute.core.documents

/**
 * The service's own documents, shipped inside the app.
 *
 * Bundled rather than fetched so the text is guaranteed present at store review
 * and offline. The cost is that changing a word needs a release — a trade worth
 * making only because these are the documents a student is entitled to read
 * before agreeing to anything.
 *
 * The four legal documents are **verbatim copies** of the iOS bundle, which is
 * itself the Web text. 開発ガイド §3 requires 同一内容, so they are not edited
 * here — including where they describe Phase 2 features this build does not yet
 * have. See [PHASE_1_CAVEAT]; the screen shows it, the documents stay untouched.
 */
enum class BundledDocument(val resource: String, val title: String) {
    About("legal-about", "ワカルートとは"),
    ExamGuide("guide-exam", "高校受験とは"),
    ForParents("legal-for-parents", "保護者の方へ"),
    Terms("legal-terms", "利用規約"),
    Privacy("legal-privacy", "プライバシーポリシー");

    /** The raw HTML, or null when the resource is missing. */
    fun html(): String? =
        BundledDocument::class.java.getResourceAsStream("/documents/$resource.html")
            ?.bufferedReader()
            ?.use { it.readText() }

    fun blocks(): List<DocumentBlock> = html()?.let(DocumentParser::parse).orEmpty()

    /**
     * Text still waiting on a decision, written as 【…】.
     *
     * A placeholder reaching a student is worse than an incomplete document:
     * 【運営者名】 in a published privacy policy is a legal document that names
     * nobody. The test suite fails while any remain, so this cannot ship by
     * being forgotten.
     */
    fun placeholders(): List<String> {
        val html = html() ?: return emptyList()
        return PLACEHOLDER_PATTERN.findAll(html).map { it.groupValues[1] }.toList()
    }

    companion object {
        private val PLACEHOLDER_PATTERN = Regex("【([^】]*)】")

        /**
         * What the bundled documents describe but this build does not do yet.
         *
         * Shown alongside them rather than edited into them. The alternative
         * was rewriting 利用規約 and プライバシーポリシー to match Phase 1, which
         * would put the app's legal text out of step with the Web — the exact
         * thing 開発ガイド §3 forbids — and is not a decision to make in code.
         *
         * Raised as an AB question; see AGENTS.md.
         */
        const val PHASE_1_CAVEAT: String =
            "この文書は、ワカルートのすべての機能について書かれています。" +
                "このアプリはまだ Phase 1 で、学習記録・クイズ・志望校の登録は動いていません。" +
                "文書のうちそれらに関する部分は、これからのバージョンについての説明です。"
    }
}
