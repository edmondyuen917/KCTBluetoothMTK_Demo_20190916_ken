package com.kct.bluetooth_demo;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.bluetooth.bean.BluetoothLeDevice;
import com.kct.bluetooth.callback.IConnectListener;

import java.util.ArrayList;
import java.util.List;

public class DeviceScanActivity extends AppCompatActivity implements View.OnClickListener {

    private ListView deviceLv;
    private BluetoothAdapter bluetoothAdapter;

    private TextView searchText;
    private ImageView imageView;

    private volatile List<BluetoothLeDevice> bluetoothLeDeviceList = new ArrayList<>();
    private DeviceAdapter adapter;
    private ArrayList<BluetoothLeDevice> list = new ArrayList<>();

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
    };

    private IConnectListener iConnectListener = new IConnectListener() {
        @Override
        public void onConnectState(int state) {
            Log.e("[DeviceScanActivity]", "state = " + state);
            if (state == 3) {
                getSharedPreferences("bluetooth", 0)
                        .edit()
                        .putBoolean("reconnect", true)
                        .apply();
                finish();
            }
        }

        @Override
        public void onConnectDevice(BluetoothDevice device) {
        }

        @Override
        public void onScanDevice(final BluetoothLeDevice device) {
            final BluetoothDevice bluetoothDevice = device.getDevice();
            if (null != bluetoothDevice.getName()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean isFound = false;
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i).getDevice().equals(bluetoothDevice)) {
                                isFound = true;
                            }
                        }
                        if (!isFound) {
                            list.add(device);
                            bluetoothLeDeviceList.add(device);
                            adapter.setDeviceList(bluetoothLeDeviceList);
                        }
                    }
                });
            }
        }

        @Override
        public void onCommand_d2a(byte[] bytes) {

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan);
        init();

        SharedPreferences sharedpreferences = getSharedPreferences("MyPref", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedpreferences.edit();

        if (!sharedpreferences.getBoolean("DeviceScanPermission", false)) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) ;
                if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) ;
                {
                    requestPermissions(PERMISSIONS_STORAGE,
                            REQUEST_EXTERNAL_STORAGE);
                    editor.putBoolean("DeviceScanPermission", true).commit();
                }
            }
        }
        KCTBluetoothManager.getInstance().registerListener(iConnectListener);
    }

    private void init() {
        deviceLv = (ListView) findViewById(android.R.id.list);

        imageView = (ImageView) findViewById(R.id.img_bluetooth_sesarch);
        searchText = (TextView) findViewById(R.id.tx_bluetooth_search);

        imageView.setOnClickListener(this);
        searchText.setOnClickListener(this);

        adapter = new DeviceAdapter(this);
        deviceLv.setAdapter(adapter);

        if (android.os.Build.VERSION.SDK_INT >= 18
                && !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Checks if Bluetooth is supported on the device.
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, R.string.pls_switch_bt_on, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        deviceLv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                BluetoothLeDevice device = (BluetoothLeDevice) adapter.getItem(position);
                if (device == null)
                    return;
                stopScan();
                getSharedPreferences("bluetooth", 0)
                        .edit()
                        .putString("address", device.getAddress())
                        .putString("addressName", device.getName())
                        .putInt("deviceType", device.getDeviceType())
                        .apply();
                KCTBluetoothManager.getInstance().connect(device.getDevice(), device.getDeviceType());
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        KCTBluetoothManager.getInstance().unregisterListener(iConnectListener);
    }


    private void startScan() {
        KCTBluetoothManager.getInstance().scanDevice(true);
    }


    private void stopScan() {
        KCTBluetoothManager.getInstance().scanDevice(false);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.img_bluetooth_sesarch:
                stopScan();
                imageView.setVisibility(View.GONE);
                searchText.setVisibility(View.VISIBLE);
                list.clear();
                bluetoothLeDeviceList.clear();
                adapter.notifyDataSetChanged();
                break;
            case R.id.tx_bluetooth_search:
                startScan();
                imageView.setVisibility(View.VISIBLE);
                searchText.setVisibility(View.GONE);
                break;
        }
    }
}
