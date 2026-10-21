package com.wsa.client.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * BrowserRouter deep links: serve the React shell so client-side routes work on refresh.
 */
@Controller
public class SpaForwardController {

    @GetMapping({
            "/apis",
            "/alerts",
            "/rate",
            "/infra",
            "/stats",
            "/stats.html"
    })
    public String spaRoutes() {
        return "forward:/index.html";
    }
}
