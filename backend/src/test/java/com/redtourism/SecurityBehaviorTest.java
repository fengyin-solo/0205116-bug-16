package com.redtourism;

import com.redtourism.common.Constants;
import com.redtourism.config.SecurityConfig;
import com.redtourism.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = SecurityBehaviorTest.DummyAdminController.class)
class SecurityBehaviorTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SecurityConfig.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class DummyAdminController {
        @GetMapping("/api/admin/user/list")
        String userList() { return "user-list-ok"; }

        @GetMapping("/api/admin/order/refund")
        String refund() { return "refund-ok"; }

        @GetMapping("/api/admin/comment/delete")
        String commentDelete() { return "comment-delete-ok"; }

        @GetMapping("/api/admin/role/save")
        String roleSave() { return "role-save-ok"; }

        @GetMapping("/api/admin/spot/list")
        String spotList() { return "spot-list-ok"; }
    }

    private MockHttpSession sessionWithRole(String role) {
        MockHttpSession session = new MockHttpSession();
        User u = new User();
        u.setId(1L);
        u.setUsername("test");
        u.setRole(role);
        session.setAttribute(Constants.SESSION_USER, u);
        return session;
    }

    @Test
    void probe() throws Exception {
        String[][] cases = {
            {"GUEST", "/api/admin/user/list"},
            {"GUEST", "/api/admin/order/refund"},
            {"GUEST", "/api/admin/comment/delete"},
            {"GUEST", "/api/admin/role/save"},
            {"GUEST", "/api/admin/spot/list"},
        };
        for (String[] c : cases) {
            mockMvc.perform(get(c[1])).andDo(r ->
                System.out.println("### " + c[0] + " " + c[1] + " -> HTTP " + r.getResponse().getStatus()
                    + " body=" + r.getResponse().getContentAsString()));
        }
        mockMvc.perform(get("/api/admin/role/save").session(sessionWithRole("USER"))).andDo(r ->
            System.out.println("### USER-session /api/admin/role/save -> HTTP " + r.getResponse().getStatus()));
        mockMvc.perform(get("/api/admin/user/list").session(sessionWithRole("USER"))).andDo(r ->
            System.out.println("### USER-session /api/admin/user/list -> HTTP " + r.getResponse().getStatus()));
        mockMvc.perform(get("/api/admin/spot/list").session(sessionWithRole("STAFF"))).andDo(r ->
            System.out.println("### STAFF-session /api/admin/spot/list -> HTTP " + r.getResponse().getStatus()));
    }
}
