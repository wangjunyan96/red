package com.aimovies.platform.service;

import com.aimovies.platform.web.ApiException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts an invite/reunion code from either a raw code or a pasted 口令 text
 * such as: 【和平精英】...复制口令 [ZVmVRVRVRm2fQNOTipM] 即可领取
 */
public final class InviteCodeParser {
    private static final Pattern BRACKET = Pattern.compile("\\[([A-Za-z0-9_-]{4,})\\]");
    private static final Pattern PLAIN = Pattern.compile("^[A-Za-z0-9_-]{4,}$");

    private InviteCodeParser() {
    }

    public static String parse(String input) {
        if (input == null || input.isBlank()) {
            throw ApiException.badRequest("invite code (口令) is required");
        }
        String trimmed = input.trim();
        Matcher m = BRACKET.matcher(trimmed);
        if (m.find()) {
            return m.group(1);
        }
        if (PLAIN.matcher(trimmed).matches()) {
            return trimmed;
        }
        throw ApiException.badRequest("could not find an invite code in the input");
    }
}
