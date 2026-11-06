# CUSTOM_MAP — 257F-DK 本地修改/外设启用/自定义板导航

> 面向 AI：客户说“我想改 XXX”时，先在本表定位任务，再读 `SKILL.md` 对应章节。
> 官方 257F-DK 的默认节点见 `HARDWARE_MAP.md`；客户自定义板必须先要原理图/引脚表。

## 1. 官方 257F-DK 默认配置速查

| 功能 | 默认状态 | 位置 |
|------|----------|------|
| 控制台 | okay | `&usart2`（serial0，115200） |
| 蓝牙 | okay | `&usart1/bluetooth` |
| 备用 UART6 | disabled | `&usart6` |
| 备用 I2C8 | disabled | `&i2c8` |
| 备用 SPI6 | 控制器 disabled；external-dt 无板级覆盖 | SoC `&spi6`（`spi@40350000`），缺 pinctrl/CS |
| 以太网 | okay | `&eth1` + `ethernet-phy@1` |
| SD 卡 | okay | `&sdmmc1` |
| eMMC | okay | `&sdmmc2` |
| WiFi SDIO | okay | `&sdmmc3` |
| WiFi 设备 | disabled | `&sdmmc3/brcmf@1` |
| HDMI | okay | `&i2c2/adv7535@3d` |
| Camera | okay | `&i2c2/imx335@1a` |
| LVDS panel | okay | `panel_lvds` + `&lvds` |
| User-1 | okay | `gpio-keys/button-user-1`（PC5） |
| User-2 | disabled | `gpio-keys/button-user-2`（PC11） |
| 蓝色心跳灯 | okay | `gpio-leds/led-blue`（PH7） |
| USB Host | okay | `&usbh` / `usbh_ehci` |
| USB DRD | okay | `&usb3dr` / `dwc3` |

## 2. 常用修改 → 文件/命令/验证

| 客户需求 | 改哪里 | 重建 | 验证 |
|----------|--------|------|------|
| 改主机名（固定） | `hostname:pn-base-files` + 禁用 `st-hostname`（见 §3.7） | `bitbake st-image-weston` | `hostname`、`/etc/hosts`、`systemctl is-enabled st-hostname` |
| 静态 IP | `/etc/systemd/network/52-static.network` 或 `systemd-networkd-configuration` bbappend | 镜像/目标机 | `networkctl`、`ip a`、ping |
| WiFi | DTS `brcmf` + `wpa_supplicant@wlan0` | 内核/镜像 | `ip link`、`iw dev` |
| 加软件包 | `local.conf` `IMAGE_INSTALL:append` | `bitbake st-image-weston` | `dpkg -l` |
| 改 rootfs 大小 | `local.conf` `IMAGE_ROOTFS_SIZE` / `IMAGE_ROOTFS_EXTRA_SPACE` | 镜像 | `e2fsck`、TSV 分区 |
| 改 bootargs | U-Boot `UBOOT_EXTLINUX_*` 或 bbappend | U-Boot/镜像 | 串口启动日志 |
| 换开机 logo | M33 StarterApp（`psplash-via-m33`） | M33 固件/镜像 | 上电看屏 |
| 加 SSH key | `ROOTFS_POSTPROCESS_COMMAND` / 自定义 layer | 镜像 | `ssh root@<ip>` |
| 时区 | `DEFAULT_TIMEZONE` 或目标机 `timedatectl` | 镜像/目标机 | `timedatectl` |
| 启用 UART6 | external-dt `&usart6 { status = "okay"; }` + RIF 检查 | external-dt/内核/镜像 | `/dev/ttySTM*` |
| 启用 I2C8 | external-dt `&i2c8 { status = "okay"; }` + RIF 检查 | external-dt/内核/镜像 | `i2cdetect` |
| 启用 SPI6 | external-dt 补完整 `&spi6` + pinctrl + CS | external-dt/内核/镜像 | `/dev/spidev*` |
| 加 GPIO 输出 | `gpio-leds` / `regulator-fixed` | 内核/镜像 | `gpioinfo`/`gpioset` |
| 改 PHY 地址 | `&eth1/mdio1/ethernet-phy@N` | 内核/镜像 | `dmesg`、`ip a` |
| 自定义板 | 新 machine conf + 新 DTS + 自定义 layer | 全量 | §5 清单 |

## 3. DTS 最小改动模板

> 以下模板以 257F-DK 为例。`X/Y/N/AFn` 必须查原理图/SoC DTSI 后替换，禁止猜。

### 3.1 启用 USART6（DK 已定义 pinctrl，默认 disabled）

```dts
&usart6 {
    status = "okay";
};
```

