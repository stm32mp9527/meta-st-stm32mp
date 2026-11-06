# HARDWARE_MAP — STM32MP257F-DK 板级映射（AI 问答库）

> 用途：客户描述硬件/外设时，AI 按本表定位 DTS 节点、pinctrl、recipe 和验证命令。
> 官方 257F-DK 可直接用本表；客户自定义板必须补充原理图/网表/引脚表，禁止猜。

## 1. 板级总览

| 项目 | 值 |
|------|----|
| Board | STM32MP257F-DK Discovery kit |
| SoC | STM32MP257FAK3，VFBGA424 |
| CPU | 2× Cortex-A35 + 1× Cortex-M33 |
| DDR | 4 GB LPDDR4 |
| eMMC | 8 GB，SDMMC2，8-bit，non-removable |
| microSD | SDMMC1，4-bit，CD = PD3 |
| Console | USART2，115200 8N1，ST-LINK VCP |
| Boot | TF-A → OP-TEE → U-Boot → Linux；M33 跑 TF-M/StarterApp |
| PMIC | STPMIC25 |
| 参考手册 | UM3385（ST 官网/交付文档） |

## 2. Boot switch（SW1/SW2）

`SW1` = BOOT0/BOOT1，`SW2` = BOOT2/BOOT3。板上 `ON=1`、`OFF=0`，以丝印为准。

| BOOT3 | BOOT2 | BOOT1 | BOOT0 | 启动模式 |
|-------|-------|-------|-------|----------|
| 0 | 0 | 0 | 0 | USB DFU（STM32CubeProgrammer） |
| 0 | 0 | 0 | 1 | SD card（SDMMC1） |
| 0 | 0 | 1 | 0 | eMMC（SDMMC2） |

- 烧录：全 0，USB Type-C DRD 口连接主机。
- 启动：按目标介质切回 SD/eMMC，重新上电。
- 若板子不启动，第一步先核对这 4 个开关，不要先怀疑镜像。

## 3. 连接器（以板上丝印为准）

| 功能 | 连接器/丝印 | 说明 |
|------|-------------|------|
| 供电 | USB Type-C（5 V/3 A 推荐） | 电源/PD |
| USB DRD | USB Type-C DRD | DFU 烧录/设备模式/OTG |
| USB Host | 2× USB Type-A | 板上 HUB |
| 调试/串口 | ST-LINK USB Type-C | ST-LINK V3EC，VCP = USART2 |
| Ethernet | RJ45 | RGMII，Realtek PHY |
| microSD | microSD slot | SDMMC1 |
| HDMI | HDMI Type-A | ADV7535 DSI→HDMI |
| LVDS | LVDS 排线 | EDT ETML0700Z9NDHA 7" panel |
| CSI camera | CSI-2 排线 | IMX335，2-lane |
| GPIO 扩展 | 40-pin header | 3.3 V/5 V/GND + GPIO/I2C/SPI/UART |
| 按键 | B1 reset / B2 wake-up / B3 tamper / B4,B5 user | 以丝印为准 |
| LED | LD6 power / LD7-LD9 user / LD5 COM / LD11 5V | 以丝印为准 |

### 3.1 CN5 40-pin 排针关键引脚（UM3385 Table 24）

| 信号 | SoC 引脚 | CN5 pin |
|------|----------|---------|
| 3V3 | - | 1, 17 |
| 5V | - | 2, 4 |
| GND | - | 6, 9, 14, 20, 25, 30, 34, 39 |
| I2C8_SDA | PZ3 | 3 |
| I2C8_SCL | PZ4 | 5 |
| USART6_TX | PF13 | 8 |
| USART6_RX | PF14 | 10 |
| USART6_RTS | PG5 | 11 |
| USART6_CTS | PF15 | 36 |
| SPI6_MOSI | PC7 | 19 |
| SPI6_MISO | PC4 | 21 |
| SPI6_SCK | PF7 | 23 |
| SPI6_NSS | PF4 | 24 |
| I2C2_SDA | PF0 | 27 |
| I2C2_SCL | PF2 | 28 |

## 4. 外设 → DTS 节点

### 4.1 串口/调试

| 功能 | 节点 | 说明 |
|------|------|------|
| 控制台 | `&usart2` | `serial0`，`stdout-path = "serial0:115200n8"` |
| 蓝牙 | `&usart1` | BCM43438，`uart-has-rtscts`，已 okay |
| 备用 UART | `&usart6` | 默认 disabled；已引出 CN5 pin 8/10/11/36；**先查 RIF/M33 归属** |
| 调试/STM32 | `&usart3`/`&uart5` 等 | 以 SoC DTSI 和原理图为准；不要猜 |

