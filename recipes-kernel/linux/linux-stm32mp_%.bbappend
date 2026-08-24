do_configure:append() {
    ${S}/scripts/config --file ${B}/.config -m STMMAC_ETH
    ${S}/scripts/config --file ${B}/.config -m STMMAC_PLATFORM
    ${S}/scripts/config --file ${B}/.config -m DWMAC_DWC_QOS_ETH
    ${S}/scripts/config --file ${B}/.config -m DWMAC_GENERIC
    ${S}/scripts/config --file ${B}/.config -m DWMAC_STM32
    ${S}/scripts/config --file ${B}/.config -e SMSC_PHY
    ${S}/scripts/config --file ${B}/.config --set-val SMSC_PHY m
    ${S}/scripts/config --file ${B}/.config -m DRM_SII902X
    ${S}/scripts/config --file ${B}/.config -m MOTORCOMM_PHY
}
