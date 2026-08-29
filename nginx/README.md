# nginx 部署（玩法 1：托管前端 + /api 反代网关）

## 这是什么

用 nginx 替代「前端直连网关 + CORS」的开发形态：

```
浏览器 ──► nginx:80 ── 静态资源（lms-web/dist）
                  └── /api/** ──► Spring Cloud Gateway:8080（去 /api 前缀）
```

- 前端构建产物默认 `baseURL=/api`（见 `lms-web/src/api/request.js` 兜底值），与本方案天然匹配
- 同源后不再依赖网关 `globalcors` 放行（网关配置无需改动）
- 网关路由不变：`/api/auth/login` 经 nginx 转发后为 `/auth/login`

## 使用步骤（本机 Windows nginx）

1. 构建前端产物：

   ```bash
   cd lms-web
   npm run build        # 生成 dist/
   ```

2. 把 `nginx/nginx.conf` 放到你的 nginx 安装目录 `conf/` 下
   （或新建 `conf/lms.conf` 并在 `nginx.conf` 的 http 块里 `include lms.conf;`）

3. 修改 `nginx.conf` 中的 `root` 为 `lms-web/dist` 的实际路径

4. 启动：

   ```bash
   nginx -t             # 校验配置
   nginx                # 启动（默认 80 端口，需保证 80 空闲）
   ```

5. 访问 http://localhost → 前端页面；登录等接口自动走 `/api` 反代到网关

## 使用步骤（docker compose 可选）

如果想用容器跑 nginx（不依赖本机安装），在 `docker-compose.yml` 增加服务（放到
`nginx/nginx.conf` 的同级或按需调整）：

```yaml
  nginx-web:
    image: nginx:1.27-alpine
    container_name: lms-nginx
    profiles: ["web"]
    ports:
      - "80:80"
    volumes:
      - ./lms-web/dist:/usr/share/nginx/html:ro
      - ./nginx/nginx.conf:/etc/nginx/conf.d/default.conf:ro
```

> 容器内访问宿主网关需把 `nginx/nginx.conf` 的 `proxy_pass` 改为
> `http://host.docker.internal:8080/`（Docker Desktop 支持）。

## 注意

- 本配置面向「生产/演示形态」；开发调试仍用 `npm run dev`（5173 直连网关 8080，见 `.env.development`）
- 端口 80 被占用时可改 `listen`（如 `8081`），访问地址同步调整
- 网关的 CORS 配置可保留（对同源请求无影响），也可在确认走 nginx 后移除