> **USART6 RIF 风险**：本配置把 USART6 分给 **CID2/M33**（TF-M RIF 文件 line 45
> 的 `RIF_CID2`）；A35 Linux 访问可能触发 `SERC exception ID: 36`。先查
> `cat /sys/kernel/debug/stm32_firewall/rifsc | grep -i USART6`（SCID=2 即 M33）。
> 要给 A35：把 line 45 改 `RIF_CID1`，重编 `tf-m-stm32mp`（必要时
> `m33tdprojects-starter-stm32mp2`），再 `bitbake m33fw-stm32mp2` 生成
> `m33fw-a` 的 signed bin，最后整包烧录；停/改 UCSI 必须客户确认，
> 不能只改 DTS 就承诺可用。

### 4.2 网络

| 功能 | 节点 | 关键属性 |
|------|------|----------|
| 以太网 | `&eth1` | `phy-mode = "rgmii-id"`，`max-speed = <1000>` |
| MDIO/PHY | `&eth1/mdio1/ethernet-phy@1` | `compatible = "ethernet-phy-id001c.c916"`，`reg = <1>`，reset `&gpioa 2` |
| WiFi SDIO | `&sdmmc3` | 4-bit，`mmc-pwrseq = <&wifi_pwrseq>`，复位 `&gpiog 8` |
| WiFi 设备 | `&sdmmc3/brcmf@1` | `compatible = "brcm,bcm4329-fmac"`，**默认 disabled** |
| BT | `&usart1/bluetooth` | `compatible = "brcm,bcm43438-bt"`，shutdown `&gpiog 4` |

> 换 PHY 时必须核对原理图 PHY Address strap；`reg` 与 strap 不一致会直接不通。

### 4.3 存储

| 功能 | 节点 | 说明 |
|------|------|------|
| microSD | `&sdmmc1` | 4-bit，CD `&gpiod 3`，`disable-wp` |
| eMMC | `&sdmmc2` | 8-bit，`non-removable`，`mmc-ddr-1_8v`/`hs200` |
| WiFi SDIO | `&sdmmc3` | 4-bit，non-removable |

### 4.4 显示/视频

| 功能 | 节点 | 说明 |
|------|------|------|
| LCD 控制器 | `&ltdc` | 输出到 DSI 和 LVDS |
| DSI | `&dsi` | 4-lane，接 ADV7535 |
| LVDS | `&lvds` | 接 EDT 7" panel |
| HDMI 桥 | `&i2c2/adv7535@3d` | `adi,adv7535`，addrs 0x3d/0x3c/0x3f/0x38，reset `&gpiob 6`，中断 `&gpiob 4` |
| HDMI 连接器 | `hdmi` | `hdmi-connector`，`type = "a"` |
| LVDS panel | `panel_lvds` | `edt,etml0700z9ndha`，1024×600 |
| 背光 | `panel_lvds_backlight` | `pwm-backlight`，`&pwm3` |
| 触摸 | `&i2c2/ili2511@41` | `ilitek,ili251x` |

### 4.5 Camera/音频

| 功能 | 节点 | 说明 |
|------|------|------|
| Camera | `&i2c2/imx335@1a` | Sony IMX335，CSI 2-lane，reset `&gpiob 1`，pwdn `&gpiob 11` |
| CSI | `&csi` | 2-lane，接 DCMIPP |
| DCMIPP | `&dcmipp` | |
| 音频 | `sound` + `&i2s2` + `&mdf1` | ADV7535 HDMI audio、DMIC0/1 |
| DMIC | `dmic0`/`dmic1` | `dmic-codec` |

### 4.6 USB

| 功能 | 节点 | 说明 |
|------|------|------|
| USB Host | `&usbh` / `usbh_ehci` | 板上 HUB（`usb424,2514`），2× Type-A |
| USB DRD | `&usb3dr` / `dwc3` | Type-C DRD，`usb-role-switch` |
| USB2 PHY | `&usb2_phy1` / `&usb2_phy2` | 3.3 V supply |
| Type-C 控制器 | `&i2c_rpmsg/typec@35` | `st,stm32mp25-typec`，M33 rpmsg |

### 4.7 GPIO/按键/LED

| 功能 | 节点/属性 | 说明 |
|------|-----------|------|
| User-1 | `gpio-keys/button-user-1` | `&gpioc 5`，`BTN_1` |
| User-2 | `gpio-keys/button-user-2` | `&gpioc 11`，**默认 disabled** |
| Wake-up | `gpio-keys/button-wake-up` | 通过 OP-TEE 中断 |
| 蓝色心跳灯 | `gpio-leds/led-blue` | `&gpioh 7`，heartbeat |
| 其他板载 LED | 原理图 LD7/LD8/LD9 等 | DTS 未全部描述；不要猜 GPIO |

