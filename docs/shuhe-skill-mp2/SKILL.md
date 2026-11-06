---
name: shuhe-ostl-mp2
description: |
  STM32MP257F-DK OpenSTLinux (MACHINE=stm32mp2-m33td, st-image-weston) 的 AI 工作手册。
  触发词: STM32MP257、257F-DK、MP2、一键部署、构建镜像、烧录 eMMC、烧录 SD、
  改设备树、改引脚、pinctrl、新增 UART/I2C/SPI/GPIO、新外设、自定义板、M33、TF-M、
  改主机名、改网络、加软件包、改分区、开机 logo、排错。
  本 skill 是纯知识库；AI 读取后用自身工具执行。所有路径用 <ROOT>/<BUILD>/<DEPLOY>
  相对定位，禁止把本机绝对路径写进客户交付物。
---

# /shuhe-ostl-mp2 — STM32MP257F-DK OpenSTLinux AI 工作手册

## 0. 铁律（每次会话先读，违反任何一条都算事故）

1. **先定位，再动手**：先找到项目根目录 `<ROOT>`、构建目录 `<BUILD>`、部署目录 `<DEPLOY>`。
   找不到就问用户要交付包/源码路径，**禁止猜路径、禁止硬编码 `/home/xxx`**。
2. **官方 257F-DK 优先**：官方板直接用 `HARDWARE_MAP.md` 的内置映射；客户自定义板必须有
   原理图/网表/引脚表，**禁止猜引脚、猜 AF、猜 PHY 地址、猜 I2C 地址**。
3. **永久修改落到 layer**：DTS/驱动/配置的永久修改必须落到自定义 layer、bbappend、patch
   或 `devtool` workspace。**禁止只改 `tmp-glibc/work*`**（会被清理，且不可复现）。
4. **改前记录，改后验证**：改 DTS、分区、启动参数、boot switch 前，先记录原值和回滚方式；
   改完必须重新构建，并给出板端验证命令。不能只说“应该可以”。
5. **不污染官方参考文件**：客户定制新建 machine/DTS/bbappend；不要直接改官方
   `stm32mp257f-dk.dts` 作为交付基线。
6. **破坏性操作先确认**：烧录、`dd`、`mkfs`、删除 `tmp-glibc`/`sstate-cache`、
   改 boot switch、擦 eMMC 前，先向用户确认目标和介质。
7. **构建/烧录前四确认**：板型、启动介质（eMMC/SD/USB）、boot switch、串口/电源。
8. **以项目为准**：SoC 引脚/AF 以 DTS、SoC DTSI、原理图、`bitbake -e` 为准；
   不要凭记忆写寄存器或复用 MP1 的引脚号。

## 1. 项目速查

| 项目 | 值 |
|------|----|
| Board | STM32MP257F-DK Discovery kit |
| SoC | STM32MP257FAK3（2×Cortex-A35 + Cortex-M33，VFBGA424） |
| MACHINE | `stm32mp2-m33td` |
| DISTRO | `openstlinux-weston` |
| Image | `st-image-weston` |
| Boot chain | TF-A → OP-TEE → U-Boot → Linux；M33 跑 TF-M/StarterApp |
| DDR | 4 GB LPDDR4 |
| eMMC | 8 GB（SDMMC2，8-bit，non-removable） |
| SD | microSD（SDMMC1，4-bit） |
| Console | USART2，115200 8N1，ST-LINK VCP |
| Ethernet | 1× RGMII，Realtek PHY（MDIO addr 1，reset PA2） |
| WiFi/BT | Murata/BCM43438 类模块；BT 在 USART1，WiFi 在 SDMMC3 |

### 1.1 目录地图

```text
<ROOT>/
├── .repo/                              # repo 清单（如果使用 repo sync）
├── layers/
│   ├── meta-st/meta-st-stm32mp/        # machine conf、recipes、本手册
│   ├── meta-st/meta-st-openstlinux/    # distro、image、demo
│   ├── openembedded-core/
│   └── meta-openembedded/
├── build-*stm32mp2-m33td*/
│   ├── conf/local.conf
│   └── tmp-glibc/deploy/images/stm32mp2-m33td/
└── ...
```

### 1.2 定位命令

```bash
# 项目根：包含 .repo/ 或 layers/meta-st/meta-st-stm32mp 的目录
pwd
find . -maxdepth 5 -type d -name meta-st-stm32mp 2>/dev/null
find .. -maxdepth 5 -type d -name meta-st-stm32mp 2>/dev/null

# 构建目录
ls -d build-*stm32mp2-m33td* 2>/dev/null

# 部署目录
find . -maxdepth 5 -type d -path '*/tmp-glibc/deploy/images/stm32mp2-m33td' 2>/dev/null
```

如果以上都找不到：**停止**，向用户确认交付包/源码路径；不要自行 clone 未知仓库。

## 2. 新环境部署（AI 按步执行，每步汇报）

### 2.1 主机要求

- Ubuntu 20.04/22.04/24.04，x86_64
- 磁盘 ≥100 GB 可用；内存 ≥8 GB（建议 16 GB）
- `en_US.UTF-8` locale
- 能访问 GitHub / ST 源；GitHub HTTPS 不稳定时用 SSH 或本地 mirror

### 2.2 装依赖

