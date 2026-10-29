const BASE = "http://127.0.0.1:8080/api";

let pass = 0;
let fail = 0;

function check(name, condition, extra = "") {
  if (condition) {
    pass++;
    console.log(`  PASS  ${name}`);
  } else {
    fail++;
    console.log(`  FAIL  ${name}  ${extra}`);
  }
}

async function call(path, { method = "GET", token, body, headers = {} } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  return { status: res.status, json };
}

async function login(username) {
  const r = await call("/auth/login", { method: "POST", body: { username, password: "123456" } });
  return r.json.data.token;
}

(async () => {
  console.log("\n== 1. 登录与身份 ==");
  const tokens = {};
  for (const u of ["zhangwei", "lijing", "wangfang", "chenming", "liuyang", "zhaolei"]) {
    tokens[u] = await login(u);
  }
  check("六个角色均可登录", Object.values(tokens).every(Boolean));
  const wrong = await call("/auth/login", { method: "POST", body: { username: "zhangwei", password: "x" } });
  check("错误密码返回 400", wrong.status === 400, `got ${wrong.status}`);
  const noToken = await call("/requests");
  check("未登录访问返回 401", noToken.status === 401, `got ${noToken.status}`);

  console.log("\n== 2. 数据范围（同一接口不同角色） ==");
  const mine = await call("/requests?tab=mine", { token: tokens.zhangwei });
  check("张伟：我的申请 5 条", mine.json.data.total === 5, `got ${mine.json.data.total}`);

  const allAdmin = await call("/requests?tab=all", { token: tokens.chenming });
  check("陈明：全部 9 条", allAdmin.json.data.total === 9, `got ${allAdmin.json.data.total}`);

  const allZhang = await call("/requests?tab=all", { token: tokens.zhangwei });
  check("张伟：all 仍只能看到自己 5 条", allZhang.json.data.total === 5, `got ${allZhang.json.data.total}`);

  const todoLi = await call("/requests?tab=todo", { token: tokens.lijing });
  const liDepts = new Set(todoLi.json.data.items.map((i) => i.department));
  check("李静：待办仅研发部", todoLi.json.data.items.length === 1 && [...liDepts].every((d) => d === "研发部"),
    JSON.stringify([...liDepts]));

  const todoWang = await call("/requests?tab=todo", { token: tokens.wangfang });
  check("王芳：待办 2 条（待财务复核）", todoWang.json.data.total === 2, `got ${todoWang.json.data.total}`);

  const todoZhang = await call("/requests?tab=todo", { token: tokens.zhangwei });
  check("张伟：员工待办为空", todoZhang.json.data.total === 0, `got ${todoZhang.json.data.total}`);

  console.log("\n== 3. 越权防护 ==");
  const detailOfDraft = await call("/requests/2", { token: tokens.wangfang });
  check("财务访问员工草稿详情 → 403", detailOfDraft.status === 403, `got ${detailOfDraft.status}`);

  const outsider = await call("/requests/1", { token: tokens.zhaolei });
  check("其他部门主管访问研发部单据 → 403", outsider.status === 403, `got ${outsider.status}`);

  const empApprove = await call("/requests/1/approve", { method: "POST", token: tokens.zhangwei, body: {} });
  check("员工调用审批 → 403", empApprove.status === 403, `got ${empApprove.status}`);

  console.log("\n== 4. 正常流程：小额一级审批 ==");
  const created = await call("/requests", {
    method: "POST",
    token: tokens.zhangwei,
    body: { title: "接口自测-小额", reason: "自动化用例", category: "OFFICE", amount: 1200 }
  });
  check("创建草稿成功", created.status === 200 && created.json.data.status === "DRAFT", `got ${created.status}`);
  const id = created.json.data.id;

  const submitted = await call(`/requests/${id}/submit`, { method: "POST", token: tokens.zhangwei });
  check("提交后进入待主管审批", submitted.json.data.status === "PENDING_MANAGER");

  const approved = await call(`/requests/${id}/approve`, { method: "POST", token: tokens.lijing, body: { comment: "同意" } });
  check("1200 元经主管审批即通过", approved.json.data.status === "APPROVED", `got ${approved.json.data.status}`);

  const again = await call(`/requests/${id}/approve`, { method: "POST", token: tokens.lijing, body: {} });
  check("终态重复审批 → 409", again.status === 409, `got ${again.status}`);

  console.log("\n== 5. 边界：金额正好 5000 ==");
  const edge = await call("/requests", {
    method: "POST",
    token: tokens.zhangwei,
    body: { title: "接口自测-边界5000", reason: "自动化用例", category: "SERVICE", amount: 5000 }
  });
  const edgeId = edge.json.data.id;
  await call(`/requests/${edgeId}/submit`, { method: "POST", token: tokens.zhangwei });
  const edgeApprove = await call(`/requests/${edgeId}/approve`, { method: "POST", token: tokens.lijing, body: {} });
  check("5000 元整经主管后进入财务复核", edgeApprove.json.data.status === "PENDING_FINANCE",
    `got ${edgeApprove.json.data.status}`);

  const financePass = await call(`/requests/${edgeId}/approve`, { method: "POST", token: tokens.wangfang, body: { comment: "预算内" } });
  check("财务复核后通过", financePass.json.data.status === "APPROVED", `got ${financePass.json.data.status}`);

  const edge2 = await call("/requests", {
    method: "POST",
    token: tokens.zhangwei,
    body: { title: "接口自测-边界4999", reason: "自动化用例", category: "SERVICE", amount: 4999.99 }
  });
  await call(`/requests/${edge2.json.data.id}/submit`, { method: "POST", token: tokens.zhangwei });
  const e2 = await call(`/requests/${edge2.json.data.id}/approve`, { method: "POST", token: tokens.lijing, body: {} });
  check("4999.99 元主管后直接通过", e2.json.data.status === "APPROVED", `got ${e2.json.data.status}`);

  console.log("\n== 6. 参数校验 ==");
  const bad = await call("/requests", {
    method: "POST",
    token: tokens.zhangwei,
    body: { title: "", reason: "", category: "OFFICE", amount: -1 }
  });
  check("空标题/负金额 → 400", bad.status === 400, `got ${bad.status}`);

  const rejectNoReason = await call("/requests/6/reject", { method: "POST", token: tokens.zhaolei, body: { reason: "" } });
  check("驳回不填原因 → 400", rejectNoReason.status === 400, `got ${rejectNoReason.status}`);

  console.log("\n== 7. 驳回流程 ==");
  const rj = await call("/requests", {
    method: "POST",
    token: tokens.zhangwei,
    body: { title: "接口自测-驳回", reason: "自动化用例", category: "TRAVEL", amount: 800 }
  });
  await call(`/requests/${rj.json.data.id}/submit`, { method: "POST", token: tokens.zhangwei });
  const rejected = await call(`/requests/${rj.json.data.id}/reject`, {
    method: "POST",
    token: tokens.lijing,
    body: { reason: "预算不足" }
  });
  check("驳回后状态为 REJECTED", rejected.json.data.status === "REJECTED", `got ${rejected.json.data.status}`);

  console.log("\n== 8. 并发审批（同一张单，两人同时点通过） ==");
  const cc = await call("/requests", {
    method: "POST",
    token: tokens.zhangwei,
    body: { title: "接口自测-并发", reason: "自动化用例", category: "IT_ASSET", amount: 300 }
  });
  const ccId = cc.json.data.id;
  await call(`/requests/${ccId}/submit`, { method: "POST", token: tokens.zhangwei });

  const headers = { "X-Demo-Delay-Ms": "1200" };
  const [a, b] = await Promise.all([
    call(`/requests/${ccId}/approve`, { method: "POST", token: tokens.lijing, body: {}, headers }),
    call(`/requests/${ccId}/approve`, { method: "POST", token: tokens.lijing, body: {}, headers })
  ]);
  const statuses = [a.status, b.status].sort();
  check("并发结果为一个 200 + 一个 409", statuses[0] === 200 && statuses[1] === 409, JSON.stringify(statuses));

  const detail = await call(`/requests/${ccId}`, { token: tokens.zhangwei });
  const approveRecords = detail.json.data.records.filter((r) => r.action === "APPROVE");
  check("审批流水只有一条 APPROVE（没有重复入账）", approveRecords.length === 1, `got ${approveRecords.length}`);

  console.log("\n== 9. 统计接口 ==");
  const stats = await call("/requests/stats", { token: tokens.lijing });
  check("统计返回待办数", typeof stats.json.data.pendingForMe === "number");
  check("状态分布为 6 项", stats.json.data.statusCounts.length === 6);

  console.log(`\n结果：${pass} 通过 / ${fail} 失败`);
  process.exit(fail === 0 ? 0 : 1);
})().catch((e) => {
  console.error("脚本异常", e);
  process.exit(1);
});
