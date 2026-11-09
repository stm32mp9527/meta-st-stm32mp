# shuhe-ostl-deploy — 完整手册(唯一真相源)

本目录是 shuhe OpenSTLinux(STM32MP135F 双板型)项目的 AI 工作手册**完整版**,
随本层(meta-st-stm32mp)的 `repo sync` 自动分发,无需单独安装。

## 文件清单

| 文件 | 内容 |
|------|------|
| `SKILL.md` | 手册正文:铁律 / 部署流程 / 本地改源码 / 板卡适配 / 新板生成 / SRCREV 指向表 / 排错 |
| `CUSTOM_MAP.md` | 现有双板型外设修改点(行号级定位 + 验证命令) |
| `HARDWARE_MAP.md` | 硬件参数 → 修改点映射(板卡适配问答库) |
| `ROLLBACK.md` | 历史改动原状与回滚方式(铁律 #5,每次改动必须更新) |
| `templates/` | local.conf.shuhe / local.conf.dk / machine-variant.tmpl |

## 使用方式

- AI/人进入本项目:**先读 `SKILL.md`,并遵守其第 0 节铁律**
- opencode / OpenClaw 的注册入口是本手册的 **stub**(在 oe-manifest 仓库 `skill/`
  目录),stub 只负责触发词与指路;本目录**不需要**复制到 agent 的 skills 目录
- 另一层(meta-st-openstlinux)`docs/shuhe-skill/README.md` 有指针指向本目录

## 维护纪律

- 手册内容改动**只改本目录**(唯一真相源),改完在 `ROLLBACK.md` 记录原状与回滚方式
- 版本历史见 oe-manifest / meta-st-stm32mp 的 git 提交(v2.x 系列)
- stub 与安装说明在 oe-manifest 仓库 `skill/README.md`,不在本目录

## MP2 / STM32MP257F-DK

257F-DK 请读隔壁 `docs/shuhe-skill-mp2/SKILL.md`，不要混用 MP1 的引脚/节点。

## 上层应用开发（type 2 客户）

只做 app（网络/显示/开机自启），不改 kernel/uboot/dts → 读
`meta-st-openstlinux` 层的 `docs/shuhe-skill-app/SKILL.md`（`shuhe-ostl-app`）。
本目录是 BSP/部署手册。

## 交付方式（repo 为主，VM 可选）

- 主交付：客户在 Linux 服务器/PC 上 `repo sync`，AI 读本目录/BSP skill 构建；**不需要虚拟机**。
- 可选：没有 Linux 环境、需要隔离或展会演示时，才用 VM/Docker。
- 统一客户入口：同目录 `CUSTOMER_PROMPT.md`，按 app / BSP / MP1 / MP2 自动分流。
