# MySQL 初始化脚本

本目录下的 `.sql` 脚本会在 MySQL 容器**首次启动**时按文件名顺序自动执行（仅首次，数据卷已存在时不会重复执行）。

## 使用方式

每设计一个业务模块（如 `lms-user`），在这里新增对应的建库脚本：

```sql
-- 01-lms-user.sql
CREATE DATABASE IF NOT EXISTS `lms_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

> 注意：脚本只在数据卷为空时执行。若已在跑，改完脚本后需 `docker compose down -v && docker compose up -d` 重建（会清空所有数据）。