### 4.8 时钟/电源/看门狗

| 功能 | 节点 | 说明 |
|------|------|------|
| RTC | `&rtc` | LSCO 输出 |
| 看门狗 | `&arm_wdt` | 32 s |
| PMIC | `&scmi_regu` | STPMIC25，各 rail |
| DDR/GPU 预留 | `stm32mp257f-dk-resmem.dtsi` | 改 DDR/GPU/M33 内存时必看 |
| M33 通信 | `&ipcc1`/`&ipcc2`/`&mlahb` | rpmsg/mailbox |

## 5. 默认 disabled / 需要客户确认的节点

| 节点 | 默认 | 启用前确认 |
|------|------|------------|
| `&usart6` | disabled | 已引出 CN5 pin 8/10/11/36；**本配置 RIF=CID2/M33** |
| `&i2c8` | disabled | 已引出 CN5 pin 3/5；**本配置 RIF=CID2/M33**；外设地址/上拉 |
| `&spi6` | 无板级覆盖 | SoC 控制器存在，但 external-dt 缺 pinctrl/CS；需补完整 `&spi6` + PF4 CS；RIF=CID1 |
| `&sdmmc3/brcmf@1` | disabled | WiFi 模块是否装配、firmware、pwrseq |
| `button-user-2` | disabled | PC11 是否被其他功能占用 |
| `&usbh_ohci` | disabled | 是否需要 OHCI |
| `&i3c4` | disabled | 是否需要 I3C |

## 6. 引脚冲突检查（AI 必做）

1. 在全部 DTS/DTSI 里搜目标引脚。**本 BSP 先查 `external-dt` 源**（启动 DTB 来自它），
   再查内核参考 DTS：
   ```bash
   # external-dt（本 BSP 默认）
   grep -rn "gpioX [0-9]" <BUILD>/tmp-glibc/work-shared/stm32mp2-m33td/external-dt/
   grep -rn "STM32_PINMUX('X', N" <BUILD>/tmp-glibc/work-shared/stm32mp2-m33td/external-dt/

   # 内核参考
   grep -rn "gpioX [0-9]" arch/arm64/boot/dts/st/
   grep -rn "STM32_PINMUX('X', N" arch/arm64/boot/dts/st/
   ```
2. 查同一 pinctrl 组是否被多个外设引用。
3. 板上用 `gpioinfo` 看 line 是否已被申请；`EBUSY` 说明冲突。
4. 不要把“某个引脚在别的板子上能用”直接搬到 257F-DK；以本板原理图为准。

## 7. 串口/电源/启动检查顺序

1. 5V/3A 供电，LD6/LD11 亮。
2. ST-LINK USB 接主机，`/dev/ttyACM0` 出现。
3. 115200 8N1 打开串口。
4. Boot switch 按目标介质设置。
5. 上电，先看 TF-A/OP-TEE/U-Boot 日志，再看 Linux。
6. 无日志：换线/换口/检查 boot switch/检查镜像，不要先改 DTS。

## 8. 已知板级注意点

- **M33 splash**：257F-DK 的开机 logo 是 `psplash-via-m33`，由 M33 StarterApp 显示；
  不是 Linux `psplash`。换 logo 要改 M33 工程。
- **WiFi 默认未启用**：`brcmf` 节点 disabled；BT 节点 okay。不要假设 WiFi 开箱可用。
- **eMMC/SD 启动介质不同**：eMMC 用 `mmc1_extlinux`，SD 用 `mmc0_extlinux`；
  烧录 TSV 也不同。
- **4 GB DDR**：`memory@80000000` 是 4 GB；改 DDR 时 TF-A/OP-TEE/内核/机器 conf 要联动。
- **PHY 地址**：`ethernet-phy@1` + Realtek PHY；换 PHY/换板必须查 strap。
- **HDMI 桥是 ADV7535**，不是 MP1 板的 SII9022；不要套用 MP1 的节点。
- **本 BSP 默认 external-dt**：改 DTS 前先确认 `EXTERNAL_DT_ENABLED`；内核
  `arch/arm64/boot/dts/st/stm32mp257f-dk.dts` 只是参考，不是启动 DTB 的唯一来源。
- **USART6/I2C8 的 RIF=CID2/M33**：A35 访问可能触发 SERC ID 36/被防火墙拦截；
  先查 RIF，再决定是否改归属/停 UCSI；改归属要重编 TF-M/M33 并烧 `m33fw-a`。
- **SPI6 在 external-dt 里没有板级 `&spi6` 覆盖**：SoC 控制器存在，但缺 pinctrl/CS；
  只写 `status = "okay"` 不工作；需补完整节点 + PF4 CS；SPI6 RIF 是 CID1/A35。
