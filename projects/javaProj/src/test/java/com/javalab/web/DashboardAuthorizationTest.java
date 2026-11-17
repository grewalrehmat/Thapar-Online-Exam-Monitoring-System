package com.javalab.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import com.javalab.account.AccountAdminService;
import com.javalab.account.Role;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:auth-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "javalab.demo.enabled=false"
})
@AutoConfigureMockMvc
class DashboardAuthorizationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountAdminService accounts;

    @Test void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("login"));
    }

    @Test @Transactional
    void databaseCredentialsAuthenticateAndRedirectByRole() throws Exception {
        accounts.createUser("Login Student", "login@example.edu", "long-test-password", Role.STUDENT, "LOGIN-1");
        MvcResult login = mvc.perform(post("/login").with(csrf()).param("username", "login@example.edu").param("password", "long-test-password"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/student")).andReturn();
        mvc.perform(get("/student").session((MockHttpSession) login.getRequest().getSession(false)))
            .andExpect(status().isOk()).andExpect(view().name("student/dashboard"));
    }

    @Test @WithMockUser(roles = "STUDENT")
    void studentCannotOpenTeacherDashboard() throws Exception {
        mvc.perform(get("/teacher")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "TEACHER")
    void teacherCannotOpenAdminDashboard() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminCannotOpenStudentDashboard() throws Exception {
        mvc.perform(get("/student")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminDashboardRenders() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(view().name("admin/dashboard"));
    }
}
