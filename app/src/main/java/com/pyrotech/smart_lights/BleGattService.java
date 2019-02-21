
package com.pyrotech.smart_lights;


import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.stsdemo.test.BleLedCmd;
import com.stsdemo.test.BleLedDevTree;
import com.stsdemo.test.BleLedDeviceNode;
import com.stsdemo.test.ToshibaBleServiceUuid;

/**
 * Service of the GATT client of BluetoothLE. In this class, it corresponds to
 * GATT transmission and reception. The activityl by the side of GUI transmits
 * the data received by GATT in the GSON form TEXT.
 * 
 */
public class BleGattService extends Service {
	private final static String TAG = BleGattService.class.getSimpleName();

	public static final String EXTRAS_BD_ADDR = "EXTRAS_BD_ADDR";
	public static final String EXTRAS_SVR_CMD = "EXTRAS_SVR_CMD";
	public static final String EXTRAS_TREE_NODE_DATA = "EXTRAS_TREE_NODE_DATA";

	// Message for communicationÃ£â‚¬â‚¬(JavascriptÃ¢â€¡â€�GattService)
	public static final int MSG_START_INIT = 0; // Gatt Initialization request
	public static final int MSG_START_CONNECT = 1; // Gatt connection request
	public static final int MSG_GET_DEV_STS = 2; // BLE-LED device status
													// request
	public static final int MSG_RET_DEV_STS = 3; // BLE-LED device status reply
	public static final int MSG_SET_DEV_STS = 4; // BLE-LED device ServerToNode
													// command setting
	public static final int MSG_TREE_REDRAW = 5; // Topology graph display
													// redraw demand. (Service
													// to activity)
	public static final int MSG_SERVER_CLOSE = 6; // Server display close
													// demand. (Service to
													// activity)
	public static final int MSG_GATTSERVER_STOP = 7; // BleGattService Stop.

	public static final String ACTION_NODE_TREE = "toshiba.led_ble_controler.ACTION_NODE_TREE";
	public static final String ACTION_NODE_DISCONNECT = "toshiba.led_ble_controler.ACTION_NODE_DISCONNECT";

	private Messenger mMessenger; // for Messenger of GUI-Activity

	private String mBluetoothDeviceAddress = "";

	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothGattService mBluetoothService = null;
	public static BluetoothGatt mBluetoothGatt;
	private BluetoothGattCharacteristic mBleGattChar;

	private BleLedCmd mBleLedCmd = new BleLedCmd();

	private int mConnectionState = STATE_DISCONNECTED;

	private static final int STATE_DISCONNECTED = 0;
	private static final int STATE_CONNECTING = 1;
	private static final int STATE_CONNECTED = 2;

	public static   byte[] mWriteCmdQue = null;

	private boolean bSmartPhoneMode = false;

	/**
	 * Topology graph information management class All the node information
	 * which constitutes topology is stored.
	 */
	private BleLedDevTree mBleDevTree = new BleLedDevTree();

	private Context mServiceContext = this;
	private Handler mHandler = null;

	private final byte BLE_SEND_MAX_RETRY = 10;
	

