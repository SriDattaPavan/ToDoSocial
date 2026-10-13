import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError, type FriendRequest, type Task, type TaskStatus } from '../api/client'
import * as friendsApi from '../api/friends'
import * as tasksApi from '../api/tasks'
import { useAuth } from '../auth/AuthContext'

type Tab = 'my_tasks' | 'assigned_to_others' | 'friend_requests'

function formatWhen(value: string | null): string {
  if (!value) {
    return '—'
  }
  return new Date(value).toLocaleString()
}

export function HomePage() {
  const { user, logout } = useAuth()
  const [tab, setTab] = useState<Tab>('my_tasks')
  const [tasks, setTasks] = useState<Task[]>([])
  const [incoming, setIncoming] = useState<FriendRequest[]>([])
  const [outgoing, setOutgoing] = useState<FriendRequest[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const [assigneeUsername, setAssigneeUsername] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [friendUsername, setFriendUsername] = useState('')

  const loadTab = useCallback(async () => {
    if (!user) {
      return
    }
    setError(null)
    try {
      if (tab === 'friend_requests') {
        const [incomingList, outgoingList] = await Promise.all([
          friendsApi.listIncomingPending(),
          friendsApi.listOutgoingPending(),
        ])
        setIncoming(incomingList)
        setOutgoing(outgoingList)
        return
      }

      const view = tab === 'my_tasks' ? 'assigned_to_me' : 'created_by_me'
      setTasks(await tasksApi.listTasks(view))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load data')
    }
  }, [user, tab])

  useEffect(() => {
    void loadTab()
  }, [loadTab])

  async function onCreateTask(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await tasksApi.createTask({
        assigneeUsername: assigneeUsername.trim(),
        title: title.trim(),
        description: description.trim() || undefined,
        dueDate: dueDate ? new Date(dueDate).toISOString() : undefined,
      })
      setTitle('')
      setDescription('')
      setDueDate('')
      setTasks(await tasksApi.listTasks('created_by_me'))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create task')
    } finally {
      setBusy(false)
    }
  }

  async function onStatusChange(taskId: number, status: Exclude<TaskStatus, 'archived'>) {
    setError(null)
    try {
      await tasksApi.updateTaskStatus(taskId, status)
      await loadTab()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update status')
    }
  }

  async function onDelete(taskId: number) {
    setError(null)
    try {
      await tasksApi.deleteTask(taskId)
      await loadTab()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete task')
    }
  }

  async function onSendFriend(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await friendsApi.sendFriendRequest(friendUsername.trim())
      setFriendUsername('')
      await loadTab()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send friend request')
    } finally {
      setBusy(false)
    }
  }

  async function onAccept(requestId: number) {
    setError(null)
    try {
      await friendsApi.acceptFriendRequest(requestId)
      await loadTab()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not accept request')
    }
  }

  if (!user) {
    return null
  }

  return (
    <main className="page">
      <header className="topbar">
        <div>
          <h1>ToDoSocial</h1>
          <p className="user-meta">
            {user.username} · {user.email}
          </p>
        </div>
        <button type="button" onClick={logout}>
          Log out
        </button>
      </header>

      <nav className="tabs" aria-label="Main">
        <button
          type="button"
          className={tab === 'my_tasks' ? 'active' : ''}
          onClick={() => setTab('my_tasks')}
        >
          My tasks
        </button>
        <button
          type="button"
          className={tab === 'assigned_to_others' ? 'active' : ''}
          onClick={() => setTab('assigned_to_others')}
        >
          Assigned to others
        </button>
        <button
          type="button"
          className={tab === 'friend_requests' ? 'active' : ''}
          onClick={() => setTab('friend_requests')}
        >
          Friend requests
        </button>
      </nav>

      {error ? <p className="error">{error}</p> : null}

      {tab === 'my_tasks' ? (
        <section>
          <h2>Tasks assigned to me</h2>
          <TaskList
            tasks={tasks}
            currentUserId={user.id}
            onStatusChange={onStatusChange}
            onDelete={onDelete}
          />
        </section>
      ) : null}

      {tab === 'assigned_to_others' ? (
        <section>
          <h2>Tasks I assign to others</h2>
          <form className="stack-form" onSubmit={onCreateTask}>
            <label>
              Assignee username
              <input
                value={assigneeUsername}
                onChange={(e) => setAssigneeUsername(e.target.value)}
                required
              />
            </label>
            <label>
              Title
              <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} />
            </label>
            <label>
              Description
              <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} />
            </label>
            <label>
              Due date
              <input type="datetime-local" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
            </label>
            <button type="submit" className="primary" disabled={busy}>
              Create task
            </button>
          </form>
          <TaskList
            tasks={tasks}
            currentUserId={user.id}
            onStatusChange={onStatusChange}
            onDelete={onDelete}
          />
        </section>
      ) : null}

      {tab === 'friend_requests' ? (
        <section>
          <h2>Friend requests</h2>
          <form className="stack-form" onSubmit={onSendFriend}>
            <label>
              Username
              <input
                value={friendUsername}
                onChange={(e) => setFriendUsername(e.target.value)}
                required
              />
            </label>
            <button type="submit" className="primary" disabled={busy}>
              Send request
            </button>
          </form>

          <h3>Incoming</h3>
          <ul className="list">
            {incoming.length === 0 ? (
              <li className="muted">No incoming requests.</li>
            ) : (
              incoming.map((request) => (
                <li key={request.id} className="row">
                  <div>
                    <p className="item-title">From {request.other_username}</p>
                    <p className="item-meta">Request #{request.id}</p>
                  </div>
                  <div className="row-actions">
                    <button type="button" className="primary" onClick={() => void onAccept(request.id)}>
                      Accept
                    </button>
                  </div>
                </li>
              ))
            )}
          </ul>

          <h3>Outgoing</h3>
          <ul className="list">
            {outgoing.length === 0 ? (
              <li className="muted">No outgoing requests.</li>
            ) : (
              outgoing.map((request) => (
                <li key={request.id}>
                  <p className="item-title">To {request.other_username}</p>
                  <p className="item-meta">Request #{request.id} · pending</p>
                </li>
              ))
            )}
          </ul>
        </section>
      ) : null}
    </main>
  )
}

