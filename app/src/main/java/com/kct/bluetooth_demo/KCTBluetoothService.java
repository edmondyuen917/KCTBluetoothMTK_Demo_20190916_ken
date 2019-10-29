package com.kct.bluetooth_demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.bluetooth.bean.BluetoothLeDevice;
import com.kct.bluetooth.callback.IConnectListener;
import com.kct.bluetooth_demo.db.entity.GPSInterconn;
import com.kct.command.BLEBluetoothManager;
import com.kct.command.IReceiveListener;
import com.kct.command.KCTBluetoothCommand;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * KCTBluetoothService
 */

@SuppressLint("NewApi")
public class KCTBluetoothService extends Service {

    private static final String TAG = KCTBluetoothService.class.getSimpleName();
    private static final Context mContext = KCTApp.getInstance().getApplicationContext();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    public static boolean isDFU;   //判断是否处于DFU模式

    private final List<Integer> mMTKSportHistoryIndexList = new ArrayList<>();

    private LocationManager mLocationManager;

    public class LocalBinder extends Binder {
        public KCTBluetoothService getService() {
            return KCTBluetoothService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();


    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        return true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        KCTBluetoothManager.getInstance().registerListener(iConnectListener);
        registerReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        unRegisterReceiver();
        super.onDestroy();
        KCTBluetoothManager.getInstance().unregisterListener(iConnectListener);
    }


    private Runnable mReconnectTask = new Runnable() {
        @Override
        public void run() {
            SharedPreferences preferences = mContext.getSharedPreferences("bluetooth", 0);
            String addr = preferences.getString("address", null);
            int deviceType = preferences.getInt("deviceType", KCTBluetoothManager.DEVICE_NONE);
            boolean reconnect = preferences.getBoolean("reconnect", false);
            if (!TextUtils.isEmpty(addr) && reconnect
                    && (deviceType == KCTBluetoothManager.DEVICE_BLE || deviceType == KCTBluetoothManager.DEVICE_MTK)
                    && mBluetoothAdapter.isEnabled()) {
                try {
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(addr);
                    KCTBluetoothManager.getInstance().connect(device, deviceType);
                } catch (Exception e) {
                    Log.e(TAG, "try reconnect", e);
                }
            }
        }
    };

    private Executor mExecutor = new Executor(this);

    private static class Executor extends android.os.Handler {
        private static final int RUN_RUNNABLE = 0;

        private WeakReference<KCTBluetoothService> mS;

        Executor(KCTBluetoothService service) {
            mS = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            KCTBluetoothService s = mS.get();
            if (s == null) {
                return;
            }

            switch (msg.what) {
                case RUN_RUNNABLE:
                    if (msg.obj instanceof Runnable) {
                        ((Runnable) msg.obj).run();
                    }
                    break;
            }
        }

        void execute(Runnable task) {
            obtainMessage(RUN_RUNNABLE, task).sendToTarget();
        }

        void executeDelayed(Runnable task, long delayMillis) {
            sendMessageDelayed(obtainMessage(RUN_RUNNABLE, task), delayMillis);
        }

