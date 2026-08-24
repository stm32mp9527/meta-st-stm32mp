#
# Copyright (C) 2007 OpenedHand Ltd.
#

SUMMARY = "Shuhe minimal package group for hello world with TSV support"
DESCRIPTION = "Minimal packages required to run basic hello world program and generate TSV"

PACKAGE_ARCH = "${MACHINE_ARCH}"

inherit packagegroup

# Distro can override the following VIRTUAL-RUNTIME providers:
VIRTUAL-RUNTIME_dev_manager ?= "udev"
VIRTUAL-RUNTIME_keymaps ?= "keymaps"

# Remove EFI support to reduce size
EFI_PROVIDER ??= ""

# Minimal init scripts - only RTC related if needed

RDEPENDS:${PN} = "\
    base-files \
    base-passwd \
    ${VIRTUAL-RUNTIME_base-utils} \
    ${VIRTUAL-RUNTIME_login_manager} \
    ${VIRTUAL-RUNTIME_init_manager} \
    ${VIRTUAL-RUNTIME_dev_manager} \
    build-info-openstlinux \
    "

# Keep RRECOMMENDS empty to avoid syslog and other non-essential packages
RRECOMMENDS:${PN} = ""
