# API 接口设计

| 项目 | 内容 |
| --- | --- |
| 风格 | RESTful，JSON over HTTP |
| 基础路径 | `/api` |
| 鉴权 | `Authorization: Bearer <token>` |
| 字符集 | UTF-8 |
| 文档版本 | v1.0 |

---

## 1. 通用约定

### 1.1 响应信封

所有接口（**包括异常**）返回同一结构，前端只需处理一种格式：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": { }
}
```

失败示例：

```json
{
  "success": false,
  "code": "FORBIDDEN",
  "message": "您无权审批该申请单",
  "data": null
}
```

### 1.2 HTTP 状态码与业务码

| HTTP | 业务码 | 含义 | 前端处理 |
| --- | --- | --- | --- |
| 200 | OK | 成功 | 正常渲染 |
| 400 | BAD_REQUEST / VALIDATION_FAILED | 参数错误 | 展示 message |
| 401 | UNAUTHORIZED | 未登录 / 令牌过期 | 清除令牌，跳登录页 |
| 403 | FORBIDDEN | 已登录但无权限 | 提示"无权操作" |
| 404 | NOT_FOUND | 资源不存在 | 提示并刷新列表 |
| 409 | CONFLICT | 状态冲突（重复审批、非法状态变更） | 提示"请刷新后重试" |
| 409 | CONCURRENT_MODIFICATION | 并发冲突（乐观锁） | 同上，并刷新详情 |
| 500 | INTERNAL_ERROR | 服务端异常 | 通用错误提示 |

> 设计要点：**不同原因给不同状态码**。若所有错误都返回 200 + `success:false`，前端无法区分"该重新登录"还是"该刷新数据"，只能弹同一个提示。

### 1.3 分页

请求参数 `page`（从 1 开始）、`size`（服务端强制 1–100）。

响应：

```json
{
  "items": [],
  "total": 9,
  "page": 1,
  "size": 10
}
```

### 1.4 时间与金额

- 时间：ISO-8601 UTC 字符串，如 `2026-10-22T14:03:11.512Z`，前端按本地时区渲染。
- 金额：JSON 数字，两位小数；服务端以 `BigDecimal` 计算，不经过浮点。

---

## 2. 认证

### 2.1 登录

`POST /api/auth/login` · 无需鉴权

**请求**

```json
{ "username": "zhangwei", "password": "123456" }
```

**响应**

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "token": "6173861692de4c35938ccb612fd620cc",
    "user": {
      "id": 1,
      "username": "zhangwei",
      "displayName": "张伟",
      "department": "研发部",
      "role": "EMPLOYEE",
      "roleLabel": "员工",
      "deptAndRole": "研发部 · 员工"
    }
  }
}
```

**错误**：`400` 用户名或密码错误（不区分两者，防止账号枚举）

### 2.2 当前用户

`GET /api/auth/me` · 需鉴权 → `data` 为用户对象

### 2.3 退出登录

`POST /api/auth/logout` · 需鉴权 → 令牌立即失效，`data` 为 `null`

---

## 3. 申请单

### 3.1 列表查询

`GET /api/requests`

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| tab | string | 否 | `mine` / `todo` / `all`，默认 `all` |
| status | string | 否 | 状态枚举值 |
| category | string | 否 | 类别枚举值 |
| keyword | string | 否 | 匹配标题或单号，忽略大小写 |
| minAmount / maxAmount | number | 否 | 金额区间 |
| page | int | 否 | 默认 1 |
| size | int | 否 | 默认 10，上限 100 |

> `tab` 的语义由**服务端**结合角色决定，前端无法通过传参扩大可见范围。

**响应 `data`**

```json
{
  "items": [
    {
      "id": 1,
      "code": "PR20261021-0001",
      "title": "研发部 Q4 办公用品采购",
      "reason": "打印纸、硒鼓、签字笔等常规消耗品补充，覆盖 10-12 月用量",
      "category": "OFFICE",
      "categoryLabel": "办公用品",
      "amount": 2340.00,
      "department": "研发部",
      "applicantName": "张伟",
      "applicantUsername": "zhangwei",
      "status": "PENDING_MANAGER",
      "statusLabel": "待主管审批",
      "createdAt": "2026-10-21T12:41:00Z",
      "updatedAt": "2026-10-21T13:41:00Z",
      "canApprove": true,
      "canReject": true,
      "canSubmit": false,
      "canCancel": false,
      "canEdit": false,
      "waitingFor": "部门主管"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 10
}
```

> `canApprove / canReject / canSubmit / canCancel / canEdit` 由服务端按"当前登录人"计算后下发，前端据此渲染按钮，避免"点下去才报错"。

### 3.2 工作台统计

`GET /api/requests/stats`

```json
{
  "pendingForMe": 1,
  "myOngoing": 0,
  "total": 5,
  "todoCount": 1,
  "mineCount": 0,
  "allCount": 5,
  "monthApprovedAmount": 1280.00,
  "statusCounts": [
    { "status": "DRAFT", "label": "草稿", "count": 1 },
    { "status": "PENDING_MANAGER", "label": "待主管审批", "count": 1 },
    { "status": "PENDING_FINANCE", "label": "待财务复核", "count": 1 },
    { "status": "APPROVED", "label": "已通过", "count": 1 },
    { "status": "REJECTED", "label": "已驳回", "count": 0 },
    { "status": "CANCELLED", "label": "已作废", "count": 1 }
  ],
  "categoryAmounts": [
    { "category": "OFFICE", "label": "办公用品", "amount": 1280.00 }
  ]
}
```

