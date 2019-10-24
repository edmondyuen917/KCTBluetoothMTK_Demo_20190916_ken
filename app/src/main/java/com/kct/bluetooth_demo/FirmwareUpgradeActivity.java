package com.kct.bluetooth_demo;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.content.CursorLoader;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.bluetooth.callback.IDFUProgressCallback;
import com.kct.command.BLEBluetoothManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class FirmwareUpgradeActivity extends AppCompatActivity {
    private static final String TAG = "FirmwareUpgradeActivity";

    public static final String ACTIVITY_PARAM_KEY_currentVersion = "currentVersion";
    public static final String ACTIVITY_PARAM_KEY_serverVersion = "serverVersion";
    public static final String ACTIVITY_PARAM_KEY_braceletType = "braceletType";
    public static final String ACTIVITY_PARAM_KEY_platformCode = "platformCode";
    public static final String ACTIVITY_PARAM_KEY_dfuFileUri = "dfuFileUri";

    public static final int ACTIVITY_REQ_CODE_upgrade = 1;
    public static final int ACTIVITY_REQ_CODE_selectDfuFile = 2;

    private Toast mToast;

    private TextView currentVersionView, serverVersionView, statusMessageView;
    private Button upgradeButton, selectFileToUpgradeButton;

    private String currentVersion, serverVersion;
    private int braceletType, platformCode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firmware);

        currentVersionView = findViewById(R.id.device_current_version);
        serverVersionView = findViewById(R.id.server_version);
        statusMessageView = findViewById(R.id.status_message);
        upgradeButton = findViewById(R.id.upgrade);
        selectFileToUpgradeButton = findViewById(R.id.select_file_to_upgrade);


        if (KCTBluetoothManager.getInstance().getConnectState() != KCTBluetoothManager.STATE_CONNECTED) {
            showMessage(R.string.device_not_connect);
            return;
        }

        EventBus.getDefault().register(this);

        showMessage(R.string.reading_current_version_from_device);
        // get device current version:
        // send BLE_COMMAND_a2d_getFirmwareData_pack, then wait MessageEvent.FirmwareInfo Event.
        KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_getFirmwareData_pack());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ACTIVITY_REQ_CODE_upgrade:
                if (KCTBluetoothManager.getInstance().getConnectState() != KCTBluetoothManager.STATE_CONNECTED) {
                    upgradeButton.setEnabled(false);
                    selectFileToUpgradeButton.setEnabled(false);
                }
                break;
            case ACTIVITY_REQ_CODE_selectDfuFile:
                if (resultCode == RESULT_OK) {
                    Uri selectedFileUri = data.getData();
                    if (selectedFileUri != null) {
                        startFirmwareUpgradeAct(selectedFileUri);
                    } else {
                        toast("no file select");
                    }
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
            case R.id.upgrade:
                startFirmwareUpgradeAct(null);
                break;
            case R.id.select_file_to_upgrade:
                selectDfuFile();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEventFirmwareData(MessageEvent.FirmwareInfo firmwareInfo) {
        currentVersion = firmwareInfo.version;
        braceletType = firmwareInfo.braceletType;
        platformCode = firmwareInfo.platformCode;
        currentVersionView.setText(currentVersion);
        showMessage(R.string.query_newer_version_from_server);
        // checkDFU_upgrade should access network, so put it to Thread
        EventBus.getDefault().post(new CheckDFUUpgradeEvent());
    }

    public static class CheckDFUUpgradeEvent { }
    public static class CheckDFUUpgradeDoneEvent {
        final String serverVersion;
        CheckDFUUpgradeDoneEvent(String serverVersion) {
            this.serverVersion = serverVersion;
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onCheckDFUUpgradeEvent(CheckDFUUpgradeEvent ev) {
        String version = null;
        try {
            version = KCTBluetoothManager.getInstance().checkDFU_upgrade(platformCode);
            // this in background, should to Main thread
            EventBus.getDefault().post(new CheckDFUUpgradeDoneEvent(version));
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("checkDFU_upgrade Exception: " + e.getMessage());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCheckDFUUpgradeDoneEvent(CheckDFUUpgradeDoneEvent ev) {
        if (TextUtils.isEmpty(ev.serverVersion)) {
            showMessage("is up to date. (no network? or no firmware file on server?)");
            return;
        }

        serverVersion = ev.serverVersion;
        serverVersionView.setText(serverVersion);

        // compare currentVersion and serverVersion
        if (!Utils.versionCompare(currentVersion, serverVersion)) {
            // should upgrade
            showMessage("can upgrade");
            upgradeButton.setEnabled(true);
        } else {
            // no need to upgrade
            showMessage("It is up to date");
        }
    }

    private void startFirmwareUpgradeAct(Uri dfuFileUri) {
        Intent intent;
        if (braceletType == 0) {
            intent = new Intent(this, FirmwareUpgradeFor0Act.class);
        } else if (braceletType == 4) {
            intent = new Intent(this, FirmwareUpgradeFor4Act.class);
        } else {
            // TODO: other braceletType process
            toast("not implementation!");
            return;
        }
        intent.putExtra(ACTIVITY_PARAM_KEY_currentVersion, currentVersion);
        intent.putExtra(ACTIVITY_PARAM_KEY_serverVersion, serverVersion);
        intent.putExtra(ACTIVITY_PARAM_KEY_braceletType, braceletType);
        intent.putExtra(ACTIVITY_PARAM_KEY_platformCode, platformCode);
        if (dfuFileUri != null) {
            intent.putExtra(ACTIVITY_PARAM_KEY_dfuFileUri, dfuFileUri);
        }
        startActivityForResult(intent, ACTIVITY_REQ_CODE_upgrade);
    }

    private void selectDfuFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
        boolean isIntentSafe = activities.size() > 0;
        if(isIntentSafe) {
            startActivityForResult(intent, ACTIVITY_REQ_CODE_selectDfuFile);
        } else {
            toast("install a file manager app first");
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
}
