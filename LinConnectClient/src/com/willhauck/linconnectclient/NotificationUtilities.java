package com.willhauck.linconnectclient;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

@SuppressWarnings("deprecation")
public class NotificationUtilities {

	public static boolean sendData(Context c, Notification n, String packageName) {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(c);

		ConnectivityManager connManager = (ConnectivityManager) c
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		// Check Wifi state, whether notifications are enabled globally, and
		// whether notifications are enabled for specific application
		if (prefs.getBoolean("pref_toggle", true)
				&& prefs.getBoolean(packageName, true) && mWifi.isConnected()) {
			String ip = prefs.getString("pref_ip", "0.0.0.0:9090");

			// Magically extract text from notification
			ArrayList<String> notificationData = NotificationUtilities
					.getNotificationText(n);

			// Use PackageManager to get application name and icon
			final PackageManager pm = c.getPackageManager();
			ApplicationInfo ai;
			try {
				ai = pm.getApplicationInfo(packageName, 0);
			} catch (final NameNotFoundException e) {
				ai = null;
			}

			// Create header and body of notification
			String d1 = notificationData.get(0);
			String d2 = notificationData.get(1);
			for (int i = 2; i < notificationData.size(); i++) {
				d2 += "\n" + notificationData.get(i);
			}

			// Append application name to body
			if (pm.getApplicationLabel(ai) != null) {
				if (d2.isEmpty()) {
					d2 = "via " + pm.getApplicationLabel(ai);
				} else {
					d2 += " (via " + pm.getApplicationLabel(ai) + ")";
				}
			}

			// Setup HTTP request
			MultipartEntity entity = new MultipartEntity(
					HttpMultipartMode.BROWSER_COMPATIBLE);

			// If the notification contains an icon, use it
			if (n.largeIcon != null) {
				entity.addPart(
						"notificon",
						new InputStreamBody(ImageUtilities
								.bitmapToInputStream(n.largeIcon),
								"drawable.png"));
			}
			// Otherwise, use the application's icon
			else {
				entity.addPart(
						"notificon",
						new InputStreamBody(ImageUtilities
								.bitmapToInputStream(ImageUtilities
										.drawableToBitmap(pm
												.getApplicationIcon(ai))),
								"drawable.png"));
			}

			HttpPost post = new HttpPost("http://" + ip + "/notif");
			post.setEntity(entity);
			post.addHeader("notifheader", d1);
			post.addHeader("notifdescription", d2);

			// Send HTTP request
			HttpClient client = new DefaultHttpClient();
			HttpResponse response;
			try {
				response = client.execute(post);
				String html = EntityUtils.toString(response.getEntity());
				if (html.contains("true")) {
					return true;
				}
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		return false;
	}

	@SuppressLint("DefaultLocale")
	public static ArrayList<String> getNotificationText(
			Notification notification) {
		RemoteViews views = notification.contentView;
		Class<?> secretClass = views.getClass();

		try {
			ArrayList<String> notificationData = new ArrayList<String>();

			Field outerFields[] = secretClass.getDeclaredFields();
			for (int i = 0; i < outerFields.length; i++) {
				if (!outerFields[i].getName().equals("mActions"))
					continue;

				outerFields[i].setAccessible(true);

				@SuppressWarnings("unchecked")
				ArrayList<Object> actions = (ArrayList<Object>) outerFields[i]
						.get(views);
				for (Object action : actions) {
					Field innerFields[] = action.getClass().getDeclaredFields();

					Object value = null;
					for (Field field : innerFields) {
						field.setAccessible(true);
						// Value field could possibly contain text
						if (field.getName().equals("value")) {
							value = field.get(action);
						}
					}

					// Check if value is a String
					if (value != null
							&& value.getClass().getName().toUpperCase()
									.contains("STRING")) {

						notificationData.add(value.toString());
					}
				}

				return notificationData;
			}
		} catch (Exception e) {
		}
		return null;
	}
}
