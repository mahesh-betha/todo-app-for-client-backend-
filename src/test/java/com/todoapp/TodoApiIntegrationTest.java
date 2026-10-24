package com.todoapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end REST API tests against a real PostgreSQL database.
 * Requires the docker-compose database to be running (docker compose up -d).
 * Verifies authentication, CRUD, ownership isolation, and error handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TodoApiIntegrationTest {

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        // Uses the docker-compose PostgreSQL by default; override via env when needed.
        registry.add("spring.datasource.url", () -> System.getenv()
                .getOrDefault("DB_URL", "jdbc:postgresql://localhost:5433/todo_db"));
        registry.add("spring.datasource.username", () -> System.getenv()
                .getOrDefault("DB_USERNAME", "todo_user"));
        registry.add("spring.datasource.password", () -> System.getenv()
                .getOrDefault("DB_PASSWORD", "todo_password"));
        registry.add("app.jwt.secret",
                () -> "integration-test-secret-that-is-long-enough-for-hs256-0123456789");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String tokenA;
    private static String tokenB;
    private static UUID todoIdOfA;

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private Map<String, Object> registerBody(String email, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Test User");
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    private String registerAndExtractToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody(email, "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("accessToken").asText();
    }

    private Map<String, Object> todoBody(String title, String status, String priority) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "Integration test todo");
        body.put("status", status);
        body.put("priority", priority);
        body.put("dueDate", "2026-12-31");
        return body;
    }

    @Test
    @Order(1)
    void registerLoginAndMeFlow() throws Exception {
        String email = uniqueEmail("alice");
        tokenA = registerAndExtractToken(email);

        // Duplicate email -> 409
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody(email, "password123"))))
                .andExpect(status().isConflict());

        // Login with correct credentials
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        // Login with wrong password -> 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        // Profile exposes no password
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @Order(2)
    void protectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/todos")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(todoBody("x", "PENDING", "LOW"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/todos/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/todos/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void registerValidationErrorsReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }


    @Test
    @Order(4)
    void fullTodoCrudLifecycle() throws Exception {
        // Create
        MvcResult created = mockMvc.perform(post("/api/todos")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                todoBody("Write tests", "PENDING", "HIGH"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Write tests"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andReturn();
        JsonNode createdNode = objectMapper.readTree(created.getResponse().getContentAsString());
        todoIdOfA = UUID.fromString(createdNode.get("id").asText());

        // Read single
        mockMvc.perform(get("/api/todos/" + todoIdOfA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(todoIdOfA.toString()));

        // List with search
        mockMvc.perform(get("/api/todos")
                        .param("search", "Write")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Write tests"));

        // Update (PUT)
        mockMvc.perform(put("/api/todos/" + todoIdOfA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                todoBody("Write more tests", "IN_PROGRESS", "MEDIUM"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Write more tests"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Patch status to COMPLETED
        mockMvc.perform(patch("/api/todos/" + todoIdOfA + "/status")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Stats reflect completion
        mockMvc.perform(get("/api/todos/stats")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(1));

        // Delete
        mockMvc.perform(delete("/api/todos/" + todoIdOfA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/todos/" + todoIdOfA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }


    @Test
    @Order(5)
    void usersCannotAccessOtherUsersTodos() throws Exception {
        // tokenA creates a todo
        MvcResult created = mockMvc.perform(post("/api/todos")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                todoBody("Private todo", "PENDING", "LOW"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID secretId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

        // tokenB registers separately
        tokenB = registerAndExtractToken(uniqueEmail("bob"));

        // tokenB cannot read, update, patch, or delete A's todo -> 404 (not 403, to avoid leaking existence)
        mockMvc.perform(get("/api/todos/" + secretId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/todos/" + secretId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                todoBody("Hijacked", "PENDING", "LOW"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/todos/" + secretId + "/status")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/todos/" + secretId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // tokenB's list does NOT include A's todo
        mockMvc.perform(get("/api/todos")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // Owner still sees it
        mockMvc.perform(get("/api/todos/" + secretId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void invalidTodoPayloadAndTokenReturn4xx() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").isNotEmpty());

        mockMvc.perform(post("/api/todos")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bad enum\",\"priority\":\"URGENT\",\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/todos/" + UUID.randomUUID() + "/status")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/todos")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

}
