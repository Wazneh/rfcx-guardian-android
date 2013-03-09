package org.rfcx.src_arduino;

import org.rfcx.rfcx_src_android.RfcxSource;
import org.rfcx.src_database.ArduinoDb;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class ArduinoService extends Service {

	private static final String TAG = ArduinoService.class.getSimpleName();
	
	static final int DELAY = 40000;
	static final int DELAY_INNER = 2000;
	String[] arduinoCommands = new String[] {"a","b"};
	
	private boolean runFlag = false;
	private ArduinoCommSvc arduinoCommSvc;
	
	ArduinoDb arduinoDbHelper = new ArduinoDb(this);

	private RfcxSource rfcxSource = null;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		((RfcxSource) getApplication()).appResume();
		this.arduinoCommSvc = new ArduinoCommSvc();
		arduinoDbHelper = new ArduinoDb(this);
		if (RfcxSource.verboseLog()) { Log.d(TAG, "onCreated()"); }
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		this.runFlag = true;
		rfcxSource = (RfcxSource) getApplication();
		rfcxSource.isServiceRunning_ArduinoState = true;
		this.arduinoCommSvc.start();
		if (RfcxSource.verboseLog()) { Log.d(TAG, "onStarted()"); }
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		this.runFlag = false;
		rfcxSource.isServiceRunning_ArduinoState = false;
		this.arduinoCommSvc.interrupt();
		this.arduinoCommSvc = null;
		if (RfcxSource.verboseLog()) { Log.d(TAG, "onDestroyed()"); }
	}
	
	public boolean isRunning() {
		return runFlag;
	}
	
	private class ArduinoCommSvc extends Thread {
		
		public ArduinoCommSvc() {
			super("ArduinoCommService-ArduinoComm");
		}
		
		@Override
		public void run() {
			ArduinoService arduinoCommService = ArduinoService.this;
			rfcxSource = (RfcxSource) getApplication();
			while (arduinoCommService.runFlag) {
				if (RfcxSource.verboseLog()) { Log.d(TAG, "ArduinoCommService running"); }
				try {
					for (int i = 0; i < arduinoCommands.length; i++) {
						rfcxSource.sendArduinoCommand(arduinoCommands[i]);
						Thread.sleep(DELAY_INNER);
					}
					Thread.sleep(DELAY);
				} catch (InterruptedException e) {
					arduinoCommService.runFlag = false;
					rfcxSource.isServiceRunning_ArduinoState = false;
				}
			}
		}		
	}
	




}
