---
name: shuhe-ostl-deploy
description: |
  OpenSTLinux shuhe 定制项目(STM32MP135F 双板型)的 AI 工作手册。
  触发词:"一键部署"、"复刻到新电脑"、"新环境构建 OpenSTLinux"、"shuhe 部署"、
  "客户机器搭建"、"bitbake 全流程"、"出个镜像"、"构建镜像"、"调网口/HDMI/USB/
  摄像头"、"改外设"、"新增外设"、"新板子适配"、"板卡适配"、"本地改 dts"。
  本 skill 是纯知识库:除 envsetup.sh 外零脚本,AI 读取本手册后用自身工具完成
  部署、构建、板卡适配(dts/dtsi)、新板生成与验证。(shuhe)
---

# /shuhe-ostl-deploy — OpenSTLinux shuhe 项目 AI 工作手册

## 0. 铁律(AI 每次会话必读,违反任何一条都算事故)

1. **零脚本**:除 `source layers/meta-st/scripts/envsetup.sh` 这一个 ST 官方脚本外,
   **禁止引用/调用/编写任何 .sh**。所有步骤由 AI 用自身工具(shell/file 编辑)直接执行。
2. **dts 优先**:修改/新增外设**尽量只改 dts/dtsi**(+pinctrl 引脚组、内核 config fragment)。
   **任何 C 代码与非配置文件不得随意修改** —— 必须先向用户说明理由并获得明确批准。
3. **test 体系保护区**:带 `-test` 后缀的一切资产(dts/dtsi/pinctrl/defconfig/conf/DDR 参数)
   与 test 构建目录(`build-openstlinuxweston-stm32mp13-disco-test`)是现役资产,
   未经用户逐项批准不得修改。
4. **分支纪律**:试验性改动一律先建/用 `shuhe-dev-lwg` 分支(或 `shuhe-dev-<主题>`);
   `shuhe-dev` 主分支只收"**构建通过 + 板上验证通过 + 人工审核**"的版本,禁止 force-push 主分支。
5. **回滚文档**:任何改动前,先在工程内记录"原状 + 回滚方式"(改动文件/原值/还原命令),
   改动后更新记录。历史提交不可改写(禁止 rebase 已推送分支)。
6. **官方板不归本项目维护**(官方有现成镜像包),相关内容仅作附录参考,不主动展开。

## 1. 项目速查

| 板型 | 机器 | 镜像 | 内存 | 介质 | 设备树 |
|------|------|------|------|------|--------|
| 自定义板(现役) | `stm32mp13-disco-test` | `shuhe-test-image-core` | 256MB | eMMC | `-test` 体系 |
| 官方板(附录) | `stm32mp13-disco-dk` | `shuhe-image-core` | 512MB | SD | 官方 `stm32mp135f-dk` |

- manifest:`github.com/stm32mp9527/oe-manifest`(branch `shuhe-dev`)
- fork 全部在 `github.com/stm32mp9527/`(linux / u-boot / optee_os / arm-trusted-firmware /
  meta-st-stm32mp / meta-st-openstlinux)
- **版本基线(2026-09 实测校准)**:meta 层 fork 基线 `v25.06.11`(分支名
  `shuhe-dev-v26.06.10` 为"向 v26.06.10 升级"的目标分支,与 `shuhe-dev` 同指向);
  addons 层 `v26.06.10`;**BSP 四件套 recipe 全部声明 ST r3.1**(u-boot/tf-a 挂官方
  r3.1 patch;linux/optee fork 代码含 r3.1 内容);组件版本:内核 6.6.129 /
  U-Boot 2023.10 / OP-TEE 4.0.0 / TF-A 2.10.24

## 1.5 分发与自举(完整手册随层分发,agent 只装 stub)

**完整手册(本文件)随 meta-st-stm32mp 层分发**:`repo sync` 后自动位于
`~/shuhe/layers/meta-st/meta-st-stm32mp/docs/shuhe-skill/`(本目录)。
opencode / OpenClaw 里注册的是 **oe-manifest 仓库 `skill/` 下的入口 stub**
(短描述 + 自举命令 + 指向本手册),正文只在需要时读取,以省 token。

