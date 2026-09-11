# 定制地图 CUSTOM_MAP — 外设修改点快速定位

> 双板型对照:自定义板(`stm32mp13-disco-test`,256MB,eMMC,`-test` 设备树)与
> 官方板(`stm32mp13-disco-dk`,512MB,SD 卡,官方 `stm32mp135f-dk` 设备树)。
> 内核版本 6.6.129 / U-Boot 2023.10 / OP-TEE 4.0.0 / TF-A 2.10.24;四件套 recipe 均声明
> ST r3.1(u-boot/tf-a 挂官方 r3.1 patch,linux/optee fork 代码含 r3.1 内容)。

## 约定

- 内核 dts 目录: `sources/linux-stm32mp/arch/arm/boot/dts/st/`
- pinctrl: 自定义板 `stm32mp13-test-pinctrl.dtsi`,官方板沿用官方 `stm32mp13-pinctrl.dtsi`
- 修改 dts 后只需重新构建内核,无需全量重编:
  `bitbake linux-stm32mp -f -c compile && bitbake <image>`

---

## 1. 网口(eth1/eth2 = MAC 硬件名;板上接口名是 end0/end1)

| 项目 | 自定义板(-test) | 官方板(官方 dk) |
|------|----------------|-----------------|
| 设备树 | `stm32mp135f-test.dts`:265-307(rgmii-id + dwmac-mdio + PHY) | `stm32mp135f-dk.dts`:252(eth1 phy-handle)/279(eth2 phy-handle) |
| pinctrl | `stm32mp13-test-pinctrl.dtsi`:159 `eth1_pins_mx` / 217 `eth2_pins_mx` | 官方 `stm32mp13-pinctrl.dtsi` 默认 pinmux |
| 网卡驱动配置 | `meta-st-stm32mp/recipes-kernel/linux/linux-stm32mp/6.6/fragment-04-modules.config`(STMMAC/DWMAC) | 同左(共用) |
| 驱动模块化 | `meta-st-stm32mp/recipes-kernel/linux/linux-stm32mp_%.bbappend`(`do_configure` 里 `scripts/config -m STMMAC_ETH ...`) | 同左(共用) |

修改方法:换 PHY 时改 `phy-handle`/`phy-mode`/`reg`;换引脚改对应 `_pins_mx` 组。
验证:`ip link`(end0/end1 LOWER_UP)、`ethtool end0`、`ping -I end0 <对端>`。

## 2. HDMI(SII9022 桥)

| 项目 | 自定义板(-test) | 官方板(官方 dk) |
|------|----------------|-----------------|
| 桥节点 | `stm32mp135f-test.dts`:348-370(sii9022, status okay) | 官方板无 HDMI 桥(DSI 显示),ltdc 官方配置 |
| LTDC 链路 | `stm32mp135f-test.dts`:487-499(`&ltdc` + `ltdc_out_rgb` → sii9022) | `stm32mp135f-dk.dts`:449-456(ltdc + `ltdc_out_rgb`) |
| 内核配置 | `linux-stm32mp_%.bbappend`: `-m DRM_SII902X` | 同左(保留无害) |

验证:`edid-decode /sys/class/drm/card0-HDMI-A-1/edid`、`cat /sys/class/drm/card0-HDMI-A-1/status`。

## 3. USB

| 项目 | 自定义板(-test) | 官方板(官方 dk) |
|------|----------------|-----------------|
| OTG | `stm32mp135f-test.dts`:735(`usbotg_hs`, role-switch 注释) | `stm32mp135f-dk.dts`:712(`usbotg_hs` + usb-role-switch) |
| HOST | `stm32mp135f-test.dts`:722(`usbh_ehci`) | `stm32mp135f-dk.dts`:699(`usbh_ehci`) |
| Type-C(ucsi) | `stm32mp135f-test.dts`:382(stm32g0-ucsi) | `stm32mp135f-dk.dts`:325(typec@53 stm32g0-typec) |
| 内核配置 | `fragment-04-modules.config`: `CONFIG_USB_ACM=m` | 同左 |