```bash
sudo apt update
sudo apt install -y \
  chrpath diffstat lz4 gawk git-core texinfo gcc-multilib build-essential \
  socat cpio python3 python3-pip python3-git python3-jinja2 xz-utils \
  debianutils iputils-ping libegl1-mesa libsdl1.2-dev zstd liblz4-tool \
  file repo bsdmainutils git-lfs rsync e2fsprogs picocom minicom \
  libncurses-dev bc flex bison mtools dosfstools parted device-tree-compiler
sudo locale-gen en_US.UTF-8
```

### 2.3 取源码

**情况 A：客户已经拿到交付源码目录** —— 直接在 `<ROOT>` 下工作，不要重新 clone。

**情况 B：需要从 manifest 同步**（先向用户确认仓库地址）：

```bash
mkdir -p <ROOT> && cd <ROOT>
repo init -u https://github.com/stm32mp9527/oe-manifest.git -b shuhe-dev -m default.xml
repo sync -j8
```

- HTTPS 拉 GitHub 失败时，优先用 SSH key；不要改 recipe 里的 URL。
- 可在当前构建机配置（只影响本机，不影响交付）：
  ```bash
  git config --global url."git@github.com:".insteadOf "https://github.com/"
  git config --global core.sshCommand "ssh -i <KEY> -o StrictHostKeyChecking=accept-new"
  ```

### 2.4 创建构建目录

```bash
cd <ROOT>
unset BUILDDIR
DISTRO=openstlinux-weston MACHINE=stm32mp2-m33td \
  source layers/meta-st/scripts/envsetup.sh
```

- 如果 `envsetup.sh` 把你带回旧目录，先 `unset BUILDDIR`。
- 日常续跑不要再用 `envsetup.sh`，用：
  ```bash
  cd <BUILD>
  unset BUILDDIR
  source <ROOT>/layers/openembedded-core/oe-init-build-env .
  ```

### 2.5 写 `local.conf`

在 `<BUILD>/conf/local.conf` 末尾加：

```conf
ACCEPT_EULA_stm32mp2-m33td = "1"

# 可选：老版本号检查
ERROR_QA:remove = "version-going-backwards"

# 可选：并行度（按主机内存调整）
# BB_NUMBER_THREADS = "8"
# PARALLEL_MAKE = "-j8"

# 可选：共享下载/缓存目录；客户交付物里不要写死本机路径
# DL_DIR = "<SHARED_DOWNLOADS>"
# SSTATE_DIR = "<SHARED_SSTATE>"
```

**交付纪律**：客户新机器上用 `envsetup.sh` 生成自己的 `local.conf`，不要把本机的
`DL_DIR`/`SSTATE_DIR` 复制给客户。

### 2.6 构建

```bash
cd <BUILD>
unset BUILDDIR
source <ROOT>/layers/openembedded-core/oe-init-build-env .
bitbake st-image-weston
```

- 首次 1–2 小时；可中断续跑；不要轻易删 `tmp-glibc`。
- 只改 DTS/内核时，不必全量重编，见 §4.6。

### 2.7 产物验证（必做）

```bash
DEPLOY=<BUILD>/tmp-glibc/deploy/images/stm32mp2-m33td

ls -l "$DEPLOY"/flashlayout_st-image-weston/optee/FlashLayout_*257f-dk*optee.tsv
ls -l "$DEPLOY"/flashlayout_st-image-weston/deleteall/FlashLayout_*257f-dk*deleteall.tsv
```

检查：

1. **TSV 引用文件都存在**：TSV 里的路径相对 `$DEPLOY`，不是相对 TSV 所在目录。
2. **ext4 干净**：用 e2fsprogs ≥1.47（旧版会报 “please get a newer version”）：
   ```bash
   e2fsck -fn "$DEPLOY"/st-image-weston-*.splitted-bootfs-2026*.ext4
   e2fsck -fn "$DEPLOY"/st-image-weston-*.splitted-vendorfs-2026*.ext4
   e2fsck -fn "$DEPLOY"/st-image-weston-*.splitted-rootfs-2026*.ext4
   e2fsck -fn "$DEPLOY"/st-image-weston-*.splitted-userfs-2026*.ext4
   ```
3. **分区不越界**：TSV 中写入 `mmc0/mmc1` 的项，文件大小必须 ≤ 下一项 offset − 当前 offset。
   `IP=none` 的 `fsbl-boot/fip-ddr/fip-boot` 是 RAM 加载项，不参与分区越界检查。
4. **文件头 sanity**：
   - TF-A / BL2：前 4 字节 `STM2`（`53 54 4d 32`）
   - FIP：`01 00 64 aa 78 56 34 12`（STM32 头 + FIP magic）
   - M33 signed：`3d b8 f3 96`
   - 最稳的验证：与 ST 官方 257F-DK 参考包的 TSV/大小/头对比。

## 3. 烧录与首次启动

### 3.1 Boot switch（UM3385）

`SW1` = BOOT0/BOOT1，`SW2` = BOOT2/BOOT3；板上 `ON=1`、`OFF=0`，以丝印为准。

| BOOT3 | BOOT2 | BOOT1 | BOOT0 | 启动模式 |
|-------|-------|-------|-------|----------|
| 0 | 0 | 0 | 0 | USB DFU（STM32CubeProgrammer 烧录） |
| 0 | 0 | 0 | 1 | SD card（SDMMC1） |
| 0 | 0 | 1 | 0 | eMMC（SDMMC2） |

> 烧录时先把 BOOT0..3 全设 0，进入 USB DFU；烧完改回目标介质，重新上电。

### 3.2 烧录命令

