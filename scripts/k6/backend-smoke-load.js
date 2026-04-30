import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EMAIL = __ENV.TEST_EMAIL || `k6-${Date.now()}@example.com`;
const PASSWORD = __ENV.TEST_PASSWORD || "StrongerPass123!";

export const options = {
  vus: Number(__ENV.VUS || "2"),
  duration: __ENV.DURATION || "30s",
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<1000"],
  },
};

export function setup() {
  const signup = http.post(
    `${BASE_URL}/auth/signup`,
    JSON.stringify({ email: EMAIL, password: PASSWORD, name: "K6 User" }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(signup, { "signup ok or duplicate": (r) => [200, 201, 409].includes(r.status) });

  const login = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(login, { "login ok": (r) => r.status === 200 });
  const token = login.json("accessToken");

  const workspace = http.post(
    `${BASE_URL}/workspaces`,
    JSON.stringify({ name: `K6 Workspace ${Date.now()}`, type: "TEAM" }),
    { headers: authHeaders(token) }
  );
  check(workspace, { "workspace create ok": (r) => r.status === 201 });
  const workspaceId = workspace.json("id");

  const notebook = http.post(
    `${BASE_URL}/workspaces/${workspaceId}/notebooks`,
    JSON.stringify({ name: "K6 Notebook", icon: "book" }),
    { headers: authHeaders(token) }
  );
  check(notebook, { "notebook create ok": (r) => r.status === 201 });

  return { token, workspaceId, notebookId: notebook.json("id") };
}

export default function (data) {
  const headers = authHeaders(data.token, data.workspaceId);
  const title = `K6 Note ${Date.now()}-${__VU}-${__ITER}`;
  const create = http.post(
    `${BASE_URL}/notebooks/${data.notebookId}/notes`,
    JSON.stringify({
      title,
      contentBlocks: [{ id: "b1", type: "paragraph", content: [] }],
    }),
    { headers }
  );
  check(create, { "note create ok": (r) => r.status === 201 });
  const noteId = create.json("id");

  const update = http.patch(
    `${BASE_URL}/notes/${noteId}`,
    JSON.stringify({
      title: `${title} Updated`,
      contentBlocks: [{ id: "b1", type: "paragraph", content: [] }],
    }),
    { headers }
  );
  check(update, { "note update ok": (r) => r.status === 200 });

  check(http.get(`${BASE_URL}/notes/search?workspaceId=${data.workspaceId}&q=K6`, { headers }), {
    "search ok": (r) => r.status === 200,
  });
  check(
    http.post(
      `${BASE_URL}/notes/${noteId}/comments`,
      JSON.stringify({ blockId: "b1", content: "k6 comment" }),
      { headers }
    ),
    { "comment create ok": (r) => r.status === 201 }
  );
  sleep(1);
}

function authHeaders(token, workspaceId) {
  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  };
  if (workspaceId) {
    headers["X-Workspace-Id"] = workspaceId;
  }
  return headers;
}