> 本 BSP 默认 `EXTERNAL_DT_ENABLED=1`：改 `external-dt` 的
> `stm32mp257f-dk-cm33tdcid-ostl.dts`，不是内核参考 DTS。
>
> **RIF/M33 风险**：本配置把 USART6 分给 CID2/M33（TF-M RIF 文件 line 45 的
> `RIF_CID2`）；A35 访问可能触发 `SERC exception ID: 36`。先查：
> `cat /sys/kernel/debug/stm32_firewall/rifsc | grep -i USART6`。
> RIF 文件：
> `<BUILD>/tmp-glibc/work-shared/stm32mp2-m33td/external-dt/stm32mp2/m33-td/tfm/stm32mp257f-dk-cm33tdcid-ostl-s-rif.dtsi`，
> line 45 当前是 `RIF_CID2`（M33）。要给 A35：改成 `RIF_CID1`，重编
> `tf-m-stm32mp`（必要时 `m33tdprojects-starter-stm32mp2`），再
> `bitbake m33fw-stm32mp2` 生成 `m33fw-a` 的 signed bin，最后整包烧录；
> 不是只重编 Linux。停/改 UCSI 必须客户确认。
> 永久化同样走 `external-dt` bbappend/patch。
>
> CN5 引脚：TX pin 8（PF13）、RX pin 10（PF14）、RTS pin 11（PG5）、
> CTS pin 36（PF15）、GND pin 9/39；3.3 V TTL。
>
> `external-dt` 的 `linux/u-boot/tf-a/optee/tfm` 各有一份 DTS；只改 `linux/`
> 只影响 Linux，U-Boot 阶段也要用 UART6 时同步改 `u-boot/`。

如果要换到别的引脚：

```dts
&pinctrl {
    my_uart6_pins: my-uart6-pins {
        pins1 {
            pinmux = <STM32_PINMUX('X', Y, AFn)>;
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

### 3.2 启用 I2C8 并挂设备

```dts
&i2c8 {
    status = "okay";

    my_sensor: sensor@48 {
        compatible = "<vendor,part>";
        reg = <0x48>;
        /* interrupts / reset-gpios / vdd-supply 按原理图补 */
    };
};
```

> **RIF 风险**：本配置把 I2C8 分给 CID2/M33（RIF 文件 line 57 `RIF_CID2`）。
> A35 访问可能被防火墙拦截。先查
> `cat /sys/kernel/debug/stm32_firewall/rifsc | grep -i I2C8`。
> 要给 A35，需把 `RIF_CID2` 改 `RIF_CID1`，重编 TF-M/M33，再
> `bitbake m33fw-stm32mp2` 生成 signed bin 并烧 `m33fw-a`；客户确认后再做。
> CN5：SDA pin 3（PZ3）、SCL pin 5（PZ4）。

### 3.3 启用 SPI6（external-dt 板级文件没有 `&spi6` 覆盖）

**只写 `status = "okay"` 不会工作**：SoC 的 `spi6` 控制器节点存在，但
external-dt 板级文件没有 `&spi6` 覆盖，缺 pinctrl/CS；`spi6_pins_a` 也不含
PF4 NSS。需要补完整节点：

```dts
&spi6 {
    pinctrl-names = "default", "sleep";
    pinctrl-0 = <&spi6_pins_a>;
    pinctrl-1 = <&spi6_sleep_pins_a>;
    cs-gpios = <&gpiof 4 GPIO_ACTIVE_LOW>; /* PF4；确认软件 CS 还是硬件 NSS */
    status = "okay";
};
```

- `spi6_pins_a`：SCK PF7（CN5 23）、MOSI PC7（CN5 19）、MISO PC4（CN5 21）。
- PF4（CN5 24）是 NSS；用 `cs-gpios` 时由 GPIO 控制；硬件 NSS 复用则把 PF4
  加进 pinctrl，AF 编号查 SoC DTSI/原理图。
- SPI6 RIF 本配置是 CID1/A35，一般没有 RIF 问题；M33 UCSI 固件可能碰 SPI6，需实测。
- 用 `spidev` 测试时补 child node（正式产品换成客户器件）：
  ```dts
  &spi6 {
      spidev@0 {
          compatible = "rohm,dh2228fv";
          reg = <0>;
          spi-max-frequency = <1000000>;
      };
  };
  ```
- 没有 child node 时 `/dev/spidev*` 不会出现，不要误判控制器没工作。

### 3.4 增加 GPIO 输出/LED

```dts
/ {
    gpio-leds {
        led-user2 {
            gpios = <&gpioX N GPIO_ACTIVE_HIGH>;
            linux,default-trigger = "default-on";
        };
    };
};
```

### 3.5 增加固定 regulator/使能脚

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

### 3.6 改以太网 PHY

```dts
&eth1 {
    mdio1 {
        phy1_eth1: ethernet-phy@1 {
            reg = <1>; /* 与 PHY strap 一致 */
            reset-gpios = <&gpioa 2 GPIO_ACTIVE_LOW>;
        };
    };
};
```

### 3.7 固定主机名（st-hostname 会追加 MAC）

只写 `/etc/hostname` 不够：镜像的 `st-hostname` 每次启动会把 `end0` 的 MAC 后缀
追加到主机名，可能变成 `customer-board<mac>`。

推荐做法：

```conf
# local.conf / distro conf
hostname:pn-base-files = "customer-board"
```

并在自定义 layer 的 `st-hostname.bbappend` 里：

```bitbake
SYSTEMD_AUTO_ENABLE:${PN} = "disable"
```

或 `ROOTFS_POSTPROCESS_COMMAND` 屏蔽：

```conf
ROOTFS_POSTPROCESS_COMMAND += "mask_st_hostname;"
mask_st_hostname() {
    ln -sf /dev/null ${IMAGE_ROOTFS}/etc/systemd/system/st-hostname.service
}
```

验证：

```bash
hostname
cat /etc/hostname
grep 127.0.1.1 /etc/hosts
systemctl is-enabled st-hostname.service
```

- 客户想保留“前缀+MAC”的唯一主机名时，不要禁用 `st-hostname`，只设置前缀。
- 目标机 `hostnamectl set-hostname` 只对当前启动有效；服务未禁用时重启会再追加 MAC。

## 4. 修改后怎么构建

### 4.1 先确认 DTS 来源（关键）

```bash
cd <BUILD>
unset BUILDDIR
source <ROOT>/layers/openembedded-core/oe-init-build-env .
bitbake -e linux-stm32mp | grep -E 'EXTERNAL_DT_ENABLED|EXTDT|EXTERNAL_DEVICETREE'
```

- `EXTERNAL_DT_ENABLED=1`（本 BSP 默认）→ 改 `external-dt`。
- 为 0 → 改内核 `linux-stm32mp` 的 DTS。

### 4.2 临时验证

**external-dt（默认，推荐 devtool）**：

```bash
cd <BUILD>
unset BUILDDIR
source <ROOT>/layers/openembedded-core/oe-init-build-env .

