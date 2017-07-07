package com.zx.zpush;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Notification.Action;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import java.io.File;
import java.io.IOException;

import org.teleal.cling.android.AndroidUpnpService;
import org.teleal.cling.android.AndroidUpnpServiceImpl;
import org.teleal.cling.controlpoint.ActionCallback;
import org.teleal.cling.model.action.ActionException;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.Device;
import org.teleal.cling.model.meta.LocalDevice;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.types.ServiceId;
import org.teleal.cling.model.types.UDAServiceId;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.Registry;
import org.teleal.cling.registry.RegistryListener;
import org.teleal.cling.support.avtransport.callback.Pause;
import org.teleal.cling.support.avtransport.callback.Play;
import org.teleal.cling.support.avtransport.callback.SetAVTransportURI;
import org.teleal.cling.support.avtransport.callback.Stop;
import org.teleal.cling.support.connectionmanager.ConnectionManagerService;
import org.teleal.cling.support.connectionmanager.callback.GetProtocolInfo;
import org.teleal.cling.support.connectionmanager.callback.PrepareForConnection;
import org.teleal.cling.support.model.ProtocolInfo;
import org.teleal.cling.support.model.ProtocolInfos;

import com.zx.zpush.R.id;



public class MainActivity extends Activity implements android.view.View.OnClickListener{

	private String s="AVTransport";
	private String s1="ConnectionManager";
	//	ProtocolInfo sink;

	private Dialog listdialog;
	private Button btnStart,btnSearch,btnMedia,btnPlay,btnPause,btnStop;
	private ListView devicelist;
	private ArrayAdapter<DeviceDisplay> listAdapter;
	
	private Device device;
	private AndroidUpnpService upnpService;
	private RegistryListener registryListener = new BrowseRegistryListener();
	
	private static final int REQUEST_CODE = 1;
    private MediaProjectionManager mMediaProjectionManager;
    private ScreenRecorder mRecorder;
    private Button mButton;
	private int mScreenWidth;
	private int mScreenHeight;
	private int mScreenDensity;
	private static final int bitrate = 600000;
	

	private ServiceConnection serviceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {

			upnpService = (AndroidUpnpService) service;
			// Refresh the list with all known devices
			listAdapter.clear();
			for (Device device : upnpService.getRegistry().getDevices()) {
				((BrowseRegistryListener) registryListener).deviceAdded(device);
			}
			// Getting ready for future device advertisements
			upnpService.getRegistry().addListener(registryListener);
			// Search asynchronously for all devices
			upnpService.getControlPoint().search();
		}
		public void onServiceDisconnected(ComponentName className) {
			upnpService = null;
		}
	};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		btnStart=(Button)findViewById(R.id.start_btn);
		btnSearch = (Button) findViewById(R.id.restart_btn);
		btnMedia = (Button) findViewById(R.id.button);
		btnPlay = (Button) findViewById(R.id.play_btn);
		btnPause = (Button) findViewById(R.id.pause_btn);
		btnStop = (Button) findViewById(R.id.stop_btn);
		
		btnStart.setOnClickListener(this);
		btnSearch.setOnClickListener(this);
		btnMedia.setOnClickListener(this);
		btnPlay.setOnClickListener(this);
		btnPause.setOnClickListener(this);
		btnStop.setOnClickListener(this);
		

		mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        initDisplayMetrics();
	}
	


	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.start_btn:
			if (upnpService != null) { 
				upnpService.getRegistry().removeAllRemoteDevices(); 
				upnpService.getControlPoint().search(); 
			} 
			showDialog();
			break;
		case R.id.restart_btn:
			if (upnpService != null) { 
				upnpService.getRegistry().removeAllRemoteDevices(); 
				upnpService.getControlPoint().search(); 
				Toast.makeText(this, "刷新成功", Toast.LENGTH_SHORT).show();
			}
			break;
		case R.id.button: 		// start mediaProjection
	        if (mRecorder != null) {
	            mRecorder.quit();
	            mRecorder = null;
	            mButton.setText("Restart recorder");
	        } else {
	            Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
	            startActivityForResult(captureIntent, REQUEST_CODE);
	        }	
			break;
		case R.id.play_btn:
			if(device == null){
				Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show();
				return;
			}
			executePlay(device);
			break;
		case R.id.pause_btn:
			if(device == null){
				Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show();
				return;
			}			executePause(device);
			break;
		case R.id.stop_btn:
			if(device == null){
				Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show();
				return;
			}			executeStop(device);
			break;
		}
	}


	private void initDisplayMetrics() {
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		mScreenWidth = metrics.widthPixels;
		mScreenHeight = metrics.heightPixels;
		mScreenDensity = metrics.densityDpi;
	}
	
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e("TAG", "media projection is null");
            return;
        }
        // video size