验证:`lsusb`、`dmesg | grep -i usb`。

## 4. 摄像头(ov5640)

| 项目 | 自定义板(-test) | 官方板(官方 dk) |
|------|----------------|-----------------|
| 节点 | `stm32mp135f-test.dts`:465(ov5640,**默认 disabled**) | `stm32mp135f-dk.dts`:414(ov5640,**默认 disabled**) |
| 软件栈 | libcamera(camera-tx 等,meta-st-openstlinux) | 同左 |

启用方法:节点 `status = "disabled"` → `"okay"`,补 `clocks`/`pinctrl` 后重建内核。
验证:`libcamera-hello -t 5`(需在镜像中安装 libcamera 工具)。

## 5. PLL4 / VCO(像素与外设时钟)

| 项目 | 自定义板(-test) | 官方板(官方 dk) |
|------|----------------|-----------------|
| TF-A | `sources/tf-a-stm32mp/fdts/stm32mp135f-test.dts`:148-190(`pll4_vco_594Mhz`, divmn `<3 98>`, div_pqr `<5 11 12>`) | 官方 `stm32mp135f-dk.dts`(500MHz 默认) |
| OP-TEE | `sources/optee-os-stm32mp/core/arch/arm/dts/stm32mp135f-dk-test.dts`:424-489(同上 594MHz) | 官方 `stm32mp135f-dk.dts`:508(500MHz 默认) |

注意:TF-A 与 OP-TEE 的 PLL4 必须一致;改 VCO 后 HDMI/网口时钟同步变化。
验证:`cat /sys/kernel/debug/clk/clk_summary | grep pll4`。

## 6. 内核模块装载

| 项目 | 位置 | 说明 |
|------|------|------|
| 模块化开关 | `meta-st-stm32mp/recipes-kernel/linux/linux-stm32mp_%.bbappend`(`do_configure` 内 `scripts/config`) | 双板共用 |
| 模块清单 | `meta-st-stm32mp/recipes-kernel/linux/linux-stm32mp/6.6/fragment-04-modules.config` | 双板共用 |

## 7. 镜像 /boot 内容

| 项目 | 自定义板 | 官方板 |
|------|---------|--------|
| 镜像 recipe | `meta-st-*/recipes-st/images/shuhe-test-image-core.bb` | `shuhe-image-core.bb` |
| 关键行 | `IMAGE_INSTALL:append = " ${MACHINE_ESSENTIAL_EXTRA_RDEPENDS} st-initrd"`(bootfs 修复,两镜像均已含) | 同左 |

## 8. 启动介质

| 项目 | 自定义板(eMMC) | 官方板(SD 卡) |
|------|---------------|---------------|
| 机器 conf | `meta-st-stm32mp/conf/machine/stm32mp13-disco-test.conf`(`BOOTDEVICE_LABELS=emmc` + `STM32MP_DT_FILES_EMMC`) | `stm32mp13-disco-dk.conf`(`BOOTDEVICE_LABELS=sdcard` + `STM32MP_DT_FILES_SDCARD`) |
| TF-A 覆盖 | local.conf `TF_A_CONFIG[optee-emmc]`(STM32MP_EMMC_BOOT=1) | 自动派生 `optee-sdcard`,一般无需覆盖 |
| wic | eMMC 用 FlashLayout tsv 烧录;SD 卡用 `sdcard-stm32mp135f-test-optee-example.wks.in` | `sdcard-stm32mp135f-dk-optee-example.wks.in` |

---

## 快速验证命令汇总

```bash
ip link                                    # 网口
edid-decode /sys/class/drm/card0-HDMI-A-1/edid   # HDMI
lsusb                                      # USB
cat /sys/kernel/debug/clk/clk_summary | grep pll4   # PLL4
libcamera-hello -t 5                       # 摄像头(需安装)
cat /proc/meminfo | grep MemTotal          # 内存(官方板≈512MB)
```