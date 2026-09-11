# ROLLBACK — 历史改动原状与回滚方式(铁律 #5)

> 规则:任何改动落地前,先在此记录"原状 + 回滚方式";改动完成后更新为"当前状态"。
> 历史提交不可改写,回滚一律用新提交(revert / 改回旧值再 commit)。

## 2026-09-03 批次(optee/tf-a 一致性修复 + manifest 钉升级 + 文档 v2.4)

| # | 改动 | 原状 | 回滚方式 |
|---|------|------|---------|
| 1 | optee fork `shuhe-dev` 快进到 `14014073d`(revert DeepSeek marker in boot.c) | `ca77602a` | `git push origin ca77602a…:refs/heads/shuhe-dev`(非快进,需确认覆盖) |
| 2 | tf-a fork `shuhe-dev` 快进到 `64d7fa968`(revert DeepSeek marker in bl2_main.c) | `8e5c3d902` | 同上 |
| 3 | `meta-st-stm32mp/recipes-security/optee/optee-os-stm32mp-common_4.0.0.inc` SRCREV → `14014073d…` | `ca77602a…` | 改回旧值 commit + push |
| 4 | `meta-st-stm32mp/recipes-bsp/trusted-firmware-a/tf-a-stm32mp-common.inc` SRCREV → `64d7fa968…` | `8e5c3d902…` | 改回旧值 commit + push |
| 5 | `oe-manifest/default.xml` 四钉升级:bitbake `b2404004` / oe-core `52380df` / meta-oe `5124ac4` / addons `ad667af` | bitbake `7375d32` / oe-core `cd2b608` / meta-oe `e92d017` / addons `40ddfa3` | 改回旧 SHA commit + push |
| 6 | 本机 build-test 目录 `devtool reset optee-os-stm32mp tf-a-stm32mp` | 原 workspace 在 `workspace/attic/sources/`(2026-09-03 备份) | `devtool modify` 重建 workspace 或用 attic 源码 |

### 当前状态

- optee/tf-a fork `shuhe-dev` = `14014073d` / `64d7fa968`(已含 revert 提交,构建产物一致)
- meta-st-stm32mp `shuhe-dev`/`shuhe-dev-v26.06.10` = `4f76dcb`(SRCREV 已同步)
- oe-manifest `shuhe-dev` = `7f7eefc`(manifest 钉 = 本机构建实际版本)
- 本机:linux workspace 保留;optee/tf-a 由 recipe 驱动构建

## 2026-09-03 批次 B(手册分发模式迁移:skill → Meta 层)

| # | 改动 | 原状 | 回滚方式 |
|---|------|------|---------|
| 1 | 手册主体迁入 `meta-st-stm32mp/docs/shuhe-skill/`(SKILL / CUSTOM_MAP / HARDWARE_MAP / ROLLBACK / templates,含本文件) | 全部在 oe-manifest 仓库 `skill/` 目录 | 从 oe-manifest git 历史(v2.4 提交 `556b30f`)取回 `skill/` 内容;删除层内 `docs/shuhe-skill/` |
| 2 | meta-st-openstlinux 新增 `docs/shuhe-skill/README.md`(指针) | 无此文件 | 删除该文件 |
| 3 | oe-manifest `skill/` 瘦身为 stub(仅 `SKILL.md` + `README.md`),原 `docs/`、`templates/` 删除 | 完整手册目录 | 见 #1 |

### 当前状态

- 注册入口(agent skills 目录)= oe-manifest `skill/` stub(短描述,省 token)
- 完整手册唯一真相源 = `meta-st-stm32mp/docs/shuhe-skill/`(随 repo sync 分发)
- openstlinux 层 = 指针;迁移前完整版 = oe-manifest `556b30f`