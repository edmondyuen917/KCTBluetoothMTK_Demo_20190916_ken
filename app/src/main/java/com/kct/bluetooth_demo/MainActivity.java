package com.kct.bluetooth_demo;


import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.command.BLEBluetoothManager;
import com.mediatek.ctrl.notification.NotificationController;
import com.mediatek.ctrl.notification.NotificationData;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private Button button;
    private TextView textView_send,textView_receive,textView_device,textView_connectState;
    private static final String TAG = MainActivity.class.getSimpleName();
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE
    };


    /*
            debug 1 again
            debug 2
            debug 3
            debug 4



     */


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServiceEventMainThread(MessageEvent messageEvent){
        if(messageEvent != null && messageEvent.getMessage() != null){
            switch (messageEvent.getMessage()){
                case MessageEvent.RECEIVE_DATA:
                    textView_receive.setText("RECEIVE_DATA : " + Arrays.toString((byte[])messageEvent.getObject()));
                    break;
                case MessageEvent.CONNECT_DEVICE:
                    textView_device.setText("CONNECT_DEVICE : " + ((BluetoothDevice)messageEvent.getObject()).getName());
                    break;
                case MessageEvent.CONNECT_STATE:
                    int connect_state = (int) messageEvent.getObject();
                    if(connect_state == KCTBluetoothManager.STATE_NONE){
                        textView_connectState.setText(getString(R.string.connect_none));
                    }else if(connect_state == KCTBluetoothManager.STATE_CONNECTING){
                        textView_connectState.setText(getString(R.string.connecting));
                    }else if(connect_state == KCTBluetoothManager.STATE_CONNECTED){
                        textView_connectState.setText(getString(R.string.connected));
                    }else if(connect_state == KCTBluetoothManager.STATE_CONNECT_FAIL){
                        textView_connectState.setText(getString(R.string.connect_fail));
                    }
                    break;

                case MessageEvent.RSP_INFO:
                    textView_receive.setText("RSP_INFO : " + (String) messageEvent.getObject());
                    break;

                case MessageEvent.DEVICE_NOTI_INFO:
                    textView_receive.setText("DEVICE_NOTI_INFO : " + (String) messageEvent.getObject());
                    break;
            }

        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EventBus.getDefault().register(this);
        button = (Button) findViewById(R.id.connect);
        button.setOnClickListener(this);
        textView_send = (TextView) findViewById(R.id.textView_send_data);
        textView_receive = (TextView) findViewById(R.id.textView_receive_data);
        textView_device = (TextView) findViewById(R.id.textView_device);
        textView_connectState = (TextView) findViewById(R.id.textView_connectState);

        if(Build.VERSION.SDK_INT >= 23 ) {
            if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED) ;
            if (this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED) ;
            requestPermissions(PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE);
        }

        // define button here

        findViewById(R.id.test).setOnClickListener(this);
        findViewById(R.id.connect).setOnClickListener(this);
        findViewById(R.id.disConnect).setOnClickListener(this);
        findViewById(R.id.checkDelay).setOnClickListener(this);
        findViewById(R.id.syn_time).setOnClickListener(this);
        findViewById(R.id.syn_mtk_birthday).setOnClickListener(this);
        findViewById(R.id.syn_firmware).setOnClickListener(this);
        findViewById(R.id.find_device).setOnClickListener(this);
        findViewById(R.id.syn_brace).setOnClickListener(this);
        findViewById(R.id.syn_run).setOnClickListener(this);
        findViewById(R.id.syn_sleep).setOnClickListener(this);
        findViewById(R.id.syn_heart).setOnClickListener(this);
        findViewById(R.id.syn_blood).setOnClickListener(this);
        findViewById(R.id.syn_oxygen).setOnClickListener(this);
        findViewById(R.id.syn_sport).setOnClickListener(this);
        findViewById(R.id.syn_realRun).setOnClickListener(this);
        findViewById(R.id.syn_realHeart).setOnClickListener(this);
        findViewById(R.id.syn_userInfo).setOnClickListener(this);
        findViewById(R.id.syn_camera).setOnClickListener(this);
        findViewById(R.id.syn_unit).setOnClickListener(this);
        findViewById(R.id.syn_notification).setOnClickListener(this);
        findViewById(R.id.syn_call).setOnClickListener(this);
        findViewById(R.id.syn_sms).setOnClickListener(this);
        findViewById(R.id.syn_clock).setOnClickListener(this);
        findViewById(R.id.syn_water).setOnClickListener(this);
        findViewById(R.id.syn_longSit).setOnClickListener(this);
        findViewById(R.id.syn_noDir).setOnClickListener(this);
        findViewById(R.id.firmware_upgrade).setOnClickListener(this);
        findViewById(R.id.syn_motion_state_device).setOnClickListener(this);    //运动状态同步到设备
        findViewById(R.id.syn_sport_motion_setting).setOnClickListener(this);    //运动目标设置
        findViewById(R.id.gesture_control).setOnClickListener(this);    //手势智控设置
        findViewById(R.id.heart_rate_monitoring_setup).setOnClickListener(this); //心率监测设置
        findViewById(R.id.syn_bind_device).setOnClickListener(this);    //设备绑定请求
        findViewById(R.id.syn_unbind_device).setOnClickListener(this);    //解除绑定请求
        findViewById(R.id.device_reset).setOnClickListener(this);    //恢复出厂
        findViewById(R.id.syn_hid_connect).setOnClickListener(this);    //HID蓝牙连接
        findViewById(R.id.syn_hid_disConnect).setOnClickListener(this);    //HID断开连接
        findViewById(R.id.push_agps).setOnClickListener(this);
        findViewById(R.id.custom_clock_dial).setOnClickListener(this);
        findViewById(R.id.set_night_mode).setOnClickListener(this); // 设置夜间模式
        findViewById(R.id.syn_weather).setOnClickListener(this); // 同步天气
        findViewById(R.id.get_battery).setOnClickListener(this); // 获取电池状态
        findViewById(R.id.rename_device_bt_name).setOnClickListener(this); // 重命名设备蓝牙名称
        findViewById(R.id.bond_auth).setOnClickListener(this); // 让设备弹出绑定授权界面
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    // define button click event

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.test:
                Toast.makeText(this,"Test",Toast.LENGTH_SHORT).show();
                break;
            case R.id.connect:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED){
                    Toast.makeText(this,getString(R.string.please_disconnect),Toast.LENGTH_SHORT).show();
                }else {
                    getSharedPreferences("bluetooth", 0)
                            .edit()
                            .putBoolean("reconnect", false)
                            .apply();
                    startActivity(new Intent(this, DeviceScanActivity.class));
                }
                break;
            case R.id.disConnect: {
                getSharedPreferences("bluetooth", 0)
                        .edit()
                        .putBoolean("reconnect", false)
                        .apply();
                KCTBluetoothManager.getInstance().disConnect_a2d();
            }
                break;
            case R.id.checkDelay: {
                rxSyncWorkouts();
            }
            case R.id.syn_time:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] setTime_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        setTime_pack = BLEBluetoothManager.BLE_COMMAND_a2d_settime_pack();
                    } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        setTime_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKTime_pack(this);
                    }
                    if (setTime_pack != null) {
                        textView_send.setText(Arrays.toString(setTime_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(setTime_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.syn_mtk_birthday:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] setTime_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        Toast.makeText(this,getString(R.string.device_not_support),Toast.LENGTH_SHORT).show();
                        return;
                    } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        setTime_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKSynUserInfoBirthday_pack(this, new Date());
                    }
                    if (setTime_pack != null) {
                        textView_send.setText(Arrays.toString(setTime_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(setTime_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.syn_firmware:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] firmwareData_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        firmwareData_pack = BLEBluetoothManager.BLE_COMMAND_a2d_getFirmwareData_pack();
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        firmwareData_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKConfig_pack();
                    }
                    textView_send.setText(Arrays.toString(firmwareData_pack));
                    textView_receive.setText("");
                    KCTBluetoothManager.getInstance().sendCommand_a2d(firmwareData_pack);
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.find_device:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] findDevice_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        findDevice_pack = BLEBluetoothManager.BLE_COMMAND_a2d_findDevice_pack();
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        findDevice_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKFindDevice_pack(1);
                    }
                    textView_send.setText(Arrays.toString(findDevice_pack));
                    textView_receive.setText("");
                    KCTBluetoothManager.getInstance().sendCommand_a2d(findDevice_pack);
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_brace:  //syn Smart bracelet Info
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] braceletSet_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        braceletSet_pack = BLEBluetoothManager.BLE_COMMAND_a2d_getBraceletSet_pack();
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        braceletSet_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKConfig_pack();
                    }
                    textView_send.setText(Arrays.toString(braceletSet_pack));
                    textView_receive.setText("");
                    KCTBluetoothManager.getInstance().sendCommand_a2d(braceletSet_pack);
                }else {
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_run:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] run_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        run_pack = BLEBluetoothManager.BLE_COMMAND_a2d_synData_pack(3, simpleDateFormat.format(new Date()));
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        run_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKBurstRun_pack();
                    }
                    textView_send.setText(Arrays.toString(run_pack));
                    textView_receive.setText("");
                    KCTBluetoothManager.getInstance().sendCommand_a2d(run_pack);
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_sleep:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] sleep_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        Calendar yesterday = Calendar.getInstance();
                        yesterday.add(Calendar.DAY_OF_YEAR, -1);
                        sleep_pack = BLEBluetoothManager.BLE_COMMAND_a2d_synData_pack(1, simpleDateFormat.format(yesterday.getTime()));
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        sleep_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKBurstSleep_pack();
                    }
                    textView_send.setText(Arrays.toString(sleep_pack));
                    textView_receive.setText("");
                    KCTBluetoothManager.getInstance().sendCommand_a2d(sleep_pack);
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_heart:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] heart_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        heart_pack = BLEBluetoothManager.BLE_COMMAND_a2d_synData_pack(2, simpleDateFormat.format(new Date()));
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        heart_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKHeart_pack();
                    }
                    textView_send.setText(Arrays.toString(heart_pack));
                    textView_receive.setText("");
                    KCTBluetoothManager.getInstance().sendCommand_a2d(heart_pack);
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_sport:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] sport_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        sport_pack = BLEBluetoothManager.BLE_COMMAND_a2d_synData_pack(4, simpleDateFormat.format(new Date()));
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        sport_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKSportIndex_pack();
                    }
                    textView_send.setText(Arrays.toString(sport_pack));
                    textView_receive.setText("");
                    KCTBluetoothManager.getInstance().sendCommand_a2d(sport_pack);
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_realRun:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] realRun_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        realRun_pack = BLEBluetoothManager.BLE_COMMAND_a2d_synRealData_pack(3);
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        realRun_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKCurrentAllRun_pack();
                        // this command will response with IReceiveListener.onReceive(17, true, "xxx,xxx,xxx,...")
                        // see KCTBluetoothService.iReceiveCallback:
                        //     case 0x11:
                        //         }else if(KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK){
                        //
                    }
                    textView_send.setText(Arrays.toString(realRun_pack));
                    textView_receive.setText("");
                    KCTBluetoothManager.getInstance().sendCommand_a2d(realRun_pack);
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_realHeart:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] realHeart_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        realHeart_pack = BLEBluetoothManager.BLE_COMMAND_a2d_synRealData_pack(2);
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        realHeart_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKHeart_pack();
                    }
                    textView_send.setText(Arrays.toString(realHeart_pack));
                    textView_receive.setText("");
                    KCTBluetoothManager.getInstance().sendCommand_a2d(realHeart_pack);
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_blood:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] blood_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        Toast.makeText(this,getString(R.string.device_not_support),Toast.LENGTH_SHORT).show();
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        blood_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKPressure_pack();
                    }
                    if(blood_pack != null) {
                        textView_send.setText(Arrays.toString(blood_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(blood_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_oxygen:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] oxygen_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        Toast.makeText(this,getString(R.string.device_not_support),Toast.LENGTH_SHORT).show();
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        oxygen_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKOxyen_pack();
                    }
                    if(oxygen_pack != null) {
                        textView_send.setText(Arrays.toString(oxygen_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(oxygen_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_userInfo:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] userInfo_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        HashMap<String,Object> userInfoMap = new HashMap<>();
                        userInfoMap.put("sex",1);
                        userInfoMap.put("weight",60);
                        userInfoMap.put("height",170);
                        userInfoMap.put("age",18);
                        userInfoMap.put("goal",10000);
                        userInfo_pack = BLEBluetoothManager.BLE_COMMAND_a2d_setInformation_pack(this,userInfoMap);
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        userInfo_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKSynUserInfo_pack(this,10000,1,170,60);
                    }
                    if(userInfo_pack != null) {
                        textView_send.setText(Arrays.toString(userInfo_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(userInfo_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_camera:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] camera_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        camera_pack =  BLEBluetoothManager.BLE_COMMAND_a2d_setTakePhoto_pack(1);
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        camera_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKCamera_pack(1);
                    }
                    if(camera_pack != null) {
                        textView_send.setText(Arrays.toString(camera_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(camera_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_unit:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] unit_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        unit_pack =  BLEBluetoothManager.BLE_COMMAND_a2d_setUnit_pack(0,0);
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        unit_pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKUnit_pack(0,0);
                    }
                    if(unit_pack != null) {
                        textView_send.setText(Arrays.toString(unit_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(unit_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_notification:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] call_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        call_pack =  BLEBluetoothManager.BLE_COMMAND_a2d_sendNotificationData_pack(2,"It is qq notification message");
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        NotificationData notificationData = new NotificationData();
                        notificationData.setTextList(new String[]{"notification","notification message"});
                        notificationData.setPackageName("com.tencent.mobileqq");
                        notificationData.setTickerText("notification message");
                        notificationData.setGroupKey("");
                        notificationData.setAppID("4");
                        notificationData.setTag("");
                        notificationData.setWhen(System.currentTimeMillis());
                        notificationData.setMsgId(121);
                        notificationData.setActionsList(null);
                        NotificationController.getInstance(this).sendNotfications(notificationData);
                    }
                    if(call_pack != null) {
                        textView_send.setText(Arrays.toString(call_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(call_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_call:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] call_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        call_pack =  BLEBluetoothManager.BLE_COMMAND_a2d_sendNotificationData_pack(0,"It is call message");
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        NotificationController.getInstance(this).sendCallMessage("123456789","call","call message",1);
                    }
                    if(call_pack != null) {
                        textView_send.setText(Arrays.toString(call_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(call_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_sms:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] sms_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        sms_pack =  BLEBluetoothManager.BLE_COMMAND_a2d_sendNotificationData_pack(1,"It is sms message");
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        NotificationController.getInstance(this).sendSmsMessage("sms message","123456789");
                    }
                    if(sms_pack != null) {
                        textView_send.setText(Arrays.toString(sms_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(sms_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_clock:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] clock_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        ArrayList<HashMap<String,Object>> list = new ArrayList<>();
                        HashMap<String,Object> clockMap = new HashMap<>();
                        clockMap.put("enable",true);
                        clockMap.put("hour",9);
                        clockMap.put("minute",0);
                        clockMap.put("repeat","1001111");
                        clockMap.put("type",1);

                        HashMap<String,Object> clockMap1 = new HashMap<>();
                        clockMap1.put("enable",true);
                        clockMap1.put("hour",10);
                        clockMap1.put("minute",0);
                        clockMap1.put("repeat","1011010");
                        clockMap1.put("type",1);

                        list.add(clockMap);
                        list.add(clockMap1);
                        clock_pack = BLEBluetoothManager.BLE_COMMAND_a2d_setAlarmClock_pack(list);
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        Toast.makeText(this,getString(R.string.device_not_support),Toast.LENGTH_SHORT).show();
                    }
                    if(clock_pack != null) {
                        textView_send.setText(Arrays.toString(clock_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(clock_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_water:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] water_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        HashMap<String,Object> waterMap = new HashMap<>();
                        waterMap.put("enable",false);
                        waterMap.put("startHour",9);
                        waterMap.put("startMin",0);
                        waterMap.put("endHour",11);
                        waterMap.put("endMin",0);
                        waterMap.put("repeat", "1010111");
                        waterMap.put("interval",30);
                        water_pack = BLEBluetoothManager.BLE_COMMAND_a2d_setDrink_pack(waterMap);
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        Toast.makeText(this,getString(R.string.device_not_support),Toast.LENGTH_SHORT).show();
                    }
                    if(water_pack != null) {
                        textView_send.setText(Arrays.toString(water_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(water_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_longSit:
                startActivity(new Intent(this, LongSitActivity.class));
                break;
            case R.id.syn_noDir:
                if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] disturb_pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        HashMap<String,Object> noDisturbMap = new HashMap<>();
                        noDisturbMap.put("enable",false);
                        noDisturbMap.put("startHour",9);
                        noDisturbMap.put("startMin",0);
                        noDisturbMap.put("endHour",11);
                        noDisturbMap.put("endMin",0);
                        disturb_pack = BLEBluetoothManager.BLE_COMMAND_a2d_setDisturb_pack(noDisturbMap);
                    }else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        Toast.makeText(this,getString(R.string.device_not_support),Toast.LENGTH_SHORT).show();
                    }
                    if(disturb_pack != null) {
                        textView_send.setText(Arrays.toString(disturb_pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(disturb_pack);
                    }
                }else{
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.firmware_upgrade:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        startActivity(new Intent(this, FirmwareUpgradeActivity.class));
                    } else {
                        Toast.makeText(this,getString(R.string.device_not_support),Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this,getString(R.string.device_not_connect),Toast.LENGTH_SHORT).show();
                }

//                SharedPreferences preferences = MainActivity.this.getSharedPreferences("bluetooth", 0);
//                final String platformCode = preferences.getString("platformCode", "");
//                final String version = preferences.getString("version", "");
//                if(!TextUtils.isEmpty(platformCode)){
//                    new Thread(new Runnable() {
//                        @Override
//                        public void run() {
//                            String serverVersion = KCTBluetoothManager.getInstance().checkDFU_upgrade(Integer.parseInt(platformCode));
//                            if(Utils.versionCompare(version,serverVersion)){
//                                byte[] bytes = KCTBluetoothManager.getInstance().getDFU_data(Integer.parseInt(platformCode));
//                                File file = new File(Environment.getExternalStorageDirectory() + "/KCTBluetoothMTK_SDK/" + "dfu.zip");
//                                if(!file.getParentFile().exists()){
//                                    file.getParentFile().mkdirs();
//                                }
//                                try {
//                                    BufferedOutputStream bw = new BufferedOutputStream(new FileOutputStream(file));
//                                    bw.write(bytes);
//                                } catch (FileNotFoundException e) {
//                                    e.printStackTrace();
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                                KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_sendFirmwareUpdate_pack());
//                            }else{
//                                runOnUiThread(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        Toast.makeText(MainActivity.this,"It is already the latest version",Toast.LENGTH_SHORT).show();
//                                    }
//                                });
//
//                            }
//                        }
//                    }).start();
//                }else{
//                    Toast.makeText(MainActivity.this,"It is empty platformCode ",Toast.LENGTH_SHORT).show();
//                }
                break;
            case R.id.syn_motion_state_device:
                KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_synMotionStateToDevice_pack(0,1));
                break;
            case R.id.syn_sport_motion_setting:
                KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_synMotionSetting_pack(50,1,100,1000));
                break;
            case R.id.gesture_control:
                startActivity(new Intent(this, GestureActivity.class));
                break;
            case R.id.set_night_mode:
                startActivity(new Intent(this, NightModeActivity.class));
                break;
            case R.id.heart_rate_monitoring_setup:
                startActivity(new Intent(this, HeartRateMonitoringActivity.class));
                break;
            case R.id.syn_bind_device:
                KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_setBindDevice_pack());
                break;
            case R.id.syn_unbind_device:
                KCTBluetoothManager.getInstance().sendCommand_a2d(BLEBluetoothManager.BLE_COMMAND_a2d_setUnBindDevice_pack());
                break;
            case R.id.device_reset:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendReset_pack();
                    } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        Toast.makeText(this, getString(R.string.device_not_support), Toast.LENGTH_SHORT).show();
                    }
                    if (pack != null) {
                        textView_send.setText(Arrays.toString(pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.device_not_connect), Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.syn_hid_connect:
                KCTBluetoothManager.getInstance().hidConnect(KCTBluetoothManager.getInstance().getConnectDevice());
                break;
            case R.id.syn_hid_disConnect:
                KCTBluetoothManager.getInstance().hidDisConnect(KCTBluetoothManager.getInstance().getConnectDevice());
                break;

//            case R.id.push_agps:
//                startActivityForResult(new Intent(this, PushAGPSDataActivity.class), 1);
//                break;
            case R.id.custom_clock_dial:
                startActivityForResult(new Intent(this, CustomClockDialActivity.class), 2);
                break;

            case R.id.syn_weather:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        selectWeatherDataSet();
//                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_synWeatherData_pack(
//                                new HashMap<String, Object>() {{
//                                    put("lowTem", 5); // 最低气温
//                                    put("highTem", 25); // 最高气温
//                                    put("code", 1); // 天气现象 （0：晴，1：阴，2：雨，3：雪）
//                                    // 根据具体的设备情况，启用以下的功能
////                                    put("curTem", 22); // 当前气温
////                                    put("city", "重庆"); // 地理地址
//                                }},
//                                new HashMap<String, Object>() {{
//                                    put("lowTem", 4);
//                                    put("highTem", 20);
//                                    put("code", 2);
//                                }},
//                                new HashMap<String, Object>() {{
//                                    put("lowTem", 5);
//                                    put("highTem", 25);
//                                    put("code", 3);
//                                }}
//                                // 根据具体设备情况，可以增加更多天数的天气信息
////                                , new HashMap<String, Object>() {{
////                                    put("lowTem", 5);
////                                    put("highTem", 20);
////                                    put("code", 3);
////                                }}
////                                , new HashMap<String, Object>() {{
////                                    put("lowTem", 4);
////                                    put("highTem", 25);
////                                    put("code", 3);
////                                }}
////                                ...
//                                );
                    } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_sendMTKWeatherData_pack("重庆",
                                new HashMap<String, Object>(){{
                                    Calendar calendar = Calendar.getInstance();
                                    int week = calendar.get(Calendar.WEEK_OF_MONTH);
                                    String date = simpleDateFormat.format(calendar.getTime());
                                    put("week", week);
                                    put("date", date);
                                    put("lowTem", 5);
                                    put("highTem", 25);
                                    put("code", 1);
                                }},
                                new HashMap<String, Object>(){{
                                    Calendar calendar = Calendar.getInstance();
                                    calendar.add(Calendar.DAY_OF_YEAR, 1);
                                    int week = calendar.get(Calendar.WEEK_OF_MONTH);
                                    String date = simpleDateFormat.format(calendar.getTime());
                                    put("week", week);
                                    put("date", date);
                                    put("lowTem", 4);
                                    put("highTem", 20);
                                    put("code", 2);
                                }},
                                new HashMap<String, Object>(){{
                                    Calendar calendar = Calendar.getInstance();
                                    calendar.add(Calendar.DAY_OF_YEAR, 2);
                                    int week = calendar.get(Calendar.WEEK_OF_MONTH);
                                    String date = simpleDateFormat.format(calendar.getTime());
                                    put("week", week);
                                    put("date", date);
                                    put("lowTem", 5);
                                    put("highTem", 25);
                                    put("code", 3);
                                }});
                    }
                    if (pack != null) {
                        textView_send.setText(Arrays.toString(pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.device_not_connect), Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.get_battery:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_getBatteryStatus_pack();
                    } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        Toast.makeText(this, getString(R.string.device_not_support), Toast.LENGTH_SHORT).show();
                    }
                    if (pack != null) {
                        textView_send.setText(Arrays.toString(pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.device_not_connect), Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.rename_device_bt_name:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_renameDevice_pack("new name");
                    } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        Toast.makeText(this, getString(R.string.device_not_support), Toast.LENGTH_SHORT).show();
                    }
                    if (pack != null) {
                        textView_send.setText(Arrays.toString(pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.device_not_connect), Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.bond_auth:
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] pack = null;
                    if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_BLE) {
                        pack = BLEBluetoothManager.BLE_COMMAND_a2d_bondAuth_pack();
                    } else if (KCTBluetoothManager.getInstance().getDeviceType() == KCTBluetoothManager.DEVICE_MTK) {
                        Toast.makeText(this, getString(R.string.device_not_support), Toast.LENGTH_SHORT).show();
                    }
                    if (pack != null) {
                        textView_send.setText(Arrays.toString(pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.device_not_connect), Toast.LENGTH_SHORT).show();
                }
                break;
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }


//    private final IDFUProgressCallback mDfuProgressListener = new IDFUProgressCallback() {
//        @Override
//        public void onDeviceConnecting(String deviceAddress) {
//            Log.e(TAG,"onDeviceConnecting");
//        }
//
//        @Override
//        public void onDeviceConnected(String deviceAddress) {
//            Log.e(TAG,"onDeviceConnected");
//        }
//
//        @Override
//        public void onDfuProcessStarting(String deviceAddress) {
//            Log.e(TAG,"onDfuProcessStarting");
//        }
//
//        @Override
//        public void onDfuProcessStarted(String deviceAddress) {
//            Log.e(TAG,"onDfuProcessStarted");
//        }
//
//        @Override
//        public void onEnablingDfuMode(String deviceAddress) {
//            Log.e(TAG,"onEnablingDfuMode");
//        }
//
//        @Override
//        public void onProgressChanged(String deviceAddress, int percent, float speed, float avgSpeed, int currentPart, int partsTotal) {
//            textView_receive.setText("onProgressChanged = " + percent);
//            Log.e(TAG,"onProgressChanged = " + percent);
//            if(percent == 100){
//                KCTBluetoothService.isDFU = false;
//            }
//        }
//
//        @Override
//        public void onFirmwareValidating(String deviceAddress) {
//            Log.e(TAG,"onFirmwareValidating");
//        }
//
//        @Override
//        public void onDeviceDisconnecting(String deviceAddress) {
//            Log.e(TAG,"onDeviceDisconnecting");
//        }
//
//        @Override
//        public void onDeviceDisconnected(String deviceAddress) {
//            Log.e(TAG,"onDeviceDisconnected");
//        }
//
//        @Override
//        public void onDfuCompleted(String deviceAddress) {
//            Log.e(TAG,"onDfuCompleted");
//            KCTBluetoothService.isDFU = false;
//            textView_receive.setText("");
//        }
//
//        @Override
//        public void onDfuAborted(String deviceAddress) {
//            Log.e(TAG,"onDfuAborted");
//        }
//
//        @Override
//        public void onError(String deviceAddress, int error, int errorType, String message) {
//            Log.e(TAG,"onError");
//        }
//    };

    private void selectWeatherDataSet() {
        String[] items = new String[]{"basic", "contain current temperature", "contain geo address"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, final int which) {
                if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
                    byte[] pack = BLEBluetoothManager.BLE_COMMAND_a2d_synWeatherData_pack(
                            new HashMap<String, Object>() {{
                                put("lowTem", 5); // 最低气温
                                put("highTem", 25); // 最高气温
                                put("code", 1); // 天气现象 （0：晴，1：阴，2：雨，3：雪）
                                if (which > 0) {
                                    put("curTem", 22); // 当前气温
                                }
                                if (which > 1) {
                                    put("city", "重庆九龙坡"); // 地理地址
                                }
                            }},
                            new HashMap<String, Object>() {{
                                put("lowTem", 4);
                                put("highTem", 20);
                                put("code", 2);
                            }},
                            new HashMap<String, Object>() {{
                                put("lowTem", -3);
                                put("highTem", -2);
                                put("code", 3);
                            }}
                    );
                    if (pack != null) {
                        textView_send.setText(Arrays.toString(pack));
                        textView_receive.setText("");
                        KCTBluetoothManager.getInstance().sendCommand_a2d(pack);
                    }
                }
                dialogInterface.dismiss();
            }
        });
        builder.setCancelable(true).show();
    }

    private Boolean syncSports(Date date) {
        if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
            byte[] sport_pack = BLEBluetoothManager.BLE_COMMAND_a2d_synData_pack(4, simpleDateFormat.format(date));
            KCTBluetoothManager.getInstance().sendCommand_a2d(sport_pack);
            return true;
        }
        return false;
    }

    private Boolean syncWorkout(Date date) {
        if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
            byte[] run_pack = BLEBluetoothManager.BLE_COMMAND_a2d_synData_pack(3, simpleDateFormat.format(date));
            KCTBluetoothManager.getInstance().sendCommand_a2d(run_pack);
            return true;
        }
        return false;
    }

    private Boolean syncSleep(Date date) {
        if (KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
            byte[] sleep_pack = BLEBluetoothManager.BLE_COMMAND_a2d_synData_pack(1, simpleDateFormat.format(date));
            KCTBluetoothManager.getInstance().sendCommand_a2d(sleep_pack);
            return true;
        }
        return false;
    }

    private Boolean syncHeartRate(Date date) {
        if(KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED) {
            byte[] heart_pack = BLEBluetoothManager.BLE_COMMAND_a2d_synData_pack(2, simpleDateFormat.format(date));
            KCTBluetoothManager.getInstance().sendCommand_a2d(heart_pack);
            return true;
        }
        return false;
    }

    private void rxSyncWorkouts() {
        Observable.intervalRange(0, 7, 500, 1000, TimeUnit.MILLISECONDS)
//                .repeatWhen(objectObservable -> objectObservable.delay(2,TimeUnit.MINUTES))
//                .repeatUntil(() -> KCTBluetoothManager.getInstance().getConnectState() == KCTBluetoothManager.STATE_CONNECTED)
                .subscribeOn(Schedulers.newThread())
                .subscribe(new Observer<Long>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Long number) {
                        Log.d(TAG, "workout ==============onNext " + number);
                        Calendar calendar = Calendar.getInstance();
                        calendar.add(Calendar.DAY_OF_YEAR, -number.intValue());
                        syncWorkout(calendar.getTime());
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        rxSyncHeartRate();
                    }
                });
    }

    private void rxSyncHeartRate() {
        Observable.intervalRange(0, 7, 500, 1000, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.newThread())
                .subscribe(new Observer <Long> () {
                    @Override
                    public void onSubscribe(Disposable d) {
                        //progressHUD.setLabel("Synchronizing Heart Rates");
                    }

                    @Override
                    public void onNext(Long number) {
                        Log.d(TAG, "hr ==============onNext " + number);
                        Calendar calendar = Calendar.getInstance();
                        calendar.add(Calendar.DAY_OF_YEAR, -number.intValue());
                        syncHeartRate(calendar.getTime());
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "hr ==============onComplete");
                        rxSyncSleep();
                    }
                });
    }

    private void rxSyncSleep() {
        Observable.intervalRange(0, 7, 500, 1000, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.newThread())
                .subscribe(new Observer <Long> () {
                    @Override
                    public void onSubscribe(Disposable d) {
                        //progressHUD.setLabel("Synchronizing Sleep Information");
                    }

                    @Override
                    public void onNext(Long number) {
                        Log.d(TAG, "sleep ==============onNext " + number);
                        Calendar calendar = Calendar.getInstance();
                        calendar.add(Calendar.DAY_OF_YEAR, -number.intValue());
                        syncSleep(calendar.getTime());
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "sleep ==============onComplete");
                        rxSyncSports();
                    }
                });
    }

    private void rxSyncSports() {
        Observable.intervalRange(0, 1, 500, 5000, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.newThread())
                .subscribe(new Observer <Long> () {
                    @Override
                    public void onSubscribe(Disposable d) {
                        //progressHUD.setLabel("Synchronizing Sports data");
                    }

                    @Override
                    public void onNext(Long number) {
                        Log.d(TAG, "sports ==============onNext " + number);
                        Calendar calendar = Calendar.getInstance();
                        calendar.add(Calendar.DAY_OF_YEAR, -number.intValue());
                        syncSports(calendar.getTime());
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "sports ==============onComplete");
                    }
                });
    }
}