新电脑只需安装 stub(纯 markdown,agent 无关,AI 代劳或领导照抄;https 失败换 ssh
协议或先配 SSH key):

| agent | 安装目标目录 | 触发方式 |
|-------|-------------|---------|
| opencode | `~/.config/opencode/skills/shuhe-ostl-deploy/` | 触发词自动加载 |
| OpenClaw(龙虾) | `~/.openclaw/skills/shuhe-ostl-deploy/`(全机共享)或 `~/.openclaw/workspace/skills/`(单 agent,优先级更高) | 触发词自动注入 |
| 其他 agent(Crush/Claude Code 等) | 工作区任意位置,如 `~/shuhe-skill/` | 开局让 agent 读 stub 并遵守铁律 |

```bash
git clone -b shuhe-dev --depth 1 https://github.com/stm32mp9527/oe-manifest.git /tmp/om
mkdir -p ~/.config/opencode/skills && cp -r /tmp/om/skill ~/.config/opencode/skills/shuhe-ostl-deploy
# OpenClaw:把 mkdir/cp 的目标换成 ~/.openclaw/skills/shuhe-ostl-deploy
```

- 装完自检:下一轮对话 agent 应能在 skill 清单中看到 `shuhe-ostl-deploy`
  (`openclaw skills list`,或直接问 agent"你有哪些 skill")
- **硬约束**:bitbake 只能跑在 Linux(Ubuntu,≥100GB 磁盘)。agent 主机不是 Linux 时,
  让 agent ssh 到 Linux 构建机执行,手册流程不变
- 装好后的开场白:**"一键部署"**(全新环境)/ "shuhe 部署" / "新板子适配"

## 1.6 BSP 组件指向表(SRCREV 纪律)

四个 BSP 组件由 meta 层 recipe 专门指向 fork 的 `shuhe-dev` 分支(你改源码后 push,
**必须同步更新对应 SRCREV**,否则新机器构建的组件与本机不一致):

| 组件 | meta 层指向文件 | 分支 | SRCREV 字段 |
|------|----------------|------|------------|
| linux | `meta-st-stm32mp/recipes-kernel/linux/linux-stm32mp_6.6.bb` | `shuhe-dev` | `SRCREV:class-devupstream` |
| u-boot | `meta-st-stm32mp/recipes-bsp/u-boot/u-boot-stm32mp-common_2023.10.inc` | `shuhe-dev` | 同上 |
| optee | `meta-st-stm32mp/recipes-security/optee/optee-os-stm32mp-common_4.0.0.inc` | `shuhe-dev` | 同上 |
| tf-a | `meta-st-stm32mp/recipes-bsp/trusted-firmware-a/tf-a-stm32mp-common.inc` | `shuhe-dev` | 同上 |

**更新纪律**(改完源码必走):

1. 向 BSP fork 的 `shuhe-dev` push 新提交
2. 取 fork tip 完整 SHA(`git ls-remote git@github.com:stm32mp9527/<repo>.git refs/heads/shuhe-dev`)
3. 更新对应 recipe 的 `SRCREV:class-devupstream`,commit 后 push:
   `git push ShuHeLinux HEAD:refs/heads/shuhe-dev`(本地分支为 `shuhe-dev-v26.06.10`,
   与 `shuhe-dev` 同指向,推一个即可,或两分支同推)
4. 新机器 `repo sync` 后即与本机一致;本机如有该组件 devtool workspace,内容同步后
   `devtool reset <组件>` 回到 recipe 驱动

## 2. 全新环境部署(AI 按步执行,每步向用户汇报进度)

1. **环境检查**:`df -h`(≥100GB)、`lsb_release -a`(Ubuntu 20.04/22.04/24.04)、
   `curl -sI https://github.com`(网络)、`locale | grep LANG`(需 en_US.UTF-8)
2. **装依赖**:
   `sudo apt install -y chrpath diffstat lz4 gawk git-core texinfo gcc-multilib build-essential
   socat cpio python3 python3-pip python3-git python3-jinja2 xz-utils debianutils iputils-ping
   libegl1-mesa libsdl1.2-dev zstd liblz4-tool file repo bsdmainutils git-lfs`
