package com.todo.core.task;

import com.todo.core.common.ApiException;
import com.todo.core.friendship.FriendRequest;
import com.todo.core.friendship.FriendRequestRepository;
import com.todo.core.friendship.FriendshipStatus;
import com.todo.core.user.User;
import com.todo.core.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;

    public TaskService(
            TaskRepository taskRepository,
            FriendRequestRepository friendRequestRepository,
            UserRepository userRepository
    ) {
        this.taskRepository = taskRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Task create(long assignerId, String assigneeUsername, String title, String description, OffsetDateTime dueDate) {
        User assignee = userRepository.findByUsername(assigneeUsername.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Assignee not found"));
        return create(assignerId, assignee.id(), title, description, dueDate);
    }

    @Transactional
    public Task create(long assignerId, long assigneeId, String title, String description, OffsetDateTime dueDate) {
        if (assignerId == assigneeId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot assign a task to yourself");
        }
        if (userRepository.findById(assigneeId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignee not found");
        }

        FriendRequest friendship = friendRequestRepository.findByPairForUpdate(assignerId, assigneeId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tasks can only be assigned to friends"));
        if (friendship.status() != FriendshipStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tasks can only be assigned to friends");
        }
        return taskRepository.create(assignerId, assigneeId, title, description, dueDate);
    }

    public Task get(long currentUserId, long taskId) {
        Task task = findVisibleTask(taskId);
        requireParticipant(task, currentUserId);
        return task;
    }

    public List<Task> list(long currentUserId, boolean assignedToMe, boolean createdByMe) {
        if (assignedToMe == createdByMe) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Specify exactly one of assigned_to_me or created_by_me as true");
        }
        return assignedToMe
                ? taskRepository.findVisibleByAssigneeId(currentUserId)
                : taskRepository.findVisibleByAssignerId(currentUserId);
    }

    @Transactional
    public Task update(long currentUserId, long taskId, TaskUpdateRequest request) {
        Task task = findVisibleTask(taskId);
        requireParticipant(task, currentUserId);
        requireMutable(task);

        if (task.assignerId() == currentUserId) {
            if (!request.hasAssignerFields() || request.hasStatus()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "The assigner may update only title, description, and due_date");
            }
            return taskRepository.updateAssignerFields(taskId, new TaskRepository.TaskUpdate(
                    request.hasTitle(), request.title(), request.hasDescription(), request.description(), request.hasDueDate(), request.dueDate()
            ));
        }

        if (!request.hasOnlyStatus()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "The assignee may update only status");
        }
        if (request.status() == TaskStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A task cannot be archived through the status update API");
        }
        return taskRepository.updateStatus(taskId, request.status());
    }

    @Transactional
    public void delete(long currentUserId, long taskId) {
        Task task = findVisibleTask(taskId);
        if (task.assignerId() != currentUserId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the assigner can delete a task");
        }
        requireMutable(task);
        taskRepository.softDelete(taskId);
    }

    private Task findVisibleTask(long taskId) {
        return taskRepository.findVisibleById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private void requireParticipant(Task task, long userId) {
        if (!task.involves(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this task");
        }
    }

    private void requireMutable(Task task) {
        if (task.status() == TaskStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Archived tasks are read-only");
        }
    }

    public record TaskUpdateRequest(
            boolean hasTitle,
            String title,
            boolean hasDescription,
            String description,
            boolean hasDueDate,
            OffsetDateTime dueDate,
            boolean hasStatus,
            TaskStatus status
    ) {
        public boolean hasAssignerFields() {
            return hasTitle || hasDescription || hasDueDate;
        }

        public boolean hasOnlyStatus() {
            return hasStatus && !hasAssignerFields();
        }
    }
}