	/**
	 * The activity of GATT communication service is generated. In order to
	 * perform messaging with GUI, the handle of a message service is generated.
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		mHandler = new ServiceHandler();
		mMessenger = new Messenger(mHandler);

	}

	/**
	 * The start event of GATT communication service. Only Override.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}

	/**
	 * The handler for message communication with GUI.
	 * 
	 */
	class ServiceHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			/*
			 * For message communication with GUI Event handler. MSG_START_INIT
			 * Ã¯Â¼Å¡ The GATT service initialization request from GUI.
			 * MSG_START_CONNECT Ã¯Â¼Å¡ The GATT connection request from GUI
			 * MSG_GET_DEV_STS Ã¯Â¼Å¡ The BLE-LED device information acquisition
			 * demand from GUI (unused) MSG_SET_DEV_STS Ã¯Â¼Å¡ The BLE-LED
			 * device setting request from GUI
			 */
			try {
				Bundle bundle = msg.getData();
				switch (msg.what) {
				case MSG_START_INIT:
					initialize();
					break;
				case MSG_START_CONNECT:
					connect();
					break;
				case MSG_GET_DEV_STS:

					String sTargetBdAddr = bundle.getString("TargetBdAddr");
					BleLedDeviceNode retDevSts = mBleDevTree
							.getDeviceStatus(sTargetBdAddr);
					if (mBleDevTree != null) {
						Messenger mClient = msg.replyTo;
						Message replyMsg = Message.obtain(null,
								MSG_RET_DEV_STS, 0, 0);
						bundle = new Bundle();
						bundle.putString(
								"sts",
								retDevSts.getDeviceName() + ":"
										+ retDevSts.getLedColor() + ":"
										+ retDevSts.getLedOn() + ":"
										+ retDevSts.getAutoOn() + ":"
										+ retDevSts.getProximity() + ":"
										+ retDevSts.getProximityColor());
						replyMsg.setData(bundle);
						try {
							mClient.send(replyMsg);
						} catch (RemoteException e) {
							Log.d(TAG,
									"BleGattService handleMessage() Exception"
											+ e.toString());
						}
					}
					break;
				case MSG_SET_DEV_STS:
					
					{
						Messenger mClient = msg.replyTo;
						Message replyMsg = Message.obtain(null, MSG_TREE_REDRAW, 0,
								0);
						try {
							mClient.send(replyMsg);
						} catch (RemoteException e) {
							Log.d(TAG, "BleGattService handleMessage() Exception"
									+ e.toString());
						}

						byte bCmd[] = bundle.getByteArray("cmd");
						gattWriteCommand(bCmd);
						
					}
					
					
					break;
				case MSG_GATTSERVER_STOP:
					if (mBluetoothGatt != null) {
						mBluetoothGatt.disconnect();
					}
					break;
				default:
					super.handleMessage(msg);
				}
			} catch (Exception e) {
				Log.d(TAG,
						"BleGattService handleMessage() Exception"
								+ e.toString());
			}
		}
	}

	/**
	 * GATT service initialization
	 */
	public boolean initialize() {
		try {
			// For API level 18 and above, get a reference to BluetoothAdapter
			// through
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
		} catch (Exception e) {
			Log.d(TAG, "BleGattService initialize() Exception" + e.toString());
		}

		return true;
	}

	/**
	 * The event of GATT service bind
	 * 
	 * @return Messenger
	 */

	@Override
	public IBinder onBind(Intent intent) {
		try {
			mBluetoothDeviceAddress = intent.getStringExtra(EXTRAS_BD_ADDR);
			mWriteCmdQue = intent.getByteArrayExtra(EXTRAS_SVR_CMD);
			if (mWriteCmdQue.length == 0) {
				mWriteCmdQue = null;
			}

			if (mWriteCmdQue[0] == BleLedCmd.Svr2Node3) {
				bSmartPhoneMode = true;
			}
		} catch (Exception e) {
			Log.d(TAG, "BleGattService onBind() Exception" + e.toString());
			return null;
		}
		return mMessenger.getBinder();
	}

	/**
	 * The event of GATT service unbind
	 * 
	 * @return trueÃ¯Â¼Å¡unbind successfulÃ£â‚¬â‚¬Ã£â‚¬â‚¬false:unbind failure
	 */
	@Override
	public boolean onUnbind(Intent intent) {
		// After using a given device, you should make sure that
		// BluetoothGatt.close() is called
		// such that resources are cleaned up properly. In this particular
		// example, close() is
		// invoked when the UI is disconnected from the Service.
		disconnect();
		close();
		return super.onUnbind(intent);
	}

	/**
	 * GATT connection of BluetoothLE is established. GATT connection is
	 * established to a LED device. GATT connection is only tried. GATT
	 * connection is not completed even if this method returns true.
	 * 
	 * @return true:GATT connection establishment false:GATT connection failure
	 */
	public boolean connect() {
		try {
			// Previously connected device. Try to reconnect.
			if (mBluetoothGatt != null) {
				Log.d(TAG,
						"Trying to use an existing mBluetoothGatt for connection.");
				if (mBluetoothGatt.connect()) {
					mConnectionState = STATE_CONNECTING;
					
					
					return true;
				} else {
					Log.d(TAG, "mBluetoothGatt.connect error.");
					return false;
				}
			}

			final BluetoothDevice device = mBluetoothAdapter
					.getRemoteDevice(mBluetoothDeviceAddress);
			if (device == null) {
				Log.w(TAG, "Device not found.  Unable to connect.");

				return false;
			}

			// mBluetoothDeviceAddress = sBdAddr;

			// GATT connection
			mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
			Log.d(TAG, "Trying to create a new connection.");
			mConnectionState = STATE_CONNECTING;

			// It waits for GATT connection by callback.
		} catch (Exception e) {
			Log.d(TAG, "BleGattService connect() Exception" + e.toString());
			return false;
		}

		return true;
	}

	/**
	 * The disconnect request of GATT connection.
	 */
	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.disconnect();
	}

	/**
	 * GATT connection is closed.
	 */
	public void close() {
		if (mBluetoothGatt == null) {
			Log.d(TAG, "mBluetoothGatt.close error.");
			return;
		}
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}

	/**
	 * The Callback class of GATT connection The Callback function at the time
	 * of connectGatt() by a connect() method.
	 */
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		private BluetoothGattCharacteristic mBleCharacteristic;

		/**
		 * An Event when status of GATT connection changes.
		 * 
		 * @param gatt
		 *            connected GATT
		 * @param status
		 *            status of GATT
		 * @param newState
		 *            new status of GATT
		 */

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
			try {
				if (status == BluetoothGatt.GATT_SUCCESS) {
					if (newState == BluetoothProfile.STATE_CONNECTED) {

						//if (DeviceScanActivity.onlyOnce == 0) {
						//	System.out.println("Mtu Response ="
						//			+ gatt.requestMtu(26));
						//	System.out.println("flag=="+ DeviceScanActivity.onlyOnce);
						//	DeviceScanActivity.onlyOnce = 1;
						//} else {
							//String address = DeviceScanActivity.ref_Address;

						//	String rootadd = DeviceScanActivity.node_Address;
						//	if (!rootadd.equals(address)) {
								//System.out.println("Mtu Response ="
									//	+ gatt.requestMtu(26));
								
						//	}
						//}
//							if (rootadd.equals(address)) {
//								System.out.println("Mtu Response ="
//										+ gatt.requestMtu(26));
//								System.out.println("flag==26");
//						//	}
//						}
					//	Thread.sleep(300);
						gatt.discoverServices();

						mConnectionState = STATE_CONNECTED;
						// mBluetoothGatt.readRemoteRssi();
					} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
						if (mBluetoothGatt != null) {
							mBluetoothGatt.close();
							mBluetoothGatt = null;

						}
						// mIsBluetoothEnable = false;
					} else {
						Log.d(TAG, "BluetoothGattCallback Error. status="
								+ status);
					}
				} else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
					mBleDevTree.clearNodeTree();
					broadcastUpdate(ACTION_NODE_TREE, ACTION_NODE_DISCONNECT);
				}
			} catch (Exception e) {
				Log.d(TAG, "BleGattService onConnectionStateChange() Exception"
						+ e.toString());
			}
		}

		/**
		 * The event of the connectable state of a remote device
		 * 
		 * @param gatt
		 *            connected GATT
		 * @param status
		 *            status of GATT
		 * @Node In the case of GATT connection, "NodeToServer1" (0x80 root node
		 *       specification) command is transmitted
		 */
		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			try {
				if (status == BluetoothGatt.GATT_SUCCESS) {

					BluetoothGattService service = gatt
							.getService(ToshibaBleServiceUuid.ScatterNetServiceUuid);
					// BluetoothGattService service =
					// gatt.getService(UUID.fromString(sUuid4));

					if (service != null) {
						mBluetoothService = service;
						mBluetoothGatt = gatt;

						if (mWriteCmdQue != null) {
							gattWriteCommand(mWriteCmdQue);
							 
						}
					}
				} else {
					Log.d(TAG, "onServicesDiscovered Error. status. status="
							+ status);
				}
			} catch (Exception e) {
				Log.d(TAG, "BleGattService onServicesDiscovered() Exception"
						+ e.toString());
			}
		}

		/**
		 * An event when Characteristic changes by GATT communication.<br>
		 * The NodeToServer command from a BLE-LED device is received.<br>
		 * BLE-LED device (GATT server) is indicate, Android (GATT client) is
		 * notificate.<br>
		 * Notice: Since data is sent from a BLE-LED device by Notify, a
		 * readCharacteristic() method is unreceivable.<br>
		 * Therefore, a readCharacteristic() method is not used this time.<br>
		 * 
		 * @param gatt
		 *            Connected GATT
		 * @param characteristic
		 *            The NodeToServer command sent from the LED device is
		 *            received.
		 */
		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
			try {
				// UUID of characteristic is checked. (The returned value of
				// getUuid() is changed by UpperCase.)
				if (ToshibaBleServiceUuid.ScatterNetGattCharUuid
						.equals(characteristic.getUuid())) {
					// Notification is received when a value is updated by
					// Peripheral.
					// The NodeToServer command is received.
					byte aGetByte[] = characteristic.getValue();

					// Debug
					if (BuildConfig.DEBUG) {
						BleLedCmd.BleLogger("Recv", aGetByte);
					}

					// The NodeToServer command is analyzed.
					BleLedDeviceNode bleLedDeviceNode = (new BleLedCmd())
							.analizeNodeData(aGetByte);

					// A command-analysis result is reflected in a topology
					// information management class.
					mBleDevTree.set(bleLedDeviceNode);
					String sWorkNodeTree = "";

					if (bSmartPhoneMode == false) {
						sWorkNodeTree = mBleDevTree.sort();
					} else {
						bleLedDeviceNode = mBleDevTree
								.getNodeData(bleLedDeviceNode.getOwnBdAddr());
						if (bleLedDeviceNode != null) {
							sWorkNodeTree = bleLedDeviceNode.getUrl();
						}
					}

					if (sWorkNodeTree != null) {
						// Data is transmitted to GUI for a tree view.
						broadcastUpdate(ACTION_NODE_TREE, sWorkNodeTree);
					}
					// A value is set to TextView by a main thread.
					// mBleHandler.sendEmptyMessage(MESSAGE_NEW_RECEIVEDNUM);

				}
			} catch (Exception e) {
				Log.d(TAG, "BleGattService onCharacteristicChanged() Exception"
						+ e.toString());
			}
		}

		long TotalRssi = 0;
		int index = 0;

		String cmdMod = DeviceScanActivity.getCmode();

		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			System.out.println(gatt.getDevice() + " RSSI:" + rssi + "db ");

			try {

				TotalRssi += rssi;

				index++;

				if (index == 80) {

					TotalRssi = TotalRssi / 80;

					if (TotalRssi <= -80 && cmdMod.equals("Walk")) {
						// Message msg = Message.obtain(null,
						// BleGattService.MSG_GATTSERVER_STOP);
						// msg.replyTo = mMessenger;
						// mMessenger.send(msg);

						mBluetoothGatt.disconnect();

						// to start Scanning......
						Intent dialogIntent = new Intent(BleGattService.this,
								DeviceScanActivity.class);
						dialogIntent.putExtra(DeviceScanActivity.Con_Mode,
								"Walk");
						dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						Thread.sleep(1000);
						startActivity(dialogIntent);

					}
					index = 0;
					TotalRssi = 0;

				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			gatt.readRemoteRssi();

		}

		/**
		 * Unused (The command from a LED device is received by
		 * onCharacteristicChanged().)
		 */
		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
			try {
				// UUID of Characteristic is checked. (The returned value of
				// getUuid() is changed by UpperCase.)
				if (ToshibaBleServiceUuid.ScatterNetGattCharUuid
						.equals(characteristic.getUuid())) {
					// Notification is received when a value is updated by
					// Peripheral.
					byte aGetByte[] = characteristic.getValue();

					// Debug
					if (BuildConfig.DEBUG) {
						BleLedCmd.BleLogger("Read", aGetByte);
					}
				}
			} catch (Exception e) {
				Log.d(TAG, "BleGattService onCharacteristicRead() Exception"
						+ e.toString());
			}
		}

		/**
		 * The write permission event of GATT The command (ServerToNode) which
		 * transmits in this event is written in. The written result is acquired
		 * in an onCharacteristicWrite() event.
		 * 
		 * @param gatt
		 *            : Connected GATT
		 * @param descriptor
		 *            : descriptor which can be written in
		 * @param status
		 *            : Status of GATT
		 */
		@Override
		public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
			boolean bRet;

			try {

				if (status == BluetoothGatt.GATT_SUCCESS) {

					// Write without response. 2015.11.02 Append.
					mBleGattChar
							.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

					// Transmit data is set to characteristic.
					System.out.println("= "+mWriteCmdQue);
					bRet = mBleGattChar.setValue(mWriteCmdQue);
					if (bRet == false) {
						Log.d(TAG, "onDescriptorWrite setValue error.");
						return;
					}

					// Write without response. 2015.11.02 Append.
					mBleGattChar
							.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

					// characteristic is set to GATT.
					bRet = mBluetoothGatt.writeCharacteristic(mBleGattChar);
					if (bRet == false) {
						Log.d(TAG,
								"onDescriptorWrite writeCharacteristic error.");
						if (mBluetoothGatt.connect()) {
							if (mWriteCmdQue[1] < BLE_SEND_MAX_RETRY) {
								mWriteCmdQue[1]++;
								gattWriteCommand(mWriteCmdQue);
								return;
							}
						} else {
							Log.d(TAG, "mBluetoothGatt disconnecting.");
						}
					} else {
						bRet = descriptor
								.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
						if (bRet == false) {
							Log.d(TAG, "gattWriteCommand setValue error.");
						}
						bRet = gatt.writeDescriptor(descriptor);
						if (bRet == false) {
							Log.d(TAG,
									"gattWriteCommand writeDescriptor error.");
						}
					}

				} else {
					Log.d(TAG, "onDescriptorWrite Error. status=" + status);
				}
			} catch (Exception e) {
				Log.d(TAG,
						"BleGattService onDescriptorWrite() Exception"
								+ e.toString());
			}

			mWriteCmdQue = null;
		}

		/**
		 * Write-in result events of GATT In BluetoothLE, in order to
		 * communicate high reliability, the data written in characteristic is
		 * returned from a LED device (Peripheral) It becomes an event success
		 * when a receiving command and a transmission command are the same
		 * values. Even if an error does not return by writeCharacteristic()
		 * method, if this event is unreceivable, BluetoothLE will become a
		 * transmission failure.
		 * 
		 * @param gatt
		 *            : Connected GATT
		 * @param characteristic
		 *            : characteristic which wrote the command
		 * @param status
		 *            : Status of GATT
		 */
		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
			try {
				if (status == BluetoothGatt.GATT_SUCCESS) {
					// UUID of Characteristic is checked. (The returned value of
					// getUuid() is changed by UpperCase.)
					if (ToshibaBleServiceUuid.ScatterNetGattCharUuid
							.equals(characteristic.getUuid())) {
						// Notification is received when a value is updated by
						// Peripheral.
						byte aGetByte[] = characteristic.getValue();
						// Debug
						if (BuildConfig.DEBUG) {
							BleLedCmd.BleLogger("Send-Recv", aGetByte);
						}

					}
				} else {
					Log.d(TAG, "onCharacteristicWrite Error. status=" + status);
				}
			} catch (Exception e) {
				Log.d(TAG, "BleGattService onCharacteristicWrite() Exception"
						+ e.toString());
			}
		}

	};

	/**
	 * BluetoothLE Data sending (Android(GATT Client) => BLE-LED device(GATT
	 * ServerÃ¯Â¼â€°) <Notice> Event acquisition of onCharacteristicWrite()
	 * shows whether normal sending was able to be carried out.
	 * 
	 * @param bSndData
	 *            It is an array of a transmission command to a LED device
	 *            (Peripheral).
	 * @return true: Completion of transmission false:Failure of transmission
	 */
	public boolean gattWriteCommand(byte[] bSndData) {
		boolean bRet = false;

		try {
			if (mBluetoothService != null) {
				// It connects with Characteristic of specified UUID.
				mBleGattChar = mBluetoothService
						.getCharacteristic(ToshibaBleServiceUuid.ScatterNetGattCharUuid);

				// Notification will be requested if a characteristic is found.
				bRet = mBluetoothGatt.setCharacteristicNotification(
						mBleGattChar, true);
				if (bRet == false) {
					Log.d(TAG,
							"gattWriteCommand setCharacteristicNotification error.");
					return bRet;
				}

				// Write without response. 2015.11.02 Append.
				mBleGattChar
						.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

				// Notification of Characteristic is validated.
				// Characteristic Ã£ï¿½Â®
				// NotificationÃ£â€šâ€™Ã¦Å“â€°Ã¥Å Â¹Ã¥Å’â€“Ã£ï¿½â„¢Ã£â€šâ€¹.
				BluetoothGattDescriptor descriptor = mBleGattChar
						.getDescriptor(ToshibaBleServiceUuid.ScatterNetGattCharConfigUuid);

				// Modify 2015.11.02 ENABLE_INDICATION_VALUE to
				// ENABLE_NOTIFICATION_VALUE
				bRet = descriptor
						.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				if (bRet == false) {
					Log.d(TAG, "gattWriteCommand setValue error.");
					return bRet;
				}
				// registered =
				// descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				bRet = mBluetoothGatt.writeDescriptor(descriptor);
				if (bRet == false) {
					return bRet;
				} else {
					bRet = true;
				}
				mWriteCmdQue = bSndData;

				// Debug
				if (BuildConfig.DEBUG) {
					BleLedCmd.BleLogger("Send", bSndData);
				}

			} else {
				// When the NodeToServer command is executed before service
				// starts
				if (mWriteCmdQue == null) {
					mWriteCmdQue = bSndData;
					bRet = true;
				} else {
					Log.d(TAG, "gattWriteCommand mBluetoothService is null.");
					bRet = false;
				}
			}
		} catch (Exception e) {
			Log.d(TAG,
					"BleGattService gattWriteCommand() Exception"
							+ e.toString());
			bRet = false;
		}
		return bRet;
	}

	/**
	 * GATT command recieve Unused (The command from a LED device is received by
	 * onCharacteristicChanged().)
	 */
	public byte[] gattReadCommand() {
		boolean bRet;
		byte bCmd[] = {};

		try {
			if (mBluetoothService != null) {
				// It connects with Characteristic of specified UUID.
				BluetoothGattCharacteristic mBleGattChar = mBluetoothService
						.getCharacteristic(ToshibaBleServiceUuid.ScatterNetGattCharUuid);

				bRet = mBluetoothGatt.setCharacteristicNotification(
						mBleGattChar, true);
				if (bRet == false) {
					Log.d(TAG,
							"gattReadCommand setCharacteristicNotification Error.");
					return bCmd;
				}

				// Notification of Characteristic is validated.
				BluetoothGattDescriptor descriptor = mBleGattChar
						.getDescriptor(ToshibaBleServiceUuid.ScatterNetGattCharConfigUuid);

				bRet = descriptor
						.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				if (bRet == false) {
					Log.d(TAG, "gattReadCommand setValue Error.");
					return bCmd;
				}

				bRet = mBluetoothGatt.writeDescriptor(descriptor);
				if (bRet == false) {
					Log.d(TAG, "gattReadCommand writeDescriptor Error.");
					return bCmd;
				}

				bRet = mBluetoothGatt.setCharacteristicNotification(
						mBleGattChar, true);
				if (bRet == false) {
					Log.d(TAG,
							"gattReadCommand setCharacteristicNotification Error.");
					return bCmd;
				}

				// Indication is published and data transmission is carried out.
				bRet = mBluetoothGatt.readCharacteristic(mBleGattChar);
				if (bRet == false) {
					Log.d(TAG, "gattReadCommand readCharacteristic Error.");
					return bCmd;
				}
				bRet = true;

			} else {
				Log.d(TAG, "gattReadCommand mBluetoothService is null.");
				bRet = false;
			}
		} catch (Exception e) {
			Log.d(TAG,
					"BleGattService gattReadCommand() Exception" + e.toString());
		}
		return bCmd;
	}

	/**
	 * Topology information transmission to GUI The data of sData (Topology
	 * information of JSON form) is transmitted to GUI.
	 * 
	 * @param action
	 *            The contents of ebroadcast
	 * @param sData
	 *            Transmit data to GUI.
	 */
	private void broadcastUpdate(final String action, final String sData) {
		final Intent intent = new Intent(action);

		intent.putExtra(action, sData);
		sendBroadcast(intent);
	}
}
