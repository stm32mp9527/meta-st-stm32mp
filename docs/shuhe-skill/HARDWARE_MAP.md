# HARDWARE_MAP — 硬件参数 → 修改点映射(板卡适配问答库)

> 用途:领导/硬件同事报出原理图信息(PHY 型号、MDIO 地址、I2C 地址、接哪个 IO、
> 内存大小等),AI 按本表定位**全部需要修改的文件与属性**,只改 dts/dtsi。
> 四层 = 内核 dts / U-Boot dts / TF-A fdts / OP-TEE dts。

## 通用规则

- **MDIO 地址必须与 PHY 地址 strap 一致**(原理图查 PHY Address[2:0],由 RXD3/PHY_ADO、
  RX_CLK/PHY_AD1、RX_CTL/PHY_AD2 的上/下拉决定)。不一致 = PHY 探测不到、网口全死。
  实测案例:GK 板 PHY2 strap=001,dts 写 reg=0 → end1 全死;改 reg=1 → 立即恢复。
- **PHY 复位脚必须核对原理图**,不能想当然。实测案例:PA11 在 GK 板是 ETH_25M 时钟脚
  (PIN122),不是复位脚。
- **按键/LED 的 GPIO 要与 USB 使能等引脚查冲突**。实测案例:PA13 在 GK 板是 USB1_EN,
  dts 把它当按键 → `gpio-keys: error -EBUSY`。
- **PLL4P(ETH 125M 时钟)改动必须 TF-A 与 OP-TEE 双处同步**,一处改一处不改 = 网口时序错。
- 内存大小四层联动:内核 dts memory 节点、TF-A DDR 参数(± fw-config)、OP-TEE conf.mk
  flavor 规则、U-Boot(如有 DDR 配置)。

## 1. 网口(RGMII 千兆,PHY=Motorcomm YT8521/YT8531)

| 领导提供 | 修改点 |
|---------|--------|
| PHY 型号(YT8521/YT8531) | dts `compatible = "ethernet-phy-id0000.011a"`;驱动 `drivers/net/phy/motorcomm.c`(内核/uboot 各一份,**改 C 需批准**) |
| MDIO 地址 N | 节点名 `ethernet-phy@N` + `reg = <N>`(两处必须同改) |
| 接哪个 IO(RGMII 引脚) | pinctrl 引脚组 `ethX_pins_mx`(STM32_PINMUX 逐脚对照原理图;核对 AF 编号与 bias/slew) |
| 延时模式 | `phy-mode`:`rgmii-id`(PHY 内部 RX+TX 延时 1950ps)/ `rgmii`;需微调时 phy 节点加 `rx-internal-delay-ps` / `tx-internal-delay-ps`(驱动支持,0/50…2000ps) |
| 125M 时钟 | PLL4P:TF-A `fdts/<dt>.dts` + OP-TEE `<dt>.dts` 双处 `pll4_vco/div_pqr`,**必须一致** |
| PHY 复位脚 | dts `reset-gpios = <&gpioX N GPIO_ACTIVE_LOW>`(对照原理图 NRST 网络) |
| 驱动模块化 | `meta-st-stm32mp/recipes-kernel/linux/linux-stm32mp_%.bbappend`(scripts/config)+ `fragment-04-modules.config` |

验证:`ip a`(endX LOWER_UP)、`dmesg | grep -i 'YT85\|stmmac'`、
`ls /sys/bus/mdio_bus/devices/`(应见 stmmac-N:0M)、`ping -I endX <网关>`。

## 2. HDMI(SII9022 桥)

| 领导提供 | 修改点 |
|---------|--------|
| 桥型号 SII9022 + I2C 总线/地址 | 内核 dts i2cX 节点下 `hdmi-transmitter@39`(`reg` = I2C 地址,`compatible = "sil,sii9022"`) |
| 接 LCD 哪组 RGB | `ltdc` 节点 pinctrl + endpoint 链路(ltdc_out → sii9022_in) |
| 驱动 | `linux-stm32mp_%.bbappend`:`-m DRM_SII902X` |
| 时钟 | PLL4P 像素时钟(同网口注意项) |

验证:`cat /sys/class/drm/card0-HDMI-A-1/status`、`edid-decode /sys/class/drm/card0-HDMI-A-1/edid`。

## 3. USB

| 领导提供 | 修改点 |
|---------|--------|
| OTG / Host 分布 | dts `&usbotg_hs` / `&usbh_ehci`(role、pinctrl) |
| HUB 芯片(如 SL2.1A) | 通常免驱(纯硬件 hub),确认供电使能脚 GPIO 对应 dts regulator/固定电平 |
| 供电使能脚(如 MT9700 EN) | dts 固定 regulator 或 pinctrl;**注意与其他功能引脚冲突**(PA13 案例) |

验证:`lsusb`、`dmesg | grep -i usb`(hub 枚举端口数)。

## 4. 摄像头

| 领导提供 | 修改点 |
|---------|--------|
| 型号(OV2640/OV5640) | 内核 dts `ov5640`/对应节点(`compatible`、I2C `reg`、`status="okay"`)、DCMIPP/CSI 链路 |
| MCLK/CSI 接线 | dts clocks(pinctrl CSI 时钟)+ dcmipp 端点 |
| 软件栈 | libcamera / camera 相关包(meta-st-openstlinux) |

验证:`dmesg | grep -i ov5640`(或对应型号)、libcamera 工具抓帧。

## 5. 内存 / 存储 / 启动介质

| 领导提供 | 修改点(四层联动!) |
|---------|---------------------|
| DDR 大小(256M/512M) | TF-A `fdts/<板>.dts` + DDR 参数 dtsi(`DDR_MEM_SIZE`/ADDRMAP)→ **OP-TEE conf.mk flavor 规则**(`CFG_DRAM_SIZE`)→ 内核 dts `memory@` 节点 → 机器 conf |
| eMMC / SD | 四层 dts `sdmmcX` 节点(bus-width、vmmc-supply)+ 机器 conf `BOOTDEVICE_LABELS` + `STM32MP_DT_FILES_*` |
| TF-A 配置 | local.conf `TF_A_CONFIG[optee-emmc/optee-sdcard]`(STM32MP_EMMC_BOOT=1 或 STM32MP_SDMMC=1) |

验证:启动日志 DDR 容量、`cat /proc/meminfo | grep MemTotal`、`ls /dev/mmcblk*`。

## 6. 按键 / LED / 其他 GPIO

| 领导提供 | 修改点 |
|---------|--------|
| 按键 GPIO | dts `gpio-keys` 节点(`gpios = <&gpioX N ...>`)+ pinctrl;**先查该脚是否被 USB_EN/时钟等占用**(EBUSY 案例) |
| LED GPIO | dts `leds` 节点 + pinctrl |

验证:板上 `dmesg | grep gpio-keys`(无 -EBUSY)、按键触发 `cat /proc/bus/input/devices`。

## 7. 已知坑速查(本项目实测)

1. PHY strap 地址 ≠ dts reg → 网口全死(先查原理图 strap 再定 reg)
2. 复位脚写成时钟脚(PA11 案例)→ 复位无效
3. GPIO 被其他驱动占用(PA13=USB1_EN 案例)→ EBUSY
4. PLL4P 只改一处(TF-A 改了 OP-TEE 没改)→ 网口/HDMI 时钟错
5. 内存改了 dts 没改 OP-TEE conf.mk → optee 编译报 `Wrong CFG_DRAM_SIZE`