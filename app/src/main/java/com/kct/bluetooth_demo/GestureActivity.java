package com.kct.bluetooth_demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.Toast;

import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.command.BLEBluetoothManager;

public class GestureActivity extends AppCompatActivity {

    RadioButton left_hand, right_hand;
    CheckBox raiseCheckBox, wristCheckBox;
    Button setup;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gesture);

        left_hand = (RadioButton) findViewById(R.id.left_hand);
        right_hand = (RadioButton) findViewById(R.id.right_hand);
        raiseCheckBox = (CheckBox) findViewById(R.id.raise);
        wristCheckBox = (CheckBox) findViewById(R.id.wrist);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.setup:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    int hand = left_hand.isChecked() ? 0 : 1;
                    boolean raise = raiseCheckBox.isChecked();
                    boolean wrist = wristCheckBox.isChecked();
                    byte[] pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_setGestureData_pack(hand, raise, wrist);
                    } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        // 注意，MTK 的抬手亮屏没有独立的接口，APP需要同步设置 【来电翻转静音】 【闹钟静音】 【主菜单晃动翻页】 【晃动接听】 这几个功能。
                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKFunction_pack(raise || wrist, true, true, true, true);
                    }
                    if (pack != null) {
                        KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                    }
                } else {
                    Toast.makeText(this, R.string.device_not_connect, Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
}
