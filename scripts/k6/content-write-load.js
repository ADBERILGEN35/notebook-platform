import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const TOKEN = __ENV.ACCESS_TOKEN;
const WORKSPACE_ID = __ENV.WORKSPACE_ID;
const NOTEBOOK_ID = __ENV.NOTEBOOK_ID;

export const options = {
  vus: Number(__ENV.VUS || "3"),
  duration: __ENV.DURATION || "30s",
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<1200"],
  },
};

export default function () {
  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${TOKEN}`,
    "X-Workspace-Id": WORKSPACE_ID,
  };
  const title = `Content Load ${Date.now()}-${__VU}-${__ITER}`;
  const create = http.post(
    `${BASE_URL}/notebooks/${NOTEBOOK_ID}/notes`,
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
  sleep(1);
}
