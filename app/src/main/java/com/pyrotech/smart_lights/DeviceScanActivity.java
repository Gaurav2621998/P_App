
package com.pyrotech.smart_lights;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.stsdemo.test.BleLedCmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends AppCompatActivity {

	private static final int MY_PERMISSIONS_REQUEST_LOCATION = 1;
	public static int onlyOnce=0;


	public static final boolean BLE_CONTROLLER_MODE = true;
	public static final boolean BLE_SERVER_MODE = false;
	private static final int REQUEST_ENABLE_BT = 1;
	private static final long SCAN_PERIOD = 5000;
	protected static final String Con_Mode = "ControllingMode";
	public static String ControllingMode = "Hand";


	private LeDeviceListAdapter mLeDeviceListAdapter;
	public BluetoothAdapter bluetoothAdapter;
	public Scanner scanner;

	public boolean mScanning;
	public static String RootAddress = "";
	private String RootDeviceName = "";
	private static boolean mBleApplicationMode = BLE_CONTROLLER_MODE;
	public static String ref_Address="";
	public static String node_Address="";
    public ArrayList<BluetoothDevice> LeDevices = new ArrayList<BluetoothDevice>();
	ListView listView;
	private int fmenu =0;

	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Toolbar toolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
		setSupportActionBar(toolbar);
		String sApplicationId = getApplication().getPackageName();
		listView = (ListView)findViewById(R.id.listview);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                final BluetoothDevice device = LeDevices.get(i);
                Toast.makeText(DeviceScanActivity.this, ""+device.getAddress(), Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(DeviceScanActivity.this,connectedDevice.class);
                intent.putExtra(connectedDevice.EXTRAS_DEVICE_ADDRESS,
                        device.getAddress());
                startActivity(intent);
            }
        });


			setTitle(R.string.title);


		// Use this check to determine whether BLE is supported on the device.
		// Then you can selectively disable BLE-related features.
		if (!getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, "ble_not_supported", Toast.LENGTH_LONG)
					.show();
			finish();
		}

		// Initializes a Bluetooth adapter. For API level 18 and above, get a
		// reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		bluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (bluetoothAdapter == null) {
			Toast.makeText(this, "error_bluetooth_not_supported", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

	}

    public static boolean getAppMode() {
        return mBleApplicationMode;
    }


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.gatt_scan, menu);

		if (fmenu==1){
			menu.findItem(R.id.menu_stop).setVisible(false);
			menu.findItem(R.id.menu_scan).setVisible(true);
			menu.findItem(R.id.menu_log).setVisible(true);
		//	menu.findItem(R.id.menu_refresh).setActionView(null);
	}else {
			menu.findItem(R.id.menu_stop).setVisible(true);
			menu.findItem(R.id.menu_scan).setVisible(false);
			menu.findItem(R.id.menu_log).setVisible(true);
//			menu.findItem(R.id.menu_refresh).setActionView(
//					R.layout.actionbar_indeterminate_progress);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_scan:
			mLeDeviceListAdapter.clear();
			if (fmenu == 1) {
				fmenu = 0;
				bluetoothAdapter.startLeScan(mLeScanCallback);
				mLeDeviceListAdapter.ClearNodeMode();
				invalidateOptionsMenu();
			}
			break;
		case R.id.menu_stop:
				fmenu=1;
				bluetoothAdapter.stopLeScan(mLeScanCallback);
                mLeDeviceListAdapter.notifyDataSetChanged();
				invalidateOptionsMenu();

			break;
		}
		if (item.getItemId() == R.id.menu_log) {
//			Intent details = new Intent(DeviceScanActivity.this, ConnectionDetails.class);
//			startActivity(details);
			}

		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
		checkLocationPermission();

		// Ensures Bluetooth is enabled on the device. If Bluetooth is not
				// currently enabled,
				// fire an intent to display a dialog asking the user to grant
				// permission to enable it.
				if (!bluetoothAdapter.isEnabled()) {
					if (!bluetoothAdapter.isEnabled()) {
						Intent enableBtIntent = new Intent(
								BluetoothAdapter.ACTION_REQUEST_ENABLE);
						startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
					}
				}

				// Initializes list view adapter.
				mLeDeviceListAdapter = new LeDeviceListAdapter();
