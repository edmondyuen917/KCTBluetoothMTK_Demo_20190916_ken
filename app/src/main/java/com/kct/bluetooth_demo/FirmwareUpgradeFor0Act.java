package com.kct.bluetooth_demo;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
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
import com.kct.bluetooth.KCTDFUServiceController;
import com.kct.bluetooth.bean.BluetoothLeDevice;
import com.kct.bluetooth.callback.IConnectListener;
import com.kct.bluetooth.callback.IDFUProgressCallback;
import com.kct.command.BLEBluetoothManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.Locale;

public class FirmwareUpgradeFor0Act extends AppCompatActivity {
    private static final String TAG = "FirmwareUpgradeFor0Act";

    private Toast mToast;

    private TextView currentVersionView, serverVersionView, statusMessageView, percentView;
    private ProgressBar mProgressbar;

    private String currentVersion, serverVersion;
    private int braceletType, platformCode;
    private Uri dfuFileUri;

    private BluetoothDevice device;
    private static final String DFU_FILE_PATH = Environment.getExternalStorageDirectory() + "/FunDoSDK/dfu.zip";

    private boolean isInDFU;
    private KCTDFUServiceController dfuServiceController;

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
        KCTBluetoothManager.getInstance().registerListener(iConnectListener);
        KCTBluetoothManager.getInstance().registerDFUProgressListener(mIDFUProgressCallback);

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
                if (!dfuFileUri.getPath().toLowerCase().endsWith(".zip")) {
                    showMessage("selected DFU file (" + dfuFileUri + ") not valid");
                    return;
                }
                long fileSize = parcelFileDescriptor.getStatSize();
                if (fileSize > 1024 * 1024) {
                    showMessage("selected DFU file (" + dfuFileUri + ") too big");
                    return;
                }

                // file ready, start DFU directly.
                switchDeviceToDfuMode();
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

    private long backPressedTime;
    @Override
    public void onBackPressed() {
        if (isInDFU) {
            toast("in DFU, don't quit!");
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
        if (dfuServiceController != null) {
            dfuServiceController.abort();
        }
        KCTBluetoothManager.getInstance().unregisterDFUProgressListener();
        KCTBluetoothManager.getInstance().unregisterListener(iConnectListener);
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent ev) {
        Log.d(TAG, "MessageEvent: " + ev.getMessage());
        if (MessageEvent.OTA.equals(ev.getMessage())) {
            // switch to DFU mode successful
            // rescan device (device name contains "DfuTarg"), then call upgrade_DFU() to start.
            // waiting disconnect event now. (see iConnectListener.onConnectState)
            showMessage("switch to DFU mode successful. waiting disconnect event...");
        }
    }

    private void switchDeviceToDfuMode() {
        if (KCTBluetoothManager.getInstance().getConnectState() != KCTBluetoothManager.STATE_CONNECTED) {
            showMessage(R.string.device_not_connect);
            return;
        }
        showMessage("switching device to DFU mode...");
        isInDFU = true;
        KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_sendFirmwareUpdate_pack());
        // then wait MessageEvent(MessageEvent.OTA) event
    }

