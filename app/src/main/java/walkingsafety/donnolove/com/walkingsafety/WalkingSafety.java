package walkingsafety.donnolove.com.walkingsafety;

import java.util.List;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

public class WalkingSafety extends PreferenceActivity implements
		OnPreferenceChangeListener, OnPreferenceClickListener {

	private SharedPreferences sp;
	private boolean isServiceExist = false;
    private final int REQUEST_CODE_ALERT_WINDOWS = -1010101;


    @Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.walking_safety_preference);
		checkService();
		findPreferences();
	}

	private void findView() {
		// TODO Auto-generated method stub
	}

	private void findPreferences() {
		// TODO Auto-generated method stub

		sp = PreferenceManager.getDefaultSharedPreferences(this);
		ListPreference listPreferences;
		CheckBoxPreference cbPreferences;
		EditTextPreference editPreference;
		Preference pf;

		// 開機自動啟動
		cbPreferences = (CheckBoxPreference) findPreference("pf_auto_run");
		cbPreferences.setOnPreferenceChangeListener(this);
		cbPreferences.setSummary("" + cbPreferences.isChecked());
		// 啟動 Service
		cbPreferences = (CheckBoxPreference) findPreference("pf_start_service");
		cbPreferences.setOnPreferenceChangeListener(this);
		cbPreferences.setSummary("" + cbPreferences.isChecked());
		if (cbPreferences.isChecked() && isServiceExist == false) {
			// on
			Intent intent = new Intent(WalkingSafety.this, AlertService.class);
			startService(intent);
		}
	}

    @Override
    protected void onResume() {
        super.onResume();
        chkAlertPermission();
    }

    @TargetApi(23)
	private void chkAlertPermission(){
		if (!Settings.canDrawOverlays(WalkingSafety.this)) {
			/** if not construct intent to request permission */
			Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
					Uri.parse("package:" + getPackageName()));
            intent.setFlags(/*Intent.FLAG_ACTIVITY_NEW_TASK|*/Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
			/** request permission via start activity for result */
			startActivityForResult(intent, REQUEST_CODE_ALERT_WINDOWS);
		}
	}

    @TargetApi(23)
    @Override
    protected void onActivityResult(int requestCode, int resultCode,  Intent data) {
        /** check if received result code
         is equal our requested code for draw permission  */
        if (requestCode == REQUEST_CODE_ALERT_WINDOWS) {
            if (Settings.canDrawOverlays(this)) {
                // continue here - permission was granted
            }
        }
    }

    @Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		// TODO Auto-generated method stub

		if (preference.getKey().equals("pf_start_service")) {
			if (preference.getSharedPreferences().getBoolean(
					"pf_start_service", false)) {
				Intent intent = new Intent(WalkingSafety.this,
						AlertService.class);
				stopService(intent);
			} else {
				Intent intent = new Intent(WalkingSafety.this,
						AlertService.class);
				startService(intent);
			}
			preference.setSummary(newValue.toString());
		}

		return true;
	}

	@Override
	public boolean onPreferenceClick(final Preference preference) {
		// TODO Auto-generated method stub
		return true;
	}

	private void checkService() {
		ActivityManager mActivityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

		List<ActivityManager.RunningServiceInfo> mServiceList = mActivityManager
				.getRunningServices(30);
		final String musicClassName = "com.example.walkingalert.AlertService";
		isServiceExist = MusicServiceIsStart(mServiceList, musicClassName);
	}

	private String getServiceClassName(
			List<ActivityManager.RunningServiceInfo> mServiceList) {
		String res = "";
		for (int i = 0; i < mServiceList.size(); i++) {
			res += mServiceList.get(i).service.getClassName() + " /n";
		}
		return res;
	}

	private boolean MusicServiceIsStart(
			List<ActivityManager.RunningServiceInfo> mServiceList,
			String className) {

		for (int i = 0; i < mServiceList.size(); i++) {
			if (className.equals(mServiceList.get(i).service.getClassName())) {
				return true;
			}
		}
		return false;
	}

}
