package com.todo.core.friendship;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final UserRepository userRepository;
    private final AuthenticatedUser authenticatedUser;

    public FriendshipController(
            FriendshipService friendshipService,
            UserRepository userRepository,
            AuthenticatedUser authenticatedUser
    ) {
        this.friendshipService = friendshipService;
        this.userRepository = userRepository;
        this.authenticatedUser = authenticatedUser;
    }

    @PostMapping("/friend-requests")
    @ResponseStatus(HttpStatus.CREATED)
    FriendRequestResponse sendRequest(@Valid @RequestBody SendFriendRequest request) {
        return toResponse(friendshipService.sendRequest(authenticatedUser.id(), request.username()));
    }

    @GetMapping("/friend-requests")
    List<FriendRequestResponse> listPending(
            @RequestParam(required = false) String incoming,
            @RequestParam(required = false) String outgoing
    ) {
        boolean hasIncoming = incoming != null;
        boolean hasOutgoing = outgoing != null;
        if (hasIncoming == hasOutgoing) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Specify exactly one of incoming=pending or outgoing=pending");
        }
        if (hasIncoming && !"pending".equals(incoming)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "incoming must be pending");
        }
        if (hasOutgoing && !"pending".equals(outgoing)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "outgoing must be pending");
        }
        return friendshipService.listPending(authenticatedUser.id(), hasIncoming, hasOutgoing)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/friend-requests/{id}/accept")
    FriendRequestResponse acceptRequest(@PathVariable @Positive long id) {
        return toResponse(friendshipService.acceptRequest(authenticatedUser.id(), id));
    }

    @PostMapping("/friend-requests/{id}/reject")
    FriendRequestResponse rejectRequest(@PathVariable @Positive long id) {
        return toResponse(friendshipService.rejectRequest(authenticatedUser.id(), id));
    }

    @DeleteMapping("/friends/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfriend(@PathVariable @Positive long friendId) {
        friendshipService.unfriend(authenticatedUser.id(), friendId);
    }

    public record SendFriendRequest(@NotBlank @Size(max = 30) String username) {
    }

    public record FriendRequestResponse(
            long id,
            long userA,
            long userB,
            long requestedBy,
            String otherUsername,
            String requestedByUsername,
            FriendshipStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    private FriendRequestResponse toResponse(FriendRequest request) {
        Set<Long> ids = new HashSet<>();
        ids.add(request.userA());
        ids.add(request.userB());
        ids.add(request.requestedBy());
        Map<Long, String> usernames = userRepository.findUsernamesByIds(ids);
        long currentUserId = authenticatedUser.id();
        long otherId = request.userA() == currentUserId ? request.userB() : request.userA();
        return new FriendRequestResponse(
                request.id(),
                request.userA(),
                request.userB(),
                request.requestedBy(),
                usernames.get(otherId),
                usernames.get(request.requestedBy()),
                request.status(),
                request.createdAt(),
                request.updatedAt()
        );
    }
}
