package com.kct.bluetooth_demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TimePicker;
import android.widget.Toast;

import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.command.BLEBluetoothManager;

import java.util.HashMap;

public class HeartRateMonitoringActivity extends AppCompatActivity {

    TimePicker startTimePicker, endTimePicker;
    EditText intervalEditText;
    CheckBox enableCheckBox;
    Button setup;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_monitoring);

        startTimePicker = (TimePicker) findViewById(R.id.start_time);
        endTimePicker = (TimePicker) findViewById(R.id.end_time);
        intervalEditText = (EditText) findViewById(R.id.interval);
        enableCheckBox = (CheckBox) findViewById(R.id.enable);
        setup = (Button) findViewById(R.id.setup);

        startTimePicker.setIs24HourView(true);
        startTimePicker.setCurrentHour(7);
        startTimePicker.setCurrentMinute(0);
        endTimePicker.setIs24HourView(true);
        endTimePicker.setCurrentHour(22);
        endTimePicker.setCurrentMinute(0);

        intervalEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                setup.setEnabled(editable.length() != 0);
            }
        });
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.setup:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        HashMap<String,Object> autoHeartMap = new HashMap<>();
                        autoHeartMap.put("enable", enableCheckBox.isChecked());
                        autoHeartMap.put("startHour", startTimePicker.getCurrentHour());
                        autoHeartMap.put("startMin", startTimePicker.getCurrentMinute());
                        autoHeartMap.put("endHour", endTimePicker.getCurrentHour());
                        autoHeartMap.put("endMin", endTimePicker.getCurrentMinute());
                        autoHeartMap.put("interval", Integer.valueOf(intervalEditText.getText().toString()));
                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_setAutoHeartData_pack(autoHeartMap);
                    } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKAutoHeart_pack(enableCheckBox.isChecked(),
                                startTimePicker.getCurrentHour(), startTimePicker.getCurrentMinute(),
                                endTimePicker.getCurrentHour(), endTimePicker.getCurrentMinute(),
                                Integer.valueOf(intervalEditText.getText().toString()));
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
