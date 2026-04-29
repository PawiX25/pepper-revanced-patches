package app.revanced.patches.pepper.shared

/**
 * All Pepper.com Group / TippingCanoe sister apps that share the same
 * obfuscated codebase as Pepper PL 8.12.00. Verified at this version: every
 * fingerprint string ("liveramp.com", "custom_app_icon_tier1_default",
 * "custom_app_icon_event_theming_black_friday", MainActivity class name)
 * matches identically across all of them.
 */
internal val pepperFamilyPackages = arrayOf(
    "com.tippingcanoe.pepperpl",       // Pepper PL
    "com.tippingcanoe.peppernl",       // Pepper NL
    "com.tippingcanoe.mydealz",        // Mydealz (DE)
    "com.tippingcanoe.hukd",           // HotUKDeals (UK)
    "com.tippingcanoe.promodescuentos",// PromoDescuentos (MX)
    "com.chollometro",                 // Chollometros (ES)
    "com.dealabs.apps.android",        // Dealabs (FR)
    "com.preisjaeger",                 // Preisjäger (AT)
    "com.pepperdeals",                 // Pepper.com (US/global)
    "se.pepperdeals",                  // Pepper SE
)
