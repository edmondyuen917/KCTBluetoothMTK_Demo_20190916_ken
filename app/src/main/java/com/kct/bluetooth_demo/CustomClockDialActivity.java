package com.kct.bluetooth_demo;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.bluetooth.bean.BluetoothLeDevice;
import com.kct.bluetooth.callback.IConnectListener;
import com.kct.command.BLEBluetoothManager;
import com.kct.command.IReceiveListener;
import com.kct.command.KCTBluetoothCommand;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class CustomClockDialActivity extends AppCompatActivity {
    private static final String TAG = CustomClockDialActivity.class.getSimpleName();

    private static final int FLASH_DATA_TYPE = 3; // clock dial type is 3

    private static final int FILE_SELECT_CODE_FOR_CLOCK_DIAL = 1;
    private static final int FILE_SELECT_CODE_FOR_CLOCK_DIAL_BACKGROUND = 2;

    private Toast mToast;

    TextView messageTextView;
    Button getCurrentStatusButton;
    Button getDialListButton;
    Button getCompatButton;
    Button switchDialButton;
    Button deleteDialButton;
    Button setDialBackgroundButton;
    Button pushDialButton;

    private Event.CustomClockDial.InquireCurrentStatusResponse mCurrentStatus;
    private Event.CustomClockDial.InquireCurrentListResponse mCurrentList;
    private Event.CustomClockDial.InquireCompatInfoResponse mCompatInfo;

    private int flash_pack_size = 179;
    private byte[] flash_data;
    private int flash_pack_idx_of_write_done;

    ////////////////////////////////////
    // 计算传输速率用
    int currentSentSize;
    int totalSentSize;
    long startSendTime;
    long lastPercentShowTime;
    ////////////////////////////////////

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_clock_dial);

        messageTextView = findViewById(R.id.message);
        getCurrentStatusButton = findViewById(R.id.get_status);
        getDialListButton = findViewById(R.id.get_dial_list);
        getCompatButton = findViewById(R.id.get_compat_info);
        switchDialButton = findViewById(R.id.switch_dial);
        deleteDialButton = findViewById(R.id.del_dial);
        setDialBackgroundButton = findViewById(R.id.set_background);
        pushDialButton = findViewById(R.id.push_dial);

        EventBus.getDefault().register(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case FILE_SELECT_CODE_FOR_CLOCK_DIAL:
            case FILE_SELECT_CODE_FOR_CLOCK_DIAL_BACKGROUND:
                if (resultCode == RESULT_OK) {
                    // read selected file
                    InputStream inputStream = null;
                    try {
                        Uri selectedFileUri = data.getData();
                        Log.i(TAG, "selectedFileUri: " + selectedFileUri);
                        if (selectedFileUri == null) {
                            showMessage("no file select");
                            return;
                        }
                        ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(selectedFileUri, "r");
                        if (parcelFileDescriptor == null) {
                            showMessage("cannot open selected file (" + selectedFileUri + ")");
                            return;
                        }
                        long fileSize = parcelFileDescriptor.getStatSize();
                        if (fileSize > 1024 * 1024 * 2 /* 2M */) {
                            showMessage("file too big!");
                            return;
                        }
                        inputStream = new ParcelFileDescriptor.AutoCloseInputStream(parcelFileDescriptor);

                        byte[] buf = new byte[(int) fileSize];
                        int n, total = 0;
                        while ((n = inputStream.read(buf, total, buf.length - total)) > 0) {
                            total += n;
                        }

                        flash_data = buf;
                    } catch (Exception e) {
                        Log.e(TAG, "select " + (requestCode == FILE_SELECT_CODE_FOR_CLOCK_DIAL ? "clock dial" : "clock dial background") + " file error", e);
                        showMessage("select " + (requestCode == FILE_SELECT_CODE_FOR_CLOCK_DIAL ? "clock dial" : "clock dial background") + " file error: " + e.getMessage());
                        return;
                    } finally {
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    int mtu = KCTBluetoothManager.getInstance().getBluetoothGattMtu();
                    if (mtu - 3 > 8 + 5 + 8 + flash_pack_size) {
                        flash_pack_size = mtu - 3 - 8 - 5 - 8;
                    }

                    byte[] cmdData;
                    if (requestCode == FILE_SELECT_CODE_FOR_CLOCK_DIAL) {
                        cmdData = BLEBluetoothManager.BLE_COMMAND_a2d_customClockDialRequirePushDial(0 /* clock room */, flash_data.length);
                    } else {
                        cmdData = BLEBluetoothManager.BLE_COMMAND_a2d_customClockDialRequireSetBackground(flash_data.length, mCompatInfo.lcdWidth, mCompatInfo.lcdHeight, 0, 0);
                    }
                    KCTBluetoothManager.getInstance().sendCommand_a2d(cmdData);
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.get_status:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] cmdData = BLEBluetoothManager.BLE_COMMAND_a2d_customClockDialInquireCurrentStatus();
                    KCTBluetoothManager.getInstance().sendCommand_a2d(cmdData);
                }
                break;
            case R.id.get_dial_list:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] cmdData = BLEBluetoothManager.BLE_COMMAND_a2d_customClockDialInquireCurrentList();
                    KCTBluetoothManager.getInstance().sendCommand_a2d(cmdData);
                }
                break;
            case R.id.get_compat_info:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] cmdData = BLEBluetoothManager.BLE_COMMAND_a2d_customClockDialInquireCompatInfo();
                    KCTBluetoothManager.getInstance().sendCommand_a2d(cmdData);
                }
                break;
            case R.id.switch_dial:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    int switchTo = 0;
                    if (mCurrentList.numberOfCustomDialRoom > 0 && mCurrentList.numberOfUsedCustomDialRoom > 0) {
                        // 已有自定义表盘
                        for (int id : mCurrentList.customList) {
                            if (id != mCurrentStatus.currentDialId && id != 0) {
                                switchTo = id;
                            }
                        }
                    } else {
                        for (int id : mCurrentList.presetList) {
                            if (id != mCurrentStatus.currentDialId && id != 0) {
                                switchTo = id;
                            }
                        }
                    }
                    if (switchTo == 0) {
                        showMessage("there is no dial can switch to!");
                    } else {
                        byte[] cmdData = BLEBluetoothManager.BLE_COMMAND_a2d_customClockDialSwitchTo(switchTo);
                        KCTBluetoothManager.getInstance().sendCommand_a2d(cmdData);
                    }
                }
                break;
            case R.id.del_dial:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    int deleteId = 0;
                    if (mCurrentList.numberOfCustomDialRoom > 0 && mCurrentList.numberOfUsedCustomDialRoom > 0) {
                        // 已有自定义表盘
                        for (int id : mCurrentList.customList) {
                            if (id != mCurrentStatus.currentDialId && id != 0) {
                                deleteId = id;
                            }
                        }
                    }
                    if (deleteId == 0) {
                        showMessage("there is no dial can delete!");
                    } else {
                        byte[] cmdData = BLEBluetoothManager.BLE_COMMAND_a2d_customClockDialDelete(deleteId);
                        KCTBluetoothManager.getInstance().sendCommand_a2d(cmdData);
                    }
                }
                break;
            case R.id.set_background:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    // 先准备好表盘背景图数据。具体的宽、高、色彩模型，可以使用 BLE_COMMAND_a2d_customClockDialInquireCompatInfo 获取。
                    // 使用 BLE_COMMAND_a2d_customClockDialRequireSetBackground 请求设置表盘背景
                    // 然后使用 BLE_COMMAND_a2d_setFlashCommand_pack 请求开始传输表盘背景数据
                    // 然后使用 BLE_COMMAND_a2d_sendFlashData_pack 传输具体的表盘背景数据

                    // 先选择背景图文件
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    PackageManager packageManager = getPackageManager();
                    List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
                    boolean isIntentSafe = activities.size() > 0;
                    if (isIntentSafe) {
                        startActivityForResult(intent, FILE_SELECT_CODE_FOR_CLOCK_DIAL_BACKGROUND);
                    } else {
                        toast("install a file manager app first");
                    }
                }
                break;
            case R.id.push_dial:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    // 使用 BLE_COMMAND_a2d_customClockDialInquireCurrentStatus 请求是否支持表盘推送
                    // 使用 BLE_COMMAND_a2d_customClockDialInquireCurrentList 列出可硬的自定义表盘盘位（customList 数组的索引下标号就是表盘盘位）
                    // 先准备好表盘文件
                    // 使用 BLE_COMMAND_a2d_customClockDialRequirePushDial 请求设置表盘背景
                    // 然后使用 BLE_COMMAND_a2d_setFlashCommand_pack 请求开始传输表盘背景数据
                    // 然后使用 BLE_COMMAND_a2d_sendFlashData_pack 传输具体的表盘背景数据

                    // 先选择表盘文件
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    PackageManager packageManager = getPackageManager();
                    List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
                    boolean isIntentSafe = activities.size() > 0;
                    if (isIntentSafe) {
                        startActivityForResult(intent, FILE_SELECT_CODE_FOR_CLOCK_DIAL);
                    } else {
                        toast("install a file manager app first");
                    }
                }
                break;

            case R.id.clear_message:
                messageTextView.setText("");
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConnectionState(MessageEvent ev) {
        switch (ev.getMessage()) {
            case MessageEvent.CONNECT_STATE:
                if ((int) ev.getObject() == KCTBluetoothManager.STATE_CONNECT_FAIL) {
                    getCurrentStatusButton.setEnabled(false);
                    getDialListButton.setEnabled(false);
                    getCompatButton.setEnabled(false);
                    switchDialButton.setEnabled(false);
                    deleteDialButton.setEnabled(false);
                    setDialBackgroundButton.setEnabled(false);
                    pushDialButton.setEnabled(false);
                }
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCustomClockDialInquireCurrentStatusResponse(Event.CustomClockDial.InquireCurrentStatusResponse ev) {
        mCurrentStatus = ev;
        showMessage("InquireCurrentStatusResponse: \nsupportCustom: " + ev.supportCustom + ", \nsupportSwitchDial: " + ev.supportSwitchDial + ", \nsupportSetBackground: " + ev.supportSetBackground + ", \ncurrentDialId: 0x" + Integer.toHexString(ev.currentDialId));
        getDialListButton.setEnabled(mCurrentStatus.supportCustom);
        getCompatButton.setEnabled(mCurrentStatus.supportCustom);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCustomClockDialInquireCurrentListResponse(Event.CustomClockDial.InquireCurrentListResponse ev) {
        mCurrentList = ev;

        String presetListStr = "[";
        String sep = "";
        for (int id : mCurrentList.presetList) {
            presetListStr += sep + "0x" + Integer.toHexString(id);
            sep = ",";
        }
        presetListStr += "]";
        sep = "";
        String customListStr = "[";
        for (int id : mCurrentList.customList) {
            customListStr += sep + "0x" + Integer.toHexString(id);
            sep = ",";
        }
        customListStr += "]";
        showMessage("InquireCurrentStatusResponse: \nnumberOfCustomDialRoom: " + mCurrentList.numberOfCustomDialRoom + ", \nnumberOfPresetDialRoom: " + mCurrentList.numberOfPresetDialRoom + ", \nnumberOfUsedCustomDialRoom: " + mCurrentList.numberOfUsedCustomDialRoom + ", \npresetList: " + presetListStr + ", \ncustomList: " + customListStr);

        switchDialButton.setEnabled(mCurrentStatus.supportSwitchDial && (mCurrentList.presetList.length > 0 || mCurrentList.numberOfUsedCustomDialRoom > 0));
        deleteDialButton.setEnabled(mCurrentStatus.supportCustom && mCurrentList.numberOfUsedCustomDialRoom > 0);
        setDialBackgroundButton.setEnabled(mCurrentStatus.supportSetBackground);
        pushDialButton.setEnabled(mCurrentStatus.supportCustom);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCustomClockDialInquireCompatInfoResponse(Event.CustomClockDial.InquireCompatInfoResponse ev) {
        mCompatInfo = ev;

        showMessage("InquireCompatInfoResponse: \nmagic: " + Integer.toHexString(mCompatInfo.magic) + ", \nsupportVersion: " + mCompatInfo.supportVersion + ", \nlcdWH: " + mCompatInfo.lcdWidth + "x" + mCompatInfo.lcdHeight + ", \ncolorMode: 0x" + Integer.toHexString(mCompatInfo.colorMode) + ", \nfreeSpace: " + mCompatInfo.freeSpace);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCustomClockDialSwitchToResponse(Event.CustomClockDial.SwitchToResponse ev) {
        showMessage("SwitchTo Response: " + ev.status);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCustomClockDialDeleteResponse(Event.CustomClockDial.DeleteResponse ev) {
        showMessage("Delete Response: " + ev.status);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCustomClockDialRequireSetBackgroundResponse(Event.CustomClockDial.RequireSetBackgroundResponse ev) {
        showMessage("RequireSetBackgroundResponse: " + ev.status);
        if (ev.status == 1) { // 请求成功
            // 请求开始传输flash数据
            byte[] cmdData = BLEBluetoothManager.BLE_COMMAND_a2d_setFlashCommand_pack(3 /* clock dial type is 3 */, 1);
            KCTBluetoothManager.getInstance().sendCommand_a2d(cmdData);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCustomClockDialRequirePushDialResponse(Event.CustomClockDial.RequirePushDialResponse ev) {
        showMessage("RequirePushDialResponse: " + ev.status);
        if (ev.status == 1) { // 请求成功
            // 请求开始传输flash数据
            byte[] cmdData = BLEBluetoothManager.BLE_COMMAND_a2d_setFlashCommand_pack(FLASH_DATA_TYPE, 1);
            KCTBluetoothManager.getInstance().sendCommand_a2d(cmdData);
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFlashCommandRequireWriteResponse(Event.FlashCommand.RequireWriteResponse ev) {
        if (ev.dataType == FLASH_DATA_TYPE) {
            if (ev.success) {
                // 可以开始传输表盘数据了
                flash_pack_idx_of_write_done = 0;
                startSendTime = SystemClock.elapsedRealtime(); // 计算速度有用到
                sendFlash_data((flash_data.length + (flash_pack_size - 1)) / flash_pack_size, flash_pack_idx_of_write_done + 1);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFlashCommandWriteResponse(Event.FlashCommand.WriteResponse ev) {
        if (ev.success) {
            long now = SystemClock.elapsedRealtime();
            int pktSize = Math.min(flash_pack_size, flash_data.length - ev.packIndex * flash_pack_size);
            currentSentSize += pktSize;
            totalSentSize += pktSize;
            long diffTime = now - lastPercentShowTime;
            if (diffTime >= 1000) {
                float speed = currentSentSize * 1.0f / (diffTime / 1000.0f);
                float avgSpeed = totalSentSize * 1.0f / ((now - startSendTime) / 1000.0f);
                showMessage(String.format("total %d, sent %d. sped %.02f, avg %.02f", ev.packSum, ev.packIndex, speed, avgSpeed));
                lastPercentShowTime = now;
                currentSentSize = 0;
            }

            if (ev.packIndex > flash_pack_idx_of_write_done) {
                ++flash_pack_idx_of_write_done;
                sendFlash_data(ev.packSum, flash_pack_idx_of_write_done + 1);
            }
        } else {
            showMessage("flash write response: packSum: " + ev.packSum + ", packIndex: " + ev.packIndex + ", success: " + ev.success);
        }
    }

    private void showMessage(final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messageTextView.append(text);
                messageTextView.append("\n");
            }
        });
    }

    private void sendFlash_data(int pack_Sum, int pack_index) {
        if (flash_data == null) {
            return;
        }
        Log.e(TAG, "pack_Sum = " + pack_Sum + " ; pack_index = " + pack_index);

        // pack_index 从 1 开始，最后一个发成功后，调用到这里是，pack_index 应该是 pack_Sum + 1

        int lenSent = (pack_index - 1) * flash_pack_size;
        int lenPack = Math.min(flash_pack_size, flash_data.length - lenSent);
        if (lenPack > 0) {
            byte[] value = new byte[lenPack];
            System.arraycopy(flash_data, lenSent, value, 0, value.length);
            byte[] cmdData = BLEBluetoothManager.BLE_COMMAND_a2d_sendFlashData_pack(pack_Sum, pack_index, value);
            KCTBluetoothManager.getInstance().sendCommand_a2d(cmdData);
            //showMessage("FLASH发送:" + "\n" + "总包数 = " + pack_Sum + "\n" + "当前包数 = " + pack_index);
        } else {
            showMessage("FLASH发送: 完成");
            float elapsedTime = (SystemClock.elapsedRealtime() - startSendTime) / 1000f;
            showMessage(String.format("用时 %.02f 秒，传输了 %d 字节，平均速率: %.02f", elapsedTime, flash_data.length, flash_data.length / elapsedTime));
            flash_data = null;
        }
    }

    private void toast(CharSequence text) {
        if (mToast == null) {
            mToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(text);
        }
        mToast.show();
    }
}
