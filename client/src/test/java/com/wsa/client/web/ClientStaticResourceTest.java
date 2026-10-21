package com.wsa.client.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClientStaticResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reactShellIsServed() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"root\"")))
                .andExpect(content().string(containsString("/assets/")));
    }

    @Test
    void spaDeepLinksForwardToReactShell() throws Exception {
        for (String path : new String[]{"/stats", "/stats.html", "/apis", "/alerts", "/rate", "/infra"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void demoAlertRulesAreServed() throws Exception {
        mockMvc.perform(get("/alert-rules.json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INJECTION")));
    }
}