devtool modify external-dt
# 改 workspace/sources/external-dt/stm32mp2/m33-td/linux/
#   stm32mp257f-dk-cm33tdcid-ostl.dts

bitbake external-dt
bitbake linux-stm32mp -f -c compile
bitbake linux-stm32mp -f -c install
bitbake linux-stm32mp -f -c deploy
bitbake st-image-weston
```

如果直接改 `work-shared/.../external-dt`：

```bash
# 找到实际源
find <BUILD>/tmp-glibc/work-shared/stm32mp2-m33td \
  -path '*external-dt*' -name 'stm32mp257f-dk*.dts'

# 改完后只重编 linux，不要 cleanall external-dt
bitbake linux-stm32mp -f -c compile
bitbake linux-stm32mp -f -c install
bitbake linux-stm32mp -f -c deploy
bitbake st-image-weston
```

> **不要**在改完 work-shared 后执行 `bitbake external-dt -c cleanall`：
> `external-dt` 的 `do_clean[cleandirs]` 会删掉 `STAGING_EXTDT_DIR`，改动直接丢失。
> `cleanall` 只用于“已经做成 patch”的永久修改。

**内核 DTS（仅当 external-dt=0）**：

```bash
devtool modify linux-stm32mp
# 改 workspace/sources/linux-stm32mp/arch/arm64/boot/dts/st/...
bitbake linux-stm32mp
bitbake st-image-weston
devtool finish linux-stm32mp <你的 layer>
# 或 devtool reset linux-stm32mp
```

### 4.3 永久交付（推荐）

**external-dt 方案（本 BSP 默认）**：

```text
meta-customer/
├── conf/layer.conf
└── recipes-extended/external-dt/
    ├── external-dt_%.bbappend
    └── files/0001-customer-dts.patch
