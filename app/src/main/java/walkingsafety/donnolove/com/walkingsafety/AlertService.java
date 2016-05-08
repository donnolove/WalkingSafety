package walkingsafety.donnolove.com.walkingsafety;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView.HitTestResult;
import android.widget.LinearLayout;
import android.widget.Toast;

public class AlertService extends Service implements SensorEventListener {

	
	/*
	 * 監聽 Alert / ScreenLock / Backoff Mechanism / Control Backoff
	 * */
	private Handler alert_handler = new Handler();
	private Handler chk_screen_handler = new Handler();
	private Handler backoff_handler = new Handler();
	private Handler control_backoff_handler = new Handler();
	
	//G-Sensor
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;

	//save currentStep
	private int currentStep = 0;
	
	//backoff_flag: check now state , sleep_time: Power-Saving sleep time
	private int backoff_flag = 0, sleep_time = 5000;// 5s	
	
	//check screen lock state
	private boolean locked = false;
	
	//if no flash light, then Vibrator is true
	private boolean isVibrator = false;
	
	//control flash light on/off
	private boolean camera_toggle = false;
	
	//device has camera
	private boolean hasHardwareCamera = false;
	
	//check sensor run
	private boolean is_sensor_run = false;
	
	//check backoff state
	private boolean is_back_off = false;
	
	//SharedPreferences
	private boolean pf_light = false, pf_sound = false, pf_dialog = false;
	
	//control and check sensor
	private boolean g_sensor_flag = true;

    //每一步是否在規定的時間內
    private boolean b_is_step_interval = false;

	// (String format) dialog/Bflash light/Bsound timestamp
	private String dialog_time_1, dialog_time_2, light_time_1, light_time_2,
			sound_time_1, sound_time_2,sensor_time_1,sensor_time_2 = null;
	
	//db
	//private String toDay, tmp_day;
	
	private SharedPreferences sp;
	//private Toast mToast;
	private Builder builder = null;
	private AlertDialog alert = null;
	private MediaPlayer warning;
	private Vibrator mVibrator;
	// private Timer tm;
	
	// (Date format) dialog Bflash light Bsound timestamp
	private Date light_timeStamp, light_currentTime;
	private Date dialog_timeStamp, dialog_currentTime;
	private Date sound_timeStamp, sound_currentTime;
    private Date sensor_timeStamp, sensor_currentTime;


	// Date formatter
	private SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
	private SimpleDateFormat formatter_ymd = new SimpleDateFormat("yyyy�~MM��dd��");
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	private Camera camera;// flash
	private Parameters param_camera;
	private Parameters camera_parameters;

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		// Log.v("debug", "Service onBind()");
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
//				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorManager.registerListener(this, mAccelerometer, 200000);
		is_sensor_run = true;
		is_back_off = true;
//		this.checkHardwareCamera();
		// checkFroe();
		warning = MediaPlayer.create(this, R.raw.alarm);


		//Service
		//if ( (Alert trigger-time) - (Alert timeStamp) > 5s )
		//Then (Alert timeStamp) = (Alert trigger-time)
		dialog_timeStamp = new Date(System.currentTimeMillis());
		dialog_time_1 = formatter.format(dialog_timeStamp);

		light_timeStamp = new Date(System.currentTimeMillis());
		light_time_1 = formatter.format(light_timeStamp);

		sound_timeStamp = new Date(System.currentTimeMillis());
		sound_time_1 = formatter.format(sound_timeStamp);

		mVibrator = (Vibrator) getApplication().getSystemService(
				Service.VIBRATOR_SERVICE);

		//Notification
		Notification notification = new Notification(R.drawable.ic_launcher,
				"Start WalkingAlert", System.currentTimeMillis());
		Intent intent = new Intent(this, WalkingSafety.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				intent, 0);

		//new method for notification
		Notification.Builder builder = new Notification.Builder(AlertService.this);
		builder.setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle(getString(R.string.title_notification))
				.setContentText(getString(R.string.des_notification))
				.setContentIntent(contentIntent)
				.setSmallIcon(R.drawable.ic_launcher)
				.setWhen(System.currentTimeMillis());
				//.setOngoing(true);
		notification=builder.getNotification();