```bash
cd <DEPLOY>

# eMMC
STM32_Programmer_CLI -c port=usb1 \
  -w flashlayout_st-image-weston/optee/FlashLayout_emmc_stm32mp257f-dk-cm33tdcid-ostl-optee.tsv

# SD card
STM32_Programmer_CLI -c port=usb1 \
  -w flashlayout_st-image-weston/optee/FlashLayout_sdcard_stm32mp257f-dk-cm33tdcid-ostl-optee.tsv

# 擦除/恢复（谨慎）
STM32_Programmer_CLI -c port=usb1 \
  -w flashlayout_st-image-weston/deleteall/FlashLayout_board_stm32mp257f-dk-cm33tdcid-ostl-deleteall.tsv
```

- USB 连接用板上的 **USB Type-C DRD**（数据/DFU）口，不是 ST-LINK 调试口。
- 供电用 5V/3A USB Type-C；烧录前确认板子已上电。

### 3.3 串口

```bash
# Linux
picocom -b 115200 /dev/ttyACM0
# 或
minicom -D /dev/ttyACM0 -b 115200
```

- USART2，115200 8N1；Windows 下是 ST-LINK 的 COM 口。
- 上电后应看到 TF-A/OP-TEE/U-Boot/Linux 日志。

### 3.4 首次启动检查

```bash
dmesg | grep -iE 'mmc|eth|realtek|brcm|imx335|adv7535'
ip a
lsblk
systemctl status systemd-networkd
weston --version
```

- 有线网络默认 `80-wired.network`，DHCP；接口名通常是 `end0`。
- 若没有 IP：`systemctl status systemd-networkd`、`networkctl`、`ip a`。
- 若 HDMI/DSI 无显示：检查 `adv7535`、`dmesg | grep -i adv`、`modetest`。

## 4. 设备树与引脚修改（核心）

### 4.1 先判断 DTS 来自哪里（关键）

本 BSP 的 MP2 构建默认 `EXTERNAL_DT_ENABLED=1`：启动用的 DTB 来自 **external-dt**，
不是内核源码里的 `arch/arm64/boot/dts/st/stm32mp257f-dk.dts`。
**只改内核 DTS 会“构建成功但 DTB 不变”。**

先执行：

```bash
cd <BUILD>
unset BUILDDIR
source <ROOT>/layers/openembedded-core/oe-init-build-env .
bitbake -e linux-stm32mp | grep -E 'EXTERNAL_DT_ENABLED|EXTDT|EXTERNAL_DEVICETREE|STAGING_EXTDT'
```

- `EXTERNAL_DT_ENABLED = "1"` → 改 `external-dt` 源（见 §4.2 情况 A）。
- 为 `0` → 改内核 DTS（见 §4.2 情况 B）。

### 4.2 找到实际编译的 DTS

**情况 A：external-dt（本 BSP 默认）**

```bash
find <BUILD>/tmp-glibc/work-shared/stm32mp2-m33td \
  \( -path '*external-dt*' -name 'stm32mp257f-dk*.dts' \
     -o -path '*external-dt*' -name 'stm32mp257f-dk*.dtsi' \) \
  -printf '%p\n' 2>/dev/null
```

典型路径：

```text
<BUILD>/tmp-glibc/work-shared/stm32mp2-m33td/external-dt/stm32mp2/m33-td/linux/stm32mp257f-dk-cm33tdcid-ostl.dts
```

- `external-dt` 会按 eMMC/SD 后缀生成最终 DTB；`&usart6` 等节点在该源里。
- 永久修改：给 `external-dt` 做 bbappend + patch（见 §4.3），不要只改 work-shared。

**情况 B：内核 DTS**

```bash
bitbake -e linux-stm32mp | grep '^S='
```

内核参考文件：

```text
arch/arm64/boot/dts/st/stm32mp257f-dk.dts
arch/arm64/boot/dts/st/stm32mp257f-dk-resmem.dtsi
arch/arm64/boot/dts/st/stm32mp257.dtsi
arch/arm64/boot/dts/st/stm32mp25xf.dtsi
arch/arm64/boot/dts/st/stm32mp25-pinctrl.dtsi
arch/arm64/boot/dts/st/stm32mp25xxak-pinctrl.dtsi
```

- 这些文件是理解 SoC/板级拓扑的参考；本 BSP 的启动 DTB 以 external-dt 为准。

其他层（四层联动时）：

```text
U-Boot:  arch/arm/dts/stm32mp257f-dk*.dts*
TF-A:    fdts/stm32mp257f-dk*.dts*
OP-TEE:  core/arch/arm/dts/stm32mp257f-dk*.dts*
M33:     TF-M external sources / m33tdprojects（StarterApp）
```

### 4.3 永久修改的正确姿势

优先顺序：

1. **external-dt bbappend + patch**（本 BSP 默认；最适合交付/复现）
2. **自定义 layer + linux-stm32mp bbappend + patch**（仅当 `EXTERNAL_DT_ENABLED=0`）
3. `devtool modify external-dt` / `devtool modify linux-stm32mp`（临时验证）
4. 直接改 `tmp-glibc/work*`（只用于临时验证，验证完必须落回 1/2/3）

external-dt 永久 patch 示例：

```text
meta-customer/
├── conf/layer.conf
└── recipes-extended/external-dt/
    ├── external-dt_%.bbappend
    └── files/0001-enable-usart6.patch
```

```bitbake
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
SRC_URI += "file://0001-enable-usart6.patch"
```

Patch 目标：

```text
stm32mp2/m33-td/linux/stm32mp257f-dk-cm33tdcid-ostl.dts
```

