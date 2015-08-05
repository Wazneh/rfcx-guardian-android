package org.rfcx.guardian.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;

import org.rfcx.guardian.installer.api.ApiCore;
import org.rfcx.guardian.installer.receiver.ConnectivityReceiver;
import org.rfcx.guardian.installer.service.ApiCheckVersionService;
import org.rfcx.guardian.installer.service.DownloadFileService;
import org.rfcx.guardian.installer.service.InstallAppService;
import org.rfcx.guardian.installer.service.ApiCheckVersionIntentService;
import org.rfcx.guardian.utility.DeviceGuid;
import org.rfcx.guardian.utility.DeviceToken;
import org.rfcx.guardian.utility.ShellCommands;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.text.TextUtils;
import android.util.Log;

public class RfcxGuardianInstaller extends Application implements OnSharedPreferenceChangeListener {

	private static final String TAG = "RfcxGuardianInstaller-"+RfcxGuardianInstaller.class.getSimpleName();
	private static final String NULL_EXC = "Exception thrown, but exception itself is null.";
	public String version;
	Context context;
	public boolean verboseLog = true;
	public boolean isConnected = false;
	public long lastConnectedAt = Calendar.getInstance().getTimeInMillis();
	public long lastDisconnectedAt = Calendar.getInstance().getTimeInMillis();
	public long lastApiCheckTriggeredAt = Calendar.getInstance().getTimeInMillis();
	
	private String deviceId = null;
	private String deviceToken = null;
	
	public static final String thisAppRole = "installer";
	public static final String targetAppRoleApiEndpoint = "updater";
	public String targetAppRole = "updater";
	
	private RfcxGuardianInstallerPrefs rfcxGuardianInstallerPrefs = new RfcxGuardianInstallerPrefs();
	public SharedPreferences sharedPrefs = rfcxGuardianInstallerPrefs.createPrefs(this);
		
	private final BroadcastReceiver connectivityReceiver = new ConnectivityReceiver();
	
	public ApiCore apiCore = new ApiCore();
	
	public boolean isRunning_ApiCheckVersion = false;
	public boolean isRunning_DownloadFile = false;
	public boolean isRunning_InstallApp = false;
	
	public boolean isRunning_UpdaterService = false;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		rfcxGuardianInstallerPrefs.initializePrefs();
		rfcxGuardianInstallerPrefs.checkAndSet(this);
		
		setAppVersion();
		
		Log.d(TAG, "org.rfcx.guardian."+this.targetAppRole+" version: "+getCurrentGuardianTargetRoleVersion());

		(new ShellCommands()).executeCommandAsRoot("pm list features",null,getApplicationContext());
		
		this.registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		
		apiCore.setApiCheckVersionEndpoint(getDeviceId());
	    initializeRoleIntentServices(getApplicationContext());
	}
	
	public void onTerminate() {
		super.onTerminate();
		this.unregisterReceiver(connectivityReceiver);
	}
	
	public void appResume() {
		rfcxGuardianInstallerPrefs.checkAndSet(this);
	}
	
	public void appPause() {
	}
	
	@Override
	public synchronized void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (this.verboseLog) { Log.d(TAG, "Preference changed: "+key); }
		rfcxGuardianInstallerPrefs.checkAndSet(this);
	}
	
	private void setAppVersion() {
		try {
			this.version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName.trim();
			rfcxGuardianInstallerPrefs.writeVersionToFile(this.version);
		} catch (NameNotFoundException e) {
			Log.e(TAG,(e!=null) ? e.getMessage() : NULL_EXC);
		}
	}
	
	public int getAppVersionValue(String versionName) {
		try {
			int majorVersion = (int) Integer.parseInt(versionName.substring(0, versionName.indexOf(".")));
			int subVersion = (int) Integer.parseInt(versionName.substring(1+versionName.indexOf("."), versionName.lastIndexOf(".")));
			int updateVersion = (int) Integer.parseInt(versionName.substring(1+versionName.lastIndexOf(".")));
			return 1000*majorVersion+100*subVersion+updateVersion;
		} catch (Exception e) {
			Log.e(TAG,(e!=null) ? (e.getMessage() +" ||| "+ TextUtils.join(" | ", e.getStackTrace())) : NULL_EXC);
		}
		return 0;
	}

	public String getDeviceId() {
		if (this.deviceId == null) {
			this.deviceId = (new DeviceGuid(getApplicationContext(), this.sharedPrefs)).getDeviceId();
			if (this.verboseLog) { Log.d(TAG,"Device GUID: "+this.deviceId); }
			rfcxGuardianInstallerPrefs.writeGuidToFile(deviceId);
		}
		return this.deviceId;
	}
	
	public String getDeviceToken() {
		if (this.deviceToken == null) {
			this.deviceToken = (new DeviceToken(getApplicationContext(), this.sharedPrefs)).getDeviceToken();
			rfcxGuardianInstallerPrefs.writeTokenToFile(deviceToken);
		}
		return this.deviceToken;
	}
	
	public void initializeRoleIntentServices(Context context) {
		try {

			long apiCheckVersionInterval = ((getPref("apicheckversion_interval")!=null) ? Integer.parseInt(getPref("apicheckversion_interval")) : 180)*60*1000;
			PendingIntent updaterIntentService = PendingIntent.getService(context, -1, new Intent(context, ApiCheckVersionIntentService.class), PendingIntent.FLAG_UPDATE_CURRENT);
			AlarmManager updaterAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);		
			updaterAlarmManager.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis(), apiCheckVersionInterval, updaterIntentService);
			if (verboseLog) { Log.d(TAG, "ApiCheckVersion will run every "+getPref("apicheckversion_interval")+" minute(s)..."); }
			
			// reboots system at 5 minutes before midnight every day
