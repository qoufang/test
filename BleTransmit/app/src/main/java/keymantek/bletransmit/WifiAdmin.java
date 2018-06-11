package keymantek.bletransmit;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;

import keymantek.android.convert.DataConvert;
import keymantek.android.event.MessageEvent;

public class WifiAdmin {

    private static WifiAdmin admin;
    private Context context;
    public Socket socket;
    public OnBleReceiveListener listener;
    public static MessageEvent messageEvent = new MessageEvent();
    private ReadThread readThread;

    public WifiAdmin(Context context) {
        this.context = context;
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
    }

    class ReadThread extends Thread {

        Socket socket;

        ReadThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            super.run();
            try {
                while (!interrupted()) {
                    try {
                        int cnt = socket.getInputStream().available();
                        if (cnt > 0) {
                            byte[] buffer = new byte[cnt];
                            socket.getInputStream().read(buffer);
                            if (listener != null) {
                                listener.onBleReceive(buffer);
                            }
                        } else {
                            Thread.sleep(50);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public void connect(final String ip, final String port) {

        new Thread() {
            @Override
            public void run() {
                super.run();

                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, Integer.parseInt(port)), 5000);
                    if (readThread != null) {
                        readThread.interrupt();
                        try {
                            readThread.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    readThread = new ReadThread(socket);
                    readThread.start();

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bos.write(Frame.StartFlag);
                    bos.write(new byte[6]);
                    bos.write(DataConvert.Int16ToHByteArray(1), 0, 2);
                    byte[] buffer = DataConvert.HexStringToByteArray(MainActivity.SN);
                    bos.write(DataConvert.Int16ToHByteArray(buffer.length));
                    bos.write(buffer);
                    byte[] temp = bos.toByteArray();
                    byte check = 0;
                    for (int i = 0; i < temp.length; i++) {
                        check += temp[i];
                    }
                    bos.write(check);
                    bos.write(Frame.EndFlag);
                    send(bos.toByteArray());
                } catch (IOException e) {
                    e.printStackTrace();
                    messageEvent.ShowMessage("连接失败");
                }
            }
        }.start();
    }

    public static WifiAdmin getInstance(Context context) {

        if (admin == null) {
            admin = new WifiAdmin(context);
        }
        return admin;
    }

    /**
     * 发送数据
     *
     * @param data
     */
    public void send(byte[] data) {
        try {
            if (socket != null && socket.isClosed()) {
                socket.getOutputStream().write(data);
            } else {
                messageEvent.ShowMessage("socket断开");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开启热点
     *
     * @param ssid
     * @param password
     * @return
     */
    public boolean setWifiApEnabled(String ssid, String password) {

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int count = 0;
        // wifi和热点不能同时打开，所以打开热点的时候需要关闭wifi
        wifiManager.setWifiEnabled(false);
        try {
            Method status = wifiManager.getClass().getDeclaredMethod("getWifiApState");
            // 调用getWifiApState() ，获取返回值
            int state = (int) status.invoke(wifiManager);
            // 通过放射获取 WIFI_AP的开启状态属性
            Field field = wifiManager.getClass().getDeclaredField("WIFI_AP_STATE_ENABLED");
            // 获取属性值
            int value = (int) field.get(wifiManager);
            // 判断是否开启
            if (state == value) {
                Method method = wifiManager.getClass().getMethod("getWifiApConfiguration");
                WifiConfiguration config = (WifiConfiguration) method.invoke(wifiManager);
                if (config.SSID.equals(ssid) && config.preSharedKey.equals(password)) {
                    return true;
                } else {
                    config.SSID = ssid;
                    config.preSharedKey = password;
                    Method method2 = wifiManager.getClass().getMethod("setWifiApConfiguration", WifiConfiguration.class);
                    return (Boolean) method2.invoke(wifiManager, config);
                }
            } else {
                // 热点的配置类
                WifiConfiguration apConfig = new WifiConfiguration();
                apConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                apConfig.allowedKeyManagement.set(4);
                // 配置热点的名称
                apConfig.SSID = ssid;
                // 配置热点的密码
                apConfig.preSharedKey = password;
                // 通过反射调用设置热点
                Method method = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
                // 返回热点打开状态
                return (Boolean) method.invoke(wifiManager, apConfig, true);
            }

        } catch (Exception e) {
            if (count < 3) {
                setWifiApEnabled(ssid, password);
                count++;
            }
            e.printStackTrace();
            return false;
        }

    }
}