        void cancel(Runnable task) {
            removeMessages(RUN_RUNNABLE, task);
        }
    }
    private IReceiveListener iReceiveCallback = new IReceiveListener() {

        @Override
        public void onReceive(int i, boolean b, Object... objects) {
            Log.e(TAG, i + ", " + b);
            if (b) {
                switch (i) {
                    case 0:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            // BLE_COMMAND_a2d_sendMTKConfig_pack response
                            HashMap<String, String> map = (HashMap<String, String>) objects[0];
                            Log.e(TAG, "name = " + map.get("name"));
                            Log.e(TAG, "version = " + map.get("version"));
                            Log.e(TAG, "display = " + map.get("display"));
                            Log.e(TAG, "pedometer = " + map.get("pedometer"));
                            Log.e(TAG, "sleep = " + map.get("sleep"));
                            Log.e(TAG, "heart = " + map.get("heart"));
                            Log.e(TAG, "sit = " + map.get("sit"));
                            Log.e(TAG, "goal = " + map.get("goal"));
                            Log.e(TAG, "sex = " + map.get("sex"));
                            Log.e(TAG, "height = " + map.get("height"));
                            Log.e(TAG, "weight = " + map.get("weight"));
                            Log.e(TAG, "birth = " + map.get("birth"));
                            Log.e(TAG, "alarm = " + map.get("alarm"));
                            Log.e(TAG, "alart_type = " + map.get("alart_type"));
                            Log.e(TAG, "battery = " + map.get("battery"));
                            Log.e(TAG, "bt_address = " + map.get("bt_address"));
                            Log.e(TAG, "software_version = " + map.get("software_version"));
                            Log.e(TAG, "bloodpress = " + map.get("bloodpress"));
                            Log.e(TAG, "oxygen = " + map.get("oxygen"));
                            Log.e(TAG, "adaptlist = " + map.get("adaptlist"));
                            Log.e(TAG, "crc = " + map.get("crc"));
                            Log.e(TAG, "heart_set = " + map.get("heart_set"));
                            Log.e(TAG, "drink_set = " + map.get("drink_set"));
                            Log.e(TAG, "motion_set = " + map.get("motion_set"));

                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_sendMTKConfig_pack response\n\n");
                            sb.append("name: ").append(map.get("name")).append('\n');
                            sb.append("version: ").append(map.get("version")).append('\n');
                            sb.append("display: ").append(map.get("display")).append('\n');
                            sb.append("pedometer: ").append(map.get("pedometer")).append('\n');
                            sb.append("sleep: ").append(map.get("sleep")).append('\n');
                            sb.append("heart: ").append(map.get("heart")).append('\n');
                            sb.append("sit: ").append(map.get("sit")).append('\n');
                            sb.append("goal: ").append(map.get("goal")).append('\n');
                            sb.append("sex: ").append(map.get("sex")).append('\n');
                            sb.append("height: ").append(map.get("height")).append('\n');
                            sb.append("weight: ").append(map.get("weight")).append('\n');
                            sb.append("birth: ").append(map.get("birth")).append('\n');
                            sb.append("alarm: ").append(map.get("alarm")).append('\n');
                            sb.append("alart_type: ").append(map.get("alart_type")).append('\n');
                            sb.append("battery: ").append(map.get("battery")).append('\n');
                            sb.append("bt_address: ").append(map.get("bt_address")).append('\n');
                            sb.append("software_version: ").append(map.get("software_version")).append('\n');
                            sb.append("bloodpress: ").append(map.get("bloodpress")).append('\n');
                            sb.append("oxygen: ").append(map.get("oxygen")).append('\n');
                            sb.append("adaptlist: ").append(map.get("adaptlist")).append('\n');
                            sb.append("crc: ").append(map.get("crc")).append('\n');
                            sb.append("heart_set: ").append(map.get("heart_set")).append('\n');
                            sb.append("drink_set: ").append(map.get("drink_set")).append('\n');
                            sb.append("drink_set: ").append(map.get("drink_set")).append('\n');
                            SaveLog(sb);
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                        }
                        break;

                    // FLASH
                    case (byte) 0x02: // 请求写入FLASH数据 回响
                        // BLE_COMMAND_a2d_setFlashCommand_pack response
                        if (objects != null && objects.length >= 3) {
                            int dataType = (int) objects[0];
                            int version = (int) objects[1];
                            boolean success = (boolean) objects[2];
                            Log.e(TAG, "BLE_COMMAND_a2d_setFlashCommand_pack(" + dataType + ", " + version + ") -> " + (success ? "success" : "failure"));
                            EventBus.getDefault().post(new Event.FlashCommand.RequireWriteResponse(dataType, version, success));
                        }
                        break;
                    case (byte) 0x04: // 传输FLASH数据 回响
                        // BLE_COMMAND_a2d_sendFlashData_pack response
                        if (objects != null && objects.length >= 3) {
                            int pack_sum = (int) objects[0];
                            int pack_index = (int) objects[1];
                            boolean success = (boolean) objects[2];
                            Log.e(TAG, "flash write response: pack_sum: " + pack_sum + ", pack_index: " + pack_index + ", success: " + success);
                            EventBus.getDefault().post(new Event.FlashCommand.WriteResponse(pack_sum, pack_index, success));
                        }
                        break;
                    case (byte) 0x06: // 查询数据版本号 回响
                        // BLE_COMMAND_a2d_inquireFlashCommand_pack response
                        if (objects != null && objects.length >= 2) {
                            int dataType = (int) objects[0];
                            final int version = (int) objects[1];
                            Log.e(TAG, "flash type: " + dataType + ", version: " + version);
                            EventBus.getDefault().post(new Event.FlashCommand.InquireDataVersionResponse(dataType, version));
                        }
                        break;


                    case 10:    //MTK_current_run
                        // BLE_COMMAND_a2d_sendMTKCurrentAllRun_pack response
                        if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                            Log.e(TAG, getString(R.string.data_empty));
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_sendMTKCurrentAllRun_pack response\n\nthe data is empty"));
                        } else {
                            ArrayList<HashMap<String, Object>> runList = (ArrayList<HashMap<String, Object>>) objects[0];
                            if (runList != null && runList.size() > 0) {
                                StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_sendMTKCurrentAllRun_pack response\n\n");
                                sb.append("------------------------------\n");

                                for (int j = 0; j < runList.size(); j++) {
                                    HashMap<String, Object> map = runList.get(j);
                                    String date = (String) map.get("date");
                                    String step = (String) map.get("step");
                                    String calorie = (String) map.get("calorie");
                                    String distance = (String) map.get("distance");
                                    String time = (String) map.get("time");
                                    Log.e(TAG, "step = " + date + " : " + step + " : " + calorie + " : " + distance + " : " + time);

                                    sb.append("date: ").append(date).append('\n');
                                    sb.append("step: ").append(step).append('\n');
                                    sb.append("calorie: ").append(calorie).append('\n');
                                    sb.append("distance: ").append(distance).append('\n');
                                    sb.append("time: ").append(time).append('\n');
                                    sb.append("------------------------------\n");
                                }
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                            } else {
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_sendMTKCurrentAllRun_pack response\n\nempty"));
                            }
                        }
                        break;
                    case 11:    //MTK_burst_run
                        // BLE_COMMAND_a2d_retMTKBurstRun_pack response
                        if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                            Log.e(TAG, getString(R.string.data_empty));
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_retMTKBurstRun_pack response\n\nthe data is empty"));
                        } else {
                            ArrayList runLists = (ArrayList) objects[0];
                            if (runLists != null && runLists.size() > 0) {
                                StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_retMTKBurstRun_pack response\n\n");
                                sb.append("------------------------------\n");

                                for (int j = 0; j < runLists.size(); j++) {
                                    HashMap<String, Object> runMaps = (HashMap<String, Object>) runLists.get(j);
                                    String packet_sum = (String) runMaps.get("packet_sum");
                                    String packet_index = (String) runMaps.get("packet_index");
                                    String date = (String) runMaps.get("date");
                                    String time = (String) runMaps.get("time");
                                    String step = (String) runMaps.get("step");
                                    String distances = (String) runMaps.get("distance");
                                    String calories = (String) runMaps.get("calorie");
                                    String delta_time = (String) runMaps.get("delta_time");
                                    Log.e(TAG, "run = " + packet_sum + " : " + packet_index + " : " + date + " : " + time
                                            + " : " + step + " : " + distances + " : " + calories + " : " + delta_time);

                                    sb.append("packet_sum: ").append(packet_sum).append('\n');
                                    sb.append("packet_index: ").append(packet_index).append('\n');
                                    sb.append("date: ").append(date).append('\n');
                                    sb.append("time: ").append(time).append('\n');
                                    sb.append("step: ").append(step).append('\n');
                                    sb.append("distances: ").append(distances).append('\n');
                                    sb.append("calories: ").append(calories).append('\n');
                                    sb.append("delta_time: ").append(delta_time).append('\n');
                                    sb.append("------------------------------\n");
                                }

                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                            } else {
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_retMTKBurstRun_pack response\n\nempty"));
                            }
                        }
                        break;
                    case 13:    //MTK_sleep
                        // BLE_COMMAND_a2d_sendMTKBurstSleep_pack response
                        if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                            if (objects[0].equals("")) {
                                Log.e(TAG, getString(R.string.data_empty));
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_sendMTKBurstSleep_pack response\n\nthe data is empty"));
                            } else {
                                String packet_sum = (String) objects[0];
                                String packet_index = (String) objects[1];
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_sendMTKBurstSleep_pack response\n\npacket_sum: " + packet_sum + "\npacket_index: " + packet_index));
                            }
                        } else {
                            ArrayList runLists = (ArrayList) objects[0];
                            if (runLists.size() > 0) {
                                StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_sendMTKBurstSleep_pack response\n\n");
                                sb.append("------------------------------\n");

                                for (int j = 0; j < runLists.size(); j++) {
                                    HashMap<String, Object> runMaps = (HashMap<String, Object>) runLists.get(j);
                                    String date = (String) runMaps.get("date");  //日期
                                    String time = (String) runMaps.get("time");  //时间
                                    String mode = (String) runMaps.get("mode");  //睡眠模式
                                    Log.e(TAG, "sleep = " + date + " : " + time + " : " + mode);

                                    sb.append("date: ").append(date).append('\n');
                                    sb.append("time: ").append(time).append('\n');
                                    sb.append("mode: ").append(mode).append('\n');
                                    sb.append("------------------------------\n");
                                }
                                SaveLog(sb);
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                            } else {
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_sendMTKBurstSleep_pack response\n\nempty"));
                            }
                        }
                        break;
                    case 14:    //MTK_heart
                        // BLE_COMMAND_a2d_sendMTKHeart_pack response
                        if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                            Log.e(TAG, getString(R.string.data_empty));
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_sendMTKHeart_pack response\n\nthe data is empty"));
                        } else {
                            ArrayList runLists = (ArrayList) objects[0];
                            if (runLists != null && runLists.size() > 0) {
                                StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_sendMTKHeart_pack response\n\n");
                                sb.append("------------------------------\n");
                                for (int j = 0; j < runLists.size(); j++) {
                                    HashMap<String, Object> runMaps = (HashMap<String, Object>) runLists.get(j);
                                    String date = (String) runMaps.get("date");  //时间
                                    String heart = (String) runMaps.get("heart");  //心率
                                    Log.e(TAG, "heart = " + date + " : " + heart);

                                    sb.append("date: ").append(date).append('\n');
                                    sb.append("heart: ").append(heart).append('\n');
                                    sb.append("------------------------------\n");
                                }
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                            } else {
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_sendMTKHeart_pack response\n\nempty"));
                            }
                        }
                        break;
                    case 0x13:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                            // BLE_COMMAND_a2d_getFirmwareData_pack response
                            String version = (String) objects[0];
                            int braceletType = (int) objects[1];
                            int platformCode = (int) objects[2];
                            Log.e(TAG, "version = " + version + " ;  braceletType = " + braceletType + " ;  platformCode = " + platformCode);
                            SharedPreferences preferences = mContext.getSharedPreferences("bluetooth", 0);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString("version", version);
                            editor.putString("braceletType", braceletType + "");
                            editor.putString("platformCode", platformCode + "");
                            editor.apply();
                            EventBus.getDefault().post(new MessageEvent.FirmwareInfo(version, braceletType, platformCode));

                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_getFirmwareData_pack response\n\n");
                            sb.append("version: ").append(version).append('\n');
                            sb.append("braceletType: ").append(braceletType).append('\n');
                            sb.append("platformCode: ").append(platformCode).append('\n');
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                        } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            // BLE_COMMAND_a2d_sendMTKPressure_pack response
                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_sendMTKPressure_pack response\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else {
                                ArrayList runLists = (ArrayList) objects[0];
                                if (runLists != null && runLists.size() > 0) {
                                    sb.append("------------------------------\n");
                                    for (int j = 0; j < runLists.size(); j++) {
                                        HashMap<String, Object> runMaps = (HashMap<String, Object>) runLists.get(j);
                                        String date = (String) runMaps.get("date");
                                        String highBp = (String) runMaps.get("highBp");
                                        String lowBp = (String) runMaps.get("lowBp");
                                        Log.e(TAG, "blood = " + date + " : " + highBp + " : " + lowBp);

                                        sb.append("date: ").append(date).append('\n');
                                        sb.append("highBp: ").append(highBp).append('\n');
                                        sb.append("lowBp: ").append(lowBp).append('\n');
                                        sb.append("------------------------------\n");
                                    }
                                } else {
                                    sb.append("empty");
                                }
                            }
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                        }
                        break;
                    case 0x14:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            // BLE_COMMAND_a2d_sendMTKOxyen_pack response
                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_sendMTKOxyen_pack response\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else {
                                ArrayList runLists = (ArrayList) objects[0];
                                if (runLists != null && runLists.size() > 0) {
                                    sb.append("------------------------------\n");
                                    for (int j = 0; j < runLists.size(); j++) {
                                        HashMap<String, Object> runMaps = (HashMap<String, Object>) runLists.get(j);
                                        String date = (String) runMaps.get("date");
                                        String oxy = (String) runMaps.get("oxy");
                                        Log.e(TAG, "oxygen = " + date + " : " + oxy);

                                        sb.append("date: ").append(date).append('\n');
                                        sb.append("oxy: ").append(oxy).append('\n');
                                        sb.append("------------------------------\n");
                                    }
                                } else {
                                    sb.append("empty");
                                }
                            }
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                        }
                        break;
                    case 0x15:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            // device notify APP: user info
                            StringBuilder sb = new StringBuilder("user info:\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else if (objects[0] instanceof HashMap) {
                                HashMap map = (HashMap) objects[0];
                                String goal = (String) map.get("goal");
                                String sex = (String) map.get("sex");
                                String height = (String) map.get("height");
                                String weight = (String) map.get("weight");

                                sb.append("goal: ").append(goal).append('\n');
                                sb.append("sex: ").append(sex).append('\n');
                                sb.append("height: ").append(height).append('\n');
                                sb.append("weight: ").append(weight).append('\n');
                            } else {
                                sb.append("empty");
                            }
                            SaveLog(sb);
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                        }
                        break;
                    case 0x16:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            StringBuilder sb = new StringBuilder();
                            if (objects.length > 0 && objects[0] instanceof String) {
                                switch ((String) objects[0]) {
                                    case "":
                                        // do nothing
                                        sb.append("camera: do nothing");
                                        break;
                                    case "REQ":
                                        // device require APP: find phone
                                        sb.append("device require APP camera\n\n");
                                        if (objects.length > 1) {
                                            String action = (String) objects[1];
                                            switch (action) {
                                                case "0":
                                                    sb.append("action: close");
                                                    // this app dose not support camera, return to device failure
                                                    KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_retMTKCamera_pack(false));
                                                    break;
                                                case "1":
                                                    sb.append("action: open");
                                                    // this app dose not support camera, return to device failure
                                                    KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_retMTKCamera_pack(false));
                                                    break;
                                                case "2":
                                                    sb.append("action: take photograph");
                                                    // this app dose not support camera, return to device failure
                                                    KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_retMTKCamera_pack(false));
                                                    break;
                                                default:
                                                    // unknown action
                                                    sb.append("action: do nothing");
                                                    KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_retMTKCamera_pack(false));
                                                    break;
                                            }
                                        } else {
                                            // device require APP: no action! do nothing
                                            sb.append("no action! do nothing");
                                        }
                                        EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                                        break;
                                    case "RSP":
                                        // BLE_COMMAND_a2d_sendMTKCamera_pack response
                                        sb.append("APP require device camera\n\n");
                                        if (objects.length > 1) {
                                            String action = (String) objects[1];
                                            switch (action) {
                                                case "1":
                                                    // success
                                                    sb.append("successful");
                                                    break;
                                                case "0":
                                                    // failure
                                                    sb.append("failure");
                                                    break;
                                                default:
                                                    // no explicit response
                                                    sb.append("done");
                                                    break;
                                            }
                                        }
                                        EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                                        break;

                                    case "0":
                                        // device require APP: action: close
                                        sb.append("action: close");
                                        EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                                        break;
                                    case "1":
                                        // device require APP: action: open
                                        sb.append("action: open/start");
                                        EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                                        break;
                                    case "2":
                                        // device require APP: action: take photograph
                                        sb.append("take photograph");
                                        break;
                                    default:
                                        // unknown action
                                        sb.append("action: do nothing");
                                        EventBus.getDefault().post(new MessageEvent(MessageEvent.RECEIVE_DATA, sb.toString()));
                                        break;
                                }
                                Log.e(TAG, sb.toString());
                            }
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                        }
                        break;
                    case 0x17:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            // device notify APP: steps info
                            StringBuilder sb = new StringBuilder("steps info:\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else {
                                HashMap<String, Object> runMaps = (HashMap<String, Object>) objects[0];
                                String date = (String) runMaps.get("date");
                                String step = (String) runMaps.get("step");
                                String calorie = (String) runMaps.get("calorie");
                                String distance = (String) runMaps.get("distance");
                                String time = (String) runMaps.get("time");
                                Log.e(TAG, "run_real = " + date + " : " + step + " : " + calorie + " : " + distance + " : " + time);

                                sb.append("date: ").append(date).append('\n');
                                sb.append("step: ").append(step).append('\n');
                                sb.append("calorie: ").append(calorie).append('\n');
                                sb.append("distance: ").append(distance).append('\n');
                                sb.append("time: ").append(time).append('\n');
                            }
                            SaveLog(sb);
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                        }
                        break;
                    case 0x18:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            // device notify APP: heart rate
                            StringBuilder sb = new StringBuilder("heart rate:\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else {
                                Log.e(TAG, "heart_real = " + objects[0] + " : " + objects[1]);
                                sb.append("date: ").append(objects[0]).append('\n');
                                sb.append("heart: ").append(objects[1]).append('\n');
                            }
                            SaveLog(sb);
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                        }
                        break;
                    case 0x19:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            // device notify APP: blood pressure
                            StringBuilder sb = new StringBuilder("blood pressure\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else {
                                Log.e(TAG, "blood_real = " + objects[0] + " : " + objects[1] + " : " + objects[2]);
                                sb.append("date: ").append(objects[0]).append('\n');
                                sb.append("highBp: ").append(objects[1]).append('\n');
                                sb.append("lowBp: ").append(objects[2]).append('\n');
                            }
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                        }
                        break;
                    case 0x1A:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            // device notify APP: Oxygen saturation
                            StringBuilder sb = new StringBuilder("Oxygen saturation\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else {
                                Log.e(TAG, "oxygen_real = " + objects[0] + " : " + objects[1]);
                                sb.append("date: ").append(objects[0]).append('\n');
                                sb.append("oxy: ").append(objects[1]).append('\n');
                            }
                        }
                        break;
                    case 0x53:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            // ？？？
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                            } else {
                                Log.e(TAG, "unit setting = " + objects[0] + " : " + objects[1]);
                            }
                        }
                        break;
                    case 0x20:
                        // BLE_COMMAND_a2d_settime_pack response
                        EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_settime_pack response\n\ndone"));
                        break;
                    case 0x28:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            // find phone or device
                            StringBuilder sb = new StringBuilder();
                            if (objects.length > 0 && objects[0] instanceof String) {
                                switch ((String) objects[0]) {
                                    case "":
                                        // do nothing
                                        sb.append("find phone or device: do nothing");
                                        break;
                                    case "REQ":
                                        // device require APP: find phone
                                        sb.append("find phone\n\n");
                                        if (objects.length > 1) {
                                            String action = (String) objects[1];
                                            switch (action) {
                                                case "1":
                                                    // device require APP: find phone action: open/start
                                                    sb.append("action: open/start");
                                                    break;
                                                case "0":
                                                    // device require APP: find phone action: close/stop/cancel
                                                    sb.append("action: close/stop/cancel");
                                                    break;
                                                default:
                                                    // unknown action
                                                    sb.append("action: do nothing");
                                                    break;
                                            }
                                        } else {
                                            // device require APP: no action! do nothing
                                            sb.append("no action! do nothing");
                                        }
                                        EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                                        break;
                                    case "RSP":
                                        // BLE_COMMAND_a2d_sendMTKFindDevice_pack response
                                        sb.append("find device\n\n");
                                        if (objects.length > 1) {
                                            String action = (String) objects[1];
                                            switch (action) {
                                                case "1":
                                                    // find device success
                                                    sb.append("successful");
                                                    break;
                                                case "0":
                                                    // find device failure
                                                    sb.append("failure");
                                                    break;
                                                default:
                                                    // no explicit response
                                                    sb.append("done");
                                                    break;
                                            }
                                        }
                                        EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                                        break;
                                    case "1":
                                        // device require APP: find phone action: open/start
                                        sb.append("action: open/start");
                                        EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                                        break;
                                    case "0":
                                        // device require APP: find phone action: close/stop/cancel
                                        sb.append("action: close/stop/cancel");
                                        EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, sb.toString()));
                                        break;
                                    default:
                                        // unknown action
                                        sb.append("action: do nothing");
                                        EventBus.getDefault().post(new MessageEvent(MessageEvent.RECEIVE_DATA, sb.toString()));
                                        break;
                                }
                                Log.e(TAG, sb.toString());
                            }
                        }
                        break;
                    case 0x2B:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                            } else {
                                Log.e(TAG, "music = " + objects[0]);
                            }
                        }
                        break;
                    case 0x11:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                            // BLE_COMMAND_a2d_sendFirmwareUpdate_pack response
                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_sendFirmwareUpdate_pack response\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {
                                sb.append("the data is empty");
                            } else {
                                sb.append(objects[0]);
                            }
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                            Log.e(TAG, "ota");
                            isDFU = true;
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.OTA));
                        } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                            } else {
                                // If you get index data, you can put the index value into the BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKSportData_pack() request motion data.
                                Log.e(TAG, "sport_index = " + objects[0]);
                                try {
                                    String sportIndexs = (String) objects[0];
                                    String[] sportIndexArr = sportIndexs.split(",");
                                    mMTKSportHistoryIndexList.clear();
                                    for (String sportIndex : sportIndexArr) {
                                        int index = Integer.parseInt(sportIndex);
                                        mMTKSportHistoryIndexList.add(index);
                                    }
                                    if (!mMTKSportHistoryIndexList.isEmpty()) {
                                        byte[] pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKSportData_pack(mMTKSportHistoryIndexList.get(0));
                                        KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                                        // this command will response with IReceiveListener.onReceive(18, true, "xxx,xxx,xxx,...")
                                        // see KCTBluetoothService.iReceiveCallback:
                                        //     case 0x12:
                                        //         if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                                        //
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "call BLE_COMMAND_a2d_sendMTKSportData_pack", e);
                                }
                            }
                        }
                        break;
                    case 0x12:   //MTK_sport
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                            } else if (objects[0] instanceof HashMap) { // OK, the sport data of sport_index done
                                HashMap<String, Object> sportMap = (HashMap<String, Object>) objects[0];
                                Log.e(TAG, "pack_sum = " + sportMap.get("pack_sum"));
                                Log.e(TAG, "pack_index = " + sportMap.get("pack_index"));
                                Log.e(TAG, "sport_index = " + sportMap.get("sport_index"));
                                Log.e(TAG, "sportType = " + sportMap.get("sportType"));
                                Log.e(TAG, "startTime = " + sportMap.get("startTime"));
                                Log.e(TAG, "time = " + sportMap.get("time"));
                                Log.e(TAG, "distance = " + sportMap.get("distance"));
                                Log.e(TAG, "calorie = " + sportMap.get("calorie"));
                                Log.e(TAG, "pace = " + sportMap.get("pace"));
                                Log.e(TAG, "speed = " + sportMap.get("speed"));
                                Log.e(TAG, "frequency = " + sportMap.get("frequency"));
                                Log.e(TAG, "steps = " + sportMap.get("steps"));
                                Log.e(TAG, "altitude = " + sportMap.get("altitude"));
                                Log.e(TAG, "heart = " + sportMap.get("heart"));
                                Log.e(TAG, "pauseTime = " + sportMap.get("pauseTime"));
                                Log.e(TAG, "pauseNumber = " + sportMap.get("pauseNumber"));
                                Log.e(TAG, "maxStride = " + sportMap.get("maxStride"));
                                Log.e(TAG, "minStride = " + sportMap.get("minStride"));
                                Log.e(TAG, "heartArray = " + sportMap.get("heartArray"));
                                Log.e(TAG, "frequencyArray = " + sportMap.get("frequencyArray"));
                                Log.e(TAG, "speedArray = " + sportMap.get("speedArray"));
                                Log.e(TAG, "paceArray = " + sportMap.get("paceArray"));
                                Log.e(TAG, "altitudeArray = " + sportMap.get("altitudeArray"));
                                Log.e(TAG, "trajectoryArray = " + sportMap.get("trajectoryArray"));

                                StringBuilder sb = new StringBuilder();
                                for (Object o : objects) {
                                    sb.append(", ").append(o);
                                }
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));

                                try {
                                    String sport_index = (String) sportMap.get("sport_index");
                                    int index = Integer.parseInt(sport_index);
                                    for (Iterator<Integer> it = mMTKSportHistoryIndexList.iterator(); it.hasNext(); ) {
                                        Integer idx = it.next();
                                        if (idx.equals(index)) {
                                            it.remove();
                                        }
                                    }
                                    if (!mMTKSportHistoryIndexList.isEmpty()) {
                                        byte[] pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKSportData_pack(mMTKSportHistoryIndexList.get(0));
                                        KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                                        // this command will response with IReceiveListener.onReceive(18, true, "xxx,xxx,xxx,...")
                                        // see KCTBluetoothService.iReceiveCallback:
                                        //     case 0x12:
                                        //         if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                                        //
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "call BLE_COMMAND_a2d_sendMTKSportData_pack", e);
                                }
                            } else if (objects.length == 3) { // need more data
                                String sport_index = (String) objects[0];
                                String packet_sum = (String) objects[1];
                                String packet_index = (String) objects[2];
                                Log.e(TAG, "sport = " + sport_index + " : " + packet_sum + " : " + packet_index);
                                // need more data
                                // you should send the BLE_COMMAND_a2d_retMTKSportData_pack(int sport_index, int pack_sum, int pack_index)
                                try {
                                    int index = Integer.parseInt(sport_index);
                                    int pack_sum = Integer.parseInt(packet_sum);
                                    int pack_index = Integer.parseInt(packet_index);
                                    byte[] pack = BLEBluetoothManager.BLE_COMMAND_a2d_retMTKSportData_pack(index, pack_sum, pack_index);
                                    KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                                } catch (Exception e) {
                                    Log.e(TAG, "call BLE_COMMAND_a2d_retMTKSportData_pack", e);
                                }
                            }
                        }
                        break;
                    case 0x2F:
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                            // BLE_COMMAND_a2d_getBraceletSet_pack response
                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_getBraceletSet_pack response\n\n");
                            ArrayList<HashMap<String, Object>> list = (ArrayList<HashMap<String, Object>>) objects[0];
                            sb.append("alarm clock:\n");
                            sb.append("------------------------------\n");
                            for (int j = 0; j < list.size(); j++) {
                                HashMap<String, Object> clockMap = list.get(j);
                                int hour = (int) clockMap.get("hour");
                                int minute = (int) clockMap.get("minute");
                                String repeat = (String) clockMap.get("repeat");
                                int type = (int) clockMap.get("type");
                                boolean enable = (boolean) clockMap.get("enable");
                                Log.e(TAG, "clock j = " + j + " :  hour = " + hour + " ;  minute = " + minute + " ;  repeat = " + repeat + " ;  type = " + type + " ;  enable = " + enable);
                                sb.append("clock: ").append(j).append('\n');
                                sb.append("hour: ").append(hour).append('\n');
                                sb.append("minute: ").append(minute).append('\n');
                                sb.append("repeat: ").append(repeat).append('\n');
                                sb.append("type: ").append(type).append('\n');
                                sb.append("enable: ").append(enable).append('\n');
                                sb.append("------------------------------\n");
                            }

                            sb.append("\nsedentary:\n");
                            sb.append("------------------------------\n");
                            HashMap<String, Object> sedentaryMap = (HashMap<String, Object>) objects[1];
                            boolean sedentaryEnable = (boolean) sedentaryMap.get("enable");
                            int sedentaryStart = (int) sedentaryMap.get("start");
                            int sedentaryEnd = (int) sedentaryMap.get("end");
                            String sedentaryRepeat = (String) sedentaryMap.get("repeat");
                            int sedentaryTime = (int) sedentaryMap.get("time");
                            int sedentaryThreshold = (int) sedentaryMap.get("threshold");
                            Log.e(TAG, "sedentary  : " + " sedentaryEnable = " + sedentaryEnable + " ;  sedentaryStart = " + sedentaryStart + " ;  sedentaryEnd = " + sedentaryEnd
                                    + " ;  sedentaryRepeat = " + sedentaryRepeat + " ;  sedentaryTime = " + sedentaryTime + " ;  sedentaryThreshold = " + sedentaryThreshold);
                            sb.append("enable: ").append(sedentaryEnable).append('\n');
                            sb.append("start: ").append(sedentaryStart).append('\n');
                            sb.append("end: ").append(sedentaryEnd).append('\n');
                            sb.append("repeat: ").append(sedentaryRepeat).append('\n');
                            sb.append("time: ").append(sedentaryTime).append('\n');
                            sb.append("threshold: ").append(sedentaryThreshold).append('\n');
                            sb.append("------------------------------\n");

                            sb.append("\nuser info:\n");
                            sb.append("------------------------------\n");
                            HashMap<String, Object> userMap = (HashMap<String, Object>) objects[2];
                            int userSex = (int) userMap.get("sex");
                            int userWeight = (int) userMap.get("weight");
                            int userHeight = (int) userMap.get("height");
                            int userAge = (int) userMap.get("age");
                            int userGoal = (int) userMap.get("goal");
                            Log.e(TAG, "user  : " + " userSex = " + userSex + " ;  userWeight = " + userWeight + " ;  userHeight = " + userHeight
                                    + " ;  userAge = " + userAge + " ;  userGoal = " + userGoal);
                            sb.append("sex: ").append(userSex).append('\n');
                            sb.append("weight: ").append(userWeight).append('\n');
                            sb.append("height: ").append(userHeight).append('\n');
                            sb.append("age: ").append(userAge).append('\n');
                            sb.append("goal: ").append(userGoal).append('\n');
                            sb.append("------------------------------\n");

                            sb.append("\notify_mode:\n");
                            sb.append("------------------------------\n");
                            int notify_mode = (int) objects[3];
                            Log.e(TAG, "notify_mode  : " + " notify_mode = " + notify_mode);
                            sb.append("------------------------------\n");

                            sb.append("\ndisturb:\n");
                            sb.append("------------------------------\n");
                            HashMap<String, Object> disturbMap = (HashMap<String, Object>) objects[4];
                            boolean disturbEnable = (boolean) disturbMap.get("enable");
                            int disturbStartHour = (int) disturbMap.get("startHour");
                            int disturbStartMin = (int) disturbMap.get("startMin");
                            int disturbEndHour = (int) disturbMap.get("endHour");
                            int disturbEndMin = (int) disturbMap.get("endMin");
                            Log.e(TAG, "disturb  : " + " disturbEnable = " + disturbEnable + " ;  disturbStartHour = " + disturbStartHour + " ;  disturbStartMin = " + disturbStartMin
                                    + " ;  disturbEndHour = " + disturbEndHour + " ;  disturbEndMin = " + disturbEndMin);
                            sb.append("enable: ").append(disturbEnable).append('\n');
                            sb.append("startHour: ").append(disturbStartHour).append('\n');
                            sb.append("startMin: ").append(disturbStartMin).append('\n');
                            sb.append("endHour: ").append(disturbEndHour).append('\n');
                            sb.append("endMin: ").append(disturbEndMin).append('\n');
                            sb.append("------------------------------\n");

                            sb.append("\nheart:\n");
                            sb.append("------------------------------\n");
                            HashMap<String, Object> heartMap = (HashMap<String, Object>) objects[5];
                            boolean heartEnable = (boolean) heartMap.get("enable");
                            int heartStartHour = (int) heartMap.get("startHour");
                            int heartStartMin = (int) heartMap.get("startMin");
                            int heartEndHour = (int) heartMap.get("endHour");
                            int heartEndMin = (int) heartMap.get("endMin");
                            int heartInterval = (int) heartMap.get("interval");
                            Log.e(TAG, "heart  : " + " heartEnable = " + heartEnable + " ;  heartStartHour = " + heartStartHour + " ;  heartStartMin = " + heartStartMin
                                    + " ;  heartEndHour = " + heartEndHour + " ;  heartEndMin = " + heartEndMin + " ;  heartInterval = " + heartInterval);
                            sb.append("enable: ").append(heartEnable).append('\n');
                            sb.append("startHour: ").append(heartStartHour).append('\n');
                            sb.append("startMin: ").append(heartStartMin).append('\n');
                            sb.append("endHour: ").append(heartEndHour).append('\n');
                            sb.append("endMin: ").append(heartEndMin).append('\n');
                            sb.append("interval: ").append(heartInterval).append('\n');
                            sb.append("------------------------------\n");

                            sb.append("\nsystem:\n");
                            sb.append("------------------------------\n");
                            HashMap<String, Object> systemMap = (HashMap<String, Object>) objects[6];
                            int systemLanguage = (int) systemMap.get("language");
                            int systemHour = (int) systemMap.get("hour");
                            int systemScreen = (int) systemMap.get("screen");
                            int systemPair = (int) systemMap.get("pair");
                            Log.e(TAG, "system  : " + " systemLanguage = " + systemLanguage + " ;  systemHour = " + systemHour + " ;  systemScreen = " + systemScreen
                                    + " ;  systemPair = " + systemPair);
                            sb.append("language: ").append(systemLanguage).append('\n');
                            sb.append("hour: ").append(systemHour).append('\n');
                            sb.append("screen: ").append(systemScreen).append('\n');
                            sb.append("pair: ").append(systemPair).append('\n');
                            sb.append("------------------------------\n");

                            sb.append("\nwater:\n");
                            sb.append("------------------------------\n");
                            HashMap<String, Object> waterMap = (HashMap<String, Object>) objects[7];
                            boolean waterEnable = (boolean) waterMap.get("enable");
                            int waterStartHour = (int) waterMap.get("startHour");
                            int waterStartMin = (int) waterMap.get("startMin");
                            int waterEndHour = (int) waterMap.get("endHour");
                            int waterEndMin = (int) waterMap.get("endMin");
                            String waterRepeat = (String) waterMap.get("repeat");
                            int waterInterval = (int) waterMap.get("interval");
                            Log.e(TAG, "water  : " + " waterEnable = " + waterEnable + " ;  waterStartHour = " + waterStartHour + " ;  waterStartMin = " + waterStartMin
                                    + " ;  waterEndHour = " + waterEndHour + " ;  waterEndMin = " + waterEndMin + " ;  waterRepeat = " + waterRepeat + " ;  waterInterval = " + waterInterval);
                            sb.append("enable: ").append(waterEnable).append('\n');
                            sb.append("startHour: ").append(waterStartHour).append('\n');
                            sb.append("startMin: ").append(waterStartMin).append('\n');
                            sb.append("endHour: ").append(waterEndHour).append('\n');
                            sb.append("endMin: ").append(waterEndMin).append('\n');
                            sb.append("repeat: ").append(waterRepeat).append('\n');
                            sb.append("interval: ").append(waterInterval).append('\n');
                            sb.append("------------------------------\n");

                            sb.append("\ngoal:\n");
                            sb.append("------------------------------\n");
                            int goal = (int) objects[8];
                            Log.e(TAG, "goal  : " + " goal = " + goal);
                            sb.append("goal: ").append(goal).append('\n');
                            sb.append("------------------------------\n");

                            sb.append("\ngesture:\n");
                            sb.append("------------------------------\n");
                            if (objects[9] != null) {
                                HashMap<String, Object> gestureMap = (HashMap<String, Object>) objects[9];
                                int gestureHand = (int) gestureMap.get("hand");
                                boolean gestureRaise = (boolean) gestureMap.get("raise");
                                boolean gestureWrist = (boolean) gestureMap.get("wrist");
                                Log.e(TAG, "gesture  : " + " gestureHand = " + gestureHand + " ;  gestureRaise = " + gestureRaise + " ;  gestureWrist = " + gestureWrist);
                                sb.append("hand: ").append(gestureHand).append('\n');
                                sb.append("raise: ").append(gestureRaise).append('\n');
                                sb.append("wrist: ").append(gestureWrist).append('\n');
                            }
                            sb.append("------------------------------\n");

                            sb.append("\npranayama:\n");
                            sb.append("------------------------------\n");
                            if (objects[10] != null) {
                                HashMap<String, Object> pranayamaMap = (HashMap<String, Object>) objects[10];
                                int minutes = (int) pranayamaMap.get("mins");
                                Log.e(TAG, "pranayama  : minutes=" + minutes);
                                sb.append("mins: ").append(minutes).append('\n');
                            }
                            sb.append("------------------------------\n");

                            sb.append("\nset Sleep Time:\n");
                            sb.append("------------------------------\n");
                            if (objects[11] != null) {
                                HashMap<String, Object> setSleepTimeMap = (HashMap<String, Object>) objects[11];
                                int startHour = (int) setSleepTimeMap.get("spStartHour");
                                int startMin = (int) setSleepTimeMap.get("spStartMin");
                                int startSec = (int) setSleepTimeMap.get("spStartSec");
                                int endHour = (int) setSleepTimeMap.get("spEndHour");
                                int endMin = (int) setSleepTimeMap.get("spEndMin");
                                int endSec = (int) setSleepTimeMap.get("spEndSec");
                                String period = String.format("%02d:%02d:%02d - %02d:%02d:%02d", startHour, startMin, startSec, endHour, endMin, endSec);
                                Log.e(TAG, "set Sleep Time : " + period);
                                sb.append(period).append('\n');
                                sb.append('\t').append("spStartHour: ").append(startHour).append('\n');
                                sb.append('\t').append("spStartMin: ").append(startMin).append('\n');
                                sb.append('\t').append("spStartSec: ").append(startSec).append('\n');
                                sb.append('\t').append("spEndHour: ").append(endHour).append('\n');
                                sb.append('\t').append("spEndMin: ").append(endMin).append('\n');
                                sb.append('\t').append("spEndSec: ").append(endSec).append('\n');
                            }
                            sb.append("------------------------------\n");

                            sb.append("\nnight mode time:\n");
                            sb.append("------------------------------\n");
                            if (objects[12] != null) {
                                HashMap<String, Object> setSleepTimeMap = (HashMap<String, Object>) objects[12];
                                boolean enable = (boolean) setSleepTimeMap.get("enable");
                                int startHour = (int) setSleepTimeMap.get("startHour");
                                int startMin = (int) setSleepTimeMap.get("startMin");
                                int endHour = (int) setSleepTimeMap.get("endHour");
                                int endMin = (int) setSleepTimeMap.get("endMin");
                                String period = String.format("%02d:%02d-%02d:%02d", startHour, startMin, endHour, endMin);
                                Log.e(TAG, "night mode time : enable=" + enable + ", period=" + period);
                                sb.append("enable=").append(enable).append(", period=").append(period).append('\n');
                                sb.append('\t').append("enable: ").append(enable).append('\n');
                                sb.append('\t').append("startHour: ").append(startHour).append('\n');
                                sb.append('\t').append("startMin: ").append(startMin).append('\n');
                                sb.append('\t').append("endHour: ").append(endHour).append('\n');
                                sb.append('\t').append("endMin: ").append(endMin).append('\n');
                            }
                            sb.append("------------------------------\n");

                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                        }
                        break;
                    case (byte) 0xA2:    //history_sleep
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                            // BLE_COMMAND_a2d_synData_pack(type=1) response
                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_synData_pack response\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else {
                                int sleepYear = (int) objects[0];
                                int sleepMonth = (int) objects[1];
                                int sleepDay = (int) objects[2];
                                sb.append("year: ").append(sleepYear).append('\n');
                                sb.append("month: ").append(sleepMonth).append('\n');
                                sb.append("day: ").append(sleepDay).append('\n');
                                sb.append("------------------------------\n");
                                ArrayList<HashMap<String, Object>> sleepList = (ArrayList<HashMap<String, Object>>) objects[3];
                                for (int j = 0; j < sleepList.size(); j++) {
                                    HashMap<String, Object> sleepMap = sleepList.get(j);
                                    int sleepMode = (int) sleepMap.get("sleepMode");
                                    int sleepHour = (int) sleepMap.get("sleepHour");
                                    int sleepMinute = (int) sleepMap.get("sleepMinute");
                                    Log.e(TAG, "sleepYear = " + sleepYear + " ;  sleepMonth = " + sleepMonth + " ;  sleepDay = " + sleepDay + " ;  sleepMode = " +
                                            sleepMode + " ; sleepHour = " + sleepHour + " ;  sleepMinute = " + sleepMinute);

                                    sb.append("sleepMode: ").append(sleepMode).append('\n');
                                    sb.append("sleepHour: ").append(sleepHour).append('\n');
                                    sb.append("sleepMinute: ").append(sleepMinute).append('\n');
                                    sb.append("------------------------------\n");
                                }
                            }
                            SaveLog(sb);
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                        }
                        break;
                    case (byte) 0xA3:  //history_run
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                            // BLE_COMMAND_a2d_synData_pack(type=3) response
                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_synData_pack response\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else {
                                ArrayList<HashMap<String, Object>> runList = (ArrayList<HashMap<String, Object>>) objects[0];
                                sb.append("------------------------------\n");
                                for (int j = 0; j < runList.size(); j++) {
                                    HashMap<String, Object> runMap = runList.get(j);
                                    int runYear = (int) runMap.get("year");
                                    int runMonth = (int) runMap.get("month");
                                    int runDay = (int) runMap.get("day");
                                    int runHour = (int) runMap.get("hour");
                                    int runStep = (int) runMap.get("step");
                                    double calorie = (double) runMap.get("calorie");
                                    double distance = (double) runMap.get("distance");
                                    Log.e(TAG, "runYear = " + runYear + " ;  runMonth = " + runMonth + " ;  runDay = " + runDay + " ;  runHour = " +
                                            runHour + " ; runStep = " + runStep + " ; calorie = " + calorie + " ; distance = " + distance);

                                    sb.append("year: ").append(runYear).append('\n');
                                    sb.append("month: ").append(runMonth).append('\n');
                                    sb.append("day: ").append(runDay).append('\n');
                                    sb.append("hour: ").append(runHour).append('\n');
                                    sb.append("step: ").append(runStep).append('\n');
                                    sb.append("calorie: ").append(calorie).append('\n');
                                    sb.append("distance: ").append(distance).append('\n');
                                    sb.append("------------------------------\n");

                                }
                            }
                            SaveLog(sb);
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                        }
                        break;
                    case (byte) 0xA4:      //history_heart
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                            // BLE_COMMAND_a2d_synData_pack(type=2) response
                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_synData_pack response\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else {
                                ArrayList<HashMap<String, Object>> heartList = (ArrayList<HashMap<String, Object>>) objects[0];
                                sb.append("------------------------------\n");
                                for (int j = 0; j < heartList.size(); j++) {
                                    HashMap<String, Object> runMap = heartList.get(j);
                                    int heartYear = (int) runMap.get("year");
                                    int heartMonth = (int) runMap.get("month");
                                    int heartDay = (int) runMap.get("day");
                                    int heartHour = (int) runMap.get("hour");
                                    int heartMinute = (int) runMap.get("minute");
                                    int heartSecond = (int) runMap.get("second");
                                    int heart = (int) runMap.get("heart");
                                    Log.e(TAG, "heartYear = " + heartYear + " ;  heartMonth = " + heartMonth + " ;  heartDay = " + heartDay + " ;  heartHour = " +
                                            heartHour + " ; heartMinute = " + heartMinute + " ; heartSecond = " + heartSecond + " ;  heart = " + heart);
                                    sb.append("year: ").append(heartYear).append('\n');
                                    sb.append("month: ").append(heartMonth).append('\n');
                                    sb.append("day: ").append(heartDay).append('\n');
                                    sb.append("hour: ").append(heartHour).append('\n');
                                    sb.append("minute: ").append(heartMinute).append('\n');
                                    sb.append("second: ").append(heartSecond).append('\n');
                                    sb.append("heart: ").append(heart).append('\n');
                                    sb.append("------------------------------\n");
                                }
                            }
                            SaveLog(sb);
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                        }
                        break;
                    case (byte) 0xA5:   //history_sport
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                            // BLE_COMMAND_a2d_synData_pack(type=4) response
                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_synData_pack response\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else if (objects[0] instanceof ArrayList) {
                                ArrayList<HashMap<String, Object>> sportList = (ArrayList<HashMap<String, Object>>) objects[0];
                                sb.append("------------------------------\n");
                                for (int j = 0; j < sportList.size(); j++) {
                                    HashMap<String, Object> sportMap = sportList.get(j);
                                    int sportSportYear = (int) sportMap.get("year");
                                    int sportMonth = (int) sportMap.get("month");
                                    int sportDay = (int) sportMap.get("day");
                                    int sportStartHour = (int) sportMap.get("startHour");
                                    int sportStartMin = (int) sportMap.get("startMin");
                                    int sportStartSec = 0;
                                    if (sportMap.containsKey("startSec")) {
                                        sportStartSec = (int) sportMap.get("startSec");
                                    }
                                    int sportEndHour = (int) sportMap.get("endHour");
                                    int sportEndMin = (int) sportMap.get("endMin");
                                    int sportEndSec = 0;
                                    if (sportMap.containsKey("endSec")) {
                                        sportEndSec = (int) sportMap.get("endSec");
                                    }
                                    int sportType = (int) sportMap.get("type");
                                    int sportStep = (int) sportMap.get("step");
                                    float sportCalorie = (float) sportMap.get("calorie");
                                    Log.e(TAG, "sportSportYear = " + sportSportYear + " ;  sportMonth = " + sportMonth + " ;  sportDay = " + sportDay + " ;  sportStartHour = " +
                                            sportStartHour + " ; sportStartMin = " + sportStartMin + " ; sportEndHour = " + sportEndHour + " ;  sportEndMin = " + sportEndMin +
                                            " ;  sportType = " + sportType + " ;  sportStep = " + sportStep + " ;  sportCalorie = " + sportCalorie);

                                    sb.append("year: ").append(sportSportYear).append('\n');
                                    sb.append("month: ").append(sportMonth).append('\n');
                                    sb.append("day: ").append(sportDay).append('\n');
                                    sb.append("startHour: ").append(sportStartHour).append('\n');
                                    sb.append("startMin: ").append(sportStartMin).append('\n');
                                    sb.append("endMin: ").append(sportEndMin).append('\n');
                                    if (sportMap.containsKey("startSec")) {
                                        sb.append("startSec: ").append(sportStartSec).append('\n');
                                    }
                                    sb.append("endHour: ").append(sportEndHour).append('\n');
                                    sb.append("endMin: ").append(sportEndMin).append('\n');
                                    if (sportMap.containsKey("endSec")) {
                                        sb.append("endSec: ").append(sportEndSec).append('\n');
                                    }
                                    sb.append("type: ").append(sportType).append('\n');
                                    sb.append("step: ").append(sportStep).append('\n');
                                    sb.append("calorie: ").append(sportCalorie).append('\n');
                                    sb.append("------------------------------\n");
                                }
                            } else if (objects[0] instanceof HashMap) {
                                HashMap<String, Object> gpsMap = (HashMap<String, Object>) objects[0];
                                int type = (int) gpsMap.get("type");
                                int year = (int) gpsMap.get("year");
                                int month = (int) gpsMap.get("month");
                                int day = (int) gpsMap.get("day");
                                int hour = (int) gpsMap.get("hour");
                                int minute = (int) gpsMap.get("minute");
                                int second = (int) gpsMap.get("second");
                                int sportTime = (int) gpsMap.get("sportTime");
                                int sportDistance = (int) gpsMap.get("sportDistance");
                                int sportCalorie = (int) gpsMap.get("sportCalorie");
                                int sportStep = (int) gpsMap.get("sportStep");
                                int maxHeart = (int) gpsMap.get("maxHeart");
                                int avgHeart = (int) gpsMap.get("avgHeart");
                                int minHeart = (int) gpsMap.get("minHeart");
                                int maxFrequency = (int) gpsMap.get("maxFrequency");
                                int avgFrequency = (int) gpsMap.get("avgFrequency");
                                int minFrequency = (int) gpsMap.get("minFrequency");
                                int maxPace = (int) gpsMap.get("maxPace");
                                int avgPace = (int) gpsMap.get("avgPace");
                                int minPace = (int) gpsMap.get("minPace");
                                int gpsNumber = (int) gpsMap.get("gpsNumber");
                                Log.e(TAG, "type = " + type + " ;  year = " + year + " ;  month = " + month + " ;  day = " +
                                        day + " ; hour = " + hour + " ; minute = " + minute + " ;  second = " + second +
                                        " ;  sportTime = " + sportTime + " ;  sportDistance = " + sportDistance + " ;  sportCalorie = " + sportCalorie + " ;  sportStep = " + sportStep + " ;  maxHeart = " + maxHeart + " ;  avgHeart = " +
                                        avgHeart + " ; minHeart = " + minHeart + " ; maxFrequency = " + maxFrequency + " ;  avgFrequency = " + avgFrequency +
                                        " ;  minFrequency = " + minFrequency + " ;  maxPace = " + maxPace + " ;  avgPace = " + avgPace + " ; minPace = " + minPace + " ;  gpsNumber = " + gpsNumber);

                                sb.append("type: ").append(type).append('\n');
                                sb.append("year: ").append(year).append('\n');
                                sb.append("month: ").append(month).append('\n');
                                sb.append("day: ").append(day).append('\n');
                                sb.append("hour: ").append(hour).append('\n');
                                sb.append("minute: ").append(minute).append('\n');
                                sb.append("second: ").append(second).append('\n');
                                sb.append("sportTime: ").append(sportTime).append('\n');
                                sb.append("sportDistance: ").append(sportDistance).append('\n');
                                sb.append("sportCalorie: ").append(sportCalorie).append('\n');
                                sb.append("sportStep: ").append(sportStep).append('\n');
                                sb.append("maxHeart: ").append(maxHeart).append('\n');
                                sb.append("avgHeart: ").append(avgHeart).append('\n');
                                sb.append("minHeart: ").append(minHeart).append('\n');
                                sb.append("maxFrequency: ").append(maxFrequency).append('\n');
                                sb.append("avgFrequency: ").append(avgFrequency).append('\n');
                                sb.append("minFrequency: ").append(minFrequency).append('\n');
                                sb.append("maxPace: ").append(maxPace).append('\n');
                                sb.append("avgPace: ").append(avgPace).append('\n');
                                sb.append("minPace: ").append(minPace).append('\n');
                                sb.append("gpsNumber: ").append(gpsNumber).append('\n');

                                ArrayList<String> gpsList = (ArrayList) gpsMap.get("gpsList");
                                sb.append("------------------------------\n");
                                for (int j = 0; gpsList != null && j < gpsList.size(); j++) {
                                    String gpsData = gpsList.get(j);
                                    Log.e(TAG, "gpsDat = " + gpsData);

                                    sb.append("gpsDat: ").append(gpsData).append('\n');
                                    sb.append("------------------------------\n");
                                }
                            }
                            SaveLog(sb);
                            EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                        }
                        break;
                    case 0x10ABA00:   //history_sport
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                            // BLE_COMMAND_a2d_synData_pack(type=4) response
                            StringBuilder sb = new StringBuilder("BLE_COMMAND_a2d_synData_pack response\n\n");
                            if (objects[0] instanceof String && objects[0].equals("")) {   //判断数据是否为空
                                Log.e(TAG, getString(R.string.data_empty));
                                sb.append("the data is empty");
                            } else if (objects[0] instanceof ArrayList) {
                                ArrayList<HashMap<String, Object>> sportsMapList = (ArrayList<HashMap<String, Object>>) objects[0];
                                for (HashMap<String, Object> sportsMap : sportsMapList) {
                                    for (Map.Entry<String, Object> entry : sportsMap.entrySet()) {
                                        if (entry.getValue() instanceof ArrayList) {
                                            sb.append('\t').append("-------").append(entry.getKey()).append("------------\n");
                                            for (Map<String, Object> map : (ArrayList<HashMap<String, Object>>) entry.getValue()) {
                                                for (Map.Entry<String, Object> entry2 : sportsMap.entrySet()) {
                                                    sb.append("\t\t").append(entry2.getKey()).append(": ").append(entry2.getValue()).append('\n');
                                                }
                                                sb.append("\t------------------------------\n");
                                            }
                                        } else {
                                            sb.append('\t').append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
                                        }
                                        sb.append("------------------------------\n");
                                    }
                                }
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, sb.toString()));
                            }
                        }
                        break;
                    case (byte) 0xAB:   //real_heart
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                            // realtime heart rate report
                            if (objects[0] instanceof String && objects[0].equals("")) {
                                Log.e(TAG, getString(R.string.data_empty));
                            } else {
                                Log.e(TAG, "heart = " + (int) objects[0]);
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, "heart: " + objects[0]));
                            }
                        }
                        break;
                    case (byte) 0xAC:  //real_run
                        if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                            // realtime run info report
                            if (objects[0] instanceof String && objects[0].equals("")) {
                                Log.e(TAG, getString(R.string.data_empty));
                            } else {
                                int run = (int) objects[0];
                                float calorie = (float) objects[1];
                                float distance = (float) objects[2];
                                Log.e(TAG, "run = " + run + " ; calorie = " + calorie + " ;  distance = " + distance);
                                EventBus.getDefault().post(new MessageEvent(MessageEvent.DEVICE_NOTI_INFO, "run: " + run + ", calorie: " + calorie + ", distance: " + distance));
                            }
                        }
                        break;
                    case (byte) 0xb3:
                        Log.e(TAG, getString(R.string.syn_motion_status_response));
                        break;
                    case (byte) 0xb4:
                        Log.e(TAG, "" + objects[0] + objects[1]);
                        break;
                    case (byte) 0x43:
                        if (objects != null && objects.length > 0) {
                            if ((int) objects[0] == 0) {
                                Log.e(TAG, getString(R.string.unbind_device_success));
                            } else {
                                Log.e(TAG, getString(R.string.unbind_device_fail));
                            }
                        } else {
                            Log.e(TAG, getString(R.string.unbind_device_response));
                        }
                        break;
                    case (byte) 0x45:
                        if (objects != null && objects.length > 0) {
                            if ((int) objects[0] == 0) {
                                Log.e(TAG, getString(R.string.bind_device_success));
                            } else {
                                Log.e(TAG, getString(R.string.bind_device_fail));
                            }
                        } else {
                            Log.e(TAG, getString(R.string.bind_device_response));
                        }
                        break;
                    case (byte) 0x50:
                        // BLE_COMMAND_a2d_findDevice_pack response
                        EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO, "BLE_COMMAND_a2d_findDevice_pack response\n\ndone"));
                        break;

                    case 0x01FEF200:
                        // BLE_COMMAND_a2d_sendEraseFlashBeforeFirmwareUpgradeForBK_pack response
                        EventBus.getDefault().post(new Event.DFU.EraseFlashBeforeFirmwareUpgradeForBKResponse((boolean) objects[0]));
                        break;

                    case (byte) 0x41: {
                        int battery = (int) objects[0];
                        int batteryType = (int) objects[1];
                        Log.e(TAG, "battery = " + battery + " ;  batteryType = " + batteryType);
                        EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO,
                                "BLE_COMMAND_a2d_getBatteryStatus_pack response\n\nbattery: " + battery + "\nstatus=" + batteryType));
                    }
                    break;

                    // 自定义表盘 custom clock dial
                    case 0x1160200: // 获取当前表盘状态 回响
                        // BLE_COMMAND_a2d_customClockDialInquireCurrentStatus response
                        if (objects[0] instanceof String && ((String) objects[0]).isEmpty()) {
                            Log.e(TAG, "InquireCurrentStatusResponse: error!");
                        } else {
                            boolean supportCustom = (boolean) objects[0];
                            boolean supportSwitchDial = (boolean) objects[1];
                            boolean supportSetBackground = (boolean) objects[2];
                            int currentDialId = (int) objects[3];
                            Log.e(TAG, "InquireCurrentStatusResponse: supportCustom: " + supportCustom + ", supportSwitchDial: " + supportSwitchDial + ", supportSetBackground: " + supportSetBackground + ", currentDialId: " + currentDialId);
                            EventBus.getDefault().post(new Event.CustomClockDial.InquireCurrentStatusResponse(supportCustom, supportSwitchDial, supportSetBackground, currentDialId));
                        }
                        break;
                    case 0x1160201: // 获取当前表盘列表 回响
                        // BLE_COMMAND_a2d_customClockDialInquireCurrentList response
                        if (objects[0] instanceof String && ((String) objects[0]).isEmpty()) {
                            Log.e(TAG, "InquireCurrentListResponse: error!");
                        } else {
                            int numberOfCustomDialRoom = (int) objects[0];
                            int numberOfPresetDialRoom = (int) objects[1];
                            int numberOfUsedCustomDialRoom = (int) objects[2];
                            int[] presetArray = (int[]) objects[3];
                            int[] customArray = (int[]) objects[4];

                            String presetArrayStr = "[";
                            String sep = "";
                            for (int id : presetArray) {
                                presetArrayStr += sep + "0x" + Integer.toHexString(id);
                                sep = ",";
                            }
                            presetArrayStr += "]";
                            sep = "";
                            String customArrayStr = "[";
                            for (int id : customArray) {
                                customArrayStr += sep + "0x" + Integer.toHexString(id);
                                sep = ",";
                            }
                            customArrayStr += "]";
                            Log.e(TAG, "InquireCurrentStatusResponse: numberOfCustomDialRoom: " + numberOfCustomDialRoom + ", numberOfPresetDialRoom: " + numberOfPresetDialRoom + ", numberOfUsedCustomDialRoom: " + numberOfUsedCustomDialRoom + ", presetList: " + presetArrayStr + ", customList: " + customArrayStr);

                            EventBus.getDefault().post(new Event.CustomClockDial.InquireCurrentListResponse(numberOfCustomDialRoom, numberOfPresetDialRoom, numberOfUsedCustomDialRoom, presetArray, customArray));
                        }
                        break;
                    case 0x1160202: // 获取所支持的表盘信息 回响
                        // BLE_COMMAND_a2d_customClockDialInquireCompatInfo response
                        if (objects[0] instanceof String && ((String) objects[0]).isEmpty()) {
                            Log.e(TAG, "InquireCompatInfoResponse: error!");
                        } else {
                            int magic = (int) objects[0];
                            int supportVersion = (int) objects[1];
                            int lcdWidth = (int) objects[2];
                            int lcdHeight = (int) objects[3];
                            int colorMode = (int) objects[4];
                            long freeSpace = (long) objects[5];

                            Log.e(TAG, "InquireCompatInfoResponse: magic: " + magic + ", supportVersion: " + supportVersion + ", lcdWH: " + lcdWidth + "x" + lcdHeight + ", colorMode: " + colorMode + ", freeSpace: " + freeSpace);

                            EventBus.getDefault().post(new Event.CustomClockDial.InquireCompatInfoResponse(magic, supportVersion, lcdWidth, lcdHeight, colorMode, freeSpace));
                        }
                        break;
                    case 0x1160400: // 切换表盘 回响
                        // BLE_COMMAND_a2d_customClockDialSwitchTo response
                        if (objects[0] instanceof String && ((String) objects[0]).isEmpty()) {
                            Log.e(TAG, "InquireCompatInfoResponse: error!");
                        } else {
                            int status = (int) objects[0];

                            Log.e(TAG, "InquireCompatInfoResponse: status: " + status);

                            EventBus.getDefault().post(new Event.CustomClockDial.SwitchToResponse(status));
                        }
                        break;
                    case 0x1160401: // 删除表盘 回响
                        // BLE_COMMAND_a2d_customClockDialDelete response
                        if (objects[0] instanceof String && ((String) objects[0]).isEmpty()) {
                            Log.e(TAG, "InquireCompatInfoResponse: error!");
                        } else {
                            int status = (int) objects[0];

                            Log.e(TAG, "InquireCompatInfoResponse: status: " + status);

                            EventBus.getDefault().post(new Event.CustomClockDial.DeleteResponse(status));
                        }
                        break;
                    case 0x1160402: // 设置背景图 回响
                        // BLE_COMMAND_a2d_customClockDialRequireSetBackground response
                        if (objects[0] instanceof String && ((String) objects[0]).isEmpty()) {
                            Log.e(TAG, "InquireCompatInfoResponse: error!");
                        } else {
                            int status = (int) objects[0];

                            Log.e(TAG, "InquireCompatInfoResponse: status: " + status);

                            EventBus.getDefault().post(new Event.CustomClockDial.RequireSetBackgroundResponse(status));
                        }
                        break;
                    case 0x1160403: // 推送新表盘 回响
                        // BLE_COMMAND_a2d_customClockDialRequirePushDial response
                        if (objects[0] instanceof String && ((String) objects[0]).isEmpty()) {
                            Log.e(TAG, "InquireCompatInfoResponse: error!");
                        } else {
                            int status = (int) objects[0];

                            Log.e(TAG, "InquireCompatInfoResponse: status: " + status);

                            EventBus.getDefault().post(new Event.CustomClockDial.RequirePushDialResponse(status));
                        }
                        break;

                    // GPS 互联
                    case 0x01190100: // 设备申请GPS定位
                        if (objects.length >= 11) {
                            int transactionId = (int) objects[0];
                            int locationInterval = (int) objects[1];
                            int firstLocationTimeout = (int) objects[2];
                            int year = (int) objects[3];
                            int month = (int) objects[4];
                            int day = (int) objects[5];
                            int hour = (int) objects[6];
                            int minute = (int) objects[7];
                            int second = (int) objects[8];
                            long timestamp = (long) objects[9];
                            boolean gpsPointSendToDevice = (boolean) objects[10];

                            startGPSInterconn(transactionId, locationInterval, firstLocationTimeout, year, month, day, hour, minute, second, timestamp, gpsPointSendToDevice);
                        } else {
                            Log.e(TAG, "0x01190100 invalid!");
                        }
                        break;
                    case 0x01190400: // APP向设备发送GPS坐标，设备的回响
                        if (objects.length >= 2) {
                            int transactionId = (int) objects[0];
                            boolean stopLocation = (boolean) objects[1];
                            if (stopLocation) {
                                stopGPSInterconn();
                            }
                        } else {
                            Log.e(TAG, "0x01190400 invalid!");
                            stopGPSInterconn();
                        }
                        break;
                    case 0x01190500: //	设备告知APP运动结束
                    {
                        stopGPSInterconn();
                        if (mSportStartTime != null) {
                            // calc the distance and pace
                            int distance = 5000; // Unit: m
                            int pace = 555; // Unit: s
                            byte[] pack = BLEBluetoothManager.BLE_COMMAND_a2d_GPSInterconnTellSportInfo_pack(mSportStartTime, distance, pace);
                            KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                            mSportStartTime = null;
                        }
                    }
                    break;

                    case 0x010CCE00: // BLE_COMMAND_a2d_renameDevice_pack response
                    {
                        Boolean success = (Boolean) objects[0];

                        Log.e(TAG, "renameDevice: " + success);
                        EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO,
                                "BLE_COMMAND_a2d_renameDevice_pack response\n\nsuccess: " + success));
                    }
                    break;

                    case 0x01045600: // BLE_COMMAND_a2d_bondAuth_pack response
                    {
                        Boolean granted = (Boolean) objects[0];

                        Log.e(TAG, "bondAuth: granted=" + granted);
                        EventBus.getDefault().post(new MessageEvent(MessageEvent.RSP_INFO,
                                "BLE_COMMAND_a2d_bondAuth_pack response\n\ngranted: " + granted));

                        if (granted != null && !granted) {
                            KCTBluetoothManager.getInstance().disConnect_a2d();
                        }
                    }
                    break;
                }
            }
        }
    };

    private BroadcastReceiver mBroadcastReceiver;
    private IConnectListener iConnectListener = new IConnectListener() {
        @Override
        public void onConnectState(int state) {   //
            EventBus.getDefault().post(new MessageEvent(MessageEvent.CONNECT_STATE, state));
            if (state == KCTBluetoothManager.STATE_CONNECT_FAIL) {
                if (!isDFU) {
                    SharedPreferences preferences = mContext.getSharedPreferences("bluetooth", 0);
                    String addr = preferences.getString("address", null);
                    boolean reconnect = preferences.getBoolean("reconnect", false);
                    if (!TextUtils.isEmpty(addr) && reconnect) {
                        mExecutor.cancel(mReconnectTask);
                        mExecutor.executeDelayed(mReconnectTask, 3000);
                    }
                }
            }
        }

        @Override
        public void onConnectDevice(BluetoothDevice device) {
            SharedPreferences preferences = mContext.getSharedPreferences("bluetooth", 0);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("address", device.getAddress());
            editor.putString("addressName", device.getName());
            editor.apply();
            EventBus.getDefault().post(new MessageEvent(MessageEvent.CONNECT_DEVICE, device));
        }

        @Override
        public void onScanDevice(BluetoothLeDevice device) {
            if (!isDFU) {
                SharedPreferences preferences = mContext.getSharedPreferences("bluetooth", 0);
                String address = preferences.getString("address", "");
                boolean reconnect = preferences.getBoolean("reconnect", false);
                if (reconnect && device.getAddress().equals(address)) {
                    Log.d(TAG, "reconnect address=" + address);
                    Log.d(TAG, "device address=" + device.getAddress());

                    KCTBluetoothManager.getInstance().scanDevice(false);
                    preferences.edit()
                            .putString("address", device.getAddress())
                            .putString("addressName", device.getName())
                            .putInt("deviceType", device.getDeviceType())
                            .apply();
                    KCTBluetoothManager.getInstance().connect(device.getDevice(), device.getDeviceType());
                }
            }
        }

        @Override
        public void onCommand_d2a(byte[] bytes) {
            Log.i(TAG, "onCommand_d2a: " + Utils.bytesToHex(bytes));
            EventBus.getDefault().post(new MessageEvent(MessageEvent.RECEIVE_DATA, bytes));
            if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                KCTBluetoothCommand.getInstance().d2a_MTK_command(bytes, iReceiveCallback);
            } else {
                KCTBluetoothCommand.getInstance().d2a_command_Parse(mContext, bytes, iReceiveCallback);
            }

        }
    };

    private void unRegisterReceiver() {
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
    }

    private void registerReceiver() {
        if (mBroadcastReceiver == null) {
            mBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (!TextUtils.isEmpty(action)) {
                        switch (action) {
                            case BluetoothAdapter.ACTION_STATE_CHANGED: {
                                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                                if (state == BluetoothAdapter.STATE_ON) {
//                                    mExecutor.cancel(mReconnectTask);
//                                    mExecutor.executeDelayed(mReconnectTask, 5000);
                                    SharedPreferences preferences = mContext.getSharedPreferences("bluetooth", 0);
                                    String addr = preferences.getString("address", null);
                                    boolean reconnect = preferences.getBoolean("reconnect", false);
                                    if (!TextUtils.isEmpty(addr) && reconnect) {
                                        KCTBluetoothManager.getInstance().scanDevice(true);
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            };

            registerReceiver(mBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        }
    }

    private void SaveLog(StringBuilder sb) {
        try {
            // 目前時間
            Date date = new Date();
            // 設定日期格式
            SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HH:mm:ss");
            // 進行轉換
            String dateString = sdf.format(date);

            String file_path = Environment
                    .getExternalStorageDirectory()
                    .getAbsolutePath();
            String filename = "demo_log.txt";
            String writeText = "Log Time " + dateString + "\n";

            File file = new File(file_path, filename);
            if (!file.exists()) {
                file.createNewFile();
            }
//                        FileWriter fileWritter = new FileWriter(file); // overwrite
            FileWriter fileWritter = new FileWriter(file, true); // append
            BufferedWriter bufferWritter = new BufferedWriter(
                    fileWritter);
            bufferWritter.write(writeText + "\n");
            bufferWritter.write(sb + "\n");

            //使用缓冲区中的方法，将数据刷新到目的地文件中去。
            bufferWritter.flush();
            //关闭缓冲区,同时关闭了fw流对象
            bufferWritter.close();
        } catch (Exception e) {
            System.out.println(e);
        }

    }



    private Date mSportStartTime;

    private void startGPSInterconn(int transactionId, int locationInterval, int firstLocationTimeout,
                                   int year, int month, int day, int hour, int minute, int second, long timestamp,
                                   boolean gpsPointSendToDevice) {
        Log.i(TAG, String.format("startGPSInterconn(%d, %d, %d, %d-%d-%d %d:%d:%d (%d), %b", transactionId, locationInterval, firstLocationTimeout, year, month, day, hour, minute, second, timestamp, gpsPointSendToDevice));
        if (firstLocationTimeout > 0) {
            mHandler.execDelayed(mStartGPSInterconnLocationTimer, firstLocationTimeout * 1000L);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            byte[] rsp = BLEBluetoothManager.BLE_COMMAND_a2d_GPSInterconnAnswerRequire_pack(transactionId, 0); // 有定位权限
            KCTBluetoothManager.getInstance().sendCommand_a2d(rsp);

            // start location

            if (mGPSInterconnLocationListener != null) {
                if (mGPSInterconnLocationListener.transactionId != transactionId) {
                    mLocationManager.removeUpdates(mGPSInterconnLocationListener);
                }
            }
            mSportStartTime = new Date(timestamp);
            mGPSInterconnLocationListener = new GPSInterconnLocationListener(transactionId, locationInterval, mSportStartTime, gpsPointSendToDevice);
            //mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, locationInterval, 0, mGPSInterconnLocationListener);
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, locationInterval, 0, mGPSInterconnLocationListener);
        } else {
            // location permission request
//            BaseActivity activity = BaseActivity.getCurrentActivity();
//            if (activity != null) {
//                byte[] rsp = BLEBluetoothManager.BLE_COMMAND_a2d_GPSInterconnAnswerRequire_pack(transactionId, 0); // 准备中
//                KCTBluetoothManager.getInstance().sendCommand_a2d(rsp);
//                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, BaseActivity.requestCode_GPSInterconn);
//            } else {
            byte[] rsp = BLEBluetoothManager.BLE_COMMAND_a2d_GPSInterconnAnswerRequire_pack(transactionId, 2); // 无定位权限
            KCTBluetoothManager.getInstance().sendCommand_a2d(rsp);
//            }
        }
    }

    private Runnable mStartGPSInterconnLocationTimer = new Runnable() {
        @Override
        public void run() {
            stopGPSInterconn();
        }
    };

    private GPSInterconnLocationListener mGPSInterconnLocationListener;

    private class GPSInterconnLocationListener implements LocationListener {
        final int transactionId;
        final int locationInterval;
        final Date sportStartTime;
        final boolean gpsPointSendToDevice;

        GPSInterconnLocationListener(int transactionId, int locationInterval, Date sportStartTime, boolean gpsPointSendToDevice) {
            this.transactionId = transactionId;
            this.locationInterval = locationInterval;
            this.sportStartTime = sportStartTime;
            this.gpsPointSendToDevice = gpsPointSendToDevice;
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.i(TAG, "onLocationChanged(" + location + ") for " + transactionId);

            mHandler.cancel(mStartGPSInterconnLocationTimer);
            if (gpsPointSendToDevice) {
                mHandler.execDelayed(mStartGPSInterconnLocationTimer, locationInterval * 3 * 1000L);
                byte[] gpsPack = BLEBluetoothManager.BLE_COMMAND_a2d_GPSInterconnTellLocation_pack(transactionId, location);
                KCTBluetoothManager.getInstance().sendCommand_a2d(gpsPack);
            } else {
                GPSInterconn entity = new GPSInterconn();
                entity.setTransactionId(transactionId);
                entity.setSportStartTime(sportStartTime);
                entity.setLocationTime(new Date());
                entity.setLatitude(location.getLatitude());
                entity.setLongitude(location.getLongitude());
                KCTApp.getInstance().getDaoSession().getGPSInterconnDao().insert(entity);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    }

    private void stopGPSInterconn() {
        mHandler.cancel(mStartGPSInterconnLocationTimer);
        if (mGPSInterconnLocationListener != null) {
            mLocationManager.removeUpdates(mGPSInterconnLocationListener);
            mGPSInterconnLocationListener = null;
        }
    }


    private Handler mHandler = new Handler(this);

    private static class Handler extends android.os.Handler {
        private static final int EXEC_RUNNABLE = 0;

        private WeakReference<KCTBluetoothService> mS;

        private Handler(KCTBluetoothService s) {
            mS = new WeakReference<>(s);
        }

        @Override
        public void handleMessage(Message msg) {
            KCTBluetoothService s = mS.get();
            if (s == null) {
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
