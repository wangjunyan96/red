package com.red.cloud.token;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token 类型注册表。导入 Token、结义从库取号都认这里的 code。
 *
 * 后续新游戏号源：在 TYPES 里追加即可，不必改导入页结构。
 */
@Component
public class TokenTypeCatalog {
    public static final String HERO_KILLER = "hero_killer";

    private final List<TokenTypeDef> types = List.of(
        new TokenTypeDef(HERO_KILLER, "英雄杀")
        // 其它类型在此追加，例如：
        // new TokenTypeDef("other_game", "其它游戏")
    );

    public List<TokenTypeDef> types() {
        return types;
    }

    public TokenTypeDef require(String tokenType) {
        String code = tokenType == null ? "" : tokenType.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("请选择 Token 类型");
        }
        return types.stream()
            .filter(item -> item.code().equalsIgnoreCase(code))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知 Token 类型: " + code));
    }

    public String name(String tokenType) {
        try {
            return require(tokenType).name();
        } catch (IllegalArgumentException ex) {
            return tokenType == null || tokenType.isBlank() ? "-" : tokenType;
        }
    }
}