（实际路径以 `external-dt` 的 `S` 为准；用 `find` 确认真实目录名。）

`external-dt` 下 `linux/`、`u-boot/`、`tf-a/`、`optee/`、`tfm/` 各有一份同名 DTS。
**只改 `linux/` 只影响 Linux**；如果 U-Boot/OP-TEE 阶段也要用同一引脚，分别改对应
目录并保持 pinctrl/status 一致。

> **临时验证 vs 永久 patch**：
> - 直接改 `work-shared/.../external-dt` 时，**不要**执行
>   `bitbake external-dt -c cleanall`：`do_clean` 会删掉 `STAGING_EXTDT_DIR`，你的改动会丢。
>   临时验证请用 `devtool modify external-dt`，或改完只重编
>   `linux-stm32mp -f -c compile/install/deploy`（不要 cleanall external-dt）。
> - 只有“已经做成 external-dt patch”的永久修改，才用下面的 cleanall 序列。

DTS 修改后重建：

```bash
cd <BUILD>
unset BUILDDIR
source <ROOT>/layers/openembedded-core/oe-init-build-env .
bitbake external-dt -c cleanall
bitbake linux-stm32mp -f -c compile
bitbake linux-stm32mp -f -c install
bitbake linux-stm32mp -f -c deploy
bitbake st-image-weston
```

- 只 `-c compile` 不保证 `do_install/do_deploy` 重跑；加 `-f -c install` / `-f -c deploy`
  才能确保 bootfs 里的 DTB 更新。
- **只更新 rootfs 不会换 DTB**；必须更新 bootfs 或整包烧录。
- 验证 bootfs DTB（不要只 grep `usart6`，会先命中 pinctrl 组/`__symbols__`）：
  ```bash
  debugfs -R 'dump /stm32mp257f-dk-cm33tdcid-ostl-emmc.dtb /tmp/dtb' <bootfs>.ext4
  dtc -I dtb -O dts /tmp/dtb | grep -A20 'serial@40220000'
  # 或
  fdtget /tmp/dtb /soc@0/bus@42080000/serial@40220000 status
  ```

### 4.4 257F-DK 节点地图

| 功能 | 节点 | 说明 |
|------|------|------|
| 串口控制台 | `&usart2` | `serial0`，ST-LINK VCP，115200 |
| 蓝牙 | `&usart1` | BCM43438，`uart-has-rtscts`，已 okay |
| 备用 UART | `&usart6` | 默认 disabled；CN5 pin 8/10/11/36；**先查 RIF/M33 归属** |
| 以太网 | `&eth1` | RGMII，PHY `ethernet-phy@1`，reset `&gpioa 2` |
| SD 卡 | `&sdmmc1` | 4-bit，CD `&gpiod 3` |
| eMMC | `&sdmmc2` | 8-bit，non-removable |
| WiFi | `&sdmmc3` + `brcmf` | `brcmf` 默认 disabled，见 §5.3 |
| HDMI | `&i2c2` → `adv7535@3d` | DSI-to-HDMI，reset `&gpiob 6` |
| Camera | `&i2c2` → `imx335@1a` | CSI 2-lane，reset `&gpiob 1` |
| Touch | `&i2c2` → `ili2511@41` | |
| 备用 I2C | `&i2c8` | 默认 disabled；CN5 pin 3/5（PZ3/PZ4）；**本配置 RIF 属 CID2/M33** |
| 备用 SPI | `&spi6` | external-dt 板级文件**无 `&spi6` 覆盖**（SoC 控制器存在，缺 pinctrl/CS）；CN5 pin 19/21/23/24 |
| 显示 | `&ltdc`、`&dsi`、`&lvds` | LVDS panel + HDMI |
| 按键 | `gpio-keys` | PC5 User-1；PC11 User-2 disabled |
| LED | `gpio-leds` | PH7 蓝色 heartbeat |
| USB Host | `&usbh` / `usbh_ehci` | 板上 HUB，2× Type-A |
| USB DRD | `&usb3dr` / `dwc3` | Type-C DRD，角色切换 |

### 4.5 Pinmux 规则

- 引脚组（USART6/I2C8/SPI6 等）在 `stm32mp25-pinctrl.dtsi`；先在现有 DTS 里搜组名。
  `stm32mp25xxak-pinctrl.dtsi` 主要定义 AK 封装的 GPIO 范围/控制器，不要在里面找外设组。
- 宏格式：`STM32_PINMUX(<PORT>, <PIN>, <AF>)`。
- **AF 编号必须查 SoC DTSI/数据手册/原理图**，不能凭经验。
- 检查冲突：同一个 `&gpioX N` 是否已被其他节点/引脚组占用；板上用 `gpioinfo` 复核。
- 常用属性：`bias-disable`、`bias-pull-up`、`drive-push-pull`、`slew-rate`、
  `output-high`/`output-low`。

### 4.6 可直接套用的最小改动

#### A. 启用备用 USART6

DK 的 `&usart6` 已经有 pinctrl，只是 `status = "disabled"`：

```dts
&usart6 {
    status = "okay";
};
```

> 本 BSP 先确认 `EXTERNAL_DT_ENABLED=1`：改 `external-dt` 的
> `stm32mp257f-dk-cm33tdcid-ostl.dts`，不是内核参考 DTS。

**先查 RIF/M33 归属（257F-DK m33-td 特有，容易踩坑）**：

