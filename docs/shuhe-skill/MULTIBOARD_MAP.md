# MULTIBOARD_MAP — meta-mp135 新板接入 / 微调 / 烧录检查

> 适用：2026-11 起的自研 STM32MP13 板。官方 `meta-st` 板不走本流程。
> 旧的四层复制流程（`SKILL.md` §5）仅保留给必须沿用 test 体系的历史板；
> **新板优先走 `layers/meta-mp135`**。
>
> 唯一真相源：
> - 层：`/home/lanyang/work/shuhe/layers/meta-mp135`
> - 板级细节：`recipes-bsp/board-dts/<machine>/README.md`
> - 本文件：`meta-st-stm32mp/docs/shuhe-skill/MULTIBOARD_MAP.md`

---

## 0. 原则（先看这 6 条）

1. **官方隔离**：`meta-st` 的官方 machine/DTS 不修改；本层所有 bbappend 用
   `MP135_DTS` 守卫，官方 machine 构建时自动 no-op。
2. **一板一目录**：每块自研板 = `conf/machine/<machine>.conf` +
   `recipes-bsp/board-dts/<machine>/`。新板只加文件，不改已有板。
3. **先备份再改**：改 machine conf/DTS/构建 conf 前先备份，并在
   `ROLLBACK.md` 写“原状 + 还原命令”。
4. **DTS 优先**：板级差异优先只改 dts/dtsi/pinctrl/config fragment。
   改 C 代码（内核/驱动/U-Boot/OP-TEE）必须先说明理由并获批准。
5. **脚本边界**：官方 BSP 流程除 `envsetup.sh` 外不塞临时脚本；
   `meta-mp135/tools/*.sh` 和已评审 recipe 内的 `phy-tune.sh`/`time-sync.sh`
   是层资产，允许使用和维护。禁止往产品仓库/官方层加一次性脚本。
6. **烧录前必须验包**：TSV 引用的每个文件都要存在且非空；空目录 =
   构建/部署失败，不要交给客户。详见 §4。

---

## 1. 层结构

```text
meta-mp135/
├── conf/
│   ├── layer.conf
│   └── machine/
│       ├── mp13xsip-v10.conf          # 已迁移、可构建
│       ├── gk-mp135-v01.conf          # 骨架
│       └── ncuc-mp135-v10.conf        # 骨架
├── recipes-bsp/
│   ├── board-dts/<machine>/           # 每板一套 DTS/defconfig
│   │   ├── kernel.dts
│   │   ├── stm32mp13*-test.dtsi
│   │   ├── stm32mp13-test-pinctrl.dtsi
│   │   ├── tf-a.dts
│   │   ├── tf-a-fw-config.dts
│   │   ├── ddr.dtsi
│   │   ├── optee.dts
│   │   ├── u-boot.dts
│   │   ├── u-boot.dtsi
│   │   ├── kernel.cfg / uboot.cfg
│   │   └── README.md
│   ├── fip-stm32mp/
│   ├── trusted-firmware-a/
│   └── u-boot/
├── recipes-kernel/linux/
├── recipes-security/optee/
├── recipes-core/
│   ├── phy-tune/                     # YT8531 PHY tap + AFE PLL
│   └── network-config/               # end0/end1 静态 IP
├── recipes-apps/
│   └── mp13-dashboard/               # LCD 仪表盘 + 开机对时
└── tools/
    ├── new-board.sh
    ├── build-board.sh
    └── rollback.sh
```

### 1.1 machine conf 关键字段

```bitbake
require conf/machine/stm32mp13-disco.conf
require conf/machine/include/mp135-board.inc

MP135_DTS = "stm32mp135f-<machine>"
MP135_BOOTDEVICE = "emmc"          # 或 sdcard
MP135_DTS_DIR = "<machine>"

# OP-TEE：按板内存/PMIC 设置
MP135_OPTEE_DRAM_SIZE = "0x10000000"   # 256MB
MP135_OPTEE_STPMIC1 = "n"              # 无 PMIC

# 自研板沿用旧 test U-Boot（bootfs/bootcmd 与分区布局匹配）
UBOOT_CONFIG[default_stm32mp13] := "stm32mp13_test_defconfig,,u-boot.dtb"

# 板级运行时组件（按板安装；mp13xsip-v10 的已验证组合）
IMAGE_INSTALL:append = " phy-tune network-config mp13-dashboard"
```

