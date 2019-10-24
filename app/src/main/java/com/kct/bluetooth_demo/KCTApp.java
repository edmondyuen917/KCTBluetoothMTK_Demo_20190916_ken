package com.kct.bluetooth_demo;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.facebook.stetho.Stetho;
import com.kct.bluetooth.KCTBluetoothManager;
import com.kct.bluetooth_demo.db.DBOpenHelper;
import com.kct.bluetooth_demo.db.dao.DaoMaster;
import com.kct.bluetooth_demo.db.dao.DaoSession;

import org.greenrobot.greendao.database.Database;


/**
 * KCTApplication
 */
public class KCTApp extends android.app.Application{

    private static KCTApp instance;
    private static KCTBluetoothService mKCTBluetoothService;

    private DaoSession mDaoSession;

    public static KCTApp getInstance() {
        return instance;
    }

    public static KCTBluetoothService getmBluetoothLeService() {
        return mKCTBluetoothService;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        KCTBluetoothManager.getInstance().init(this);

        DBOpenHelper helper = new DBOpenHelper(this, "bluetooth_demo");
        Database db = helper.getWritableDb();
        mDaoSession = new DaoMaster(db).newSession();

        Stetho.initializeWithDefaults(this);

        initBlueTooth();
    }

    public DaoSession getDaoSession() {
        return mDaoSession;
    }

    public static void initBlueTooth() {
        Intent gattServiceIntent = new Intent(instance, KCTBluetoothService.class);
        instance.bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    public final static ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            try {mKCTBluetoothService = ((KCTBluetoothService.LocalBinder) service).getService();
                if (!mKCTBluetoothService.initialize()) {
                }
            } catch (Exception e) {e.printStackTrace();}
        }@Override
        public void onServiceDisconnected(ComponentName componentName) {
            mKCTBluetoothService = null;}
    };
}
