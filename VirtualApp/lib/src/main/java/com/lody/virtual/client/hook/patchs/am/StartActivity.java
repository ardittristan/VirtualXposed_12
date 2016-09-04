package com.lody.virtual.client.hook.patchs.am;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.TypedValue;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.local.ActivityClientRecord;
import com.lody.virtual.client.local.VActivityManager;
import com.lody.virtual.helper.utils.ArrayUtils;
import com.lody.virtual.helper.utils.ComponentUtils;
import com.lody.virtual.os.VUserHandle;

import java.lang.reflect.Method;

/**
 * @author Lody
 */
/* package */ class StartActivity extends BaseStartActivity {


	@Override
	public String getName() {
		return "startActivity";
	}

	@Override
	public Object call(Object who, Method method, Object... args) throws Throwable {
		super.call(who, method, args);
		int intentIndex = ArrayUtils.indexOfFirst(args, Intent.class);
		int resultToIndex = ArrayUtils.indexOfObject(args, IBinder.class, 2);

		String resolvedType = (String) args[intentIndex + 1];
		Intent intent = (Intent) args[intentIndex];
		intent.setDataAndType(intent.getData(), resolvedType);
		IBinder resultTo = resultToIndex >= 0 ? (IBinder) args[resultToIndex] : null;
		int userId = VUserHandle.myUserId();

		if (ComponentUtils.isStubComponent(intent)) {
			return method.invoke(who, args);
		}
		ActivityInfo activityInfo = VirtualCore.get().resolveActivityInfo(intent, userId);
		if (activityInfo == null) {
			return method.invoke(who, args);
		}
		String resultWho = null;
		int requestCode = 0;
		Bundle options = ArrayUtils.getFirst(args, Bundle.class);
		if (resultTo != null) {
			resultWho = (String) args[resultToIndex + 1];
			requestCode = (int) args[resultToIndex + 2];
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			args[intentIndex - 1] = getHostPkg();
		}

		Intent res = VActivityManager.get().startActivity(intent, activityInfo, resultTo, options, userId);
		// If activity has been started by VAMS, res will be null,
		// else, res will be the intent we need to start.
		if (res == null) {
			if (resultTo != null && requestCode > 0) {
				// caller need to a result, give a sugar for it.
				VActivityManager.get().sendActivityResult(resultTo, resultWho, requestCode);
			}
			return 0;
		}

		// We force to start the new Intent.
		args[intentIndex] = res;

		if (resultTo != null) {
			// Only for outside-installed app.
			ActivityClientRecord r = VActivityManager.get().getActivityRecord(resultTo);
			if (r != null && r.activity != null && !r.activity.isTaskRoot()) {
				try {
					TypedValue out = new TypedValue();
					Resources.Theme theme = r.activity.getResources().newTheme();
					theme.applyStyle(activityInfo.getThemeResource(), true);
					if (theme.resolveAttribute(android.R.attr.windowAnimationStyle, out, true)) {
						TypedArray array = theme.obtainStyledAttributes(out.data,
								new int[]{
										android.R.attr.activityOpenEnterAnimation,
										android.R.attr.activityOpenExitAnimation
								});

						r.activity.overridePendingTransition(array.getResourceId(0, 0), array.getResourceId(1, 0));
						array.recycle();
					}
				} catch (Throwable e) {
					// Ignore
				}
			}
		}
		return method.invoke(who, args);
	}

}