//			PendingIntent rebootIntentService = PendingIntent.getService(context, -1, new Intent(context, RebootIntentService.class), PendingIntent.FLAG_UPDATE_CURRENT);
//			AlarmManager rebootAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);		
//			rebootAlarmManager.setRepeating(AlarmManager.RTC, (new DateTimeUtils()).nextOccurenceOf(23,55,0).getTimeInMillis(), 24*60*60*1000, rebootIntentService);
			
		} catch (Exception e) {
			Log.e(TAG,(e!=null) ? (e.getMessage() +" ||| "+ TextUtils.join(" | ", e.getStackTrace())) : NULL_EXC);
		}
	}
	
	public String getPref(String prefName) {
		return this.sharedPrefs.getString(prefName, null);
	}
	
	public boolean setPref(String prefName, String prefValue) {
		return this.sharedPrefs.edit().putString(prefName,prefValue).commit();
	}
	
	public void triggerService(String serviceName, boolean forceReTrigger) {
		context = getApplicationContext();
		
		if (serviceName.equals("ApiCheckVersion")) {
			if (!this.isRunning_ApiCheckVersion || forceReTrigger) {
				context.stopService(new Intent(context, ApiCheckVersionService.class));
				context.startService(new Intent(context, ApiCheckVersionService.class));
			} else {
				if (this.verboseLog) { Log.d(TAG, "Service ApiCheckVersion is already running..."); }
			}
		} else if (serviceName.equals("DownloadFile")) {
			if (!this.isRunning_DownloadFile || forceReTrigger) {
				context.stopService(new Intent(context, DownloadFileService.class));
				context.startService(new Intent(context, DownloadFileService.class));
			} else {
				if (this.verboseLog) { Log.d(TAG, "Service DownloadFile is already running..."); }
			}
		} else if (serviceName.equals("InstallApp")) {
			if (!this.isRunning_InstallApp || forceReTrigger) {
				context.stopService(new Intent(context, InstallAppService.class));
				context.startService(new Intent(context, InstallAppService.class));
			} else {
				if (this.verboseLog) { Log.d(TAG, "Service InstallApp is already running..."); }
			}
		} else {
			Log.e(TAG, "There is no service named '"+serviceName+"'.");
		}
	}
	
	public void stopService(String serviceName) {
		context = getApplicationContext();		
		if (serviceName.equals("ApiCheckVersion")) {
			context.stopService(new Intent(context, ApiCheckVersionService.class));
		} else if (serviceName.equals("DownloadFile")) {
			context.stopService(new Intent(context, DownloadFileService.class));
		} else if (serviceName.equals("InstallApp")) {
			context.stopService(new Intent(context, InstallAppService.class));
		} else {
			Log.e(TAG, "There is no service named '"+serviceName+"'.");
		}	
	}
	
	private String getValueFromGuardianTargetRoleTxtFile(String fileNameNoExt) {
    	context = getApplicationContext();
    	try {
    		String mainAppPath = context.getFilesDir().getAbsolutePath();
    		Log.d(TAG,mainAppPath.substring(0,mainAppPath.lastIndexOf("/files")-(("."+this.thisAppRole).length()))+"."+this.targetAppRole+"/files/txt/"+fileNameNoExt+".txt");
    		File txtFile = new File(mainAppPath.substring(0,mainAppPath.lastIndexOf("/files")-(("."+this.thisAppRole).length()))+"."+this.targetAppRole+"/files/txt",fileNameNoExt+".txt");
    		if (txtFile.exists()) {
				FileInputStream input = new FileInputStream(txtFile);
				StringBuffer fileContent = new StringBuffer("");
				byte[] buffer = new byte[12];
				while (input.read(buffer) != -1) {
				    fileContent.append(new String(buffer));
				}
	    		String txtFileContents = fileContent.toString().trim();
	    		input.close();
	    		Log.d(TAG, "Fetched '"+fileNameNoExt+"' from org.rfcx.guardian."+this.targetAppRole+": "+txtFileContents);
	    		return txtFileContents;
    		} else {
    			Log.e(TAG, "No file '"+fileNameNoExt+"' saved by org.rfcx.guardian."+this.targetAppRole+"...");
    		}
    	} catch (FileNotFoundException e) {
    		Log.e(TAG,(e!=null) ? (e.getMessage() +" ||| "+ TextUtils.join(" | ", e.getStackTrace())) : NULL_EXC);
    	} catch (IOException e) {
    		Log.e(TAG,(e!=null) ? (e.getMessage() +" ||| "+ TextUtils.join(" | ", e.getStackTrace())) : NULL_EXC);
		}
    	return null;
	}
	
    public String getCurrentGuardianTargetRoleVersion() {
    	return getValueFromGuardianTargetRoleTxtFile("version");
    }
    
}