//        File file = new File(Environment.getExternalStorageDirectory() + "/"
//                + this.getPackageName() + "/ScreenRecorder-" + mScreenWidth + "x" + mScreenHeight + "-" 
//                + ".mp4"); 
//        File dirs = new File(file.getParent());
//        if (!dirs.exists())
//            dirs.mkdirs();
//        try {
//			file.createNewFile();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
        mRecorder = new ScreenRecorder(mScreenWidth, mScreenHeight, bitrate , mScreenDensity, mediaProjection);
        mRecorder.start();
        mButton.setText("Stop Recorder");
        Toast.makeText(this, "Screen recorder is running...", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }


	public void showDialog(){
		AlertDialog.Builder builder = new Builder(this);
		builder.setTitle("可选择设备......");
		final LayoutInflater inflater = LayoutInflater.from(this);
		View v = inflater.inflate(R.layout.listview, null);

		getApplicationContext().bindService(
				new Intent(this, MyUpnpService.class),
				serviceConnection,
				Context.BIND_AUTO_CREATE
				);
		devicelist = (ListView) v.findViewById(R.id.devicelist);
		listAdapter = new ArrayAdapter<DeviceDisplay>( this, android.R.layout.simple_list_item_1);
		devicelist.setAdapter(listAdapter);

		builder.setView(v);
		builder.setNegativeButton("取消", new OnClickListener() {	
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		listdialog=builder.create();
		listdialog.show();

		devicelist.setOnItemClickListener(new OnItemClickListener(){

			public void onItemClick(AdapterView<?> parent, View v,int position, long id) {
				Toast.makeText(getApplicationContext(), "选中第"+(position+1)+"项",  Toast.LENGTH_SHORT).show();
				DeviceDisplay devicePlay=listAdapter.getItem(position);
				device= devicePlay.getDevice();
//				String url="http://v.cctv.com/flash/mp4video6/TMS/2011/01/05/cf752b1c12ce452b3040cab2f90bc265_h264818000nero_aac32-1.mp4";
				String url="http://imgsrc.baidu.com/baike/pic/item/3b87e950352ac65c2c0b49dbf1f2b21193138a0f.jpg";
				Uri.parse(url);
				GetInfo(device);
				executeAVTransportURI(device,url);
//				executePlay(device);
				listdialog.dismiss();
			}
		});
	}


	class BrowseRegistryListener extends DefaultRegistryListener { 

		@Override 
		public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) { 
			deviceAdded(device); 
		} 

		@Override 
		public void remoteDeviceDiscoveryFailed(Registry registry, final RemoteDevice device, final Exception ex) { 

			deviceRemoved(device); 
		} 

		@Override 
		public void remoteDeviceAdded(Registry registry, RemoteDevice device) { 
			deviceAdded(device); 
		} 

		@Override 
		public void remoteDeviceRemoved(Registry registry, RemoteDevice device) { 
			deviceRemoved(device); 
		} 

		@Override 
		public void localDeviceAdded(Registry registry, LocalDevice device) { 
			deviceAdded(device); 
		} 

		@Override 
		public void localDeviceRemoved(Registry registry, LocalDevice device) { 
			deviceRemoved(device); 
		} 

		public void deviceAdded(final Device device) { 
			runOnUiThread(new Runnable() { 
				public void run() { 
					DeviceDisplay d = new DeviceDisplay(device); 
					int position = listAdapter.getPosition(d); 
					if (position >= 0) { 
						// Device already in the list, re-set new value at same position 
						listAdapter.remove(d); 
						listAdapter.insert(d, position); 
					} else { 
						listAdapter.add(d); 
					} 
					//	                listAdapter.sort(DISPLAY_COMPARATOR);
					listAdapter.notifyDataSetChanged();
				} 
			}); 
		} 

		public void deviceRemoved(final Device device) { 
			runOnUiThread(new Runnable() { 
				public void run() { 
					listAdapter.remove(new DeviceDisplay(device)); 
				} 
			}); 
		} 
	}

	public void executeAVTransportURI(Device device, String uri){

		ServiceId AVTransportId = new UDAServiceId(s);
		Service service = device.findService(AVTransportId);
		ActionCallback callback = new SetAVTransportURI(service, uri){
			@Override
			public void failure(ActionInvocation arg0, UpnpResponse arg1,String arg2) {
				Log.e("SetAVTransportURI","failed^^^^^^^");
			}
		};
		upnpService.getControlPoint().execute(callback);

	}
	
	/**
	 *  start player
	 * @param device
	 */
	public void executePlay(Device device){
		ServiceId AVTransportId = new UDAServiceId(s);
		Service service = device.findService(AVTransportId);
		ActionCallback playcallback = new Play(service){
			@Override
			public void failure(ActionInvocation arg0, UpnpResponse arg1,String arg2) {
				// TODO Auto-generated method stub
				Log.e("Play","failed^^^^^^^");
			}
		};
		upnpService.getControlPoint().execute(playcallback);
	}
	
	
	/**
	 *  pause player
	 * @param device
	 */
	public void executePause(Device device){
		ServiceId AVTransportId = new UDAServiceId(s);
		Service service = device.findService(AVTransportId);
		ActionCallback pausecallback = new Pause(service) {
			@Override
			public void failure(ActionInvocation arg0, UpnpResponse arg1, String arg2) {
				Log.e("Pause", "failed~~~~~~~");
			}
		};
		upnpService.getControlPoint().execute(pausecallback);
	}
	
	
	/**
	 *  Stop player
	 * @param device
	 */
	public void executeStop(Device device){
		ServiceId AVTransportId = new UDAServiceId(s);
		Service service = device.findService(AVTransportId);
		ActionCallback stopcallback = new Stop(service) {
			@Override
			public void failure(ActionInvocation arg0, UpnpResponse arg1, String arg2) {
				Log.i("Stop", "failed~~~~~~~");
			}
		};
		upnpService.getControlPoint().execute(stopcallback);
	}
	
	

	public void GetInfo(Device device){
		ServiceId AVTransportId = new UDAServiceId(s1);
		Service service = device.findService(AVTransportId);
		ActionCallback getInfocallback=new GetProtocolInfo(service){

			@Override
			public void received(ActionInvocation actionInvocation,ProtocolInfos sinkProtocolInfos,ProtocolInfos sourceProtocolInfos) {
				// TODO Auto-generated method stub
				Log.v("sinkProtocolInfos",sinkProtocolInfos.toString());
				Log.v("sourceProtocolInfos",sourceProtocolInfos.toString());
			}

			@Override
			public void failure(ActionInvocation arg0, UpnpResponse arg1,String arg2) {
				// TODO Auto-generated method stub
				Log.v("GetProtocolInfo","failed^^^^^^^");
			}

		};
		upnpService.getControlPoint().execute(getInfocallback);
	}

	public void PrepareConn(Device device){
		ServiceId AVTransportId = new UDAServiceId(s1);
		Service service = device.findService(AVTransportId);
		ActionCallback prepareConncallback=new PrepareForConnection(service,null,null,-1, null){

			@Override
			public void received(ActionInvocation invocation, int connectionID,int rcsID, int avTransportID) {
				// TODO Auto-generated method stub
				Log.v("avTransportID",Integer.toString(avTransportID));
			}

			@Override
			public void failure(ActionInvocation arg0, UpnpResponse arg1,String arg2) {
				// TODO Auto-generated method stub
				Log.v("PrepareForConnection","failed^^^^^^^");
			}

		};
		upnpService.getControlPoint().execute(prepareConncallback);
	}



	protected void onDestroy() {
		super.onDestroy();
		if (upnpService != null) {
			upnpService.getRegistry().removeListener(registryListener);
		}
		getApplicationContext().unbindService(serviceConnection);
        if(mRecorder != null){
            mRecorder.quit();
            mRecorder = null;
        }
	}


}