- **本配置把 USART6 分给 CID2/M33**（不是“可能”）。TF-M RIF 文件：
  ```text
  <BUILD>/tmp-glibc/work-shared/stm32mp2-m33td/external-dt/stm32mp2/m33-td/tfm/stm32mp257f-dk-cm33tdcid-ostl-s-rif.dtsi
  ```
  其中：
  ```c
  RIFPROT(STM32MP25_RIFSC_USART6_ID, RIF_UNUSED, RIF_UNLOCK, RIF_NSEC,
          RIF_NPRIV, RIF_CID2, RIF_SEM_DIS, RIF_CFEN)
  ```
  A35 访问时可能触发 `SERC exception ID: 36`。先查：
  ```bash
  cat /sys/kernel/debug/stm32_firewall/rifsc | grep -i USART6
  # SCID=1 -> A35；SCID=2 -> M33
  ```
- 若客户确认要给 A35：
  1. 把该行 `RIF_CID2` 改成 `RIF_CID1`；
  2. **重编 TF-M/M33 固件**并烧到 `m33fw-a` 分区（或整包烧录），不是只重编 Linux；
     可先 `bitbake tf-m-stm32mp -c cleanall`，必要时再
     `bitbake m33tdprojects-starter-stm32mp2 -c cleanall`，然后
     `bitbake m33fw-stm32mp2`（生成 `m33fw-a` 的 signed bin），最后
     `bitbake st-image-weston`；
  3. 再启用 Linux `&usart6 { status = "okay"; }`；
  4. 永久化同样走 `external-dt` bbappend/patch，不要只改 work-shared。
- 这会停掉/影响 M33 的 UCSI/USB-C 角色，**必须客户确认**。
  本 m33td 镜像里没有 `st-m33firmware-load.service`；M33 固件在 `m33fw-a`，
  `&m33_rproc` 带 `st,auto-boot`。不要用 `systemctl disable st-m33firmware-load`
  来“停 M33”；先 `systemctl list-unit-files | grep -i m33` 确认。
- 如果客户只接 TX/RX：删除 `uart-has-rtscts`；必要时删除 `dmas`/`dma-names`，
  否则流控/DMA 可能导致收不到数据。
- CN5 引脚：TX pin 8（PF13）、RX pin 10（PF14）、RTS pin 11（PG5）、
  CTS pin 36（PF15）、GND pin 9/39；3.3 V TTL，不是 RS-232，TX/RX 要交叉并共地。

如需新引脚组（自定义板）：

```dts
&pinctrl {
    my_uart6_pins: my-uart6-pins {
        pins1 {
            pinmux = <STM32_PINMUX('X', Y, AFn)>; /* 查原理图/SoC DTSI 后填写 */
            bias-disable;
            drive-push-pull;
        };
    };
};

&usart6 {
    pinctrl-names = "default";
    pinctrl-0 = <&my_uart6_pins>;
    status = "okay";
};
```

验证：`ls /dev/ttySTM*`、`stty -F /dev/ttySTMx 115200`、回环/示波器。

#### B. 启用备用 I2C8

```dts
&i2c8 {
    status = "okay";
};
```

> **RIF 风险**：本配置把 I2C8 分给 **CID2/M33**（RIF 文件 line 57 的 `RIF_CID2`）。
> A35 访问可能被防火墙拦截。先查：
> `cat /sys/kernel/debug/stm32_firewall/rifsc | grep -i I2C8`。
> 要给 A35，需把 `RIF_CID2` 改成 `RIF_CID1`，重编 TF-M/M33，再
> `bitbake m33fw-stm32mp2` 生成 signed bin 并烧 `m33fw-a`；且需客户确认；
> 不要只改 DTS。CN5：SDA pin 3（PZ3）、SCL pin 5（PZ4）。

挂新器件：

```dts
&i2c8 {
    status = "okay";

    my_sensor: sensor@48 {
        compatible = "<厂商,型号>";
        reg = <0x48>;
        /* vdd-supply / interrupts / reset-gpios 按原理图补 */
    };
};
```

验证：`ls /dev/i2c-*`、`i2cdetect -y <bus>`。

#### C. 启用备用 SPI6（external-dt 板级文件没有 `&spi6` 覆盖）

**只写 `&spi6 { status = "okay"; }` 不会工作**：SoC 的 `spi6` 控制器节点存在，
但本 BSP 的 external-dt 板级文件没有 `&spi6` 覆盖，缺 pinctrl 和 CS；
`spi6_pins_a` 只含 SCK/MOSI/MISO，**不含 PF4 NSS**。需要补完整节点：

```dts
&spi6 {
    pinctrl-names = "default", "sleep";
    pinctrl-0 = <&spi6_pins_a>;
    pinctrl-1 = <&spi6_sleep_pins_a>;
    cs-gpios = <&gpiof 4 GPIO_ACTIVE_LOW>; /* PF4；确认是软件 CS 还是硬件 NSS */
    status = "okay";
};
```

- `spi6_pins_a`：SCK = PF7、MOSI = PC7、MISO = PC4（CN5 pin 23/19/21）。
- PF4（CN5 pin 24）是 NSS：用 `cs-gpios` 时由 GPIO 控制；若硬件使用 NSS 复用，
  不要用 `cs-gpios`，把 PF4 加到 pinctrl（AF 编号查 SoC DTSI/原理图）。
- SPI6 的 RIF 本配置是 **CID1/A35**，一般没有 USART6/I2C8 的 RIF 问题；
  但 M33 UCSI 固件可能碰 SPI6，需实测确认。
