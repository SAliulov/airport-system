package ru.airport.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** JWT login helpers для integration-тестов. */
public final class TestAuthHelper {

    private TestAuthHelper() {
    }

    public static String login(MockMvc mockMvc, ObjectMapper objectMapper, String username, String password)
            throws Exception {
        return login(mockMvc, objectMapper, username, password, "DISPATCHER");
    }

    public static String login(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String username,
            String password,
            String client
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","client":"%s"}
                                """.formatted(username, password, client)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
