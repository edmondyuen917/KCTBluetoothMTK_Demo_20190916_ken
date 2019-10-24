package com.kct.bluetooth_demo;

public class Event {

    public static class FlashCommand {
        public static class RequireWriteResponse {
            /**
             * 数据类型
             * 0: AGPS
             * 1: UI
             * 2: 字库
             * 3. 表盘
             */
            public final int dataType;
            /**
             * 数据版本
             */
            public final int version;
            /**
             * 是否成功
             */
            public final boolean success;

            public RequireWriteResponse(int dataType, int version, boolean success) {
                this.dataType = dataType;
                this.version = version;
                this.success = success;
            }
        }

        public static class WriteResponse {
            /**
             * 总包数
             */
            public final int packSum;
            /**
             * 当前包号
             */
            public final int packIndex;
            /**
             * 是否成功
             */
            public final boolean success;

            public WriteResponse(int packSum, int packIndex, boolean success) {
                this.packSum = packSum;
                this.packIndex = packIndex;
                this.success = success;
            }
        }

        public static class InquireDataVersionResponse {
            /**
             * 数据类型
             * 0: AGPS
             * 1: UI
             * 2: 字库
             * 3. 表盘
             */
            public final int dataType;
            /**
             * 数据版本
             */
            public final int version;

            public InquireDataVersionResponse(int dataType, int version) {
                this.dataType = dataType;
                this.version = version;
            }
        }
    }

    public static class CustomClockDial {
        public static class InquireCurrentStatusResponse {
            /**
             * 是否支持自定义表盘
             */
            public final boolean supportCustom;
            /**
             * 是否支持表盘切换
             */
            public final boolean supportSwitchDial;
            /**
             * 是否支持设置背景图
             */
            public final boolean supportSetBackground;
            /**
             * 当前显示的表盘ID
             */
            public final int currentDialId;
            public InquireCurrentStatusResponse(boolean supportCustom, boolean supportSwitchDial, boolean supportSetBackground, int currentDialId) {
                this.supportCustom = supportCustom;
                this.supportSwitchDial = supportSwitchDial;
                this.supportSetBackground = supportSetBackground;
                this.currentDialId = currentDialId;
            }
        }

        public static class InquireCurrentListResponse {
            /**
             * 自定义表盘盘位总数
             */
            public final int numberOfCustomDialRoom;
            /**
             * 预置表盘盘位总数
             */
            public final int numberOfPresetDialRoom;
            /**
             * 已使用的自定义表盘盘位数
             */
            public final int numberOfUsedCustomDialRoom;
            /**
             * 预置表盘列表
             */
            public final int[] presetList;
            /**
             * 自定义表盘列表。按表盘盘位依次列出。表盘盘位未使用的，以 0 填充。
             */
            public final int[] customList;

            public InquireCurrentListResponse(int numberOfCustomDialRoom, int numberOfPresetDialRoom, int numberOfUsedCustomDialRoom, int[] presetList, int[] customList) {
                this.numberOfCustomDialRoom = numberOfCustomDialRoom;
                this.numberOfPresetDialRoom = numberOfPresetDialRoom;
                this.numberOfUsedCustomDialRoom = numberOfUsedCustomDialRoom;
                this.presetList = presetList;
                this.customList = customList;
            }
        }

        public static class InquireCompatInfoResponse {
            /**
             * 标识符
             */
            public final int magic;
            /**
             * 支持自定义表盘最高版本
             */
            public final int supportVersion;
            /**
             * 表盘LCD显示宽度
             */
            public final int lcdWidth;
            /**
             * 表盘LCD显示高度
             */
            public final int lcdHeight;
            /**
             * 设备支持的色彩模型
             */
            public final int colorMode;
            /**
             * 设备剩余空间大小
             */
            public final long freeSpace;

            public InquireCompatInfoResponse(int magic, int supportVersion, int lcdWidth, int lcdHeight, int colorMode, long freeSpace) {
                this.magic = magic;
                this.supportVersion = supportVersion;
                this.lcdWidth = lcdWidth;
                this.lcdHeight = lcdHeight;
                this.colorMode = colorMode;
                this.freeSpace = freeSpace;
            }
        }

        public static class SwitchToResponse {
            /**
             * 状态
             */
            public final int status;
            public SwitchToResponse(int status) {
                this.status = status;
            }
        }

        public static class DeleteResponse {
            /**
             * 状态
             */
            public final int status;
            public DeleteResponse(int status) {
                this.status = status;
            }
        }

        public static class RequireSetBackgroundResponse {
            /**
             * 状态
             */
            public final int status;
            public RequireSetBackgroundResponse(int status) {
                this.status = status;
            }
        }

        public static class RequirePushDialResponse {
            /**
             * 状态
             */
            public final int status;
            public RequirePushDialResponse(int status) {
                this.status = status;
            }
        }
    }

    public static class DFU {
        public static class EraseFlashBeforeFirmwareUpgradeForBKResponse {
            public final boolean success;
            public EraseFlashBeforeFirmwareUpgradeForBKResponse(boolean success) {
                this.success = success;
            }
        }
    }
}
