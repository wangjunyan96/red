package com.red.cloud.client;

import com.red.cloud.client.dto.ChatSendRequest;
import com.red.cloud.client.dto.RedeemCardRequest;
import com.red.cloud.user.UserService;
import com.red.cloud.web.DatabaseErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端接口。
 */
@RestController
@RequestMapping("/api/v1/c")
public class ClientController {
    private static final Logger log = LoggerFactory.getLogger(ClientController.class);

    private final ClientService clientService;
    private final UserService userService;

    public ClientController(ClientService clientService, UserService userService) {
        this.clientService = clientService;
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestParam long userId) {
        try {
            return ResponseEntity.ok(Map.of("user", userService.requireById(userId)));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    @GetMapping("/projects")
    public ResponseEntity<?> projects() {
        try {
            return ResponseEntity.ok(Map.of("projects", clientService.listProjects()));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    @GetMapping("/keywords")
    public ResponseEntity<?> keywords() {
        try {
            return ResponseEntity.ok(Map.of("keywords", clientService.listKeywords()));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    @GetMapping("/messages")
    public ResponseEntity<?> messages(@RequestParam long userId) {
        try {
            return ResponseEntity.ok(Map.of("messages", clientService.listMessages(userId)));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<?> orders(@RequestParam long userId) {
        try {
            return ResponseEntity.ok(Map.of("orders", clientService.listOrders(userId)));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatSendRequest request) {
        try {
            if (request == null || request.userId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "请先登录"));
            }
            return ResponseEntity.ok(clientService.sendChat(request.userId(), request.text(), request.projectId()));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    @PostMapping("/cards/redeem")
    public ResponseEntity<?> redeem(@RequestBody RedeemCardRequest request) {
        try {
            if (request == null || request.userId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "请先登录"));
            }
            return ResponseEntity.ok(Map.of("user", clientService.redeemCard(request.userId(), request.cardKey())));
        } catch (DataAccessException ex) {
            return databaseError(ex);
        }
    }

    private static ResponseEntity<Map<String, String>> databaseError(DataAccessException ex) {
        log.error("C 端数据库错误: {}", DatabaseErrors.message(ex), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", DatabaseErrors.message(ex)));
    }
}