3. **repo 同步**:
   ```bash
   mkdir -p ~/shuhe && cd ~/shuhe
   repo init -u https://github.com/stm32mp9527/oe-manifest.git -b shuhe-dev -m default.xml
   repo sync -j8        # 网络失败递增间隔重试
   ```
4. **创建 build 目录(唯一允许的脚本引用)**:
   ```bash
   cd ~/shuhe
   DISTRO=openstlinux-weston MACHINE=stm32mp13-disco-test \
       source layers/meta-st/scripts/envsetup.sh
   # 首次会有缺包警告,按提示回答 y 忽略
   ```
5. **写 local.conf**:将 `templates/local.conf.shuhe` 内容追加到
   `build-*/conf/local.conf` 末尾(勿删减;DK 板用 `templates/local.conf.dk`)
6. **构建**:`bitbake shuhe-test-image-core`(首次 1~2 小时,可中断续跑)
7. **产物验证(必做,防 bootfs 空)**:
   ```bash
   IMG=tmp-glibc/deploy/images/stm32mp13-disco-test
   debugfs -R "ls -p /" $IMG/shuhe-test-image-core-*.splitted-bootfs-*.ext4 | grep -E 'uImage|dtb|extlinux|initrd'
   ls $IMG/flashlayout_shuhe-test-image-core/optee/FlashLayout_emmc_stm32mp135f-test-optee.tsv
   ```
8. **烧录指引**:STM32CubeProgrammer 加载上述 tsv 烧 eMMC(板子 USB DFU 模式)

## 3. 本地修改流程(devtool workspace,改源码必走此路)

用户要本地改内核/固件源码时,**先建 workspace,再在 workspace 里改**:

```bash
cd build-openstlinuxweston-stm32mp13-disco-test
source ../layers/openembedded-core/oe-init-build-env .
devtool modify linux-stm32mp        # → workspace/sources/linux-stm32mp
devtool modify u-boot-stm32mp       # (workspace 里默认没有 u-boot,需单独执行)
devtool modify optee-os-stm32mp
devtool modify tf-a-stm32mp
```

- 修改位置:`workspace/sources/<recipe>/`(externalsrc 生效,bitbake 直接编译工作区)
- 改完 `bitbake <recipe>` 增量重编,产物自动更新
- **内核 dts 无需注册 Makefile**(dtb 按 KERNEL_DEVICETREE 显式编译);u-boot 新 dts 需注册

## 4. 板卡适配流程(领导报硬件 → AI 特异性适配)

领导/用户懂硬件,能从原理图说出外设接在核心板哪个 IO。AI 的任务:

1. **逐外设问答收集**(用 HARDWARE_MAP.md 的问题清单):
   - 网口:PHY 型号?MDIO 地址(**必须与 PHY 地址 strap 一致**)?RGMII/RMII?复位脚?
   - HDMI:桥芯片型号?I2C 总线与地址?
   - USB:Host/OTG?HUB?使能脚?
   - 摄像头:型号?I2C 地址?接 DCMIPP 哪路?
   - 内存大小(256M/512M,影响 DDR 参数与四层 dts)?启动介质?
   - 按键/LED:GPIO 编号(**注意核对是否与 USB 使能等引脚冲突**)
2. **输出修改计划**(列出每个外设要动的文件与属性,四层:内核/u-boot/tf-a/optee
   + 连锁项:PLL4P 时钟双处一致、内核 CONFIG、pinctrl 冲突检查)
3. **用户确认后修改**:只改 dts/dtsi(pinctrl/config fragment 允许),试验分支上做
4. **构建 + 给用户板端验证命令**(验证命令见 CUSTOM_MAP.md 各节)

> 详细映射表见 **HARDWARE_MAP.md**;板级差异定位见 **CUSTOM_MAP.md**。

## 5. 新板生成流程(以 test 体系为蓝本,四层复制改名)

领导给一块新核心板/底板时,以 test 体系为模板生成全套骨架(**首次构建即可启动**,
再按第 4 节适配外设差异):

