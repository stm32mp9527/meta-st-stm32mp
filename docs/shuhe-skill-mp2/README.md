# shuhe-ostl-mp2 — STM32MP257F-DK AI 工作手册

本目录是 **MP2/257F-DK** 的完整 AI 手册，随 `meta-st-stm32mp` 层分发。
MP1（STM32MP135F 双板型）的手册在隔壁 `docs/shuhe-skill/`，不要混用引脚和节点。

## 文件清单

| 文件 | 内容 |
|------|------|
| `SKILL.md` | 主手册：铁律、部署、构建、烧录、DTS/引脚、常用修改、自定义板、排错、交付纪律 |
| `HARDWARE_MAP.md` | 257F-DK 板级参数、boot switch、连接器、DTS 节点映射、默认 disabled 节点 |
| `CUSTOM_MAP.md` | 常用本地修改/外设启用/自定义板导航、DTS 模板、验证命令 |
| `README.md` | 本文件：使用方式、stub 安装、维护纪律 |
| `ROLLBACK.md` | 改动记录模板（文件/原值/还原命令） |

## AI 使用方式

1. 进入项目后先读 `SKILL.md` 第 0 节铁律。
2. 按 `SKILL.md` §1 定位 `<ROOT>`/`<BUILD>`/`<DEPLOY>`，不要硬编码路径。
3. 客户提出具体修改时，先用 `CUSTOM_MAP.md` 的任务表定位，再读对应章节。
4. 官方 257F-DK 的硬件问题先查 `HARDWARE_MAP.md`；自定义板必须索要原理图/引脚表。
5. 改完必须构建并给出板端验证命令，不能只给“应该可以”。

## Stub 安装（可选，便于 agent 自动加载）

完整手册随层分发；agent 的注册入口是 `oe-manifest` 仓库的 `skill-mp2/` stub。

```bash
git clone -b shuhe-dev --depth 1 https://github.com/stm32mp9527/oe-manifest.git /tmp/om
mkdir -p ~/.config/opencode/skills
cp -r /tmp/om/skill-mp2 ~/.config/opencode/skills/shuhe-ostl-mp2

# OpenClaw:
# mkdir -p ~/.openclaw/skills
# cp -r /tmp/om/skill-mp2 ~/.openclaw/skills/shuhe-ostl-mp2
```

安装后下一轮对话 agent 应能加载 `shuhe-ostl-mp2`；触发词见 stub 的 description。

## 维护纪律

- 手册正文只改本目录（唯一真相源）。
- stub 只维护入口/触发词，不复制正文。
- 每次改 DTS/配置/分区，在 `ROLLBACK.md` 记录原值和还原命令。
- 不把本机绝对路径写进手册或客户 txt；统一用 `<ROOT>`/`<BUILD>`/`<DEPLOY>`。
- 不把 MP1 的 SII9022/OV5640/PA13 等案例直接套到 257F-DK；257F-DK 是 ADV7535/IMX335。
