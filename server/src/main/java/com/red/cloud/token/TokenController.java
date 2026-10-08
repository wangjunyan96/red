package com.red.cloud.token;

import com.red.cloud.token.dto.BatchDeleteTokensRequest;
import com.red.cloud.token.dto.ImportTokensRequest;
import com.red.cloud.token.dto.TokenView;
import com.red.cloud.web.DatabaseErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 后台 Token 管理：按类型导入 txt、按状态/类型/重逢码查询、批量删除。
 */
@RestController
@RequestMapping("/api/v1/admin/tokens")
public class TokenController {
    private static final Logger log = LoggerFactory.getLogger(TokenController.class);
    private static final String TEMPLATE = """
        # 每行一个完整 token，不要表头。Token 类型在导入页选择，不必写进文件。
        # 示例：
        EAED5784E9DF7E416B5650ED31EDAD1B|01BA2E545B62F125EC5AE052013D4E6F|BA4F29498668E601D3C8FADF0EFB4B4B|0c8bb26bececcf970dd88297709293fd|1789636199769
        """;

    private final TokenService tokenService;
    private final TokenTypeCatalog tokenTypeCatalog;

    public TokenController(TokenService tokenService, TokenTypeCatalog tokenTypeCatalog) {
        this.tokenService = tokenService;
        this.tokenTypeCatalog = tokenTypeCatalog;
    }

    @GetMapping("/types")
    public Map<String, Object> types() {
        return Map.of("types", tokenTypeCatalog.types());
    }

    @GetMapping
    public ResponseEntity<?> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String tokenType,
        @RequestParam(required = false) String reunionCode
    ) {
        try {
            List<TokenView> tokens = tokenService.list(status, tokenType, reunionCode);
            return ResponseEntity.ok(Map.of("tokens", tokens, "count", tokens.size()));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    /** 下载导入模板（每行一个 token）。 */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] bytes = TEMPLATE.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=token-import-template.txt")
            .contentType(MediaType.TEXT_PLAIN)
            .body(bytes);
    }

    @PostMapping("/import")
    public ResponseEntity<?> importTokens(@RequestBody ImportTokensRequest request) {
        try {
            String tokenType = request == null ? null : request.tokenType();
            String content = request == null ? null : request.content();
            Map<String, Integer> result = tokenService.importTokens(tokenType, content);
            return ResponseEntity.ok(result);
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody BatchDeleteTokensRequest request) {
        try {
            List<Long> ids = request == null ? List.of() : request.ids();
            int deleted = tokenService.deleteByIds(ids);
            return ResponseEntity.ok(Map.of("deleted", deleted));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    private static ResponseEntity<Map<String, String>> databaseError(DataAccessException ex) {
        log.error("Token 接口数据库错误: {}", DatabaseErrors.message(ex), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", DatabaseErrors.message(ex)));
    }
}
