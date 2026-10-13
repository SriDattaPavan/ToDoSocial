import { apiRequest, type Task, type TaskStatus } from './client'

export function listTasks(view: 'assigned_to_me' | 'created_by_me'): Promise<Task[]> {
  const params =
    view === 'assigned_to_me'
      ? 'assigned_to_me=true&created_by_me=false'
      : 'assigned_to_me=false&created_by_me=true'
  return apiRequest<Task[]>(`/tasks?${params}`)
}

export function createTask(input: {
  assigneeUsername: string
  title: string
  description?: string
  dueDate?: string
}): Promise<Task> {
  return apiRequest<Task>('/tasks', {
    method: 'POST',
    body: JSON.stringify({
      assignee_username: input.assigneeUsername,
      title: input.title,
      description: input.description || null,
      due_date: input.dueDate || null,
    }),
  })
}

export function updateTaskStatus(taskId: number, status: Exclude<TaskStatus, 'archived'>): Promise<Task> {
  return apiRequest<Task>(`/tasks/${taskId}`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  })
}

export function deleteTask(taskId: number): Promise<void> {
  return apiRequest<void>(`/tasks/${taskId}`, {
    method: 'DELETE',
  })
}
