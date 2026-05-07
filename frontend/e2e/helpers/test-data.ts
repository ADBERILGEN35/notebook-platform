export type E2eData = {
  suffix: string
  email: string
  password: string
  workspaceName: string
  notebookName: string
  noteTitle: string
  noteContent: string
  searchQuery: string
}

export function createE2eData(): E2eData {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 100000)}`
  return {
    suffix,
    email: `e2e+${suffix}@example.com`,
    password: 'Password1234!',
    workspaceName: `E2E Workspace ${suffix}`,
    notebookName: `E2E Notebook ${suffix}`,
    noteTitle: `E2E Note ${suffix}`,
    noteContent: `E2E content ${suffix}`,
    searchQuery: `E2E Note ${suffix}`,
  }
}

