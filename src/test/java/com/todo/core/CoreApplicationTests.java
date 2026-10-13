package com.todo.core;

import com.todo.core.common.ApiException;
import com.todo.core.friendship.FriendshipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CoreApplicationTests {

    private static final AtomicLong USER_SEQUENCE = new AtomicLong(1);
    private static final String PASSWORD = "password1";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FriendshipService friendshipService;

    @Test
    void registerReturnsTokenAndLoginRejectsBadPassword() throws Exception {
        UserSession user = registerAndLogin("alice");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"wrongpass\"}".formatted(user.email())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameOrEmailIsRejected() throws Exception {
        UserSession existing = registerAndLogin("bob");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"email\":\"other_%s\",\"password\":\"%s\"}"
                                .formatted(existing.username(), existing.email(), PASSWORD)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"other_%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(existing.username(), existing.email(), PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void listsIncomingAndOutgoingPendingFriendRequests() throws Exception {
        UserSession sender = registerAndLogin("sender");
        UserSession receiver = registerAndLogin("receiver");

        String requestResponse = mockMvc.perform(post("/friend-requests")
                        .header("Authorization", bearer(sender.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\"}".formatted(receiver.username())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long requestId = objectMapper.readTree(requestResponse).path("id").asLong();

        mockMvc.perform(get("/friend-requests")
                        .param("incoming", "pending")
                        .header("Authorization", bearer(receiver.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].requested_by").value(sender.id()))
                .andExpect(jsonPath("$[0].other_username").value(sender.username()))
                .andExpect(jsonPath("$[0].status").value("pending"));

        mockMvc.perform(get("/friend-requests")
                        .param("outgoing", "pending")
                        .header("Authorization", bearer(sender.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].other_username").value(receiver.username()))
                .andExpect(jsonPath("$[0].status").value("pending"));

        mockMvc.perform(get("/friend-requests")
                        .param("incoming", "pending")
                        .header("Authorization", bearer(sender.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/friend-requests")
                        .param("outgoing", "pending")
                        .header("Authorization", bearer(receiver.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unfriendingArchivesSharedTasksAndMakesThemReadOnly() throws Exception {
        UserSession assigner = registerAndLogin("assigner");
        UserSession assignee = registerAndLogin("assignee");
        makeFriends(assigner, assignee);

        long taskId = createTask(assigner, assignee.username());

        mockMvc.perform(delete("/friends/{friendId}", assignee.id())
                        .header("Authorization", bearer(assigner.token())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tasks")
                        .param("assigned_to_me", "true")
                        .header("Authorization", bearer(assignee.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(taskId))
                .andExpect(jsonPath("$[0].status").value("archived"))
                .andExpect(jsonPath("$[0].archived_reason").value("unfriended"))
                .andExpect(jsonPath("$[0].assigner_username").value(assigner.username()))
                .andExpect(jsonPath("$[0].assignee_username").value(assignee.username()));

        mockMvc.perform(put("/tasks/{id}", taskId)
                        .header("Authorization", bearer(assignee.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"done\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Archived tasks are read-only"));
    }

    @Test
    void assigneeCanOnlyChangeStatus() throws Exception {
        UserSession assigner = registerAndLogin("creator");
        UserSession assignee = registerAndLogin("worker");
        makeFriends(assigner, assignee);
        long taskId = createTask(assigner, assignee.username());

        mockMvc.perform(put("/tasks/{id}", taskId)
                        .header("Authorization", bearer(assignee.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Unauthorized edit\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/tasks/{id}", taskId)
                        .header("Authorization", bearer(assignee.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"in_progress\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"));
    }

    @Test
    void concurrentDuplicateFriendRequestsProduceOneRelationship() throws Exception {
        UserSession sender = registerAndLogin("csender");
        UserSession receiver = registerAndLogin("creceiver");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> sendRequestConcurrently(sender.id(), receiver.id(), ready, start));
            Future<Boolean> second = executor.submit(() -> sendRequestConcurrently(receiver.id(), sender.id(), ready, start));
            ready.await();
            start.countDown();

            assertThat(first.get() ^ second.get()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean sendRequestConcurrently(long senderId, long receiverId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException, ExecutionException {
        ready.countDown();
        start.await();
        try {
            friendshipService.sendRequest(senderId, receiverId);
            return true;
        } catch (ApiException exception) {
            assertThat(exception.status().value()).isEqualTo(409);
            return false;
        }
    }

    private UserSession registerAndLogin(String name) throws Exception {
        long suffix = USER_SEQUENCE.incrementAndGet();
        String username = name.toLowerCase() + suffix;
        String email = username + "@example.com";
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(username, email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.user.username").value(username))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return new UserSession(
                body.path("user").path("id").asLong(),
                username,
                email,
                body.path("access_token").asText()
        );
    }

    private void makeFriends(UserSession sender, UserSession receiver) throws Exception {
        String requestResponse = mockMvc.perform(post("/friend-requests")
                        .header("Authorization", bearer(sender.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\"}".formatted(receiver.username())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long requestId = objectMapper.readTree(requestResponse).path("id").asLong();

        mockMvc.perform(post("/friend-requests/{id}/accept", requestId)
                        .header("Authorization", bearer(receiver.token())))
                .andExpect(status().isOk());
    }

    private long createTask(UserSession assigner, String assigneeUsername) throws Exception {
        String response = mockMvc.perform(post("/tasks")
                        .header("Authorization", bearer(assigner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee_username\":\"%s\",\"title\":\"Prepare release\"}".formatted(assigneeUsername)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record UserSession(long id, String username, String email, String token) {
    }
}
