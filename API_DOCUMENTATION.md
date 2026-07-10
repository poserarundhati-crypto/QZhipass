# QZhipass 用户管理与注销接口

本文档对应当前 Spring Boot 4.1 / Spring Security 7 实现。所有生产凭据只能通过服务器环境变量提供，前端不得持有管理员共享密钥。

## 认证与管理员授权

- 登录入口：`POST /api/v1/auth/portal/login`。
- 受保护请求必须携带 `Authorization: Bearer <access-token>`。
- JWT 过滤器会重新查询用户；`DEACTIVATED` 或其他非 `NORMAL` 账号不能继续使用已有 Token。
- `/api/v1/admin/**` 还要求 `ROLE_ADMIN`。后端只会给 `ADMIN_USER_IDS` 环境变量中列出的数字用户 ID 赋予该角色。
- `ADMIN_USER_IDS` 为空时，所有管理接口默认拒绝访问。
- 不支持共享 Admin Key，也不存在前端可使用的默认管理员密钥。

## 获取用户列表

```http
GET /api/v1/admin/users?q=<keyword>&page=1&size=20
Authorization: Bearer <access-token>
Accept: application/json
```

Query 参数：

- `q`：可选，按用户名或手机号筛选。
- `page`：可选，默认 `1`。
- `size`：可选，默认 `20`。

成功响应为 `200 OK`：

```json
{
  "total": 1,
  "items": [
    {
      "id": "9007199254740993",
      "phone": "13800000000",
      "email": "user@example.com",
      "status": "NORMAL",
      "name": "example-user",
      "department": "研发部",
      "joinedAt": "2026-07-11T01:00:00+08:00"
    }
  ]
}
```

Snowflake 用户 ID 始终序列化为字符串，前端不得转成 JavaScript `number`。

## 注销用户

```http
DELETE /api/v1/admin/users/{userId}
Authorization: Bearer <access-token>
Accept: application/json
```

- `userId`：必填，目标用户的数字 ID；前端应按字符串保存并原样拼入路径。
- 注销会把用户状态改为 `DEACTIVATED`，不会物理删除用户记录。

成功响应为 `200 OK`：

```json
{
  "success": true,
  "message": "User deactivated successfully"
}
```

用户不存在或已经注销时返回 `400 Bad Request`：

```json
{
  "success": false,
  "message": "Failed to deactivate user. User may not exist or already be deactivated."
}
```

## 认证失败响应

- 未携带有效 JWT：`401 Unauthorized`，JSON 消息为“未登录或登录已失效”。
- 已登录但不在管理员白名单：`403 Forbidden`，JSON 消息为“无权访问该资源”。
- 认证阶段数据库不可用：`503 Service Unavailable`，返回固定通用 JSON，不暴露 SQL 或堆栈。

## 前端调用示例

```javascript
function authorizationHeaders() {
  const accessToken = window.localStorage.getItem('access_token');
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
}

const response = await fetch('/api/v1/admin/users?page=1&size=20', {
  headers: authorizationHeaders()
});
```

生产环境应使用同源 `/api/v1` 路径并由 Nginx 反向代理，避免在前端代码中写死服务器地址。

## 服务器环境变量

至少需要配置：

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`（Redis 未设置密码时可为空）
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `ADMIN_USER_IDS`（逗号分隔；为空则禁用管理接口）

Ubuntu 模板、启动命令和 systemd 配置见 `deploy/ubuntu/README.md`。生产 Profile 使用 `ddl-auto=validate`，启动前必须由数据库负责人核对完整表结构。

## 当前验证边界

- 自动化测试使用 H2 的 MySQL 兼容模式。
- 尚未连接真实 MySQL、真实 Redis 或 Ubuntu 服务器。
- 短信验证码已做 Redis 键隔离、单次消费和服务端频控，但仓库尚未接入真实短信发送网关，因此短信登录不能视为生产可用。
- 尚未执行完整生产 DDL，也未进行云端部署。
