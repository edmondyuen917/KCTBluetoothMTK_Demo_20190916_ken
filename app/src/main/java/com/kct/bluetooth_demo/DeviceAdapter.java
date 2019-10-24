package com.kct.bluetooth_demo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.kct.bluetooth.bean.BluetoothLeDevice;

import java.util.Arrays;
import java.util.List;

public class DeviceAdapter extends BaseAdapter {

    private Context context;
    private List<BluetoothLeDevice> deviceList;

    public DeviceAdapter(Context context) {
        this.context = context;
    }

    public DeviceAdapter setDeviceList(List<BluetoothLeDevice> deviceList) {
        this.deviceList = deviceList;
        notifyDataSetChanged();
        return this;
    }

    @Override
    public int getCount() {
        return deviceList != null ? deviceList.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return deviceList != null ? deviceList.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if(convertView == null){
            viewHolder = new ViewHolder();
            convertView = LayoutInflater.from(context).inflate(R.layout.item_scan_layout, null);
            viewHolder.deviceName = (TextView) convertView.findViewById(R.id.device_name);
            viewHolder.deviceMac = (TextView) convertView.findViewById(R.id.device_mac);
            viewHolder.deviceRssi = (TextView) convertView.findViewById(R.id.device_rssi);
            viewHolder.deviceScanRecord = (TextView) convertView.findViewById(R.id.device_scanRecord);
            convertView.setTag(viewHolder);
        } else{
            viewHolder = (ViewHolder) convertView.getTag();
        }
        if(deviceList != null && deviceList.get(position) != null && deviceList.get(position).getDevice() != null){
            String deviceName = deviceList.get(position).getDevice().getName();
            if(deviceName != null && !deviceName.isEmpty()){
                viewHolder.deviceName.setText(deviceName);
            } else{
                viewHolder.deviceName.setText(context.getString(R.string.no_know_device));
            }
            viewHolder.deviceMac.setText(deviceList.get(position).getDevice().getAddress());
            viewHolder.deviceRssi.setText("RSSI:"+deviceList.get(position).getRssi()+"dB");
            viewHolder.deviceScanRecord.setText("Scan Record"+":"+ Arrays.toString(deviceList.get(position).getScanRecord()));
        }
        return convertView;
    }

    class ViewHolder{
        TextView deviceName;
        TextView deviceMac;
        TextView deviceRssi;
        TextView deviceScanRecord;
    }
}
