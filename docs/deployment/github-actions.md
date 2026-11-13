# GitHub Actions → 阿里云 ACR → ECS

适用于当前专注花园：两个独立仓库分别发布，宿主Nginx继续管理HTTPS，Docker Compose继续管理 `mysql/server/web`。不用在本地手动打JAR或上传构建产物。

## 触发与执行

文件：`.github/workflows/deploy.yml`，辅助脚本 `.github/scripts/ssh-deploy.sh` 和 `deploy.sh`。

- PR到main：仅运行检查，不登录ACR或服务器，不读取生产Secrets。
- 推送main或在main手动Run workflow：先检查，通过后构建 `linux/amd64` 镜像，推送ACR，再经SSH更新对应服务。
- 手动选择非main分支：只检查，不发布。
- 后端检查：Java21、Maven Wrapper、单元测试，以及Runner内MySQL8.4独立临时库上的HTTP/数据库集成测试；邮件使用测试替身。
- 前端检查：Node22、项目固定pnpm10.34.9、锁文件安装、单元测试和TypeScript/Vite生产构建。当前工作流不运行Playwright浏览器测试。
- 测试通过后Dockerfile再次构建镜像；Dockerfile中的跳过测试仅避免重复执行，发布必须依赖前面的测试成功。
- 镜像同时发布 `sha-完整提交SHA` 和 `latest`，服务器实际按 `@sha256:...` 部署并写入.env，后续不会因latest变化而偷偷切版本。

目前只是新增配置文件；提交和推送工作流后，仍需完成以下Secrets与SSH准备，才能真正部署。没有在本次编辑中修改线上服务。

## 1. GitHub Secrets（两个仓库分别配置）

仓库 Settings → Secrets and variables → Actions → Secrets → New repository secret。

| Secret | 内容 |
| --- | --- |
| `ACR_USERNAME` | ACR访问凭证中的登录用户名，当前使用xiyeeee；以控制台实际值为准 |
| `ACR_PASSWORD` | ACR固定登录密码或适用的访问凭证，不是ECS SSH密码、MySQL密码或QQ授权码 |
| `SSH_PRIVATE_KEY` | 部署专用、无口令的OpenSSH私钥全文，包含BEGIN/END行 |
| `SSH_KNOWN_HOSTS` | 已核实服务器指纹的known_hosts公钥记录，支持多行 |

这些值不能直接写进YAML。这里不需要上传线上.env、数据库密码、SMTP授权码或HTTPS私钥。

可选的Repository Variables位于同一页面的Variables标签：

| Variable | 默认值 |
| --- | --- |
| `SSH_HOST` | `47.121.214.43` |
| `SSH_PORT` | `22` |
| `SSH_USER` | `root` |
| `DEPLOY_PATH` | `/home/docker/focus-garden` |

当前脚本按现有部署写定：宿主代理端口8083、ACR地址和 `xiye-docker` 命名空间。迁移服务器或端口时同步调整脚本和文档。

## 2. 一次性准备SSH密钥

在自己电脑执行，公钥可以放服务器；私钥只存本地私密目录和GitHub Secrets，不提交Git：

```sh
mkdir -p ~/.ssh
ssh-keygen -t ed25519 -N '' -C focus-garden-github-actions \
  -f ~/.ssh/focus-garden-actions
ssh root@47.121.214.43 'umask 077; mkdir -p ~/.ssh; cat >> ~/.ssh/authorized_keys' \
  < ~/.ssh/focus-garden-actions.pub
ssh -i ~/.ssh/focus-garden-actions -o IdentitiesOnly=yes root@47.121.214.43 true
```

密钥已存在时不要覆盖；确认文件名后另建一个。将 `~/.ssh/focus-garden-actions` 文件全文填入两个仓库的 `SSH_PRIVATE_KEY`。

收集并核实known_hosts公钥：

```sh
ssh-keyscan -t ed25519 -p 22 47.121.214.43 > /tmp/focus-garden-known-hosts
ssh-keygen -lf /tmp/focus-garden-known-hosts
# 通过已信任的SSH连接或阿里云终端查看服务器指纹，比较两者一致：
ssh root@47.121.214.43 'ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub'
```

把已核实的 `/tmp/focus-garden-known-hosts` 内容填入 `SSH_KNOWN_HOSTS`，不是私钥，也不是屏幕上SHA256指纹那一行。非22端口的记录应为 `[主机]:端口` 格式。工作流强制主机校验，不会自动接受陌生主机。

## 3. 服务器一次性准备

