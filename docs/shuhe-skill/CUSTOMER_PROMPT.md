# STM32MP1 / STM32MP2 项目入口（给 AI 的提示词）

我是客户，可能只做上层 app，也可能要改底层/重编镜像。
**我不学 Yocto，也不装交叉编译器。** 构建、部署、自启由你负责。

---

## 第一步：判断客户类型

| 客户需求 | 走哪条路 |
|----------|----------|
| 只做上层 app：网络/显示/电池/电机/传感器/数据采集/工业协议；**不改** kernel/uboot/dts/pinctrl/分区/启动介质 | `shuhe-ostl-app` |
| 改底层/驱动/dts/内存/uboot/分区/重编镜像，**MP1** | `shuhe-ostl-deploy` |
| 新板建层 / PHY/LCD 微调 / 烧录包检查，**MP1** | `shuhe-ostl-deploy` + `MULTIBOARD_MAP.md` |
| 改底层/驱动/dts/内存/uboot/分区/重编镜像，**MP2 / 257F-DK** | `shuhe-ostl-mp2` |
| 不确定 | 先问客户：板子是 MP1 还是 MP2？是只写 app，还是要改底层/重烧镜像？ |

对应手册（先找到 `<ROOT>`）：

```text
纯 app:
  <ROOT>/layers/meta-st/meta-st-openstlinux/docs/shuhe-skill-app/SKILL.md
  <ROOT>/layers/meta-st/meta-st-openstlinux/docs/shuhe-skill-app/APP_MAP.md

MP1 BSP:
  <ROOT>/layers/meta-st/meta-st-stm32mp/docs/shuhe-skill/SKILL.md
  <ROOT>/layers/meta-st/meta-st-stm32mp/docs/shuhe-skill/MULTIBOARD_MAP.md

MP2 BSP:
  <ROOT>/layers/meta-st/meta-st-stm32mp/docs/shuhe-skill-mp2/SKILL.md
```

`<ROOT>` 是包含 `.repo/` 或 `layers/meta-st/meta-st-stm32mp` 的目录。
找不到就问客户路径；不要自行 clone 未知仓库。

如果还没有项目（**AI 执行，不要让客户做**）：

```bash
mkdir -p <ROOT> && cd <ROOT>
repo init -u https://github.com/stm32mp9527/oe-manifest.git -b shuhe-dev -m default.xml
repo sync -j8
```

sync 完再读对应 skill。

---

## 交付方式：repo 为主，VM 可选

- **有 Linux 服务器/PC**：直接 `repo sync`，在服务器上构建；**不需要虚拟机**。
- **没有 Linux 环境、需要隔离、展会演示**：VM/Docker 才作为可选项。
- 客户只描述需求；不要要求客户学 Yocto、装交叉编译器、手写 Makefile。
- 如果项目还没同步，按对应 skill 的首节执行 `repo init/sync`；不要让客户手动做。

---

## 纯 app 客户

先问领域，不要默认电池/网络/显示：

- 输入：什么物理量/数据/协议？来自 IIO / hwmon / power_supply / I2C / SPI /
  UART / CAN / USB / V4L2 / ALSA / GPIO / 网络？
- 处理：客户算法/协议/状态机；采样率、精度、实时性。
- 输出：GPIO / PWM / 继电器 / 电机 / 屏幕 / 网络 / 存储 / 告警。
- 安全/急停/互锁/故障默认状态；开机自启/日志/远程调试。
- 内核是否已暴露标准接口？没有 → 转 BSP，不要在 app 层写驱动。

模板/骨架：

```text
网络        -> templates/network-app/
显示        -> templates/display-app/
传感器/采集  -> templates/battery-monitor/（通用 sysfs 骨架，电池只是示例）
电机/工业/视觉/音频 -> 按 templates/README.md 生成新骨架，不要硬套
```

**必须 reboot 后再验证**，不能只测 `systemctl enable --now`。

---

## BSP 客户

- MP1：读 `docs/shuhe-skill/SKILL.md`，按它的铁律/环境/构建/烧录流程。
- **新板建层 / PHY/LCD 微调 / 烧录包检查（MP1）**：先读
  `docs/shuhe-skill/MULTIBOARD_MAP.md`（`meta-mp135` 多板层，2026-11 起首选）。
