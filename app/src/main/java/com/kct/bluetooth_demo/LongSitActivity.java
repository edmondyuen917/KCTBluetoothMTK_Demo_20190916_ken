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
import android.widget.TimePicker;
import android.widget.Toast;

import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.command.BLEBluetoothManager;

import java.util.HashMap;

public class LongSitActivity extends AppCompatActivity {

    TimePicker startTimePicker, endTimePicker;
    CheckBox repeatSunCheckBox, repeatMonCheckBox, repeatTueCheckBox, repeatWedCheckBox, repeatThuCheckBox, repeatFriCheckBox, repeatSatCheckBox;
    EditText intervalEditText;
    EditText thresholdEditText;
    CheckBox enableCheckBox;
    Button setup;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_long_sit);

        startTimePicker = findViewById(R.id.start_time);
        endTimePicker = findViewById(R.id.end_time);
        repeatSunCheckBox = findViewById(R.id.repeat_sun);
        repeatMonCheckBox = findViewById(R.id.repeat_mon);
        repeatTueCheckBox = findViewById(R.id.repeat_tue);
        repeatWedCheckBox = findViewById(R.id.repeat_wed);
        repeatThuCheckBox = findViewById(R.id.repeat_thu);
        repeatFriCheckBox = findViewById(R.id.repeat_fri);
        repeatSatCheckBox = findViewById(R.id.repeat_sat);
        intervalEditText = findViewById(R.id.interval);
        thresholdEditText = findViewById(R.id.threshold);
        enableCheckBox = findViewById(R.id.enable);
        setup = findViewById(R.id.setup);

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
                setup.setEnabled(editable.length() != 0 && thresholdEditText.getText().length() != 0);
            }
        });

        thresholdEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                setup.setEnabled(s.length() != 0 && intervalEditText.getText().length() != 0);
            }
        });
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.setup:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        HashMap<String,Object> longSitMap = new HashMap<>();
                        longSitMap.put("enable", enableCheckBox.isChecked());
                        longSitMap.put("start", startTimePicker.getCurrentHour());
                        longSitMap.put("end", endTimePicker.getCurrentHour());
                        longSitMap.put("time", Integer.valueOf(intervalEditText.getText().toString()));
                        longSitMap.put("threshold", Integer.valueOf(thresholdEditText.getText().toString()));

                        CheckBox[] repeatCheckBoxs = new CheckBox[] {repeatMonCheckBox, repeatTueCheckBox, repeatWedCheckBox, repeatThuCheckBox, repeatFriCheckBox, repeatSatCheckBox, repeatSunCheckBox};
                        String repeat = "";
                        for (CheckBox checkBox : repeatCheckBoxs) {
                            repeat += checkBox.isChecked() ? "1" : "0";
                        }

                        longSitMap.put("repeat", repeat);

                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_setSedentary_pack(longSitMap);
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
