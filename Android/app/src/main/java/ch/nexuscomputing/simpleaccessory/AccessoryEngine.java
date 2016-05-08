package ch.nexuscomputing.simpleaccessory;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;

public class AccessoryEngine {

	private static final int BUFFER_SIZE = 1024;
	private final Context mContext;
	private final UsbManager mUsbManager;
	private final IEngineCallback mCallback;

	private final static String ACTION_ACCESSORY_DETACHED = "android.hardware.usb.action.USB_ACCESSORY_DETACHED";

	private volatile boolean mAccessoryConnected = false;
	private final AtomicBoolean mQuit = new AtomicBoolean(false);

	private UsbAccessory mAccessory = null;

	private ParcelFileDescriptor mParcelFileDescriptor = null;
	private FileDescriptor mFileDescriptor = null;
	private FileInputStream mInputStream = null;
	private FileOutputStream mOutputStream = null;

	public interface IEngineCallback {
		void onConnectionEstablished();

		void onDeviceDisconnected();

		void onConnectionClosed();

		void onDataRecieved(byte[] data, int num);
	}

	public AccessoryEngine(Context applicationContext, IEngineCallback callback) {
		mContext = applicationContext;
		mCallback = callback;
		mUsbManager = (UsbManager) mContext
				.getSystemService(Context.USB_SERVICE);
		mContext.registerReceiver(mDetachedReceiver, new IntentFilter(
				ACTION_ACCESSORY_DETACHED));
	}

	public void onNewIntent(Intent intent) {
		if (mUsbManager.getAccessoryList() != null) {
			mAccessory = mUsbManager.getAccessoryList()[0];
			connect();
		}
	}

	private void connect() {
		if (mAccessory != null) {
			if (sAccessoryThread == null) {
				sAccessoryThread = new Thread(mAccessoryRunnable,
						"Reader Thread");
				sAccessoryThread.start();
			} else {
				L.d("reader thread already started");
			}
		} else {
			L.d("accessory is null");
		}
	}

	public void onDestroy() {
		// closeConnection();
		mQuit.set(true);
		mContext.unregisterReceiver(mDetachedReceiver);
	}

	private final BroadcastReceiver mDetachedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent != null
					&& ACTION_ACCESSORY_DETACHED.equals(intent.getAction())) {
				mCallback.onDeviceDisconnected();
			}
		}
	};

	public void write(byte[] data) {
		if (mAccessoryConnected && mOutputStream != null) {
			try {
				mOutputStream.write(data);
			} catch (IOException e) {
				L.e("could not send data");
			}
		} else {
			L.d("accessory not connected");
		}
	}

	private static Thread sAccessoryThread;
	private final Runnable mAccessoryRunnable = new Runnable() {
		@Override
		public void run() {
			L.d("open connection");
			mParcelFileDescriptor = mUsbManager.openAccessory(mAccessory);
			if (mParcelFileDescriptor == null) {
				L.e("could not open accessory");
				mCallback.onConnectionClosed();
				return;
			}
			mFileDescriptor = mParcelFileDescriptor.getFileDescriptor();
			mInputStream = new FileInputStream(mFileDescriptor);
			mOutputStream = new FileOutputStream(mFileDescriptor);
			mCallback.onConnectionEstablished();
			mAccessoryConnected = true;

			byte[] buf = new byte[BUFFER_SIZE];
			while (!mQuit.get()) {
				try {
					int read = mInputStream.read(buf);
					mCallback.onDataRecieved(buf, read);
					//String rep = "respond from android " + (buf[0]&0xFF);
					//mOutputStream.write(rep.getBytes());
				} catch (Exception e) {
					L.e("ex " + e.getMessage());
					break;
				}
			}
			L.d("exiting reader thread");
			mCallback.onConnectionClosed();

			if (mParcelFileDescriptor != null) {
				try {
					mParcelFileDescriptor.close();
				} catch (IOException e) {
					L.e("Unable to close ParcelFD");
				}
			}

			if (mInputStream != null) {
				try {
					mInputStream.close();
				} catch (IOException e) {
					L.e("Unable to close InputStream");
				}
			}

			if (mOutputStream != null) {
				try {
					mOutputStream.close();
				} catch (IOException e) {
					L.e("Unable to close OutputStream");
				}
			}

			mAccessoryConnected = false;
			mQuit.set(false);
			sAccessoryThread = null;
		}
	};
}
