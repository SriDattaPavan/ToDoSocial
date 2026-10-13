package com.todo.core.friendship;

import com.todo.core.common.ApiException;
import com.todo.core.user.User;
import com.todo.core.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FriendshipService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;

    public FriendshipService(FriendRequestRepository friendRequestRepository, UserRepository userRepository) {
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FriendRequest sendRequest(long senderId, String receiverUsername) {
        User receiver = userRepository.findByUsername(receiverUsername.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return sendRequest(senderId, receiver.id());
    }

    @Transactional
    public FriendRequest sendRequest(long senderId, long receiverId) {
        if (senderId == receiverId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot send a friend request to yourself");
        }
        requireUser(receiverId);
        return friendRequestRepository.createOrRerequest(senderId, receiverId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "A friend request is already pending or this user is already a friend"));
    }

    public List<FriendRequest> listPending(long currentUserId, boolean incoming, boolean outgoing) {
        if (incoming == outgoing) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Specify exactly one of incoming=pending or outgoing=pending");
        }
        return incoming
                ? friendRequestRepository.findIncomingPending(currentUserId)
                : friendRequestRepository.findOutgoingPending(currentUserId);
    }

    @Transactional
    public FriendRequest acceptRequest(long currentUserId, long requestId) {
        FriendRequest request = findRequestForReceiver(currentUserId, requestId);
        if (request.status() != FriendshipStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a pending friend request can be accepted");
        }
        return friendRequestRepository.changeStatus(request.id(), FriendshipStatus.ACCEPTED);
    }

    @Transactional
    public FriendRequest rejectRequest(long currentUserId, long requestId) {
        FriendRequest request = findRequestForReceiver(currentUserId, requestId);
        if (request.status() != FriendshipStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a pending friend request can be rejected");
        }
        return friendRequestRepository.changeStatus(request.id(), FriendshipStatus.REMOVED);
    }

    @Transactional
    public void unfriend(long currentUserId, long friendId) {
        if (currentUserId == friendId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot unfriend yourself");
        }
        FriendRequest request = friendRequestRepository.findByPairForUpdate(currentUserId, friendId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No friendship exists with this user"));
        if (request.status() != FriendshipStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an accepted friendship can be removed");
        }

        friendRequestRepository.changeStatus(request.id(), FriendshipStatus.REMOVED);
        friendRequestRepository.archiveActiveTasksBetween(currentUserId, friendId);
    }

    private FriendRequest findRequestForReceiver(long currentUserId, long requestId) {
        FriendRequest request = friendRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Friend request not found"));
        if (!request.involves(currentUserId) || request.requestedBy() == currentUserId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the request receiver can perform this action");
        }
        return request;
    }

    private void requireUser(long userId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
    }
}