| 层 | 生成内容(复制 test 对应文件改名) |
|----|----------------------------------|
| 内核 | `arch/arm/boot/dts/st/<dt>.dts` + `<dt>.dtsi` + `<soc>-test-pinctrl.dtsi`(无需注册 Makefile) |
| U-Boot | `arch/arm/dts/<dt>.dts` + `<dt>-u-boot.dtsi` + **新 defconfig**(`CONFIG_DEFAULT_DEVICE_TREE=<dt>`,复制 `stm32mp13_test_defconfig`)+ `arch/arm/dts/Makefile` 注册行 |
| TF-A | `fdts/<dt>.dts` + `<dt>-fw-config.dts`(DDR 参数按板内存) |
| OP-TEE | `core/arch/arm/dts/<dt>.dts` + `plat-stm32mp1/conf.mk` flavor 注册(内存大小规则) |
| meta-st-stm32mp | `conf/machine/<name>.conf`(引用新 dt + 新 defconfig)+ `recipes-st/images/<image>.bb`(**必含 bootfs 修复行**:`IMAGE_INSTALL:append = " ${MACHINE_ESSENTIAL_EXTRA_RDEPENDS} st-initrd"`) |

提交到试验分支,push,构建验证后按分支纪律合入。

## 6. 网络说明(实测经验,直接引用)

- **接口命名是 `end0`/`end1`**,不是 eth0/eth1(end0=5800a000=ethernet1;end1=5800e000=ethernet2)
- **镜像刻意精简,无网络管理器**:插线不会自动拿 IP,需手动 `udhcpc -i end0` / `udhcpc -i end1`
  (busybox 自带);**双口同插同网段会有路由干扰**,建议一次只用一个口或分网段
- **MAC 地址**:dts 引用 OTP nvmem;板子 OTP 为空时由 U-Boot env 的
  `ethaddr`/`eth1addr` 提供(当前板是占位值 00:11:22:33:44:55/66,可在 U-Boot 里
  `setenv ethaddr <本地管理MAC>; saveenv` 永久修改;每台板要唯一)
- 双网口排错顺序:① `ip a` 看接口与链路(LOWER_UP=物理通)② `ping -I endX <目标>`
  (**必须加 -I**,否则可能走另一个口)③ `cat /proc/net/dev | grep end` 看 TX/RX 计数变化

## 7. 官方板(附录,不维护)

官方 DK 板(512MB/SD 卡)有 ST 现成镜像包,本项目仅在 `stm32mp13-disco-dk` 机器 +
`shuhe-image-core` 上保留构建能力(dk.dts 已还原官方 512MB 版),不主动展开维护。

## 8. 故障排查表

| 症状 | 原因 | 处理 |
|------|------|------|
| 烧录后卡 U-Boot `Cannot load any image` | bootfs 空 | 确认镜像 recipe 含 bootfs 修复行,重烧 |
| `TMPDIR has changed location` | build 目录被移动 | `echo "$PWD/tmp-glibc" > tmp-glibc/saved_tmpdir` |
| `version-going-backwards` 报错 | 版本回退检查 | local.conf 加 `ERROR_QA:remove = "version-going-backwards"` |
| envsetup 缺包警告卡住 | 交互确认 | 管道 `echo y \| source ...` 或补装依赖 |
| 网口 link up 但无 IP | 镜像无 DHCP 客户端(正常) | `udhcpc -i end0` |
| ping 不通但另一口通 | ping 没加 -I,走了别的口 | `ping -I end0 ...` |
| github https 拉取失败 | 网络不稳 | 用 ssh 协议;或本地建 git 镜像到 `downloads/git2/` 离线 fetch |
| locale 报错 | 非 en_US.UTF-8 | `sudo locale-gen en_US.UTF-8` |

## 9. 知识文件索引

- `CUSTOM_MAP.md` —— 现有双板型外设修改点(行号级定位 + 验证命令)
- `HARDWARE_MAP.md` —— 硬件参数 → 修改点映射(板卡适配问答库)
- `templates/local.conf.shuhe` / `local.conf.dk` / `machine-variant.tmpl` —— local.conf 内容源
- `ROLLBACK.md` —— 历史改动的原状与还原方式(铁律 #5,每次改动必须更新)