package org.cpuoff.cetacid.globals

import java.util.Locale

/**
 * The actual system locale, because [Locale.getDefault] will be set to the string resource locale.
 *
 * This is only meant to be set by [org.cpuoff.cetacid.MainActivity]!
 */
@Volatile var SystemLocale: Locale = Locale.getDefault()