    private void showMessage(final CharSequence text) {
        Log.d(TAG, text.toString());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusMessageView.setText(text);
            }
        });
    }

    private void showMessage(@StringRes int strResId) {
        showMessage(getText(strResId));
    }

    private void toast(CharSequence text) {
        if (mToast == null) {
            mToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(text);
        }
        mToast.show();
    }

    private IConnectListener iConnectListener = new IConnectListener() {

        @Override
        public void onConnectState(int state) {
            if (state == KCTBluetoothManager.STATE_CONNECT_FAIL) {
                if (KCTBluetoothService.isDFU) {
                    showMessage("scanning DFU mode device...");
                    KCTBluetoothManager.getInstance().scanDevice(true);
                }
            }
        }

        @Override
        public void onConnectDevice(BluetoothDevice bluetoothDevice) {

        }

        @Override
        public void onScanDevice(BluetoothLeDevice bluetoothLeDevice) {
            if (device == null /* no device */
                    || dfuServiceController != null /* already started upgrade */ ) {
                return;
            }

            String btName = bluetoothLeDevice.getDevice().getName();
            if (btName != null && btName.contains("DfuTarg")) {
                String dfuModeAddress = Utils.getNewMac(device.getAddress());
                if (bluetoothLeDevice.getDevice().getAddress().equals(dfuModeAddress)) {
                    // DFU mode device fond.

                    KCTBluetoothManager.getInstance().scanDevice(false);

                    showMessage("reconnecting to upgrade...");
                    // start firmware upgrade.
                    if (dfuFileUri != null) {
                        // use selected file
                        dfuServiceController = KCTBluetoothManager.getInstance().upgrade_DFU(dfuFileUri, dfuModeAddress);
                    } else {
                        // use downloaded file
                        dfuServiceController = KCTBluetoothManager.getInstance().upgrade_DFU(DFU_FILE_PATH, dfuModeAddress);
                    }
                }
            }
        }

        @Override
        public void onCommand_d2a(byte[] bytes) {

        }
    };

    private IDFUProgressCallback mIDFUProgressCallback = new IDFUProgressCallback() {

        @Override
        public void onDeviceConnecting(String deviceAddress) {
            showMessage(deviceAddress + ": connecting");
        }

        @Override
        public void onDeviceConnected(String deviceAddress) {
            showMessage(deviceAddress + ": connected");
        }

        @Override
        public void onDfuProcessStarting(String deviceAddress) {
            showMessage(deviceAddress + ": DFU process starting");
        }

        @Override
        public void onDfuProcessStarted(String deviceAddress) {
            showMessage(deviceAddress + ": DFU process started");
        }

        @Override
        public void onEnablingDfuMode(String deviceAddress) {
            showMessage(deviceAddress + ": enabling DFU mode");
        }

        @Override
        public void onProgressChanged(String deviceAddress, int percent, float speed, float avgSpeed, int currentPart, int partsTotal) {
            String msg = String.format(Locale.getDefault(), "%s: on progress: %d%%, speed: %.2f, avgSpeed: %.2f, currentPart: %d, partsTotal: %d", deviceAddress, percent, speed, avgSpeed, currentPart, partsTotal);
            showMessage(msg);
            mProgressbar.setProgress(percent);
            percentView.setText(String.valueOf(percent));
        }

        @Override
        public void onFirmwareValidating(String deviceAddress) {
            showMessage(deviceAddress + ": firmware validating");
        }

        @Override
        public void onDeviceDisconnecting(String deviceAddress) {
            showMessage(deviceAddress + ": disconnecting");
            isInDFU = false;
        }

        @Override
        public void onDeviceDisconnected(String deviceAddress) {
            showMessage(deviceAddress + ": disconnected");
            isInDFU = false;
        }

        @Override
        public void onDfuCompleted(String deviceAddress) {
            showMessage(deviceAddress + ": DFU completed");
            isInDFU = false;
            dfuServiceController = null;
        }

        @Override
        public void onDfuAborted(String deviceAddress) {
            showMessage(deviceAddress + ": DFU aborted");
            isInDFU = false;
            dfuServiceController = null;
        }

        @Override
        public void onError(String deviceAddress, int error, int errorType, String message) {
            showMessage(deviceAddress + ": DFU error: error=" + error + ", errorType=" + error + ", message=" + message);
            isInDFU = false;
            dfuServiceController = null;
        }
    };

    private static class DownloadFirmwareFileAsyncTask extends AsyncTask<Void, Void, Object[]> {
        private WeakReference<FirmwareUpgradeFor0Act> mA;

        DownloadFirmwareFileAsyncTask(FirmwareUpgradeFor0Act a) {
            mA = new WeakReference<>(a);
        }

        /**
         * @param voids
         * @return { success, error_message }
         */
        @Override
        protected Object[] doInBackground(Void... voids) {
            FirmwareUpgradeFor0Act a = mA.get();
            if (a == null) {
                return new Object[]{false, ""};
            }

            try {
                // download
                byte[] data = KCTBluetoothManager.getInstance().getDFU_data(a.platformCode);
                // save to filesystem
                File file = new File(DFU_FILE_PATH);
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                FileOutputStream fileOutputStream = null;
                try {
                    fileOutputStream = new FileOutputStream(file);
                    fileOutputStream.write(data);
                } finally {
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                }
                return new Object[]{true, ""};
            } catch (Exception e) {
                e.printStackTrace();
                return new Object[]{false, e.getMessage()};
            }
        }

        @Override
        protected void onPostExecute(Object[] downloadResult) {
            FirmwareUpgradeFor0Act a = mA.get();
            if (a == null) {
                return;
            }

            if ((boolean) downloadResult[0]) {
                a.showMessage("download DFU file success");
                // switch device to DUF mode to upgrade
                a.switchDeviceToDfuMode();
            } else {
                a.showMessage("download DFU file failure: " + downloadResult[1]);
            }
        }
    }

    private Handler mHandler = new Handler(this);
    private static class Handler extends android.os.Handler {
        private static final int EXEC_RUNNABLE = 0;

        private WeakReference<FirmwareUpgradeFor0Act> mA;
        private Handler(FirmwareUpgradeFor0Act a) {
            mA = new WeakReference<>(a);
        }

        @Override
        public void handleMessage(Message msg) {
            FirmwareUpgradeFor0Act a = mA.get();
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