- `phy-tune`/`network-config`/`mp13-dashboard` 是已评审 recipe：
  YT8531 用 `phy-tune`；LCD UI/开机对时用 `mp13-dashboard`；静态 IP 用
  `network-config`。不用的板子不要硬装（尤其 `phy-tune` 只适配本板 YT8531）。

- `MP135_ACTIVE` / `MP135_DTS` 是官方隔离开关：官方 machine 下必须为
  `0` / 空；构建前用 `bitbake -e <machine> | grep MP135_` 复核。
- 内存、eMMC/SD、PMIC 的任何变化都要四层联动（内核 / TF-A / OP-TEE /
  U-Boot），不能只改一处。

---

## 2. 新板 10 步

### 2.1 生成骨架

```bash
cd /home/lanyang/work/shuhe/layers/meta-mp135
tools/new-board.sh <machine> --from mp13xsip-v10 --desc "<板子说明>"
```

它会复制 `mp13xsip-v10` 的 machine conf + board-dts，生成新 machine 骨架。
生成后只改新目录，不动源板。

### 2.2 改 machine conf

- `MP135_DTS` / `MP135_DTS_DIR` 改成新板名。
- `MP135_BOOTDEVICE`：eMMC 或 SD。
- `MP135_OPTEE_DRAM_SIZE` / `MP135_OPTEE_STPMIC1`：按内存/PMIC。
- U-Boot defconfig、串口、分区/镜像类型（如需要）。
- 参考：`conf/machine/mp13xsip-v10.conf`。

### 2.3 内核 DTS（`kernel.dts`）

至少核对：

| 项 | 要改什么 |
|---|---|
| model/compatible | 新板名称、SoC 型号 |
| memory | DDR 大小（与 TF-A/OP-TEE 一致） |
| 串口 | `ttySTM0` 的 UART 引脚/时钟 |
| eMMC/SD | `sdmmcX`、bus-width、vmmc-supply、boot 分区 |
| 以太网 | PHY 地址/复位/中断/RGMII pinctrl/时钟 |
| LCD | panel timing、PWM、PW_EN、电源 |
| USB/HDMI/摄像头 | 按原理图增删节点 |
| pinctrl | 每脚 AF 号必须对照内核 `pinctrl-stm32mp135.c`，不能猜 |

### 2.4 TF-A（`tf-a.dts` / `tf-a-fw-config.dts` / `ddr.dtsi`）

- PLL4 VCO/`divmn`/`div_pqr`：ETH 需要 PLL4P=125MHz；LCD 需要
  PLL4Q=33.333MHz（Q=29）。TF-A 与 OP-TEE 必须一致。
- DDR 参数：按 2Gb/4Gb、533MHz 等改；`DDR_MEM_SIZE` 与内核 memory 一致。
- 串口/启动介质/看门狗。
- 不要混用 256M 金标的 secure framebuffer 与旧 U-Boot：
  `optee@ce000000` 32MB + framebuffer `cde00000` 的组合要整套一致，
  否则 TZC panic。

### 2.5 OP-TEE（`optee.dts`）

- PLL4 `pqr` 与 TF-A 一致。
- `CFG_DRAM_SIZE` flavor 与机器内存一致（`MP135_OPTEE_DRAM_SIZE`）。
- ETH/ETZPC 放行（按需）。
- 不要改官方 OP-TEE DTS；只改本层 board-dts 的 `optee.dts`。

### 2.6 U-Boot（`u-boot.dts` / `u-boot.dtsi` / defconfig）

- 新板 DTS 必须在 U-Boot `arch/arm/dts/Makefile` 注册（如走源码方式）。
- defconfig 的 `CONFIG_DEFAULT_DEVICE_TREE` 指向新 DTS。
- 本板沿用 `stm32mp13_test_defconfig` 时，确认 bootfs/bootcmd/分区匹配。
- eMMC 启动：`STM32MP_EMMC_BOOT=1` 等配置与 TF-A 一致。

### 2.7 pinctrl（`stm32mp13-test-pinctrl.dtsi`）

