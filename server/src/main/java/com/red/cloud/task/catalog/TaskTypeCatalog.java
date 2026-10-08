package com.red.cloud.task.catalog;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务类型注册表。后台新增任务、脚本领取都认这里的 gameCode + taskType。
 *
 * 后续加新游戏或新任务：在 {@link #games()} 里追加 GameDef / TaskTypeDef，
 * 并写对应 Auto.js，领取时带上同样的 gameCode、taskType。
 * 不必改新增任务接口和后台表单，表单会按 fields 自动渲染。
 */
@Component
public class TaskTypeCatalog {
    public static final String GAME_HERO_KILLER = "hero_killer";
    public static final String TYPE_REUNION = "reunion";
    public static final String TYPE_TIANJI = "tianji";

    private final List<GameDef> games = List.of(
        new GameDef(
            GAME_HERO_KILLER,
            "英雄杀",
            List.of(
                new TaskTypeDef(
                    TYPE_REUNION,
                    "结义",
                    "无需填写 Token，系统从英雄杀 Token 库自动分配未关联数据号。每行一个重逢码，可一次提交多条。",
                    List.of(
                        new TaskFieldDef("reunionCodes", "重逢码", "textarea", true, "每行一个，例如 HF123456")
                    ),
                    "hero_killer"
                ),
                new TaskTypeDef(
                    TYPE_TIANJI,
                    "天机",
                    "无需填写 Token，系统从英雄杀 Token 库自动分配未关联数据号。当前脚本执行到进入大厅为止。",
                    List.of(
                        new TaskFieldDef("count", "任务条数", "text", false, "默认 1，每条分配一个 Token")
                    ),
                    "hero_killer"
                )
            )
        )
        // 其它游戏在此追加，例如：
        // new GameDef("other_game", "其它游戏", List.of(...))
    );

    public List<GameDef> games() {
        return games;
    }

    public String defaultGameCode() {
        return GAME_HERO_KILLER;
    }

    public String defaultTaskType() {
        return TYPE_REUNION;
    }

    public GameDef requireGame(String gameCode) {
        String code = normalize(gameCode);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("请选择游戏");
        }
        return games.stream()
            .filter(game -> game.code().equalsIgnoreCase(code))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知游戏: " + code));
    }

    public TaskTypeDef requireTaskType(String gameCode, String taskType) {
        GameDef game = requireGame(gameCode);
        String type = normalize(taskType);
        if (type.isEmpty()) {
            throw new IllegalArgumentException("请选择任务类型");
        }
        return game.tasks().stream()
            .filter(item -> item.code().equalsIgnoreCase(type))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知任务类型: " + game.code() + "/" + type));
    }

    /**
     * 领取时若脚本未带类型，默认英雄杀-结义，兼容旧客户端。
     */
    public String resolveGameCode(String gameCode) {
        String code = normalize(gameCode);
        return code.isEmpty() ? defaultGameCode() : requireGame(code).code();
    }

    public String resolveTaskType(String gameCode, String taskType) {
        GameDef game = requireGame(resolveGameCode(gameCode));
        String type = normalize(taskType);
        if (type.isEmpty()) {
            if (game.tasks().isEmpty()) {
                throw new IllegalArgumentException("该游戏尚未配置任务类型");
            }
            return game.tasks().get(0).code();
        }
        return requireTaskType(game.code(), type).code();
    }

    public String gameName(String gameCode) {
        String code = normalize(gameCode);
        if (code.isEmpty()) {
            code = defaultGameCode();
        }
        try {
            return requireGame(code).name();
        } catch (IllegalArgumentException ex) {
            return gameCode == null ? "" : gameCode;
        }
    }

    public String taskName(String gameCode, String taskType) {
        String game = normalize(gameCode);
        String type = normalize(taskType);
        if (game.isEmpty()) {
            game = defaultGameCode();
        }
        if (type.isEmpty()) {
            type = defaultTaskType();
        }
        try {
            return requireTaskType(game, type).name();
        } catch (IllegalArgumentException ex) {
            return taskType == null ? "" : taskType;
        }
    }

    /**
     * 按类型定义校验并清洗 payload，丢掉未登记字段。
     */
    public Map<String, String> validatePayload(String gameCode, String taskType, Map<String, String> raw) {
        TaskTypeDef def = requireTaskType(gameCode, taskType);
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (TaskFieldDef field : def.fields()) {
            String value = "";
            if (raw != null && raw.get(field.key()) != null) {
                value = raw.get(field.key()).trim();
            }
            if (field.required() && value.isEmpty()) {
                throw new IllegalArgumentException(field.label() + "不能为空");
            }
            cleaned.put(field.key(), value);
        }
        return cleaned;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
