# Ubuntu deployment preparation

This directory contains templates only. It does not contain production credentials and does not deploy a server.

## Build

Use Java 21 and the checked-in Maven Wrapper:

```bash
chmod +x mvnw
./mvnw clean test
./mvnw clean package
```

## Install

1. Create a dedicated `qintelipass` system user and `/opt/qintelipass` directory.
2. Copy the packaged JAR, this `deploy/ubuntu` directory, and a private `.env` based on `env.example` to `/opt/qintelipass`.
   Keep `.env` owned by `root:qintelipass` with mode `0640`; keep the JAR and scripts owned by `qintelipass:qintelipass`.
3. Verify the real MySQL schema before starting. The production profile uses `ddl-auto=validate` and will not alter tables.
4. Ensure `deploy/ubuntu/start.sh` is executable, install `qintelipass.service.example` as `/etc/systemd/system/qintelipass.service`, then run:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now qintelipass
sudo systemctl status qintelipass
```

The API keeps the `/api/v1` prefix. By default the production profile binds to `127.0.0.1`; configure Nginx to proxy that prefix to `127.0.0.1:7510` (or the selected `SERVER_PORT`) and set `CORS_ALLOWED_ORIGINS` to the exact frontend origin list. Set `SERVER_ADDRESS=0.0.0.0` only when direct network exposure is intentional and protected by a firewall.

Set `ADMIN_USER_IDS` to a comma-separated list of trusted numeric user IDs when the administration API is needed. An empty value deliberately denies every `/api/v1/admin/**` request; ordinary authenticated users never receive administrator authority.

`start.sh` rejects missing database credentials, Redis host, JWT secret, or CORS origins without printing their values. Keep `TZ`/`-Duser.timezone` aligned with the business timezone because daily token aggregation uses the JVM clock.

The repository does not contain a real SMS delivery gateway. The current SMS service only stores a rate-limited one-time code in Redis, so SMS login must not be presented as production-ready until a provider is integrated and Redis 7 Lua/TTL/concurrency behavior is verified. Add trusted-proxy or API-gateway IP rate limiting before enabling the public send-code endpoint.