- 每个 `STM32_PINMUX('X', n, AFx)` 的 AF 号要能在内核
  `drivers/pinctrl/stm32/pinctrl-stm32mp135.c` 找到。
- 典型坑：PD3 的 TIM2_CH1 是 **AF3**，不是 AF1/AF2；写错会报
  `invalid function 2 on pin 51`，PWM probe 失败。
- 复位/中断/时钟脚不要与 USB_EN、按键等复用；先查原理图网表。

### 2.8 构建

```bash
cd /home/lanyang/work/shuhe/layers/meta-mp135
tools/build-board.sh <machine>
```

- 首次构建可复用 test 构建目录的 `downloads`/`sstate`（见 §4.3）。
- 构建后先验包（§4），再烧录。

### 2.9 烧录

- eMMC：STM32CubeProgrammer + `FlashLayout_emmc_*.tsv`，板子进 USB DFU。
- SD：`FlashLayout_sdcard_*.tsv` 或官方 SD 镜像流程。
- 烧录前确认 TSV 引用的文件全部存在且非空。
- 烧录后先看串口，不要只看屏幕。

### 2.10 板上验证门

| 门 | 命令/现象 | 通过标准 |
|---|---|---|
| 启动 | 串口 U-Boot + kernel log | 无 TZC/DRAM/OP-TEE panic |
| 登录 | `root@<machine>:~#` | 能进 shell |
| 网口 | `ip -br addr`、`cat /sys/class/net/end0/carrier` | end0 `UP`/`LOWER_UP`，1000Mb/s |
| PHY | `phytool ... 0xA003` | `0x20F8` |
| 收包 | `rx_packets`、`/proc/net/arp` | 持续增长，ARP `Flags=0x2` |
| ping | 双向 100 包 | 0% 丢包 |
| LCD | `/sys/class/drm/card0-DPI-1/status` | `connected`/`enabled`，800×480 |
| 背光 | `/sys/class/backlight/panel-backlight/brightness` | 默认 3/10，可调 |
| 官方隔离 | `bitbake -e stm32mp13-disco \| grep MP135_` | `MP135_ACTIVE=0`，`MP135_DTS` 空 |

---

## 3. 微调检查表（按症状定位）

### 3.1 时钟 / PLL4

| 项 | 值/位置 | 验证 |
|---|---|---|
| ETH PLL4P | 125MHz；TF-A/OP-TEE `pll4` 双处一致 | `clk_summary \| grep pll4_p` |
| LCD PLL4Q | 33.333MHz（Q=29）；VCO 1000MHz | `clk_summary \| grep ltdc_px` |
| PLL4 VCO | 1000MHz；`divmn=<2 124>`（按板复核） | `clk_summary` |
| TF-A/OP-TEE | `div_pqr` 必须一致；不要一边改一边不改 | 对比两份 `optee.dts`/`tf-a.dts` |

**不要**为了“先过启动”把 Q 改成 59 就交付；LCD 像素时钟会只有
16.67MHz，表现为画面抖动/闪烁。最终必须回到 33.333MHz。

### 3.2 千兆 YT8531（重点）

硬件：2× Motorcomm YT8531C，RGMII。

DTS 基线：

```dts
phy-mode = "rgmii-txid";
rx-internal-delay-ps = <1950>;
tx-internal-delay-ps = <0>;
```

**关键**：当前内核 motorcomm 的 YT8531 分支**不读**这两个属性；
`rgmii-txid` 还会把 PHY `0xA003` 的 RX 延时字段清 0。表现为：

- link 1000M / carrier=1
- `rx_packets` 冻结（例如 58→58）
- `/proc/net/arp` 里对端一直 `Flags=0x0`（incomplete）
- ping 100% 丢包，但板子发 ARP、PC 也回 ARP

修复：`recipes-core/phy-tune` 的 `phy-tune.service` 在接口 up 后写：

| 扩展寄存器 | 值 | 作用 |
|---|---|---|
| `0xA012` | `0x00F0` | AFE PLL（不写则链路能起但不通包） |
| `0xA003` | `0x20F8` | RGMII TX tap=8 / RX tap=15 |
| `0xA00D` | `0x0180` | 厂商裸机精调 |
| `0xA00E` | `0x0070` | 厂商裸机精调 |
| `0x0D` | `0x4007` | 初始化序列 |
| `0x0E` | `0x0000` | 初始化序列 |
| `0x00` | 读改写 `|0x8200` | 重启自协商 |

