package com.kct.bluetooth_demo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.command.BLEBluetoothManager;
import com.kct.command.BluetoothLeKctLXService;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;

public class FirmwareUpgradeFor4Act extends AppCompatActivity {
    private static final String TAG = "FirmwareUpgradeFor4Act";

    private Toast mToast;

    private TextView currentVersionView, serverVersionView, statusMessageView, percentView;
    private ProgressBar mProgressbar;

    private String currentVersion, serverVersion;
    private int braceletType, platformCode;
    private Uri dfuFileUri;

    private BluetoothDevice device;

    private BroadcastReceiver mBroadcastReceiver;
    private ServiceConnection mServiceConnection;
    private BluetoothLeKctLXService mBluetoothLeKctLXService;

    private DfuData mDfuData;

    private boolean isInDFUProcess;
    private boolean dfuTransmitting;
    private boolean dfuErrOccur;


    private class DfuData {
        String fileName;
        private long fileSize;

        private static final int HAL_FLASH_WORD_SIZE = 4;
        private static final int FILE_BUFFER_SIZE = 0x40000;
        private static final int OAD_BLOCK_SIZE = 16;
        private static final int OAD_BUFFER_SIZE = 2 + OAD_BLOCK_SIZE; //

        private int ver;
        /**
         * 固件内部标记的长度
         * 按一个字（Word 4 个字节）算
         * 应该是 文件字节数/字数
         */
        private int len;
        private int romVer;
        private byte[] uid = new byte[4];

        /**
         * 初始数据
         * 取固件文件的前16个字节
         */
        private byte[] initData = new byte[16];
        /**
         * 整个固件文件内容
         */
        private byte[] fileData;
        /**
         * 总的块数
         * 一块占 4 个字，也即 16 个字节
         */
        private int nBlocks;

        private int blockIdx;

        private byte[] oadBuf = new byte[OAD_BUFFER_SIZE];

        DfuData(File file) throws Exception {
            fileName = file.getAbsolutePath();
            fileSize = file.length();

            // load file
            load(new FileInputStream(file));
        }

        DfuData(Uri fileUri) throws Exception {
            fileName = fileUri.toString();
            ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(fileUri, "r");
            fileSize = parcelFileDescriptor.getStatSize();
            parcelFileDescriptor.close();

            // load file
            load(getContentResolver().openInputStream(fileUri));
        }

        private void load(InputStream inputStream) throws Exception {
            if (fileSize > FILE_BUFFER_SIZE) {
                throw new Exception("file too big");
            }
            fileData = new byte[(int) ((fileSize + OAD_BLOCK_SIZE - 1) / OAD_BLOCK_SIZE * OAD_BLOCK_SIZE)];

            try {
                int n, total = 0;
                do {
                    n = inputStream.read(fileData, total, fileData.length - total);
                    if (n > 0) {
                        total += n;
                    }
                } while (n > 0);
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException ignored) {
                    }
                }
            }

            // 准备相关信息
            ver = (fileData[4] & 0xFF) | ((fileData[5] & 0xFF) << 8); // 小端字节序
            len = (fileData[6] & 0xFF) | ((fileData[7] & 0xFF) << 8); // 小端字节序
            romVer = (fileData[14] & 0xFF) | ((fileData[15] & 0xFF) << 8); // 小端字节序
            System.arraycopy(fileData, 8, uid, 0, uid.length); // 将 固件包 的 起始 第 8 字节开始 copy 4 字节到 uid
            nBlocks = len / (OAD_BLOCK_SIZE / HAL_FLASH_WORD_SIZE);

            // 准备第一包数据
            System.arraycopy(fileData, 0, initData, 0, initData.length);

