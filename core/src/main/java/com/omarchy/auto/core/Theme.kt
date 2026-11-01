package com.omarchy.auto.core

/**
 * Shared Omarchy palette. Colors are ARGB ints so the same values work in the
 * Android Canvas renderer and in Compose/View previews.
 */
data class OmarchyTheme(
    val id: String,
    val name: String,
    val shortName: String,
    val background: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val foreground: Int,
    val dim: Int,
    val border: Int,
    val accent: Int,
    val green: Int,
    val yellow: Int,
    val red: Int,
)

object Themes {
    val all: List<OmarchyTheme> = listOf(
        OmarchyTheme(
            id = "tokyo-night",
            name = "TOKYO NIGHT",
            shortName = "TOKYO",
            background = 0xFF1A1B26.toInt(),
            surface = 0xFF16161E.toInt(),
            surfaceAlt = 0xFF1F2335.toInt(),
            foreground = 0xFFC0CAF5.toInt(),
            dim = 0xFF565F89.toInt(),
            border = 0xFF2F3549.toInt(),
            accent = 0xFF7AA2F7.toInt(),
            green = 0xFF9ECE6A.toInt(),
            yellow = 0xFFE0AF68.toInt(),
            red = 0xFFF7768E.toInt(),
        ),
        OmarchyTheme(
            id = "osaka-jade",
            name = "OSAKA JADE",
            shortName = "OSAKA",
            background = 0xFF111C18.toInt(),
            surface = 0xFF0D1512.toInt(),
            surfaceAlt = 0xFF17251F.toInt(),
            foreground = 0xFFD7E5D9.toInt(),
            dim = 0xFF5F7A6C.toInt(),
            border = 0xFF22382E.toInt(),
            accent = 0xFF509475.toInt(),
            green = 0xFF8BCBB1.toInt(),
            yellow = 0xFFD4B86A.toInt(),
            red = 0xFFD16D6D.toInt(),
        ),
        OmarchyTheme(
            id = "gruvbox",
            name = "GRUVBOX",
            shortName = "GRUVBOX",
            background = 0xFF1D2021.toInt(),
            surface = 0xFF161819.toInt(),
            surfaceAlt = 0xFF282828.toInt(),
            foreground = 0xFFEBDDB2.toInt(),
            dim = 0xFF928374.toInt(),
            border = 0xFF3C3836.toInt(),
            accent = 0xFF7DAEA3.toInt(),
            green = 0xFFA9B665.toInt(),
            yellow = 0xFFD8A657.toInt(),
            red = 0xFFEA6962.toInt(),
        ),
        OmarchyTheme(
            id = "catppuccin-mocha",
            name = "CATPPUCCIN",
            shortName = "MOCHA",
            background = 0xFF1E1E2E.toInt(),
            surface = 0xFF181825.toInt(),
            surfaceAlt = 0xFF313244.toInt(),
            foreground = 0xFFCDD6F4.toInt(),
            dim = 0xFF6C7086.toInt(),
            border = 0xFF45475A.toInt(),
            accent = 0xFF89B4FA.toInt(),
            green = 0xFFA6E3A1.toInt(),
            yellow = 0xFFF9E2AF.toInt(),
            red = 0xFFF38BA8.toInt(),
        ),
    )

    fun at(index: Int): OmarchyTheme = all[Math.floorMod(index, all.size)]
}