另外：`0x0C` 清 bit12、`0xA0` 置 bit15（计数器）、按需。

验证：

```bash
phytool write end0/0/0x1E 0xA003
phytool read  end0/0/0x1F        # 期望 0x20f8
cat /sys/class/net/end0/statistics/rx_packets
cat /proc/net/arp                # 对端 Flags=0x2
ping -c 100 -i 0.01 10.0.0.10    # 0% 丢包
```

- 手动 `ip link set end0 down/up` 后，驱动 `config_init` 会再次清掉延时：
  重跑 `/usr/bin/phy-tune.sh` 或 `systemctl restart phy-tune.service`。
- 新板换 PHY 型号/地址时，先按原理图核对 MDIO strap，再改 `reg`；
  不要照抄别的板。

### 3.3 LCD / 背光（HE050NA-01F）

| 项 | 值 |
|---|---|
| 分辨率 | 800×480，RGB888，DE 模式 |
| 像素时钟 | 33.333MHz |
| 时序 | HSW48 / HBP40 / HFP40；VSW13 / VBP10 / VFP3 |
| 极性 | `hsync-active=0`、`vsync-active=0`、`de-active=1`、`pixelclk-active=1` |
| PWM | PD3 = TIM2_CH1，**AF3** |
| 背光使能 | PD12（`LCD_PW_EN`，高有效） |
| PWM 参数 | `pwms=<&pwm2 0 50000 PWM_POLARITY_INVERTED>`（20kHz，反相） |
| 默认亮度 | 3/10 |
| 面板电源 | `power-supply=<&v3v3_ao>` |

坑：

1. PD3 写成 AF1/AF2 → `invalid function 2 on pin 51`，`pwm-stm32` probe
   失败，`panel-backlight` 永远 deferred。
2. `power-supply=<&scmi_v3v3_sw>` 在当前 SCMI 固件里**没有注册** →
   pwm-backlight deferred（原因为空）。用 `v3v3_ao`。
3. RT9293 在最大电流/高亮度下可能打嗝（整屏黑闪）。默认 3；
   旧板建议 ≤5，新板按硬件优化实测。
4. 这块板没有 HDMI：`&i2c1`、`sii9022`、`hdmi` 全部 disabled。

验证：

```bash
ls /sys/class/pwm                     # pwmchip0
grep pwm-0 /sys/kernel/debug/pwm      # panel-backlight requested
ls /sys/class/backlight/panel-backlight
cat /sys/class/drm/card0-DPI-1/status # connected
cat /sys/class/drm/card0-DPI-1/modes  # 800x480
```

### 3.4 启动链 / extlinux / 分区

extlinux 必须显式：

```text
KERNEL /uImage
FDT /stm32mp135f-<machine>.dtb
INITRD /st-image-resize-initrd
APPEND root=PARTLABEL=rootfs rootwait rw console=ttySTM0,115200
```

- **不要**用 `FDTDIR /`：旧 U-Boot 会去找 `stm32mp135f-test.dtb`，找不到就
  fallback 到旧 DTB，LCD/PHY 全不对。
- **不要**用旧 `root=PARTUUID=...`：新 rootfs 对不上，initramfs 报
  `root ... doesn't exist`。
- eMMC：bootfs 通常是 `mmcblk?p6`，rootfs `p8`；设备名可能在不同启动间
  从 `mmcblk0` 变 `mmcblk1`，所以用 `PARTLABEL`，不要写死设备名。
- 烧录包 TSV 与 WIC 的分区布局要一致；WIC 缺 vendorfs 而 TSV 期望
  vendorfs/rootfs 偏移不同 = 包不一致，不要硬烧。

### 3.5 网络 / 静态 IP

`recipes-core/network-config` 安装：

```ini
# /etc/systemd/network/80-wired.network
[Match]
Name=end0
[Network]
Address=10.0.0.101/24
DHCP=no
```

```ini
# /etc/systemd/network/81-wired-end1.network
[Match]
Name=end1
[Network]
Address=10.0.1.102/24
DHCP=no
```