- 如果只是用 `spidev` 做测试，补一个 child node（正式产品换成客户器件）：
  ```dts
  &spi6 {
      spidev@0 {
          compatible = "rohm,dh2228fv";
          reg = <0>;
          spi-max-frequency = <1000000>;
      };
  };
  ```
- 验证：`ls /dev/spidev*`、`spidev_test -D /dev/spidevX.Y`。没有 child node 时
  `/dev/spidev*` 不会出现，别误判 SPI 控制器没工作。

#### D. 增加一个 GPIO 输出/LED

```dts
/ {
    gpio-leds {
        led-user2 {
            gpios = <&gpioX N GPIO_ACTIVE_HIGH>; /* 查原理图后填写 */
            linux,default-trigger = "default-on";
        };
    };
};
```

验证：`gpioinfo`、`gpioset <chip> <line>=1`、`ls /sys/class/leds`。

#### E. 增加一个固定 regulator/使能脚

```dts
/ {
    reg_3v3_sw: regulator-3v3-sw {
        compatible = "regulator-fixed";
        regulator-name = "3v3-sw";
        regulator-min-microvolt = <3300000>;
        regulator-max-microvolt = <3300000>;
        gpio = <&gpioX N GPIO_ACTIVE_HIGH>;
        enable-active-high;
    };
};
```

#### F. 改以太网 PHY 地址/复位

```dts
&eth1 {
    mdio1 {
        phy1_eth1: ethernet-phy@1 {
            reg = <1>; /* 必须与 PHY strap 一致 */
            reset-gpios = <&gpioa 2 GPIO_ACTIVE_LOW>;
        };
    };
};
```

改前先查原理图 PHY Address strap；`reg` 与 strap 不一致会直接不通。

### 4.7 板端验证命令

```bash
dmesg | grep -iE 'pinctrl|gpio|i2c|spi|uart|eth|phy'
ls /dev/ttySTM* /dev/i2c-* /dev/spidev* 2>/dev/null
gpioinfo
i2cdetect -y <bus>
ip a
v4l2-ctl --list-devices
modetest -M stm
```

## 5. 常见本地修改导航

> 原则：能改 `local.conf`/bbappend 就不改源码；能改 DTS 就不改 C 驱动；
> 任何改动都要能重建、能回滚、能验证。

### 5.1 改主机名（注意 st-hostname 会追加 MAC）

镜像装了 `st-hostname`，它每次启动会把 `end0` 的 MAC 后缀追加到 `/etc/hostname`。
只写 `/etc/hostname` 的话，开机后可能变成 `customer-board<mac后缀>`。

**固定主机名（推荐）**：

1. 在 `local.conf` / distro conf 设置（`base-files` 会同时写 `/etc/hostname`
   和 `/etc/hosts` 的 `127.0.1.1`）：
   ```conf
   hostname:pn-base-files = "customer-board"
   ```
2. 禁用/屏蔽 `st-hostname.service`：
   - 自定义 layer 的 `st-hostname.bbappend`：
     ```bitbake
     SYSTEMD_AUTO_ENABLE:${PN} = "disable"
     ```
   - 或 `ROOTFS_POSTPROCESS_COMMAND`：
     ```conf
     ROOTFS_POSTPROCESS_COMMAND += "mask_st_hostname;"
     mask_st_hostname() {
         ln -sf /dev/null ${IMAGE_ROOTFS}/etc/systemd/system/st-hostname.service
     }
     ```
3. 重建并重启两次验证：
   ```bash
   hostname
   cat /etc/hostname
   grep 127.0.1.1 /etc/hosts
   systemctl is-enabled st-hostname.service
   ```

- 如果客户想保留“前缀+MAC”的唯一主机名，**不要禁用** `st-hostname`，只设置前缀，
  并确认 DHCP 上报主机名的需求。
- 永久化放自定义 layer/bbappend，不要只改 build 的 `local.conf`（交付不带走）。
- 目标机临时 `hostnamectl set-hostname customer-board` 只对当前启动有效；
  `st-hostname` 未禁用时，下次启动仍会追加 MAC。

### 5.2 有线静态 IP

镜像默认 `systemd-networkd`，`/usr/lib/systemd/network/80-wired.network` 是 DHCP。

**目标机临时验证**：

```bash
sudo cp /usr/lib/systemd/network/80-wired.network /usr/lib/systemd/network/80-wired.network.notused
sudo cp /usr/lib/systemd/network/52-static.network.static /etc/systemd/network/52-static.network
sudo vi /etc/systemd/network/52-static.network   # 改成客户网段
sudo systemctl restart systemd-networkd
```

**镜像内永久修改**：在自定义 layer 里做一个 `systemd-networkd-configuration`
bbappend，安装 `52-static.network` 并屏蔽 `80-wired.network`；或使用
`ROOTFS_POSTPROCESS_COMMAND` 写文件。不要直接改 `tmp-glibc` 里的 rootfs。

### 5.3 WiFi

- DK 的 `&sdmmc3` 是 WiFi SDIO，`brcmf` 节点默认 **disabled**；BT 在 `&usart1`。
- 镜像已含 `linux-firmware-addons-bcm43xx`、`brcmfmac`、`wpa-supplicant`、`hostapd`。
- 先确认硬件：`dmesg | grep -i brcm`、`ip link` 是否出现 `wlan0`。
- 若 `brcmf` disabled：在 DTS 里把 `brcmf` 的 `status` 改为 `"okay"`，重建内核；
  若仍无 `wlan0`，检查 SDIO 引脚、`wifi_pwrseq`、`gpiog 8` 复位、firmware 路径。
