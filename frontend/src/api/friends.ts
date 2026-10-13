import { apiRequest, type FriendRequest } from './client'

export function sendFriendRequest(username: string): Promise<FriendRequest> {
  return apiRequest<FriendRequest>('/friend-requests', {
    method: 'POST',
    body: JSON.stringify({ username }),
  })
}

export function listIncomingPending(): Promise<FriendRequest[]> {
  return apiRequest<FriendRequest[]>('/friend-requests?incoming=pending')
}

export function listOutgoingPending(): Promise<FriendRequest[]> {
  return apiRequest<FriendRequest[]>('/friend-requests?outgoing=pending')
}

export function acceptFriendRequest(requestId: number): Promise<FriendRequest> {
  return apiRequest<FriendRequest>(`/friend-requests/${requestId}/accept`, {
    method: 'POST',
  })
}