//				setListAdapter(mLeDeviceListAdapter);
		listView.setAdapter(mLeDeviceListAdapter);

					bluetoothAdapter.startLeScan(mLeScanCallback);
				invalidateOptionsMenu();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

				if (requestCode == REQUEST_ENABLE_BT
						&& resultCode == Activity.RESULT_CANCELED) {
					finish();
					return;
				}
				super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onPause() {
		super.onPause();

			mLeDeviceListAdapter.clear();
			mLeDeviceListAdapter.ClearNodeMode();
	}

	/**
	 * An event in case activity is canceled
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	public static String getCmode(){
		return ControllingMode;
	}



//i have commented this
	public void sendBleLedCommand(String sBdAddr) {
		Intent gattServiceIntent = new Intent(this, BleGattService.class);

		gattServiceIntent.putExtra(BleGattService.EXTRAS_BD_ADDR, sBdAddr);
//i have commented this
	}


	int rssiSize = 15;

	int listrssi[] = new int[rssiSize];
	int listrssiavg[] = new int[rssiSize];
	String listAddress[] = new String[rssiSize];
	String listDeviceName[] = new String[rssiSize];
	int MacCount[] = new int[rssiSize];

	int count = 0;
	int index = 0;
	String DEviceAddress;
	String DEvicename;
	int Devicecount = 0;
	boolean Added = false;
	int position = 0;

	public BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		public void onLeScan(final BluetoothDevice device, final int rssi,
                             final byte[] scanRecord) {
			runOnUiThread(new Runnable() {

				@RequiresApi(api = Build.VERSION_CODES.ECLAIR)
				public void run() {

					String Address = device.getAddress();
					String sAddr[] = Address.split(":");

					for (int j = 0; j < sAddr.length; j++) {
//						Toast.makeText(DeviceScanActivity.this, "callBack Called", Toast.LENGTH_SHORT).show();
						if (sAddr[0].equals("00")) {
							count++;
//							Toast.makeText(DeviceScanActivity.this, ""+device.getName(), Toast.LENGTH_SHORT).show();
							mLeDeviceListAdapter.addDevice(device, rssi);
							mLeDeviceListAdapter
							.AddNodeMode(
									device.getAddress(),
									rssi,
									scanRecord[BleLedCmd.ADV_PKT_STATUS_OFFSET]);
							mLeDeviceListAdapter.notifyDataSetChanged();
							//boolean bCanSelectRoot = false;




							Added = false;
							for (int i = 0; i < Devicecount; i++) {
								if (listAddress[i].equals(device.getAddress())) {
									Added = true;
									position = i;
									break;
								}
							}
							if (Added == false) {
								listrssi[Devicecount] = rssi;
								listAddress[Devicecount] = device.getAddress();
								listDeviceName[Devicecount] = device.getName();
								MacCount[Devicecount]++;
								Devicecount++;
							} else {
								if (position < 10) {
									MacCount[position]++;
									listrssi[position] += rssi;
								}
							}

							if (count >= 10) {

								count = 0;
								int lowest_index = 0;
								if (Devicecount > 1) {
									for (int i = 0; i < Devicecount; i++) {
										// System.out.println("Devicecount  =" +
										// Devicecount);
										// System.out.println("MacCount  =" +
										// MacCount[i]);
										listrssiavg[i] = (listrssi[i] / MacCount[i]);
										// System.out.println(listDeviceName[i]);
										// System.out.println(listrssiavg[i]);

									}
									for (int i = 0; i < Devicecount - 1; i++) {
										if (listrssiavg[i] > listrssiavg[i + 1]) {
											lowest_index = i;
										} else {
											lowest_index = i + 1;
										}
									}
								} else {
									lowest_index = 0;
								}

								// if user select Walk Mode....

								if (ControllingMode.equals("Walk")) {

//i have commented this
//									final Intent intent = new Intent(
//											DeviceScanActivity.this,
//											NodeTreeViewActivity.class);
//
//									//if (RootDeviceName.equals("")) {
//										intent.putExtra(
//												NodeTreeViewActivity.EXTRAS_DEVICE_NAME,
//												listDeviceName[lowest_index]);
//										intent.putExtra(
//												NodeTreeViewActivity.EXTRAS_DEVICE_ADDRESS,
//												listAddress[lowest_index]);
//i have commented this

								//	}


									//if (RootAddress.equals("")) {
										sendBleLedCommand(device.getAddress());
										ref_Address = device.getAddress();
									//} else {
									//	sendBleLedCommand(RootAddress);
									//}

									// A tablet changes to NodeTreeView activity
									// and a smart phone changes
									// to a detailed setting screen.
									DEvicename = null;
									DEviceAddress = null;
//									startActivity(intent);
								}

							}

						}
						break;
					}
				}
			});
		}
	};



	/**
	 * The operation definition class of ListView (device name,
	 * BluetoothAddress)
	 */
	private class LeDeviceListAdapter extends BaseAdapter {
		private ArrayList<BluetoothDevice> mLeDevices;
		private LayoutInflater mInflator;

		private HashMap<String, LeDeviceMode> mNodeList = new HashMap<String, LeDeviceMode>();

		/**
		 * constractor of ListView
		 */
		public LeDeviceListAdapter() {
			super();
			mLeDevices = new ArrayList<BluetoothDevice>();
			mInflator = DeviceScanActivity.this.getLayoutInflater();
		}

		/**
		 * The device is registered to ListView.
		 *
		 * @Param device The BluetoothLE device registered
		 */
		public void addDevice(BluetoothDevice device, int rssi) {
			if (!mLeDevices.contains(device)) {
				mLeDevices.add(device);
				LeDevices = mLeDevices;
				Toast.makeText(DeviceScanActivity.this, ""+device.getName()+" - "+device.getAddress(), Toast.LENGTH_SHORT).show();
			}
		}

		public void AddNodeMode(String srcBdAddr, int srcRssi, byte srcMode) {
			LeDeviceMode mNodeDat = new LeDeviceMode(srcRssi, srcMode);
			mNodeList.put(srcBdAddr, mNodeDat);
		}

		public void ClearNodeMode() {
			mNodeList.clear();
			mLeDeviceListAdapter.notifyDataSetChanged();
		}


		public BluetoothDevice getDevice(int position) {
			return mLeDevices.get(position);
		}

		public void clear() {
			mLeDevices.clear();
		}

		public int getCount() {
			return mLeDevices.size();
		}

		public Object getItem(int i) {
			return mLeDevices.get(i);
		}

		public long getItemId(int i) {
			return i;
		}

		@SuppressLint("InflateParams")
		public View getView(int i, View view, ViewGroup viewGroup) {
			ViewHolder viewHolder;
			// General ListView optimization code.

			if (view == null) {
				view = mInflator.inflate(R.layout.activity_scan_dev, null);
				viewHolder = new ViewHolder();
				viewHolder.deviceAddress = (TextView) view
						.findViewById(R.id.device_address);
				viewHolder.deviceName = (TextView) view
						.findViewById(R.id.device_name);
				viewHolder.layout = (LinearLayout)findViewById(R.id.layout);
				view.setTag(viewHolder);

			} else {
				viewHolder = (ViewHolder) view.getTag();
			}

			final BluetoothDevice device = mLeDevices.get(i);

			String sModeTitle = "";
			LeDeviceMode leNodeDev = mNodeList.get(device.getAddress());
			if (leNodeDev != null) {
				if ((leNodeDev.getmMode() & BleLedCmd.ADV_PKT_CTRL) != 0) {
					sModeTitle = sModeTitle + "     RootNode";
					RootDeviceName = device.getName();
					RootAddress = device.getAddress();
				} else if ((leNodeDev.getmMode() & BleLedCmd.ADV_PKT_PARENT) != 0) {
					sModeTitle = sModeTitle + "     Node";
					node_Address = device.getAddress();
				}
			}

			final String deviceName = device.getName();
			if (deviceName != null && deviceName.length() > 0)
				viewHolder.deviceName.setText(deviceName + sModeTitle);
			else
				viewHolder.deviceName.setText("unknown_device");
			viewHolder.deviceAddress.setText(device.getAddress());


			return view;
		}
	}

	static class ViewHolder {
		TextView deviceName;
		TextView deviceAddress;
		LinearLayout layout;
	}

	private class LeDeviceMode {
		private int mRssi;
		private byte mMode;

		public LeDeviceMode(int srcRssi, byte srcMode) {
			mRssi = srcRssi;
			mMode = srcMode;
		}

		public void setRssi(int srcRssi) {
			mRssi = srcRssi;
		}

		public void setMode(byte srcMode) {
			mRssi = srcMode;
		}

		public int getRssi() {
			return mRssi;
		}

		public byte getmMode() {
			return mMode;
		}

	}

	public boolean checkLocationPermission() {
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED) {

			// Should we show an explanation?
			if (ActivityCompat.shouldShowRequestPermissionRationale(this,
					Manifest.permission.ACCESS_FINE_LOCATION)) {

				// Show an explanation to the user *asynchronously* -- don't block
				// this thread waiting for the user's response! After the user
				// sees the explanation, try again to request the permission.
				new AlertDialog.Builder(this)
						.setTitle("title_location_permission")
						.setMessage("text_location_permission")
						.setPositiveButton("ok", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								//Prompt the user once explanation has been shown
								ActivityCompat.requestPermissions(DeviceScanActivity.this,
										new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
										MY_PERMISSIONS_REQUEST_LOCATION);
							}
						})
						.create()
						.show();


			} else {
				// No explanation needed, we can request the permission.
				ActivityCompat.requestPermissions(this,
						new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
						MY_PERMISSIONS_REQUEST_LOCATION);
			}
			return false;
		} else {
			return true;
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {

	}


}