- 两个口不同网段，**不设默认网关**，避免双口抢路由。
- 不要再同时跑 `udhcpc`；否则会多出 10.0.0.14x 地址，iperf/源地址会选错。
- 接口名是 `end0`/`end1`，不是 eth0/eth1。

### 3.6 仪表盘 / LCD UI（可选）

`recipes-apps/mp13-dashboard`：

- `mp13-dashboard.service` 在 `basic.target` 阶段启动；`getty@tty1` 被
  mask，LCD 不再出现 login。
- 启动时等 `/dev/fb0`（最多 30s），解绑 fbcon（`vtcon1/bind=0`），
  每帧 `FBIO_WAITFORVSYNC`（`vsync: yes`），2fps，避免 tearing/白线。
- `time-sync.service` 从 PC `10.0.0.10:8080/time` 取 UTC；PC 服务不在时
  显示 `WAIT`，不会显示错误时间。RTC `/dev/rtc0` 当前不可用。
- 默认亮度 3；手动 `ip link down/up` 后重跑 phy-tune。

### 3.7 官方隔离复核

```bash
bitbake -e stm32mp13-disco | grep -E '^MP135_(ACTIVE|DTS)='
# 期望：MP135_ACTIVE="0"，MP135_DTS=""（或未设置）
```

- `meta-st` 官方 `stm32mp13-disco.conf` 不修改；`stm32mp13-disco-test.conf`
  已移除（迁移到 `meta-mp135/conf/machine/mp13xsip-v10.conf`）。
- 本层 bbappend 全部带 `MP135_DTS` 守卫；官方 machine 构建时 no-op。

---

## 4. 烧录包完整性（disco-dk 事故复盘，必做）

### 4.1 事故现象

`build-openstlinuxweston-stm32mp13-disco-dk` 构建出的包，烧录失败：

- `arm-trusted-firmware/`、`optee/`、`kernel/` 目录是**空的**
- TSV 却引用 `tf-a-*.stm32`、`metadata.bin`、`fip-*.bin`、`uImage`
- STM32CubeProgrammer 直接报文件不存在
- 同时构建还在 GitHub fetch 超时；`MIRRORS` 把 URL 回退成
  `https://github.com/git/...`（多一个 `/git/`），这是**第二症状**，不是根因。

### 4.2 烧录前 30 秒检查

```bash
PKG=/path/to/flash-package       # 含 TSV + arm-trusted-firmware/ + fip/ + optee/ + kernel/ + *.ext4
TSV=$(ls "$PKG"/FlashLayout_emmc_*.tsv 2>/dev/null | head -1)
echo "TSV=$TSV"

# 1) TSV 引用的每个文件必须存在且非空
awk -F'\t' '$1=="P" {print $7}' "$TSV" | sort -u | while read -r f; do
    [ -s "$PKG/$f" ] || echo "MISSING/EMPTY: $f"
done

# 2) bootfs 必须有内核/DTB/extlinux/initrd
BOOT=$(ls "$PKG"/*splitted-bootfs*.ext4 | head -1)
debugfs -R "ls -p /" "$BOOT" 2>/dev/null | grep -E 'uImage|dtb|extlinux|initrd'

# 3) rootfs 非空
ls -l "$PKG"/*splitted-rootfs*.ext4
```

判据：

- 只要有一条 `MISSING/EMPTY`，**不要烧、不要交付**。
- `arm-trusted-firmware/`、`fip/`、`optee/`、`kernel/` 必须是实际文件，
  不是空目录。
- bootfs 里必须有 `uImage`、`stm32mp135f-<machine>.dtb`、`extlinux/extlinux.conf`、
  `st-image-resize-initrd`。
- TSV 的分区名/偏移与 WIC 的 `.wks`/`fdisk -l` 结果一致；WIC 缺
  vendorfs 而 TSV 期望 vendorfs = 包不一致。

### 4.3 缺文件时怎么重编（复用 test 缓存）

官方 DK/新板构建缺文件，优先复用 `build-openstlinuxweston-stm32mp13-disco-test`
的 workspace + downloads + sstate，避免 GitHub 抖动：

