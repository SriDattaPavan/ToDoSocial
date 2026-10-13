package com.todo.core.task;

import com.todo.core.auth.AuthenticatedUser;
import com.todo.core.common.ApiException;
import com.todo.core.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private static final Set<String> UPDATE_FIELDS = Set.of("title", "description", "due_date", "status");

    private final TaskService taskService;
    private final UserRepository userRepository;
    private final AuthenticatedUser authenticatedUser;

    public TaskController(TaskService taskService, UserRepository userRepository, AuthenticatedUser authenticatedUser) {
        this.taskService = taskService;
        this.userRepository = userRepository;
        this.authenticatedUser = authenticatedUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return toResponse(taskService.create(
                authenticatedUser.id(), request.assigneeUsername(), request.title().trim(), request.description(), request.dueDate()
        ));
    }

    @GetMapping("/{id}")
    TaskResponse get(@PathVariable @Positive long id) {
        return toResponse(taskService.get(authenticatedUser.id(), id));
    }

    @GetMapping
    List<TaskResponse> list(
            @RequestParam(name = "assigned_to_me", defaultValue = "false") boolean assignedToMe,
            @RequestParam(name = "created_by_me", defaultValue = "false") boolean createdByMe
    ) {
        List<Task> tasks = taskService.list(authenticatedUser.id(), assignedToMe, createdByMe);
        Map<Long, String> usernames = usernamesFor(tasks);
        return tasks.stream().map(task -> toResponse(task, usernames)).toList();
    }

    @PutMapping("/{id}")
    TaskResponse update(@PathVariable @Positive long id, @RequestBody JsonNode request) {
        return toResponse(taskService.update(authenticatedUser.id(), id, parseUpdate(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable @Positive long id) {
        taskService.delete(authenticatedUser.id(), id);
    }

    private TaskService.TaskUpdateRequest parseUpdate(JsonNode request) {
        if (!request.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Task update body must be a JSON object");
        }
        Set<String> unknownFields = new HashSet<>();
        request.propertyNames().forEach(field -> {
            if (!UPDATE_FIELDS.contains(field)) {
                unknownFields.add(field);
            }
        });
        if (!unknownFields.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported task update fields: " + String.join(", ", unknownFields));
        }

        boolean hasTitle = request.has("title");
        String title = hasTitle ? requireText(request.get("title"), "title", false, 200) : null;
        boolean hasDescription = request.has("description");
        String description = hasDescription ? optionalText(request.get("description"), "description", 10_000) : null;
        boolean hasDueDate = request.has("due_date");
        OffsetDateTime dueDate = hasDueDate ? optionalDate(request.get("due_date")) : null;
        boolean hasStatus = request.has("status");
        TaskStatus status = hasStatus ? parseStatus(request.get("status")) : null;

        if (!hasTitle && !hasDescription && !hasDueDate && !hasStatus) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one task field must be supplied");
        }
        return new TaskService.TaskUpdateRequest(
                hasTitle, title, hasDescription, description, hasDueDate, dueDate, hasStatus, status
        );
    }

    private String requireText(JsonNode value, String field, boolean nullable, int maximumLength) {
        if ((value == null || value.isNull()) && nullable) {
            return null;
        }
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty() || value.asText().length() > maximumLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be non-blank text up to " + maximumLength + " characters");
        }
        return value.asText().trim();
    }

    private String optionalText(JsonNode value, String field, int maximumLength) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().length() > maximumLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be text up to " + maximumLength + " characters or null");
        }
        return value.asText();
    }

    private OffsetDateTime optionalDate(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "due_date must be an ISO-8601 timestamp or null");
        }
        try {
            return OffsetDateTime.parse(value.asText());
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "due_date must be an ISO-8601 timestamp or null");
        }
    }

    private TaskStatus parseStatus(JsonNode value) {
        if (value == null || !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "status must be todo, in_progress, or done");
        }
        try {
            TaskStatus status = TaskStatus.fromRequest(value.asText());
            if (status == TaskStatus.ARCHIVED) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "status must be todo, in_progress, or done");
            }
            return status;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "status must be todo, in_progress, or done");
        }
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 30) String assigneeUsername,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10_000) String description,
            OffsetDateTime dueDate
    ) {
    }

    public record TaskResponse(
            long id,
            long assignerId,
            long assigneeId,
            String assignerUsername,
            String assigneeUsername,
            String title,
            String description,
            OffsetDateTime dueDate,
            TaskStatus status,
            OffsetDateTime archivedAt,
            TaskArchiveReason archivedReason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    private TaskResponse toResponse(Task task) {
        return toResponse(task, usernamesFor(List.of(task)));
    }

    private TaskResponse toResponse(Task task, Map<Long, String> usernames) {
        return new TaskResponse(
                task.id(),
                task.assignerId(),
                task.assigneeId(),
                usernames.get(task.assignerId()),
                usernames.get(task.assigneeId()),
                task.title(),
                task.description(),
                task.dueDate(),
                task.status(),
                task.archivedAt(),
                task.archivedReason(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    private Map<Long, String> usernamesFor(List<Task> tasks) {
        Set<Long> ids = tasks.stream()
                .flatMap(task -> Stream.of(task.assignerId(), task.assigneeId()))
                .collect(Collectors.toSet());
        return userRepository.findUsernamesByIds(ids);
    }
}
