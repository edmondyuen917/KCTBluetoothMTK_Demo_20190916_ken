package com.kct.bluetooth_demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TimePicker;
import android.widget.Toast;

import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.command.BLEBluetoothManager;

import java.util.HashMap;
import java.util.Map;

public class NightModeActivity extends AppCompatActivity {

    TimePicker startTimePicker, endTimePicker;
    CheckBox enableCheckBox;
    Button setup;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_night_mode);

        startTimePicker = findViewById(R.id.start_time);
        endTimePicker = findViewById(R.id.end_time);
        enableCheckBox = findViewById(R.id.enable);
        setup = findViewById(R.id.setup);

        startTimePicker.setIs24HourView(true);
        startTimePicker.setCurrentHour(23);
        startTimePicker.setCurrentMinute(0);
        endTimePicker.setIs24HourView(true);
        endTimePicker.setCurrentHour(7);
        endTimePicker.setCurrentMinute(0);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.setup:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("enable", enableCheckBox.isChecked());
                        map.put("startHour", startTimePicker.getCurrentHour());
                        map.put("startMin", startTimePicker.getCurrentMinute());
                        map.put("endHour", endTimePicker.getCurrentHour());
                        map.put("endMin", endTimePicker.getCurrentMinute());
                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendNightMode_pack(map);
                    } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        Toast.makeText(this,getString(R.string.device_not_support),Toast.LENGTH_SHORT).show();
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