		//old method
//		notification.setLatestEventInfo(this, "WalkingSafety", "Running",
//				contentIntent);


		notification.flags = Notification.FLAG_AUTO_CANCEL;


		startForeground(1235, notification);

		
		//screen lock / backoff handler
		chk_screen_handler.postDelayed(check_screen, 1000);
		backoff_handler.postDelayed(packup, 10000);
	}

	private void checkHardwareCamera() {
		// TODO Auto-generated method stub
		PackageManager manager = this.getPackageManager();
		// if device support camera?
		if (manager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
			hasHardwareCamera = true;
			camera = Camera.open();
			param_camera = camera.getParameters();
		}
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		updatePreferences();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopForeground(true);
		chk_screen_handler.removeCallbacks(check_screen);
		backoff_handler.removeCallbacks(packup);
		control_backoff_handler.removeCallbacks(control_packup);
		is_sensor_run = false;
		is_back_off = false;
		mSensorManager.unregisterListener(this);
		mVibrator.cancel();
		if (camera != null) {
			camera.release();
		}
	}

	private void updatePreferences() {
		// TODO Auto-generated method stub
		sp = PreferenceManager.getDefaultSharedPreferences(this);

		pf_light = sp.getBoolean("pf_al_light", false);

		pf_sound = sp.getBoolean("pf_al_sound", false);

		pf_dialog = sp.getBoolean("pf_al_dialog", false);
	}

	private Runnable check_screen = new Runnable() {
		public void run() {
			KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
			if (!km.inKeyguardRestrictedInputMode()) {// if screen unlock
				if (is_sensor_run == false) {
					mSensorManager.registerListener(AlertService.this,
							mAccelerometer, 200000);// 40000
					locked = km.inKeyguardRestrictedInputMode();
					backoff_handler.postDelayed(packup, 5000);
					is_sensor_run = true;
					is_back_off = true;
				}
			} else {
				if (is_sensor_run == true) {// if screen lock
					mSensorManager.unregisterListener(AlertService.this,
							mAccelerometer);
					locked = km.inKeyguardRestrictedInputMode();
					backoff_handler.removeCallbacks(packup);
					control_backoff_handler.removeCallbacks(control_packup);
					is_sensor_run = false;
					is_back_off = false;
				}
			}
			chk_screen_handler.postDelayed(check_screen, 5000);
		}
	};

	private Runnable packup = new Runnable() {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			if (backoff_flag == 0) {// sleep

				if (is_back_off == true) {// if screen lock
					mSensorManager.unregisterListener(AlertService.this,
							mAccelerometer);
					is_back_off = false;
				}
				if (sleep_time < 20000)
					sleep_time *= 2;

				control_backoff_handler.postDelayed(control_packup, sleep_time);
				backoff_handler.removeCallbacks(packup);

			} else {// up

				backoff_flag = 0;
				sleep_time = 5000;
				backoff_handler.postDelayed(packup, 10000);
			}

		}
	};

	private Runnable control_packup = new Runnable() {

		@Override
		public void run() {
			mSensorManager.registerListener(AlertService.this, mAccelerometer,
					200000);// 40000
			is_back_off = true;
			backoff_handler.postDelayed(packup, 10000);
			control_backoff_handler.removeCallbacks(control_packup);
		}
	};

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub

        sensor_timeStamp = new Date(System.currentTimeMillis());
        sensor_time_1 = formatter.format(sensor_timeStamp);

        if(sensor_time_2 == null){
            sensor_time_2 = sensor_time_1;
        }

        if(currentStep == 0){
            double tmp = Math.floor((double)(event.values[0]));
            currentStep = (int)tmp;
        }

        //這次計步的時間減上次紀錄計步的時間 > 5秒的話,表示隔很久所以reset記錄的時間
        //每一步之間隔超過5秒表示太久,所以不列入計算
        if(Long.valueOf(sensor_time_1) - Long.valueOf(sensor_time_2) > 5){
            b_is_step_interval = false;
            sensor_time_2 = sensor_time_1;
            currentStep = 0;
        }else{
            b_is_step_interval = true;
            sensor_time_2 = sensor_time_1;
        }
			if ((event.values[0] - (float)currentStep > 10 && b_is_step_interval == true)){
                double tmp = Math.floor((double)(event.values[0]));
				currentStep = (int)tmp;
				backoff_flag++;

				updatePreferences();

				if (!locked && isForeground() == false) {// if screen no lock,
															// and app
					// in background
					if (pf_light) {
						light_currentTime = new Date(System.currentTimeMillis());
						light_time_2 = formatter.format(light_currentTime);
						if (Long.valueOf(light_time_2)
								- Long.valueOf(light_time_1) > 5) {
							if (hasHardwareCamera) {
								show_flash_light();
							} else {
								do_vibrator();
							}
						}

					}
					if (pf_sound) {
						// warning.start();
						sound_currentTime = new Date(System.currentTimeMillis());
						sound_time_2 = formatter.format(sound_currentTime);
						if (Long.valueOf(sound_time_2)
								- Long.valueOf(sound_time_1) > 5) {
							warning.start();
							dialog_time_1 = dialog_time_2;
						}
					}
					if (pf_dialog) {
						dialog_currentTime = new Date(
								System.currentTimeMillis());
						dialog_time_2 = formatter.format(dialog_currentTime);
						if (Long.valueOf(dialog_time_2)
								- Long.valueOf(dialog_time_1) > 5) {
							show_dialog();
						}

					}
				} else {

				}
			} else {

			}
