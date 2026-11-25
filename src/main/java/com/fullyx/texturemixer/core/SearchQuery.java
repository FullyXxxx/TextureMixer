package com.fullyx.texturemixer.core;

import com.fullyx.texturemixer.ui.shared.MixerLayout;

/** Match both word queries and names written without their visual separators. */
public final class SearchQuery {
    private SearchQuery() {}

    public static boolean matches(String value, String query) {
        return MixerLayout.matches(value, query.strip());
    }
}
