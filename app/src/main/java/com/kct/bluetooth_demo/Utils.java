package com.kct.bluetooth_demo;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Utils {

    public static boolean versionCompare(String usingVer,String serverVer){
        boolean result = false;
        if(usingVer != null && serverVer != null && !usingVer.equals("") && !serverVer.equals("")
                && (usingVer.substring(0,1).equals("V") || usingVer.substring(0,1).equals("v"))
                && (serverVer.substring(0,1).equals("V") || serverVer.substring(0,1).equals("v"))){   //判断两个版本号是否为标准版本号

            String beforeStr = usingVer.substring(1, usingVer.length());
            String nowStr = serverVer.substring(1, serverVer.length());

            String beforeStrs[] = beforeStr.split("\\.");
            String nowStrs[] = nowStr.split("\\.");

            StringBuffer sbBefore = new StringBuffer();
            StringBuffer sbNow = new StringBuffer();
            for (int i = 0; i < beforeStrs.length; i++) {
                sbBefore.append(beforeStrs[i]);
            }
            for (int i = 0; i < nowStrs.length; i++) {
                sbNow.append(nowStrs[i]);
            }
            result = Integer.parseInt(String.valueOf(sbBefore)) < Integer.parseInt(String.valueOf(sbNow));
        }
        return result;
    }


    public static String getNewMac(String address) {
        String front = address.substring(0,address.length()-2);
        String back = address.substring(address.length()-2);
        int next = Integer.parseInt(back, 16)+1;
        back = Integer.toHexString(next).toUpperCase();
        if(back.length() == 3){
            back = back.substring(1,3);
        }else {
            back = back.length() == 1?"0"+back:back;
        }
        return front + back;
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static boolean copyUriToFile(Context context, Uri from, String toPath) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(from);
            File toFile = new File(toPath);
            if (!toFile.getParentFile().exists()) {
                toFile.getParentFile().mkdirs();
            }
            outputStream = new FileOutputStream(toFile);
            int n;
            byte[] buf = new byte[1048 * 4];
            while ((n = inputStream.read(buf, 0, buf.length)) > 0) {
                outputStream.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            Log.e("Utils", "copyUriToFile(" + from + ", " + toPath + ")", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
        return false;
    }
}
