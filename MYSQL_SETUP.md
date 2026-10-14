# MySQL setup — Bank Islami Corporate

Uses the same MySQL user as DFS (`dfs` / `change-me`) but a **separate database**.

## One-time (as MySQL root)

In MySQL Workbench or CLI (root password from your install):

```sql
CREATE DATABASE bankislami_corporate
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON bankislami_corporate.* TO 'dfs'@'localhost';
FLUSH PRIVILEGES;
```

## Run backend

```powershell
cd D:\Karsaaz\Bank-Islami-Corporate\backend
mvn spring-boot:run
```

Port **8091**. Flyway builds all DFS tables + `V33` NTN mock directory.

## Do not

- Do not point this app at `dfs_corporate` (that is the DFS Corporate DB).
- Do not change `D:\Karsaaz\DFS-Corporate`.
