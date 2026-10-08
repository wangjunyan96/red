package com.red.cloud.client;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 打开 /c 时转到 C 端首页。
 */
@Controller
public class ClientPageController {
    @GetMapping({"/c", "/c/"})
    public String index() {
        return "redirect:/c/index.html";
    }
}
