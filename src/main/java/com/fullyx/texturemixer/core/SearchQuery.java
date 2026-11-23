package com.fullyx.texturemixer.core;

import java.util.Arrays;
import java.util.Locale;

/** Match both word queries and names written without their visual separators. */
public final class SearchQuery {
    private SearchQuery() {}

    public static boolean matches(String value, String query) {
        String searchable = normalize(value);
        return Arrays.stream(query.strip().split("\\s+"))
                .map(SearchQuery::normalize).allMatch(searchable::contains);
    }

    private static String normalize(String value) {
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "").toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", "");
    }
}