### 3.3 详情

`GET /api/requests/{id}`

**响应 `data`**

```json
{
  "request": { "id": 1, "code": "PR20261021-0001", "status": "PENDING_MANAGER" },
  "records": [
    {
      "id": 1,
      "action": "CREATE",
      "actionLabel": "创建",
      "fromStatus": null,
      "fromStatusLabel": null,
      "toStatus": "DRAFT",
      "toStatusLabel": "草稿",
      "operatorName": "张伟",
      "operatorRole": "EMPLOYEE",
      "operatorRoleLabel": "员工",
      "comment": "创建申请单",
      "createdAt": "2026-10-21T12:41:00Z"
    },
    {
      "id": 2,
      "action": "SUBMIT",
      "actionLabel": "提交",
      "fromStatus": "DRAFT",
      "fromStatusLabel": "草稿",
      "toStatus": "PENDING_MANAGER",
      "toStatusLabel": "待主管审批",
      "operatorName": "张伟",
      "operatorRole": "EMPLOYEE",
      "operatorRoleLabel": "员工",
      "comment": "提交审批",
      "createdAt": "2026-10-21T13:41:00Z"
    }
  ]
}
```

**错误**：`403` 无权查看、`404` 单据不存在

### 3.4 创建

`POST /api/requests` · 仅 `EMPLOYEE`

```json
{
  "title": "研发部 Q4 办公用品采购",
  "reason": "打印纸、硒鼓、签字笔等常规消耗品补充",
  "category": "OFFICE",
  "amount": 2340.00
}
```

**校验规则**

| 字段 | 规则 | 失败提示 |
| --- | --- | --- |
| title | 非空，≤120 | 标题不能为空 / 标题不能超过 120 字 |
| reason | 非空，≤500 | 申请事由不能为空 |
| category | 枚举 | 请选择采购类别 |
| amount | >0，≤2 位小数 | 金额必须大于 0 / 金额最多两位小数 |

创建结果状态恒为 `DRAFT`。**部门与申请人取自登录用户**，请求体中传入无效。

### 3.5 修改草稿

`PUT /api/requests/{id}` · 仅本人且 `DRAFT`

**错误**：`403` 非本人、`409` 非草稿状态

### 3.6 提交审批

`POST /api/requests/{id}/submit` · 仅本人且 `DRAFT` → `PENDING_MANAGER`

**错误**：`409` 状态不允许提交

### 3.7 审批通过

`POST /api/requests/{id}/approve`

```json
{ "comment": "同意，按预算执行" }
```

- `comment` 可选，≤300 字。
- 权限：主管节点需同部门主管且非本人；财务节点需财务角色。
- 结果：`< 5000` → `APPROVED`；`>= 5000` → `PENDING_FINANCE`。

**演示专用请求头**

| 请求头 | 说明 |
| --- | --- |
| `X-Demo-Delay-Ms` | 仅当 `app.demo-mode=true` 生效，人为延迟提交，用于稳定复现并发冲突 |

**错误**：`403` 无权审批、`409` 已终态、`409 CONCURRENT_MODIFICATION` 并发冲突

### 3.8 驳回

`POST /api/requests/{id}/reject`

```json
{ "reason": "本季度差旅预算已用尽，请下季度再提交" }
```

`reason` **必填**，≤300 字 → 状态变为 `REJECTED`。

### 3.9 作废

`POST /api/requests/{id}/cancel`

```json
{ "reason": "活动取消，撤回申请" }
```

`reason` 可选（默认"申请人撤回"）。权限：申请人本人（未终态）或管理员（任意未终态）。

---

## 4. 调用示例

```bash
# 1. 登录拿令牌
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"lijing","password":"123456"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

# 2. 查看我的待办
curl -s "http://localhost:8080/api/requests?tab=todo" -H "Authorization: Bearer $TOKEN"

# 3. 审批通过
curl -s -X POST "http://localhost:8080/api/requests/1/approve" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"comment":"同意"}'

# 4. 复现并发冲突（需 demo-mode=true）
curl -s -X POST "http://localhost:8080/api/requests/1/approve" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Demo-Delay-Ms: 1200" \
  -H "Content-Type: application/json" -d '{}' &
curl -s -X POST "http://localhost:8080/api/requests/1/approve" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Demo-Delay-Ms: 1200" \
  -H "Content-Type: application/json" -d '{}' &
wait
```

## 5. 接口清单

| # | 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | POST | /api/auth/login | 公开 | 登录 |
| 2 | GET | /api/auth/me | 登录 | 当前用户 |
| 3 | POST | /api/auth/logout | 登录 | 退出 |
| 4 | GET | /api/requests | 登录 | 列表（含数据范围） |
| 5 | GET | /api/requests/stats | 登录 | 工作台统计 |
| 6 | GET | /api/requests/{id} | 可见 | 详情 + 时间线 |
| 7 | POST | /api/requests | 员工 | 创建 |
| 8 | PUT | /api/requests/{id} | 本人 | 修改草稿 |
| 9 | POST | /api/requests/{id}/submit | 本人 | 提交 |
| 10 | POST | /api/requests/{id}/approve | 节点审批人 | 通过 |
| 11 | POST | /api/requests/{id}/reject | 节点审批人 | 驳回 |
| 12 | POST | /api/requests/{id}/cancel | 本人/管理员 | 作废 |
