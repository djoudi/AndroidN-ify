package tk.wasdennnoch.androidn_ify.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import tk.wasdennnoch.androidn_ify.BuildConfig;
import tk.wasdennnoch.androidn_ify.R;
import tk.wasdennnoch.androidn_ify.ui.emergency.PreferenceKeys;
import tk.wasdennnoch.androidn_ify.ui.misc.DownloadService;

@SuppressWarnings("WeakerAccess")
public class UpdateUtils {

    public static void check(Context context, UpdateListener listener) {
        if (!isEnabled()) return;
        if (!isConnected(context)) return;
        new CheckUpdateTask(context).execute(BuildConfig.UPDATER_URL, listener);
    }

    // TODO FINISH and turn public
    private static void checkConfig(Context context, SharedPreferences preferences) {
        if (UpdateUtils.isConnected(context)) {
            try {
                URL url = new URL("https://raw.githubusercontent.com/wasdennnoch/AndroidN-ify/master/app/src/main/assets/assistant_hooks");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());

                BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                JSONArray hookConfigs = new JSONArray(result.toString());
                // Should have thrown error here if no valid JSON
                preferences.edit().putString(PreferenceKeys.GOOGLE_APP_HOOK_CONFIGS, result.toString()).apply();
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isEnabled() {
        return BuildConfig.ENABLE_UPDATER;
    }

    public static boolean isConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public interface UpdateListener {
        void onError(Exception e);
        void onFinish(UpdateData data);
    }

    @SuppressWarnings("deprecation")
    public static void showNotification(UpdateUtils.UpdateData updateData, Context context, boolean showExperimental) {
        Intent downloadIntent = new Intent(context, DownloadService.class);
        downloadIntent.putExtra("url", updateData.getArtifactUrl());
        downloadIntent.putExtra("number", updateData.getNumber());
        downloadIntent.putExtra("hasartifact", updateData.hasArtifact());
        PendingIntent intent = PendingIntent.getService(context, 0, downloadIntent, 0);

        Notification.Action downloadAction = new Notification.Action.Builder(R.drawable.ic_volume_expand,
                context.getString(R.string.update_notification_download), intent)
                .build();

        String content = String.format(context.getString(R.string.update_notification), updateData.getNumber());

        Notification.Builder notificationBuider = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_stat_n)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(content)
                .setColor(context.getResources().getColor(R.color.colorAccent));
        notificationBuider.setPriority(Notification.PRIORITY_HIGH).setVibrate(new long[0]);
        notificationBuider.addAction(downloadAction);

        List<String> changes = updateData.getChanges();
        if (changes.size() != 0) {
            Notification.InboxStyle style = new Notification.InboxStyle();
            style.addLine(content);
            for (String change : changes) {
                if (showExperimental || !change.toUpperCase().contains("[EXPERIMENTAL]")) {
                    style.addLine(" - " + change);
                }
            }
            notificationBuider.setStyle(style);
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, notificationBuider.build());
    }

    public static class CheckUpdateTask extends AsyncTask<Object, Void, String> {

        HttpURLConnection urlConnection;
        final Context mContext;
        UpdateListener mListener;
        Exception mException;

        public CheckUpdateTask(Context context) {
            mContext = context;
        }

        @Override
        protected String doInBackground(Object... params) {
            StringBuilder result = new StringBuilder();

            if (params[1] != null && params[1] instanceof UpdateListener) mListener = (UpdateListener) params[1];

            try {
                URL url = new URL((String) params[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());

                BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
            } catch (Exception e) {
                mException = e;
            } finally {
                urlConnection.disconnect();
            }

            return result.toString();
        }

        @Override
        protected void onPostExecute(String s) {
            if (mException != null) {
                mListener.onError(mException);
            } else {
                try {
                    UpdateData updateData = UpdateData.fromJson(new JSONObject(s));
                    mListener.onFinish(updateData);
                } catch (JSONException e) {
                    mListener.onError(e);
                }
            }
        }
    }

    public static class UpdateData {
        private final int number;
        private final boolean hasArtifact;
        private final String artifactUrl;
        private final List<String> changes;

        public UpdateData(int number, boolean hasArtifact, String artifactUrl, List<String> changes) {
            this.number = number;
            this.hasArtifact = hasArtifact;
            this.artifactUrl = artifactUrl;
            this.changes = changes;
        }

        public static UpdateData fromJson(JSONObject jsonObject) throws JSONException {
            int number = jsonObject.getInt("number");
            String url = jsonObject.getString("url");
            String artifactUrl = "";
            List<String> changes = new ArrayList<>();
            boolean hasArtifact = false;
            JSONArray artifacts = jsonObject.getJSONArray("artifacts");
            int artifactCount = artifacts.length();
            if (artifactCount == 1) {
                hasArtifact = true;
                JSONObject artifact = artifacts.getJSONObject(0);
                artifactUrl = url + "artifact/" + artifact.getString("relativePath");
            }
            JSONObject changeSet = jsonObject.getJSONObject("changeSet");
            if (changeSet != null) {
                JSONArray items = changeSet.getJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        changes.add(item.getString("msg"));
                    }
                }
            }
            return new UpdateData(number, hasArtifact, artifactUrl, changes);
        }

        public String getArtifactUrl() {
            return artifactUrl;
        }

        public boolean hasArtifact() {
            return hasArtifact;
        }

        public int getNumber() {
            return number;
        }

        public List<String> getChanges() { return changes; }
    }
}