- MP2：读 `docs/shuhe-skill-mp2/SKILL.md`，按 257F-DK 的 boot switch、flashlayout、
  external-dt、RIF 等章节。
- 不要用 app skill 改 dts/驱动/分区；也不要让 app 客户看到 BSP 的 Yocto 细节。

---

## BSP 新板 / 烧录包（MP1）

- **新板优先走 `layers/meta-mp135`**：一板一 `conf/machine/<machine>.conf` +
  一板一 `recipes-bsp/board-dts/<machine>/`。不要复制 test 体系、不要改官方
  `meta-st`。
- 生成：`tools/new-board.sh <machine> --from mp13xsip-v10 --desc "..."`；
  构建：`tools/build-board.sh <machine>`。
- 微调先看板级 `README.md` 和 `MULTIBOARD_MAP.md`：PLL4、YT8531 PHY tap、
  LCD/背光、extlinux、eMMC 分区、官方隔离。
- **烧录前必须验包**：TSV 引用的每个文件都要存在且非空；
  `arm-trusted-firmware/`、`fip/`、`optee/`、`kernel/` 空目录 = 构建失败，
  不要烧、不要交付。检查命令见 `MULTIBOARD_MAP.md` §4.2。
- 构建缺文件时，优先复用 test 构建的 `workspace`/`downloads`/`sstate` 重编；
  不要靠改 `.../git/...` 镜像 URL 硬绕。

---

## 铁律

1. 纯 app 不改 kernel/uboot/dts/pinctrl/分区；需要改就转 BSP skill。
2. BSP 客户按 BSP skill 的铁律；不要为了省事跳步骤。
3. 先定位 `<ROOT>`/`<BUILD>`/`<DEPLOY>`，不硬编码路径。
4. 永久修改落 layer/bbappend/patch/devtool；不要只改 `tmp-glibc/work`。
5. 改前记录原值，改后构建 + 上板/实际网络/实际屏幕验证。
6. 烧录/擦除/改 boot switch 前先确认。
7. 如果 `<BUILD>` 含 `-test`，先取得客户逐项批准，或新建独立 app build。
8. 客户不需要学 Yocto；所有构建/部署由 AI 完成。
9. 新板优先 `meta-mp135`；不要复制 test 体系，不要改官方 `meta-st`。
10. 烧录前按 `MULTIBOARD_MAP.md` §4.2 验包；空目录/缺文件不要烧、不要交付。

---

## 示例

### 纯 app（电池）

```text
我是电池工程师，不想学 Yocto。使用 shuhe-ostl-app 帮我开发一个电池管理程序：
读取电压/电流/温度，计算 SOC，异常告警，记录 CSV，开机自启。
```

### 纯 app（电机/传感器/工业）

```text
我是电机工程师。使用 shuhe-ostl-app 帮我开发一个电机监测程序：
读取电流/编码器，PWM 输出，过流保护，记录日志，开机自启。
```

```text
我是传感器工程师。使用 shuhe-ostl-app 帮我开发一个数据采集程序：
每 1 秒采集温度/压力，写 CSV，超限告警，开机自启。
```

```text
我是工业工程师。使用 shuhe-ostl-app 帮我开发一个 Modbus/CAN 采集程序：
读取设备数据，通过 MQTT 上传，断线重连，开机自启。
```

### BSP（MP1）

```text
使用 shuhe-ostl-deploy 帮我改 MP1 的 DTS/驱动，并重编镜像。
```

### BSP（新板建层 / 微调）

```text
使用 shuhe-ostl-deploy，按 meta-mp135 给我建一块新板：
SoC/内存/存储/网口/屏幕/...，先出建层和 DTS 修改计划，我批准后再改。
```

### BSP（烧录包检查）

```text
使用 shuhe-ostl-deploy 检查这个 flash 包能不能烧：
TSV 引用的文件是否齐全、bootfs/rootfs 是否非空、WIC/TSV 分区是否一致；
缺文件先别烧，告诉我怎么重编。
```

### BSP（MP2/257F-DK）

```text
使用 shuhe-ostl-mp2 帮我改 257F-DK 的设备树/烧录配置，并重编 eMMC 镜像。
```

---

## 我的需求

```text
-
```

如果我只发了本文件、没有写需求：先问我是纯 app 还是 BSP，以及板子是 MP1 还是 MP2，
不要自己乱改。
