package walkingsafety.donnolove.com.walkingsafety;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class Receive_Boot_Completed extends BroadcastReceiver {

	private SharedPreferences sp;

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub

		SharedPreferences prefs = context.getSharedPreferences(
				context.getPackageName() + "_preferences", 0);

		// �����}�����T��Intent.ACTION_BOOT_COMPLETED
		if (prefs.getBoolean("pf_auto_run", false)) {
			if (intent.getAction().equalsIgnoreCase(
					Intent.ACTION_BOOT_COMPLETED)) {
				// ������F�N�Ұ�service
				Intent serviceIntent = new Intent(context, AlertService.class);
				serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startService(serviceIntent);
			}
		}
	}

}
