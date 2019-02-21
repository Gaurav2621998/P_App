package com.pyrotech.smart_lights;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.stsdemo.test.BleLedCmd;
import com.stsdemo.test.BleLedDeviceNode;

import static java.lang.Boolean.TRUE;

public class connectedDevice extends AppCompatActivity {

    String temp = "";
    private String mBdAddr;
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static String mContollerSettingNode = null;
    public Messenger mServiceMsgr;
    public Messenger mJScriptMessenger;
    private static String mNodeTreeMap = "";
    public Switch aSwitch;
    TextView ad;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connected_device);
        mJScriptMessenger = new Messenger(new ActivityHandler());
         mBdAddr = this.getIntent().getStringExtra(
                EXTRAS_DEVICE_ADDRESS);

          aSwitch = (Switch)findViewById(R.id.s);
            ad = (TextView)findViewById(R.id.ad);
            ad.setText(mBdAddr);
        if (DeviceScanActivity.getAppMode() == DeviceScanActivity.BLE_CONTROLLER_MODE) {

            sendBleLedCommand(mBdAddr, BleLedCmd.Svr2Node2);

        } else {

            sendBleLedCommand(mBdAddr, BleLedCmd.Svr2Node3);

        }
        String args[] = mBdAddr.split(":");
        for(int i=0;i<args.length;i++)
        {
            temp = temp+args[i];
        }

        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Toast.makeText(connectedDevice.this, ""+b, Toast.LENGTH_SHORT).show();
                if (b== TRUE)
                {
                    setLedStatus(temp,"FFFFFF","1","0","1","5");
                }
                else
                {
                    setLedStatus(temp,"FFFFFF","0","0","1","5");
                }
            }
        });
    }

    public void getDevSts(String sBdAddr) {
        try {
            BleLedCmd mBleLedCmd = new BleLedCmd();
            Message msg = Message.obtain(null, BleGattService.MSG_GET_DEV_STS, 0,0);
            msg.replyTo = mJScriptMessenger;
            Bundle bundle = new Bundle();
            bundle.putString("TargetBdAddr",sBdAddr);
            msg.setData(bundle);

            mServiceMsgr.send(msg);
        } catch(RemoteException e) {
            Toast.makeText(connectedDevice.this,"HtmlJavaInterface getDevSts() failed.",Toast.LENGTH_SHORT).show();
        }
    }

    public void setLedStatus(String sBdAddr, String sLedColor, String sLedOn, String sAutoOn, String sNodeAll, String sDimmer) {
        try {
            BleLedCmd mBleLedCmd = new BleLedCmd();
            short iStatus = 0;
            byte bNodeAll = BleLedCmd.TargetSingle;
            short iDimmer = 0x0000;

            Message msg = Message.obtain(null, BleGattService.MSG_SET_DEV_STS, 0,0);
            msg.replyTo = mJScriptMessenger;

            iStatus = BleLedDeviceNode.convertLedColor(sLedColor);

            if(sLedOn.compareTo("1")==0){
                iStatus = (short)(iStatus | BleLedCmd.LED_ON);
            }

            if(sAutoOn.compareTo("1")==0){
                iStatus = (short)(iStatus | BleLedCmd.AUTO_ON);
            }

            if(sNodeAll.compareTo("1")==0) {
                bNodeAll = BleLedCmd.TargetAll;
            }

            // Dimmer
            try {
                iDimmer = (short)(Integer.parseInt(sDimmer));
            } catch(NumberFormatException e) {
                Toast.makeText(connectedDevice.this, "setLedStatus() Dimmer convert failed." + e.toString(),Toast.LENGTH_SHORT).show();
                iDimmer = 0x0000;
            }
            iStatus = (short)(iStatus | (BleLedCmd.DIMMER_MASK & (iDimmer << 4)));


            byte bCmd[] = mBleLedCmd.MakeCommand(BleLedCmd.Svr2Node1, sBdAddr, bNodeAll, iStatus,sLedColor);
            //Toast.makeText(mContext, ""+String.valueOf(bCmd), Toast.LENGTH_SHORT).show();
            Bundle bundle = new Bundle();

            bundle.putByteArray("cmd", bCmd);

            bundle.putByteArray("TargetBdAddr", bCmd);
            msg.setData(bundle);

            mServiceMsgr.send(msg);
        } catch(RemoteException e) {
            Toast.makeText(connectedDevice.this,"HtmlJavaInterface setLedStatus() failed.",Toast.LENGTH_SHORT).show();
        }

        return;
    }


    @Override
    protected void onResume() {
        this.registerReceiver(mAppBroadcastReceiver,
                makeAppUpdateIntentFilter());
        super.onResume();
        mContollerSettingNode = null;

    }


    @Override
    protected void onPause() {
        super.onPause();
        // Broadcast in an application is validated.
        this.unregisterReceiver(mAppBroadcastReceiver);
    }



    @Override
    protected void onStop() {
        try {
            if (mServiceMsgr != null) {
                Message msg = Message.obtain(null,
                        BleGattService.MSG_GATTSERVER_STOP);
                msg.replyTo = mServiceMsgr;
                mServiceMsgr.send(msg);
            }
        } catch (RemoteException e) {
            Toast.makeText(this, ""+e.toString(), Toast.LENGTH_SHORT).show();
        }
        this.unbindService(mServiceConnection);
        finish();
        super.onStop();
    }

    private final BroadcastReceiver mAppBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BleGattService.ACTION_NODE_TREE.equals(action)) {
                mNodeTreeMap = intent
                        .getStringExtra(BleGattService.ACTION_NODE_TREE);
                if (mNodeTreeMap
                        .compareTo(BleGattService.ACTION_NODE_DISCONNECT) == 0) {

                    Toast.makeText(connectedDevice.this,"Connection failed",Toast.LENGTH_SHORT).show();
                    finish();

                } else {
                    Toast.makeText(connectedDevice.this,"Connection success",Toast.LENGTH_SHORT).show();

                }
            }
        }
    };




    private static IntentFilter makeAppUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleGattService.ACTION_NODE_TREE);
        return intentFilter;
    }



    public void sendBleLedCommand(String sBdAddr, byte bCmd) {

        // mBleGattService.mBluetoothDeviceAddress = sBdAddr;
        byte bSndData[] = {};

        BleLedCmd mBleLedCmd = new BleLedCmd();

        if (bCmd == BleLedCmd.Svr2Node2) {
            // In the case of a tablet, a node designation command is sent.
            bSndData = mBleLedCmd.MakeCommand(BleLedCmd.Svr2Node2, null,
                    (byte) 0, (short) 0, "FFFFFF");

        }

        else {

            bSndData = mBleLedCmd.MakeCommand(BleLedCmd.Svr2Node3, null,
                    (byte) 0, (short) 0, "FFFFFF");
        }

        Intent gattServiceIntent = new Intent(this, BleGattService.class);

        gattServiceIntent.putExtra(BleGattService.EXTRAS_BD_ADDR, sBdAddr);
        gattServiceIntent.putExtra(BleGattService.EXTRAS_SVR_CMD, bSndData);

        boolean bRet = bindService(gattServiceIntent, mServiceConnection,
                BIND_AUTO_CREATE);

        // bRet = mBleGattService.gattWriteCommand(bSndData);
    }


    private final ServiceConnection mServiceConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName componentName,
                                   IBinder service) {
        // mBleGattService = ((BleGattService.LocalBinder)
        // service).getService();
        mServiceMsgr = new Messenger(service);

        try {
            Message msg = Message.obtain(null,
                    BleGattService.MSG_START_INIT);
            msg.replyTo = mServiceMsgr;
            mServiceMsgr.send(msg);

            msg = Message.obtain(null, BleGattService.MSG_START_CONNECT);
            msg.replyTo = mServiceMsgr;
            mServiceMsgr.send(msg);
        } catch (RemoteException e) {
            Toast.makeText(connectedDevice.this, ""+e.toString(), Toast.LENGTH_SHORT).show();
        }
    }
    public void onServiceDisconnected(ComponentName componentName) {
        mServiceMsgr = null;
    }
};

    class ActivityHandler extends Handler {
        /**
         * The receiving event of GATT service HTML is redrawn when
         * MSG_TREE_REDRAW is received. When others are received, a message is
         * sent to Javascript and an interface as response of Javascript.
         *
         * @param msg
         *            The message which received from GATT service
         */
        @Override
        public void handleMessage(Message msg) {
            boolean bRet = false;

            if (msg.what == BleGattService.MSG_TREE_REDRAW) {
                mContollerSettingNode = null;
                //openWebView();
            } else if (msg.what == BleGattService.MSG_SERVER_CLOSE) {
                //closeWebView();
            } else {
//                bRet = mHtmlJavaIF.messageCallback(msg);
//                if (bRet == false) {
//                    super.handleMessage(msg);
                Toast.makeText(connectedDevice.this, "handle message else part", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