            reset();
        }

        @Override
        public String toString() {
            return fileName + ": fileSize: " + fileSize + ", ver: " + ver + ", len: " + len + ", romVer: " + romVer + Utils.bytesToHex(uid)
                    + ", nBlocks: " + nBlocks;
        }

        private synchronized void incOadBlockIdx() {
            ++blockIdx;
            if (hasNextOadBlock()) {
                prepareOadBlock();
            }
        }

        private synchronized void setOadBlockIdx(int idx) {
            blockIdx = idx;
            if (hasNextOadBlock()) {
                prepareOadBlock();
            }
        }

        private synchronized void prepareOadBlock() {
            oadBuf[0] = (byte) (blockIdx & 0xFF);
            oadBuf[1] = (byte) ((blockIdx >> 8) & 0xFF);
            System.arraycopy(fileData, blockIdx * OAD_BLOCK_SIZE, oadBuf, 2, OAD_BLOCK_SIZE);
        }

        private synchronized boolean hasNextOadBlock() {
            return blockIdx < nBlocks;
        }

        private synchronized byte[] getOadBlock() {
            return oadBuf;
        }

        private synchronized void reset() {
            blockIdx = 0;
            prepareOadBlock();
        }
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firmware_upgrade);

        currentVersionView = findViewById(R.id.device_current_version);
        serverVersionView = findViewById(R.id.server_version);
        statusMessageView = findViewById(R.id.status_message);
        percentView = findViewById(R.id.percent);
        mProgressbar = findViewById(R.id.firmware_progressbar);

        Intent intent = getIntent();
        currentVersion = intent.getStringExtra(FirmwareUpgradeActivity.ACTIVITY_PARAM_KEY_currentVersion);
        serverVersion = intent.getStringExtra(FirmwareUpgradeActivity.ACTIVITY_PARAM_KEY_serverVersion);
        braceletType = intent.getIntExtra(FirmwareUpgradeActivity.ACTIVITY_PARAM_KEY_braceletType, -1);
        platformCode = intent.getIntExtra(FirmwareUpgradeActivity.ACTIVITY_PARAM_KEY_platformCode, -1);
        dfuFileUri = intent.getParcelableExtra(FirmwareUpgradeActivity.ACTIVITY_PARAM_KEY_dfuFileUri);

        if (TextUtils.isEmpty(currentVersion) || (TextUtils.isEmpty(serverVersion) && dfuFileUri == null) || braceletType < 0 || platformCode < 0) {
            showMessage("invalid Activity params");
            return;
        }

        currentVersionView.setText(currentVersion);
        serverVersionView.setText(serverVersion);

        if (KCTBluetoothManager.getInstance().getConnectState() != KCTBluetoothManager.STATE_CONNECTED) {
            showMessage(R.string.device_not_connect);
            return;
        }

        device = KCTBluetoothManager.getInstance().getConnectDevice();
        if (device == null) {
            showMessage("The connected device is null!");
            return;
        }

        EventBus.getDefault().register(this);
        registerReceiver();
        bindService();

        if (dfuFileUri == null) {
            // not select file, download from server
            showMessage("downloading firmware file...");
            // download firmware file
            new DownloadFirmwareFileAsyncTask(this).execute();
        } else {
            ParcelFileDescriptor parcelFileDescriptor = null;
            try {
                parcelFileDescriptor = getContentResolver().openFileDescriptor(dfuFileUri, "r");
                if (parcelFileDescriptor == null) {
                    showMessage("cannot open selected DFU file: " + dfuFileUri);
                    return;
                }
                if (!dfuFileUri.getPath().toLowerCase().endsWith(".bin")) {
                    showMessage("selected DFU file not valid");
                    return;
                }
                long fileSize = parcelFileDescriptor.getStatSize();
                if (fileSize > 1024 * 1024) {
                    showMessage("selected DFU file too big");
                    return;
                }

                // file ready, start DFU directly.
                onDFUFileDownloaded(null, dfuFileUri);
            } catch (Exception e) {
                Log.e(TAG, "check selected file: " + dfuFileUri, e);
                showMessage("check selected file: " + dfuFileUri + ": " + e.getMessage());
                return;
            } finally {
                if (parcelFileDescriptor != null) {
                    try {
                        parcelFileDescriptor.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    long backPressedTime;
    @Override
    public void onBackPressed() {
        if (isInDFUProcess) {
            toast("now is firmware upgrade processing!");
            long now = SystemClock.elapsedRealtime();
            if (now - backPressedTime < 1000) {
                super.onBackPressed();
            } else {
                backPressedTime = now;
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        unregisterReceiver();
        if (mBluetoothLeKctLXService != null) {
            mBluetoothLeKctLXService.disconnect();
        }
        unbindService();
        super.onDestroy();
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEraseFlashBeforeFirmwareUpgradeForBKResponse(Event.DFU.EraseFlashBeforeFirmwareUpgradeForBKResponse ev) {
        if (!ev.success) {
            showMessage("erase device firmware flash failure! cannot upgrade!");
            return;
        }
        showMessage("erase device firmware flash success!");
        onEraseFlashBeforeFirmwareUpgradeDone();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSwitchDeviceToDfuModeDone(MessageEvent ev) {
        if (MessageEvent.OTA.equals(ev.getMessage())) {
            showMessage("switch to DFU mode success");

            // disconnect device first.
            KCTBluetoothManager.getInstance().disConnect_a2d();
            // then reconnect use BluetoothLeKctLXService
            mHandler.execDelayed(new Runnable() {
                @Override
                public void run() {
                    showMessage("reconnecting...");
                    // do connect device
                    // when connected, you should receive ACTION_GATT_CONNECTED broadcast. (see mBroadcastReceiver)
                    mBluetoothLeKctLXService.connect(device.getAddress());
                }
            }, 3000);
        }
    }


    private void onDFUFileDownloaded(String dfuFilePath, Uri fileUri) {
        if (fileUri == null && TextUtils.isEmpty(dfuFilePath)) {
            showMessage("no DFU file!");
            return;
        }
        try {
            if (fileUri != null) {
                mDfuData = new DfuData(fileUri);
            } else if (!TextUtils.isEmpty(dfuFilePath)) {
                mDfuData = new DfuData(new File(dfuFilePath));
            } else {
                Log.e(TAG, "no DFU file");
                showMessage("no DFU file");
            }
        } catch (Exception e) {
            Log.e(TAG, "onDFUFileDownloaded DfuData(" + dfuFilePath + ", " + fileUri + ")", e);
            showMessage("load DFU file (" + (fileUri == null ? dfuFilePath : fileUri) + ") failure: " + e.getMessage());
            return;
        }

        if (mBluetoothLeKctLXService != null) {
            // service already OK
            // erase defile firmware flash
            eraseFlashBeforeDFU();
        }
    }

    private void eraseFlashBeforeDFU() {
        if (KCTBluetoothManager.getInstance().getConnectState() != KCTBluetoothManager.STATE_CONNECTED) {
            showMessage(R.string.device_not_connect);
            return;
        }
        isInDFUProcess = true;
        showMessage("erasing device firmware flash...");
        KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_sendEraseFlashBeforeFirmwareUpgradeForBK_pack());
        // start timer 5s
        mHandler.execDelayed(mEraseFlashBeforeFirmwareUpgradeTimer, 5000);
        // then wait Event.EraseFlashBeforeFirmwareUpgradeForBKResponse event (see onEraseFlashBeforeFirmwareUpgradeForBKResponse)
        // or timeout (see mEraseFlashBeforeFirmwareUpgradeTimer)
    }

    private Runnable mEraseFlashBeforeFirmwareUpgradeTimer = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "mEraseFlashBeforeFirmwareUpgradeTimer timeout");
            onEraseFlashBeforeFirmwareUpgradeDone();
        }
    };

    private void onEraseFlashBeforeFirmwareUpgradeDone() {
        mHandler.cancel(mEraseFlashBeforeFirmwareUpgradeTimer);

        showMessage("switch to DFU mode...");
        // switch device to DUF mode to upgrade
        switchDeviceToDfuMode();
    }

    private void switchDeviceToDfuMode() {
        if (KCTBluetoothManager.getInstance().getConnectState() != KCTBluetoothManager.STATE_CONNECTED) {
            showMessage(R.string.device_not_connect);
            return;
        }
        showMessage("switching device to DFU mode...");
        KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_sendFirmwareUpdate_pack());
        // then wait MessageEvent(MessageEvent.OTA) event (see onSwitchDeviceToDfuModeDone)
    }


    private void writeOadBlock() {
        if (mBluetoothLeKctLXService != null) {
            Log.v(TAG, "writeOTABlock block total: " + mDfuData.nBlocks + " idx: " + mDfuData.blockIdx + " percent: " + (mDfuData.blockIdx * 100) / mDfuData.nBlocks);
            mBluetoothLeKctLXService.writeOTABlock(mDfuData.getOadBlock());
        }
    }

    int writeOadBlockDelayMillis = 2;
    boolean readyToWriteDfuData;
    private boolean canWriteOadBlock;
    private class OadWriteThread extends Thread {
        @Override
        public void run() {
            Log.d(TAG, "OadWriteThread begin");
            while (dfuTransmitting) {
                try {
                    Thread.sleep(10);
                } catch (Exception ignored) {
                }

                for (int i = 0; i < 4 & dfuTransmitting; ++i) {
                    if (!readyToWriteDfuData) {
                        break;
                    }
                    if (canWriteOadBlock) {
                        canWriteOadBlock = false;
                        if (mDfuData.hasNextOadBlock()) {
                            writeOadBlock();
                        } else {
                            return;
                        }
                    }

                    if (writeOadBlockDelayMillis > 2) {
                        writeOadBlockDelayMillis--;
                    }
                    try {
                        Thread.sleep(writeOadBlockDelayMillis);
                    } catch (Exception ignored) {
                    }
                }
            }
            Log.d(TAG, "OadWriteThread end");
        }
    }

    private void updateProgress(int progress) {
        if (progress < mProgressbar.getProgress()) {
            return;
        }
        mProgressbar.setProgress(progress);
        percentView.setText(String.valueOf(progress));
    }


    private void registerReceiver() {
        if (mBroadcastReceiver != null) {
            return;
        }
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) {
                    return;
                }

                switch (action) {
                    case BluetoothLeKctLXService.ACTION_GATT_CONNECTED:
                        mHandler.execDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (mBluetoothLeKctLXService != null) {
                                    mBluetoothLeKctLXService.testDiscoverService();
                                }
                            }
                        }, 200);
                        break;

                    case BluetoothLeKctLXService.ACTION_GATT_DISCONNECTED:
                        isInDFUProcess = false;
                        dfuTransmitting = false;
                        if (dfuErrOccur) {
                            // keep statusMessageView text
                            break;
                        }
                        if (readyToWriteDfuData && !mDfuData.hasNextOadBlock()) {
                            showMessage("firmware upgrade success");
                            break;
                        }
                        showMessage("device disconnected, firmware upgrade failure!");
                        break;

                    case BluetoothLeKctLXService.ACTION_GATT_SERVICES_DISCOVERED:
                        if (mBluetoothLeKctLXService != null) {
                            List<BluetoothGattService> services = mBluetoothLeKctLXService.getSupportedGattServices();
                            BluetoothGattCharacteristic identfyCharacteristic = null;
                            BluetoothGattCharacteristic blockCharacteristic = null;
                            if (services != null) {
                                for (BluetoothGattService service : services) {
                                    if (service.getUuid().equals(BluetoothLeKctLXService.UUID_OTA_SERVICE)) {
                                        identfyCharacteristic = service.getCharacteristic(BluetoothLeKctLXService.UUID_IDENTFY);
                                        blockCharacteristic = service.getCharacteristic(BluetoothLeKctLXService.UUID_BLOCK);
                                        break;
                                    }
                                }
                            }
                            if (identfyCharacteristic == null || blockCharacteristic == null) {
                                dfuErrOccur = true;
                                showMessage("connect device error: not fond correct service");
                                mBluetoothLeKctLXService.disconnect();
                                return;
                            }
                            mBluetoothLeKctLXService.setCharacteristicNotification(identfyCharacteristic, true);
                            mBluetoothLeKctLXService.setCharacteristicNotification(blockCharacteristic, true);

                            showMessage("connect success, ready to transmit file.");
                            mDfuData.reset();
                            dfuTransmitting = true;
                            mHandler.execDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(TAG, "writeOTAIdentfy() " + Utils.bytesToHex(mDfuData.initData));
                                    mBluetoothLeKctLXService.writeOTAIdentfy(mDfuData.initData);

                                    writeOadBlockDelayMillis = 2;
                                    canWriteOadBlock = true;
                                    OadWriteThread oadWriteThread = new OadWriteThread();
                                    oadWriteThread.start();
                                }
                            }, 2000);
                        }
                        break;

                    case BluetoothLeKctLXService.ACTION_GATT_SERVICES_DISCOVERED_FAIL:
                        if (mBluetoothLeKctLXService != null) {
                            mBluetoothLeKctLXService.disconnect();
                        }
                        break;

                    case BluetoothLeKctLXService.ACTION_NOTIFY_INDEXTEN:
                    case BluetoothLeKctLXService.ACTION_DATA_WRITE_FAIL:
                        canWriteOadBlock = true;
                        break;

                    case BluetoothLeKctLXService.ACTION_NOTIFY_INDEXELE:
                    case BluetoothLeKctLXService.ACTION_DATA_WRITE_SUCCESS:
                        // block write ok
                        mDfuData.incOadBlockIdx();
                        canWriteOadBlock = true;
                        updateProgress((mDfuData.blockIdx * 100) / mDfuData.nBlocks);
                        if (!mDfuData.hasNextOadBlock()) {
                            // transmit done
                            showMessage("transmit done");
                        }
                        break;

                    case BluetoothLeKctLXService.ACTION_DATA_AVAILABLE: {
                        String strData = intent.getStringExtra(BluetoothLeKctLXService.EXTRA_DATA);
                        byte[] value = intent.getByteArrayExtra(BluetoothLeKctLXService.EXTRA_DATA_BYTE);
                        String uuidStr = intent.getStringExtra(BluetoothLeKctLXService.EXTRA_UUID);
                        if (!TextUtils.isEmpty(uuidStr)) {
                            Log.d(TAG, "Broadcast: ACTION_DATA_AVAILABLE: " + strData + ", UUID: " + uuidStr + " value: " + Utils.bytesToHex(value));
                            if (uuidStr.equals(BluetoothLeKctLXService.UUID_BLOCK.toString())) {
                                if (value != null && value.length >= 2) {
                                    // require some block
                                    int blockReq = (value[0] & 0xFF) | ((value[1] & 0xFF) << 8); // 小端字节序
                                    mDfuData.setOadBlockIdx(blockReq);
                                    updateProgress((mDfuData.blockIdx * 100) / mDfuData.nBlocks);
                                    if (mDfuData.hasNextOadBlock()) {
                                        if (blockReq == 0 && !readyToWriteDfuData) {
                                            showMessage("transmitting...");
                                            readyToWriteDfuData = true;
                                        } else {
                                            if (writeOadBlockDelayMillis < 50) {
                                                writeOadBlockDelayMillis = 50;
                                            }
                                        }
                                    } else {
                                        // transmit done
                                        showMessage("transmit done");
                                    }
                                }
                            } else if (uuidStr.equals(BluetoothLeKctLXService.UUID_IDENTFY.toString())) {

                            }
                        } else {
                            Log.d(TAG, "Broadcast: ACTION_DATA_AVAILABLE: " + strData);
                        }
                    }
                    break;

                    case BluetoothLeKctLXService.ACTION_NOTIFY_SUCCESS:
                        break;
                    case BluetoothLeKctLXService.ACTION_NOTIFY_FAIL:
                        break;
                }

            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_NOTIFY_INDEXTEN);
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_NOTIFY_INDEXELE);
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_DATA_WRITE_FAIL);
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_DATA_WRITE_SUCCESS);
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_GATT_SERVICES_DISCOVERED_FAIL);
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_NOTIFY_SUCCESS);
        intentFilter.addAction(BluetoothLeKctLXService.ACTION_NOTIFY_FAIL);
        registerReceiver(mBroadcastReceiver, intentFilter);
    }

    private void unregisterReceiver() {
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
    }


    private void bindService() {
        if (mServiceConnection != null) {
            return;
        }
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "BluetoothLeKctLXService connected");
                mBluetoothLeKctLXService = ((BluetoothLeKctLXService.LocalBinder) service).getService();
                if (!mBluetoothLeKctLXService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    showMessage("initialize service failure, back to retry.");
                    unbindService();
                } else {
                    Log.i(TAG, "BluetoothLeKctLXService ok");
                    if (mDfuData != null) {
                        // DFU data already OK
                        // erase defile firmware flash
                        eraseFlashBeforeDFU();
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "BluetoothLeKctLXService disconnected");
                mBluetoothLeKctLXService.clearQueue();
                mBluetoothLeKctLXService = null;
            }
        };
        bindService(new Intent(this, BluetoothLeKctLXService.class), mServiceConnection, BIND_AUTO_CREATE);
    }

    private void unbindService() {
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
            mServiceConnection = null;
        }
    }


    private void showMessage(final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusMessageView.setText(text);
            }
        });
    }

    private void showMessage(@StringRes final int strResId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusMessageView.setText(strResId);
            }
        });
    }

    private void toast(CharSequence text) {
        if (mToast == null) {
            mToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(text);
        }
        mToast.show();
    }

    private static class DownloadFirmwareFileAsyncTask extends AsyncTask<Void, Void, Object[]> {
        private WeakReference<FirmwareUpgradeFor4Act> mA;

        DownloadFirmwareFileAsyncTask(FirmwareUpgradeFor4Act a) {
            mA = new WeakReference<>(a);
        }

        /**
         * @param voids
         * @return { success, DFU_filepath, error_message }
         */
        @Override
        protected Object[] doInBackground(Void... voids) {
            FirmwareUpgradeFor4Act a = mA.get();
            if (a == null) {
                return new Object[]{false, "", ""};
            }

            try {
                // download
                String filePath = KCTBluetoothManager.getInstance().getDFU_dataForBK(a.platformCode);
                return new Object[]{true, filePath, ""};
            } catch (Exception e) {
                e.printStackTrace();
                return new Object[]{false, "", e.getMessage()};
            }
        }

        @Override
        protected void onPostExecute(Object[] downloadResult) {
            FirmwareUpgradeFor4Act a = mA.get();
            if (a == null) {
                return;
            }

            if ((boolean) downloadResult[0]) {
                a.showMessage("download DFU file success. file: " + downloadResult[1]);
                // erase device flash
                a.onDFUFileDownloaded(downloadResult[1].toString(), null);
            } else {
                a.showMessage("download DFU file failure: " + downloadResult[2]);
            }
        }
    }

    private Handler mHandler = new Handler(this);
    private static class Handler extends android.os.Handler {
        private static final int EXEC_RUNNABLE = 0;

        private WeakReference<FirmwareUpgradeFor4Act> mA;
        private Handler(FirmwareUpgradeFor4Act a) {
            mA = new WeakReference<>(a);
        }

        @Override
        public void handleMessage(Message msg) {
            FirmwareUpgradeFor4Act a = mA.get();
            if (a == null) {
                return;
            }

            switch (msg.what) {
                case EXEC_RUNNABLE:
                    ((Runnable) msg.obj).run();
                    break;
            }
        }

        public void exec(Runnable task) {
            obtainMessage(EXEC_RUNNABLE, task).sendToTarget();
        }

        public void execDelayed(Runnable task, long delayMillis) {
            sendMessageDelayed(obtainMessage(EXEC_RUNNABLE, task), delayMillis);
        }

        public void cancel(Runnable task) {
            removeMessages(EXEC_RUNNABLE, task);
        }
    }
}
