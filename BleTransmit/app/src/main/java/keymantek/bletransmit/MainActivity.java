package keymantek.bletransmit;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import keymantek.android.convert.DataConvert;
import keymantek.android.deviceinfo.DeviceInfo;
import keymantek.android.protocol.CheckDataCacheResult;
import keymantek.android.protocol.DataProcessing;
import keymantek.android.protocol.OnCheckDataCacheListener;
import keymantek.android.protocol.OnFrameDataReceivedListener;
import keymantek.android.serialport.OnDataReceivedListener;
import keymantek.android.serialport.Parity;
import keymantek.android.serialport.SerialPort;
import keymantek.android.serialport.StopBits;

/**
 * @author Administrator
 */
public class MainActivity extends AppCompatActivity implements Observer, OnBleReceiveListener, OnDataReceivedListener, OnFrameDataReceivedListener, OnCheckDataCacheListener {

    private BlueToothAdmin blueToothAdmin;
    private WifiAdmin wifiAdmin;
    private ProgressDialog dialog;
    private ListView listView;
    private List<String> list = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private SerialPort serialPort;
    private DataProcessing dataProcessing;
    private RecvFrameData recvFrameData;
    private Toolbar toolbar;
    private boolean wifi;
    private AlertDialog wifiDialog;
    private AlertDialog bleDialog;
    private EditText mBleName;
    private EditText IP, port;
    public static String SN;
    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what) {
                case 0:
                    Toast.makeText(MainActivity.this, msg.obj.toString(), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    break;
                case 1:
                    list.add(msg.obj.toString());
                    adapter.notifyDataSetChanged();
                    break;
                default:


            }
            return false;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SN = DeviceInfo.CreateInstance().getSN();
        initToolBar();
        initBle();
        initWifi();
        initSerialPort();
        initView();
    }

    private void initWifi() {

        wifiAdmin = WifiAdmin.getInstance(this);
        WifiAdmin.messageEvent.addObserver(this);
        wifiAdmin.listener = this;
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_wifi_layout, null, false);
        port = v.findViewById(R.id.port);
        IP = v.findViewById(R.id.ip);
        wifiDialog = new AlertDialog.Builder(this).create();

        wifiDialog.setTitle("WIFI");
        wifiDialog.setView(v);
        wifiDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        wifiDialog.setButton(DialogInterface.BUTTON_POSITIVE, "确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                connect(true);
            }
        });
    }

    private void initToolBar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle("转发");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_ble:
                bleDialog.show();
                break;
            case R.id.action_wifi:
                wifiDialog.show();
                break;
            default:
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    private void initSerialPort() {
        try {
            serialPort = SerialPort.getInstance();
            serialPort.OnDataReceived = this;
            serialPort.setPortName(DeviceInfo.CreateInstance().IRPortName());
            serialPort.setBaudRate(1200);
            serialPort.setParity(Parity.Even);
            serialPort.setStopBits(StopBits.One);
            serialPort.setDataBits(8);
            serialPort.Open();
            dataProcessing = new DataProcessing(1024 * 100, this);
            dataProcessing.FrameDataReceived = this;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initBle() {

        blueToothAdmin = BlueToothAdmin.getInstance(this);
        blueToothAdmin.messageEvent.addObserver(this);
        blueToothAdmin.onBleReceiveListener = this;
        bleDialog = new AlertDialog.Builder(this).create();
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_ble_layout, null, false);
        mBleName = v.findViewById(R.id.bleName);
        bleDialog.setTitle("输入连接的蓝牙名称");
        bleDialog.setView(v);
        bleDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        bleDialog.setButton(DialogInterface.BUTTON_POSITIVE, "确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                connect(false);
            }
        });
    }


    private void initView() {

        dialog = new ProgressDialog(this);
        dialog.setCancelable(false);
        dialog.setMessage("蓝牙连接中...");
        listView = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        listView.setAdapter(adapter);

    }

    @Override
    public void update(Observable o, Object arg) {

        handler.sendMessage(handler.obtainMessage(0, arg));
    }

    private void ClearDataCache() {
        try {
            synchronized (dataProcessing) {
                dataProcessing.ClearCache();
            }
        } catch (Exception e) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        blueToothAdmin.unRegisterReceiver();
    }

    @Override
    public void onBleReceive(byte[] data) {
        //蓝牙接收的消息，加入缓存处理，防止未收到完整帧
        dataProcessing.AddDataToCache(data);
    }

    @Override
    public void OnDataReceived(Object e) {

        //红外接收的消息处理成蓝牙协议，通过蓝牙字节转发
        try {
            int ret = serialPort.getBytesToRead();
            if (ret > 0) {
                byte[] buffer = new byte[ret];
                ret = serialPort.Read(buffer);
                handler.sendMessage(handler.obtainMessage(1, "红外接收：" + DataConvert.ByteArrayToHexString(buffer, 0, ret)));
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bos.write(Frame.StartFlag);
                bos.write(recvFrameData.uid);
                bos.write(DataConvert.Int16ToHByteArray(recvFrameData.cmd));
                bos.write(DataConvert.Int16ToHByteArray(buffer.length));
                bos.write(buffer);
                byte[] temp = bos.toByteArray();
                byte check = 0;
                for (int i = 0; i < temp.length; i++) {
                    check += temp[i];
                }
                bos.write(check);
                bos.write(Frame.EndFlag);
                if (wifi) {
                    wifiAdmin.send(bos.toByteArray());
                } else {
                    blueToothAdmin.send(bos.toByteArray());
                }
                ClearDataCache();
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }

    }

    private int GetLen(DataProcessing dp, int start) {
        int len = 0;
        len += (dp.getDataCache(start + Frame.LenStartPos) & 0xff);
        len <<= 8;
        len += (dp.getDataCache(start + Frame.LenStartPos + 1) & 0xff);
        return len;
    }

    private int GetLen(byte[] dp) {
        int len = 0;
        len += (dp[(Frame.LenStartPos)] & 0xff);
        len <<= 8;
        len += (dp[(Frame.LenStartPos + 1)] & 0xff);
        return len;
    }

    private int FindIndex(DataProcessing dp, int startPos, byte findValue) {
        int ret = -1;
        for (int i = startPos; i < dp.Count(); i++) {
            if (dp.getDataCache(i) == findValue) {
                ret = i;
                break;
            }
        }
        return ret;
    }

    private int GetCmd(byte[] data) {
        int len = 0;
        len += (data[Frame.CMDStartPos] & 0xff);
        len <<= 8;
        len += (data[(Frame.CMDStartPos + 1)] & 0xff);
        return len;
    }

    private boolean CheckSum(DataProcessing dp, int start) {
        boolean bRet = false;
        int len = GetLen(dp, start);
        int framelen = len + Frame.EndStartPosNotAddDataLen;
        byte sum = 0;
        for (int i = 0; i < framelen - 1; i++) {
            sum += dp.getDataCache(start + i);
        }
        if (dp.getDataCache(start + framelen - 1) == sum) {
            bRet = true;
        }
        return bRet;
    }

    @Override
    public CheckDataCacheResult OnCheckDataCache(DataProcessing dp, int startPosition) {

        CheckDataCacheResult result = new CheckDataCacheResult();
        result.CheckNextFrame = false;
        result.Result = false;

        int m_start = FindIndex(dp, startPosition, Frame.StartFlag);
        if (m_start >= 0) {
            result.CheckNextFrame = true;
            result.CheckNextFrameStartPosition = m_start + 1;
            if (m_start + Frame.EndStartPosNotAddDataLen + 1 <= dp.Count()) {
                int m_len = GetLen(dp, m_start);
                if (m_start + Frame.EndStartPosNotAddDataLen + m_len + 1 <= dp.Count()) {
                    if (dp.getDataCache(m_start + Frame.EndStartPosNotAddDataLen + m_len) == Frame.EndFlag) {
                        if (CheckSum(dp, m_start)) {
                            result.Result = true;
                            result.StartIndex = m_start;
                            result.Count = Frame.EndStartPosNotAddDataLen + m_len + 1;
                        }
                    }
                }
            }
        }

        return result;
    }

    @Override
    public void OnFrameDataReceived(Object o, byte[] data) {

        handler.sendMessage(handler.obtainMessage(1, "蓝牙接收：" + DataConvert.ByteArrayToHexString(data)));
        //解析数据域通过红外转发
        recvFrameData = new RecvFrameData();
        recvFrameData.cmd = GetCmd(data);
        recvFrameData.uid = DataConvert.SubArray(data, Frame.UIDStartPos, 6);
        recvFrameData.framedata = DataConvert.SubArray(data, Frame.DataStartPos, GetLen(data));
        if (recvFrameData.cmd == 2) {
            try {
                serialPort.Write(recvFrameData.framedata);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            handler.sendMessage(handler.obtainMessage(0, "连接成功"));
        }
    }

    public void connect(View view) {
        connect(wifi);
    }

    public void connect(boolean wifi) {
        this.wifi = wifi;
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.hideSoftInputFromWindow(IP.getWindowToken(), InputMethodManager.SHOW_FORCED);
        manager.hideSoftInputFromWindow(mBleName.getWindowToken(), InputMethodManager.SHOW_FORCED);
        if (wifi) {
            if (!TextUtils.isEmpty(IP.getText().toString()) && !TextUtils.isEmpty(port.getText().toString())) {
                wifiAdmin.connect(IP.getText().toString(), port.getText().toString());
                dialog.setMessage("WIFI连接中...");
                dialog.show();
            } else {
                Toast.makeText(this, "请先配置", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (!TextUtils.isEmpty(mBleName.getText().toString())) {
                blueToothAdmin.setmBluetoothDeviceName(mBleName.getText().toString());
                blueToothAdmin.startConnect();
                dialog.setMessage("蓝牙连接中...");
                dialog.show();
            } else {
                Toast.makeText(this, "请先配置", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
