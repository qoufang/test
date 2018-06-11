package keymantek.bletransmit;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import keymantek.android.convert.DataConvert;
import keymantek.android.event.MessageEvent;

/**
 * @author zm
 * @date 2017/12/7 0007
 */

public class BlueToothAdmin {

    private static String TAG = "BlueTooth";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private String mBluetoothDeviceName;
    public static String uuid = "00001101-0000-1000-8000-00805F9B34FB";
    private BluetoothSocket bluetoothSocket;
    private ReadThread readThread;
    public int mConnectionState = STATE_DISCONNECTED;
    public static final int STATE_DISCONNECTED = 2;
    public static final int STATE_CONNECTED = 1;
    private Context context;
    private static BlueToothAdmin instance;
    public MessageEvent messageEvent = new MessageEvent();

    private BlueToothAdmin(Context context) {
        this.context = context;
        initBluetooth();
    }

    public OnBleReceiveListener onBleReceiveListener;

    public static BlueToothAdmin getInstance(Context context) {
        if (instance == null) {
            synchronized (BlueToothAdmin.class) {
                instance = new BlueToothAdmin(context);
            }
        }
        return instance;
    }

    public void setmBluetoothDeviceName(String mBluetoothDeviceName) {
        this.mBluetoothDeviceName = mBluetoothDeviceName;
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.e("TTT", device.getName());
                if (device.getName() != null && device.getName().equals(mBluetoothDeviceName)) {
                    bluetoothAdapter.cancelDiscovery();
                    bluetoothDevice = device;

                    new Thread() {
                        @Override
                        public void run() {
                            super.run();
                            try {
                                if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                                    Method method = BluetoothDevice.class.getMethod("createBond");
                                    method.invoke(bluetoothDevice);
                                }
                                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString(uuid));
                                bluetoothSocket.connect();
                                startRead();
                                mConnectionState = STATE_CONNECTED;

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

                            } catch (Exception e) {
                                e.printStackTrace();
                                mConnectionState = STATE_DISCONNECTED;
                                messageEvent.ShowMessage("连接失败");
                            }
                        }
                    }.start();
                }
            }
        }
    };

    public boolean initBluetooth() {

        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            return false;
        }
        bluetoothAdapter = manager.getAdapter();
        if (bluetoothAdapter == null) {
            return false;
        }
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }
        mBluetoothDeviceName = "coco";

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(receiver, filter);

        return true;
    }

    public void unRegisterReceiver() {

        context.unregisterReceiver(receiver);
    }

    /**
     * 开始搜索
     */
    public void startConnect() {
        bluetoothAdapter.startDiscovery();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mConnectionState != STATE_CONNECTED) {
                    stopDiscovery();
                    messageEvent.ShowMessage("连接超时");
                }
            }
        }, 30000);
    }

    /**
     * 结束
     */
    public void stopDiscovery() {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
//        stopRead();
    }

    private void startRead() {

        if (readThread != null) {
            readThread.interrupt();
            try {
                readThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        readThread = new ReadThread(bluetoothSocket);
        readThread.start();
    }

    private void stopRead() {

        if (readThread != null) {
            readThread.interrupt();
            try {
                readThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            readThread = null;
        }
    }

    /**
     * 发送
     *
     * @param bytes
     */
    public void send(byte[] bytes) throws Exception {

        if (mConnectionState == STATE_CONNECTED) {
            try {
                bluetoothSocket.getOutputStream().write(bytes, 0, bytes.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new Exception("发送前先配对");
        }
    }

    //接收线程

    class ReadThread extends Thread {

        BluetoothSocket bluetoothSocket;
        InputStream inputStream;

        public ReadThread(BluetoothSocket bluetoothSocket) {
            this.bluetoothSocket = bluetoothSocket;
            try {
                inputStream = bluetoothSocket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while (!interrupted()) {
                    int len = inputStream.available();
                    if (len > 0) {
                        byte[] buffer = new byte[len];
                        inputStream.read(buffer, 0, len);
                        String s = DataConvert.ByteArrayToHexString(buffer);
                        Log.e(TAG, "run: " + s);
                        if (onBleReceiveListener != null) {
                            onBleReceiveListener.onBleReceive(buffer);
                        }
                    } else {
                        try {
                            sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
