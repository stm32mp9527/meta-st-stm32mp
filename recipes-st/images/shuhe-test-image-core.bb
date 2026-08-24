SUMMARY = "OpenSTLinux core image for test board with -test devicetree."
LICENSE = "Proprietary"

include recipes-st/images/st-image.inc

inherit core-image

# --- START: 禁用不需要的 IMAGE_FEATURES ---
# 禁用 GUI 相关功能
FEATURE_PACKAGES_weston = ""
FEATURE_PACKAGES_x11 = ""
FEATURE_PACKAGES_x11-base = ""
FEATURE_PACKAGES_x11-sato = ""
FEATURE_PACKAGES_tools-debug = ""
FEATURE_PACKAGES_eclipse-debug = ""
FEATURE_PACKAGES_tools-profile = ""
FEATURE_PACKAGES_tools-testapps = ""
FEATURE_PACKAGES_tools-sdk = ""
FEATURE_PACKAGES_nfs-server = ""
FEATURE_PACKAGES_nfs-client = ""
FEATURE_PACKAGES_hwcodecs = ""

# 使用精简包组作为基础
CORE_IMAGE_BASE_INSTALL = '\
    packagegroup-shuhe-minimal \
    '

# 安装机器必备的启动包(内核镜像/设备树/extlinux), 确保 /boot 有内容可启动
IMAGE_INSTALL:append = " ${MACHINE_ESSENTIAL_EXTRA_RDEPENDS} st-initrd"
    
IMAGE_LINGUAS = "en-us"

# 添加摄像头工具、应用及所需内核模块
#IMAGE_INSTALL:append = " camera-tx"

MACHINE_EXTRA_RRECOMMENDS:remove = "kernel-modules"

# 移除 package-management 功能
# IMAGE_FEATURES += "\
#     package-management  \
#     "

# 禁用 systemd 网络相关服务
SYSTEMD_DISABLED_SERVICES += "systemd-networkd.service systemd-resolved.service systemd-timesyncd.service"
SYSTEMD_DISABLED_SERVICES += "systemd-firstboot.service systemd-coredump.socket systemd-journald-audit.socket serial-getty@ttySTM0.service"
