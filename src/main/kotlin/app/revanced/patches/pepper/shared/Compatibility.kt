package app.revanced.patches.pepper.shared

/**
 * All Pepper.com Group / TippingCanoe sister apps that share the same
 * obfuscated codebase. Every fingerprint string and class name used by
 * the patches in this module matches identically across the family.
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
    "com.pepperdeals",                 // Pepper.com (US)
    "se.pepperdeals",                  // Pepper SE
)