- 连接 AP：

```bash
mkdir -p /etc/wpa_supplicant
wpa_passphrase "<SSID>" "<PASSWORD>" > /etc/wpa_supplicant/wpa_supplicant-wlan0.conf
cp /usr/lib/systemd/network/51-wireless.network.sample /etc/systemd/network/51-wireless.network
systemctl enable --now wpa_supplicant@wlan0
systemctl restart systemd-networkd
```

> 不要假设 WiFi 一定可用：257F-DK 的模块/天线配置要以实物和原理图为准。

### 5.4 加软件包

在 `local.conf` 或自定义 image bbappend：

```conf
IMAGE_INSTALL:append = " htop"
```

```bash
bitbake st-image-weston
```

目标机验证：`dpkg -l | grep htop`。镜像使用 DEB 包管理（`dpkg`/`apt`）。
不要用 `opkg`。

### 5.5 改 rootfs 大小/分区

- 默认 rootfs 镜像约 750 MB；eMMC rootfs 分区 3 GB，SD rootfs 分区 4 GB。
- 只加少量软件：优先 `IMAGE_ROOTFS_EXTRA_SPACE`（KB）留余量。
- 需要更大 rootfs：

```conf
IMAGE_ROOTFS_SIZE = "1048576"        # KB = 1 GB
IMAGE_ROOTFS_EXTRA_SPACE = "65536"   # 64 MB
```

- **安全上限**：eMMC rootfs 必须 < 3 GB，SD rootfs 必须 < 4 GB；超过会踩到
  `userfs` offset，烧录时分区越界。
- 要改分区表本身：改 `conf/machine/include/st-machine-image-partition-stm32mp.inc`
  或 machine conf 里的分区变量，重新生成 flashlayout，并重新核对 TSV offset。
- 首次启动 `st-image-resize-initrd` 会把 rootfs 扩到分区末尾。

### 5.6 改 bootargs / 启动参数

- 启动参数在 bootfs 的 `mmc0_extlinux/extlinux.conf`（SD）和
  `mmc1_extlinux/extlinux.conf`（eMMC），由 U-Boot/`UBOOT_EXTLINUX_*` 生成。
- **不要直接改 bootfs 镜像里的 extlinux.conf** 作为永久方案；改 U-Boot 的
  `UBOOT_EXTLINUX_*` 变量或 bbappend，重新构建。
- 只做临时验证：挂载 bootfs 分区改 `extlinux.conf`，重启验证，再落回 recipe。

### 5.7 开机 logo/splash

- 257F-DK 使用 `psplash-via-m33`：开机 logo 由 **M33 StarterApp** 显示，
  不是 Linux 的 `/usr/share/psplash`。
- 相关 recipe/目录：`st-image-weston.bb`（`M33TD_SPLASHSCREEN`）、
  `m33tdprojects-starter-stm32mp2.bb`、`m33-projects/`。
- 换 logo 需要改 M33 工程资源并重新构建 M33 固件，属于进阶修改；先向客户要
  logo 文件、分辨率、颜色格式，并在板上验证。不要承诺“改一个 png 就行”。

### 5.8 SSH / 用户

- 镜像默认装 `dropbear`，也有 `openssh-sftp`。
- 目标机临时加 key：
  ```bash
  mkdir -p /home/root/.ssh
  vi /home/root/.ssh/authorized_keys
  chmod 700 /home/root/.ssh
  chmod 600 /home/root/.ssh/authorized_keys
  ```
- 镜像内永久加 key：自定义 layer 放 `authorized_keys`，用
  `ROOTFS_POSTPROCESS_COMMAND` 安装到 `${IMAGE_ROOTFS}/home/root/.ssh/`。
- 改 root 密码/登录策略前先看 `/etc/default/dropbear` 和 `/etc/shadow`，
  不要直接把 root 登录打开给公网。

### 5.9 时区/locale

- 镜像已含 `tzdata`。目标机：`timedatectl set-timezone Asia/Shanghai`。
- 镜像内永久设置：`local.conf` 里加 `DEFAULT_TIMEZONE = "Asia/Shanghai"`，
  或 bbappend 写 `/etc/localtime`。
- locale 默认 `en-us`；需要中文界面时再评估 `IMAGE_LINGUAS` 和字体包，
  不要默认加一堆语言包。

## 6. 自定义板（客户新板）

### 6.1 先收集信息（缺一项就不要猜）

- SoC 型号/封装（AK？）、DDR 容量/类型、启动介质（eMMC/SD/NOR）
- 以太网：PHY 型号、MDIO 地址、RGMII/RMII、复位脚、时钟
- WiFi/BT：模块型号、SDIO/UART 连接、复位/使能脚
- 显示：LVDS/DSI/HDMI、桥片型号、I2C 地址、复位/中断脚
- Camera：型号、I2C 地址、CSI lane 数、MCLK/reset/pwdn
- USB：Host/DRD、HUB、Type-C CC 控制器
- 音频：I2S/SAI、DMIC、codec
- 电源：PMIC/DDR 电源、使能脚、复位脚
- 所有新增外设的引脚表（端口/引脚/AF/上下拉/驱动能力）

### 6.2 文件生成

- 新建 machine conf（不要改官方 `stm32mp2-m33td.conf`），参考它设置
  `EXTERNAL_DEVICETREE_*`、`BOOTDEVICE_LABELS`、`M33FW_BUILDCONF`。