function TaskList({
  tasks,
  currentUserId,
  onStatusChange,
  onDelete,
}: {
  tasks: Task[]
  currentUserId: number
  onStatusChange: (taskId: number, status: Exclude<TaskStatus, 'archived'>) => void
  onDelete: (taskId: number) => void
}) {
  if (tasks.length === 0) {
    return <p className="muted">No tasks here yet.</p>
  }

  return (
    <ul className="list">
      {tasks.map((task) => {
        const archived = task.status === 'archived'
        const isAssignee = task.assignee_id === currentUserId
        const isAssigner = task.assigner_id === currentUserId

        return (
          <li key={task.id} className={`row${archived ? ' archived' : ''}`}>
            <div>
              <p className="item-title">{task.title}</p>
              {task.description ? <p className="item-body">{task.description}</p> : null}
              <p className="item-meta">
                #{task.id} · from {task.assigner_username} to {task.assignee_username}
                {task.due_date ? ` · due ${formatWhen(task.due_date)}` : ''}
                {archived && task.archived_reason ? ` · archived (${task.archived_reason})` : ''}
                {!archived ? ` · ${task.status}` : ''}
              </p>
            </div>
            <div className="row-actions">
              {!archived && isAssignee ? (
                <label className="status-select">
                  Status
                  <select
                    value={task.status}
                    onChange={(e) =>
                      onStatusChange(task.id, e.target.value as Exclude<TaskStatus, 'archived'>)
                    }
                  >
                    <option value="todo">todo</option>
                    <option value="in_progress">in_progress</option>
                    <option value="done">done</option>
                  </select>
                </label>
              ) : null}
              {!archived && isAssigner ? (
                <button type="button" onClick={() => onDelete(task.id)}>
                  Delete
                </button>
              ) : null}
            </div>
          </li>
        )
      })}
    </ul>
  )
}
