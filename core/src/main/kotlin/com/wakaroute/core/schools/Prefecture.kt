package com.wakaroute.core.schools

/**
 * The 47 都道府県 with their JIS codes, as the catalogue expects them.
 *
 * Known rather than fetched: they do not change, and a search filter that
 * cannot be drawn until a request succeeds is a filter that does not work on a
 * train. The code is the key — 「東京」 and 「東京都」 are the same place, and
 * matching on the name would make that a bug.
 */
data class Prefecture(val code: String, val name: String) {
    companion object {
        val all: List<Prefecture> = listOf(
            Prefecture("01", "北海道"),
            Prefecture("02", "青森県"),
            Prefecture("03", "岩手県"),
            Prefecture("04", "宮城県"),
            Prefecture("05", "秋田県"),
            Prefecture("06", "山形県"),
            Prefecture("07", "福島県"),
            Prefecture("08", "茨城県"),
            Prefecture("09", "栃木県"),
            Prefecture("10", "群馬県"),
            Prefecture("11", "埼玉県"),
            Prefecture("12", "千葉県"),
            Prefecture("13", "東京都"),
            Prefecture("14", "神奈川県"),
            Prefecture("15", "新潟県"),
            Prefecture("16", "富山県"),
            Prefecture("17", "石川県"),
            Prefecture("18", "福井県"),
            Prefecture("19", "山梨県"),
            Prefecture("20", "長野県"),
            Prefecture("21", "岐阜県"),
            Prefecture("22", "静岡県"),
            Prefecture("23", "愛知県"),
            Prefecture("24", "三重県"),
            Prefecture("25", "滋賀県"),
            Prefecture("26", "京都府"),
            Prefecture("27", "大阪府"),
            Prefecture("28", "兵庫県"),
            Prefecture("29", "奈良県"),
            Prefecture("30", "和歌山県"),
            Prefecture("31", "鳥取県"),
            Prefecture("32", "島根県"),
            Prefecture("33", "岡山県"),
            Prefecture("34", "広島県"),
            Prefecture("35", "山口県"),
            Prefecture("36", "徳島県"),
            Prefecture("37", "香川県"),
            Prefecture("38", "愛媛県"),
            Prefecture("39", "高知県"),
            Prefecture("40", "福岡県"),
            Prefecture("41", "佐賀県"),
            Prefecture("42", "長崎県"),
            Prefecture("43", "熊本県"),
            Prefecture("44", "大分県"),
            Prefecture("45", "宮崎県"),
            Prefecture("46", "鹿児島県"),
            Prefecture("47", "沖縄県"),
        )

        fun named(code: String?): String? =
            code?.let { wanted -> all.firstOrNull { it.code == wanted }?.name }
    }
}