- 新建 DTS：**本 BSP 默认 external-dt**，以 `external-dt` 的
  `stm32mp257f-dk-cm33tdcid-ostl.dts` 为模板，通过 `external-dt` bbappend/patch
  交付；内核 `arch/arm64/boot/dts/st/stm32mp257f-dk.dts` 仅作参考。
- 仅当客户明确要求内核 DTS 方案（`EXTERNAL_DT_ENABLED=0`）时，才把新 DTS 放到
  自定义 layer 的 `arch/arm64/boot/dts/st/`。
- U-Boot/TF-A/OP-TEE 如需改 DDR/启动/安全配置，各自复制一份 DTS 并改名；
  四层保持一致。
- M33 的 TF-M/StarterApp 如需改，单独建 M33 工程配置；不要和 A35 DTS 混在一起。

### 6.3 验证清单

- [ ] 串口有 TF-A/OP-TEE/U-Boot/Linux 日志
- [ ] DDR 容量正确（`/proc/meminfo`）
- [ ] eMMC/SD 识别（`lsblk`）
- [ ] 以太网 link + DHCP/静态 IP + ping
- [ ] USB Host/DRD
- [ ] 显示/HDMI
- [ ] Camera（如适用）
- [ ] WiFi/BT（如适用）
- [ ] GPIO/按键/LED
- [ ] 重启/断电重启 3 次稳定

## 7. 故障排查

### 7.1 构建

| 症状 | 原因 | 处理 |
|------|------|------|
| `TMPDIR has changed location` | `BUILDDIR` 指向旧路径 | `unset BUILDDIR`，从正确 `<ROOT>` source |
| `Do not use Bitbake as root` | 用 root 跑 | 用普通用户 |
| `Committer identity unknown` | git 无 user.name/email | `git config --global user.name/user.email` |
| fetch GitHub 超时 | 网络/443 | 用 SSH、重试、本地 mirror；不要改 SRCREV |
| `gcc-cross` undefined reference | 中断导致 work 目录损坏 | `bitbake gcc-cross-aarch64 -c cleanall` 后重编 |
| 磁盘满 | `tmp-glibc`/sstate 太大 | 清旧 tmp/sstate；保留 deploy 镜像 |
| `version-going-backwards` | 版本回退检查 | `ERROR_QA:remove = "version-going-backwards"` |

### 7.2 启动/烧录

| 症状 | 原因 | 处理 |
|------|------|------|
| CubeProgrammer 找不到 USB | boot switch 不是全 0 / 插错口 | BOOT0..3=0，用 USB Type-C DRD 口 |
| 串口无输出 | 波特率/口错/没上电 | 115200 8N1，ST-LINK VCP，检查电源 |
| 卡在 U-Boot | bootfs 空/DTB 不对 | 检查 bootfs 里 `Image.gz`、DTB、`extlinux.conf` |
| `root=PARTUUID` 找不到 | 烧录介质/分区不对 | 重烧对应 eMMC/SD TSV |
| M33 不启动 | TF-M/StarterApp 固件缺失 | 检查 `m33-firmware/`、BL2、`remoteproc` 日志 |
| 烧录后反复重启 | boot switch 未切回 / 镜像不完整 | 切回 eMMC/SD，重烧并校验 TSV 引用 |

### 7.3 外设

| 症状 | 优先检查 |
|------|----------|
| 网口不通 | PHY reset、MDIO `reg` 与 strap、RGMII delay、时钟 |
| I2C 无设备 | pinctrl AF、`status`、上拉、供电、地址 |
| SPI 无设备 | pinctrl、CS、`spidev`、时钟 |
| UART 无数据 | TX/RX 交叉、AF、波特率、流控 |
| GPIO `EBUSY` | 同一引脚被其他节点占用；全 DTS 搜索 |
| HDMI 无显示 | ADV7535 I2C、DSI lane、regulator、`modetest` |
| Camera 无设备 | IMX335 I2C/CSI lane/MCLK/reset、`v4l2-ctl` |
| WiFi 无 wlan0 | `brcmf` 状态、SDIO、pwrseq、firmware |

## 8. 交付与路径纪律

1. **不要把本机 build 目录交付给客户**：`<BUILD>/conf/local.conf` 里的
   `DL_DIR`/`SSTATE_DIR` 是本机路径。客户用 `envsetup.sh` 生成自己的。
2. **烧录包可以交付**：`<DEPLOY>` 或整理成独立目录：
   - TSV 放根目录
   - `arm-trusted-firmware/`、`arm-trusted-firmware-m/`、`fip/`、`m33-firmware/`
     和 `*.splitted-*.ext4` 按 TSV 相对路径放置
   - 附 eMMC/SD 烧录命令和 boot switch 表
3. **skill/txt 不含绝对路径**：用 `<ROOT>`/`<BUILD>`/`<DEPLOY>` 或“项目根目录”。
4. **改动留痕**：每次改 DTS/配置/分区，在项目 `ROLLBACK.md` 记录
   “文件 + 原值 + 还原命令”。
5. **客户板不上传官方参考文件**：客户定制放在独立 layer/machine，避免污染
   官方 257F-DK 基线。

## 9. 知识文件索引

- `HARDWARE_MAP.md` —— 257F-DK 板级参数、boot switch、连接器、DTS 节点映射
- `CUSTOM_MAP.md` —— 常用本地修改/外设启用/自定义板导航
- `README.md` —— 文件清单、stub 安装、维护纪律
- `ROLLBACK.md` —— 改动记录模板