```bitbake
# conf/bblayers.conf
BBLAYERS += "${OEROOT}/build-openstlinuxweston-stm32mp13-disco-test/workspace"

# conf/local.conf
DL_DIR = "/home/lanyang/work/shuhe/build-openstlinuxweston-stm32mp13-disco-test/downloads"
SSTATE_DIR = "/home/lanyang/work/shuhe/build-openstlinuxweston-stm32mp13-disco-test/sstate-cache"

# 网络不稳时不要强制 GitHub
# STM32MP_SOURCE_SELECTION:pn-linux-stm32mp = "github"
# STM32MP_SOURCE_SELECTION:pn-optee-os-stm32mp = "github"
# STM32MP_SOURCE_SELECTION:pn-tf-a-stm32mp = "github"
```

- 改 `bblayers.conf`/`local.conf` 前备份：
  `conf/bblayers.conf.bak-<date>`、`conf/local.conf.bak-<date>`。
- 重编：`bitbake <image>`，确认 `EXIT=0`。
- 编完重新跑 §4.2 的验包命令。
- `.../git/...` 是 MIRRORS 回退症状；先查原始 fetch 和本地 mirror，
  不要只改镜像 URL。

### 4.4 烧录后

- 串口先看 TF-A/OP-TEE/U-Boot/kernel 日志，确认没有 TZC/DRAM/OP-TEE panic。
- 进系统后再验网口/LCD/PHY（§2.10）。
- 失败先回滚 `bblayers.conf`/`local.conf`，再按 §4.2 重新验包。

---

## 5. 故障排查速查（新增）

| 症状 | 根因 | 处理 |
|---|---|---|
| CubeProgrammer 报 file not found | TSV 引用的 TF-A/FIP/optee/kernel 文件缺失或空 | 跑 §4.2 验包；重编，不要硬烧 |
| `arm-trusted-firmware/`、`optee/`、`kernel/` 空 | 构建/部署未完成（fetch 失败、增量空目录） | 复用 test workspace/downloads/sstate 重编 |
| GitHub fetch timeout，日志出现 `/git/...` | 原始 fetch 失败 + MIRRORS 回退 | 复用本地 mirror；取消强制 github |
| WIC 与 TSV 的 vendorfs/rootfs 偏移不一致 | WIC/TSV 来自不同镜像配方 | 用匹配的 TSV/WIC，或重新生成 |
| link 1000M 但 `rx_packets` 不增长、ARP incomplete | YT8531 `0xA003` RX tap 被清 0 | `phy-tune.service` 写 `0xA003=0x20F8` |
| `invalid function 2 on pin 51` | PD3 写成 AF1/AF2 | 改 AF3 |
| `panel-backlight` deferred，原因为空 | `power-supply=<&scmi_v3v3_sw>` 未注册 | 改 `v3v3_ao` |
| LCD 高亮度整屏黑闪 | RT9293 最大电流/升压打嗝 | 默认亮度 3；旧板 ≤5 |
| `root ... doesn't exist` | extlinux 用旧 PARTUUID/FDTDIR | `root=PARTLABEL=rootfs` + 显式 `FDT /...dtb` |
| 设备名 `mmcblk0`/`mmcblk1` 变化 | eMMC 枚举顺序 | 用 `PARTLABEL`，不要写死 |
| 官方 DK 构建被本层影响 | bbappend 未守卫 | 检查 `MP135_DTS`；`MP135_ACTIVE=0` |

---

## 6. 相关文件

| 文件 | 用途 |
|---|---|
| `recipes-bsp/board-dts/<machine>/README.md` | 该板硬件/引脚/时钟/已验证项 |
| `recipes-core/phy-tune/files/phy-tune.sh` | YT8531 初始化序列 |
| `recipes-core/network-config/files/*.network` | end0/end1 静态 IP |
| `recipes-apps/mp13-dashboard/` | LCD 仪表盘 + 开机对时 |
| `tools/new-board.sh` | 新板骨架 |
| `tools/build-board.sh` | 按 machine 构建（临时改 local.conf，自动恢复） |
| `tools/rollback.sh` | 回滚到迁移前备份 |
| `CUSTOM_MAP.md` / `HARDWARE_MAP.md` | 旧 test 体系的外设定位（历史参考） |
