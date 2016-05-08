package ch.nexuscomputing.simpleaccessory;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import ch.nexuscomputing.simpleaccessory.AccessoryEngine.IEngineCallback;

public class MainActivity extends Activity {

	private AccessoryEngine mEngine = null;
	private ImageView mButtonImage = null;
	
	private TextView mBrightnessText = null;
	private int color = 0;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		onNewIntent(getIntent());
		setContentView(R.layout.activity_main);
		mButtonImage = (ImageView) findViewById(R.id.ivButton);
		mBrightnessText = (TextView) findViewById(R.id.tvBrightness);
		updateResource(color);
		PackageManager pm;
		pm = getPackageManager();
		boolean isUSBAccessory = pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY);

		if(!isUSBAccessory){
			Toast.makeText(this, "USB Accessory not supported", Toast.LENGTH_SHORT).show();
			this.finish();
			return;
		}else {
			Toast.makeText(this, "USB Accessory supported", Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		L.d("handling intent action: " + intent.getAction());
		if (mEngine == null) {
			mEngine = new AccessoryEngine(getApplicationContext(), mCallback);
		}
		mEngine.onNewIntent(intent);
		super.onNewIntent(intent);
	}

	@Override
	protected void onDestroy() {
		mEngine.onDestroy();
		mEngine = null;
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}


	private final IEngineCallback mCallback = new IEngineCallback() {
		@Override
		public void onDeviceDisconnected() {
			L.d("device physically disconnected");
		}

		@Override
		public void onConnectionEstablished() {
			L.d("device connected! ready to go!");
		}

		@Override
		public void onConnectionClosed() {
			L.d("connection closed");
		}

		@Override
		public void onDataRecieved(byte[] data, int num) {
			if(num > 0){
				color = data[0]&0xFF;
				updateResource(color);
			}
		}
	};
	
	private void updateResource(int in){
		final int color = in;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mBrightnessText.setText("alpha = " + color);
				mButtonImage.setBackgroundColor(Color.argb(color, 255, 255, 255));
			}
		});
	}
}