```

```bitbake
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
SRC_URI += "file://0001-customer-dts.patch"
```

Patch 目标：`stm32mp2/m33-td/linux/stm32mp257f-dk-cm33tdcid-ostl.dts`
（以 `external-dt` 的 `S` 为准）。

**内核 DTS 方案（仅当 external-dt=0）**：

```text
meta-customer/
├── recipes-kernel/linux/linux-stm32mp_%.bbappend
└── recipes-kernel/linux/files/0001-customer-dts.patch
```

把 DTS 改动做成 patch，重新构建；这样客户 `repo sync` 后仍能复现，
不依赖 `tmp-glibc/work`。

### 4.4 只重编 DTS（永久 patch 场景）

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

- 这一段只用于“已经做成 external-dt patch”的永久修改；临时改 work-shared 不要
  跑 `external-dt -c cleanall`。
- 如果 `EXTERNAL_DT_ENABLED=0`，去掉 `bitbake external-dt -c cleanall`。

验证 bootfs 里的 DTB 真的更新（不要只 grep `usart6`）：

```bash
debugfs -R 'dump /stm32mp257f-dk-cm33tdcid-ostl-emmc.dtb /tmp/dtb' <bootfs>.ext4
dtc -I dtb -O dts /tmp/dtb | grep -A20 'serial@40220000'
# 或
fdtget /tmp/dtb /soc@0/bus@42080000/serial@40220000 status
```

## 5. 自定义板（客户新板）导航

### 5.1 先问清楚（缺一项不要猜）

1. SoC 型号/封装、DDR 容量/类型、启动介质（eMMC/SD/NOR）
2. 以太网 PHY 型号、MDIO 地址、RGMII/RMII、复位脚、时钟
3. WiFi/BT 模块型号、SDIO/UART、复位/使能脚
4. 显示：LVDS/DSI/HDMI、桥片、I2C 地址、复位/中断
5. Camera：型号、I2C 地址、CSI lane、MCLK/reset/pwdn
6. USB：Host/DRD、HUB、Type-C CC
7. 音频：I2S/SAI/DMIC/codec
8. 电源：PMIC、DDR 电源、使能/复位
9. 所有新增外设的引脚表（端口/引脚/AF/上下拉/驱动能力）

### 5.2 文件骨架

| 层 | 新建/参考 |
|----|-----------|
| machine conf | 参考 `conf/machine/stm32mp2-m33td.conf`，新建 `conf/machine/<customer>-m33td.conf` |
| external-dt | **本 BSP 默认**；参考 `external-dt/stm32mp2/m33-td/linux/stm32mp257f-dk-cm33tdcid-ostl.dts`，新建客户 DTS |
| 内核 DTS | 仅当 `EXTERNAL_DT_ENABLED=0`；参考 `arch/arm64/boot/dts/st/stm32mp257f-dk.dts` |
| U-Boot DTS | 参考 `arch/arm/dts/stm32mp257f-dk*.dts*` |
| TF-A DTS | 参考 `fdts/stm32mp257f-dk*.dts*` |
| OP-TEE DTS | 参考 `core/arch/arm/dts/stm32mp257f-dk*.dts*` |
| M33/TF-M | 参考 `m33tdprojects-starter-stm32mp2.bb` / `tf-m-stm32mp` |
| 自定义 layer | `meta-customer`，加进 `bblayers.conf` |

### 5.3 不要做的事

- 不要直接改官方 `stm32mp257f-dk.dts` 作为客户基线。
- 不要只改内核 DTS 就以为 DTB 会变：本 BSP 默认 `external-dt`，先确认
  `EXTERNAL_DT_ENABLED`；U-Boot/TF-A/OP-TEE 也不会自动同步，DDR/启动/时钟经常四层联动。
- 不要猜 PHY 地址、I2C 地址、GPIO、AF；错一个就整条外设不通。
- 不要为了“先跑起来”改 C 驱动；先确认 DTS/pinctrl/regulator/clock。

### 5.4 验证清单

- [ ] 串口有完整启动日志
- [ ] DDR 容量正确
- [ ] eMMC/SD 识别
- [ ] 以太网 link + IP + ping
- [ ] USB Host/DRD
- [ ] HDMI/LVDS
- [ ] Camera
- [ ] WiFi/BT
- [ ] GPIO/按键/LED
- [ ] 冷启动/重启 3 次稳定

## 6. 常用验证命令

```bash
# 串口/内核
dmesg | grep -iE 'pinctrl|gpio|i2c|spi|uart|eth|phy|brcm|imx335|adv7535'

# GPIO
gpioinfo
gpioset <chip> <line>=1

# I2C
i2cdetect -y <bus>

# SPI
ls /dev/spidev*
spidev_test -D /dev/spidevX.Y

# 网络
ip a
networkctl
ping -I end0 <gateway>

# 显示
modetest -M stm
cat /sys/class/drm/card0-HDMI-A-1/status

# Camera
v4l2-ctl --list-devices
libcamera-hello -t 5

# 蓝牙
bluetoothctl
hciconfig -a
```