沿用当前部署目录、Docker Compose和宿主Nginx；辅助脚本还要求服务器提供curl、Python3和flock，首次启用前确认。发布用户需要读写部署目录、运行Docker、备份数据库以及在web容器里重载Nginx的权限。

服务器使用自己的Docker登录凭据拉取私有镜像。GitHub上的docker/login-action不会自动给服务器登录。若服务器尚未登录，SSH进入后执行：

```sh
docker login --username=xiyeeee \
  crpi-ow7mi2apt624v3c8.cn-heyuan.personal.cr.aliyuncs.com
cd /home/docker/focus-garden
docker compose config -q
docker compose ps
command -v curl python3 flock
```

交互输入ACR密码。确认线上.env里的生产配置、数据库与SMTP密钥保持原样，Compose仍有 `IMAGE_SERVER` / `IMAGE_WEB` 变量。

服务器原有Compose和.env不会被工作流上传或覆盖；辅助脚本通过SSH标准输入临时执行，也不要求服务器git pull源码。修改宿主Nginx、证书、Compose结构或数据库环境变量仍需单独维护。

## 4. 实际发布过程

1. GitHub在main完成测试、镜像构建与推送。
2. SSH进入部署目录，获取前后端共用的 `.github-deploy.lock`，最多等10分钟。同仓库也设置了Actions并发队列，不会主动取消正在发布的任务。
3. 将当前.env备份到 `deploy/backups/时间-服务-digest.env`，权限600，不打印内容。
4. 拉取本次精确镜像。后端发布先对MySQL执行一致性逻辑备份，写入同目录.sql；备份失败停止发布。
5. 使用 `docker compose up -d --no-deps --no-build server` 或 `web`，不重建MySQL，不删除数据卷。
6. 等待后端容器healthy，或检查前端静态首页。后端更换容器后重载web中的Nginx，刷新静态upstream缓存的容器IP。
7. 检查宿主 `http://127.0.0.1:8083/api/health`。成功后仅原子更新.env中当前服务的IMAGE变量，并记录 `deploy/releases/时间-服务-digest.txt`。

后端启动时Flyway自动执行新迁移（当前含V7昵称登录迁移）。数据库和配置备份含敏感数据，不能加入Git或公开下载。备份当前留在同机，尚无自动异机备份和清理策略，需定期转存并检查磁盘。

## 5. 失败处理与回退

检查Actions日志定位测试、ACR认证、SSH指纹、镜像拉取或健康检查失败。缺少Secret会在构建镜像前明确失败。

```sh
ssh root@47.121.214.43
cd /home/docker/focus-garden
docker compose ps
docker compose logs --tail=100 server
docker compose logs --tail=100 web
docker stats --no-stream
```

健康失败时工作流返回失败，不会将本次digest写入.env；新容器可能已启动，因此.env可能仍记录上一版，需要人工核实运行镜像。Actions会输出旧镜像引用和.env备份位置。不要看到镜像push成功就认为发布成功。

脚本不自动回退容器或数据库，因为Flyway可能已执行迁移。确认旧代码兼容新表结构后，暂停其他发布，在服务器用记录的旧digest显式启动对应服务，并同步.env的IMAGE变量。回退后端后再次重载web的Nginx并检查健康。不要直接恢复整个.env覆盖另一仓库刚更新的镜像，也不要盲目恢复数据库覆盖新业务数据。

镜像以SHA标签保留，回退不依赖可变latest。普通 `docker compose pull` 使用.env里固定的digest，不能代替发布新版本；使用Actions或明确更新IMAGE变量。

## 6. 依据与当前验证边界

- 构建推送方式依据 [GitHub官方Docker镜像工作流说明](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)。
- ACR凭证和镜像拉取依据 [阿里云个人版镜像仓库说明](https://www.alibabacloud.com/help/en/acr/user-guide/use-a-container-registry-personal-edition-instance-to-push-and-pull-images)。
- Actions固定到官方仓库核实的完整commit SHA，注释记录对应release；升级时需重新核实。
- 本地检查不等于GitHub Runner和线上发布验证。首次运行还需检查ACR网络、GitHub Secrets、SSH免密与known_hosts，以及完整健康检查结果。
- 本次本地验证：两个YAML通过actionlint 1.7.12；Bash语法检查通过；每个仓库6个发布脚本替身测试通过（前后端成功、拉取失败、备份失败、容器健康失败、代理健康失败）。后端在不加载开发数据库/SMTP环境变量的条件下通过49项测试和打包；前端TypeScript/Vite构建通过。没有向ACR推送镜像或执行线上部署。