//		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
	}

	public void do_vibrator() {
		mVibrator.vibrate(500);
		light_time_1 = light_time_2;
	}

	public void show_flash_light() {

		Thread t = new Thread() {
			public void run() {
				for (int i = 0; i < 1; i++) {
					try {
						toggle_Light();
						Thread.sleep(500);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		};
		t.start();
		dialog_time_1 = dialog_time_2;
	}

	public void toggle_Light() {
		if (!camera_toggle) {
			turnOn();
		} else {
			turnOff();
		}
	}

	public void turnOn() {
		try {
			if (camera != null) {
				// Turn on LED
				param_camera = camera.getParameters();
				param_camera.setFlashMode(Parameters.FLASH_MODE_TORCH);
				camera.setParameters(param_camera);
			}
		} catch (Exception ex) {

		}
		camera_toggle = true;
	}

	public void turnOff() {
		// Turn off flashlight
		try {
			if (camera != null) {
				param_camera = camera.getParameters();
				if (param_camera.getFlashMode().equals(
						Parameters.FLASH_MODE_TORCH)) {
					param_camera.setFlashMode(Parameters.FLASH_MODE_OFF);
					camera.setParameters(param_camera);
				}
			}
		} catch (Exception ex) {

		}
		camera_toggle = false;
	}

	public void show_dialog() {

		if (builder == null) {
			builder = new Builder(this);
			dialog_time_1 = dialog_time_2;
		}

		builder.setTitle(getString(R.string.alert_title));
		builder.setMessage(getString(R.string.alert_des));
		builder.setNegativeButton(getString(R.string.alert_confirm), new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				dialog_time_1 = dialog_time_2;
				alert.dismiss();
			}
		});

		if (alert == null) {
			// AlertDialog alert = builder.create();
			alert = builder.create();
			alert.setCancelable(false);
			alert.getWindow().setType(
					WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
		}
		if (!alert.isShowing()) {
			alert.show();
		}
	}

	public boolean isForeground() {
		ActivityManager activityManager = (ActivityManager) this
				.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningTaskInfo> services = activityManager
				.getRunningTasks(Integer.MAX_VALUE);
		boolean isActivityFound = false;

		if (services.get(0).topActivity.getPackageName().toString()
				.equalsIgnoreCase(this.getPackageName().toString())) {
			isActivityFound = true;
		}

		if (isActivityFound) {
			return true;
		} else {
			return false;
		}
	}

}
