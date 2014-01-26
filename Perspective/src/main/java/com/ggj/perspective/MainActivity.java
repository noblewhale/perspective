package com.ggj.perspective;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends ActionBarActivity
{
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private final static String TAG = "MainActivity";

    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";

    /**
     * Substitute you own sender ID here. This is the project number you got
     * from the API Console, as described in "Getting Started."
     */
    String SENDER_ID = "173918856707";

    TextView mDisplay;
    GoogleCloudMessaging gcm;
    AtomicInteger msgId = new AtomicInteger();
    SharedPreferences prefs;
    Context context;

    File imageFile;
    HashMap<ImageView, String> imageViews = new HashMap<ImageView, String>();
    ImageView thumbnailView;
    String regid;

    int alreadyLoadedImageCount = 0;

    Bitmap fullsizeImage;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this.getApplicationContext();
        mDisplay = (TextView) findViewById(R.id.display);

        thumbnailView = (ImageView)findViewById(R.id.imageView);
        imageViews.put((ImageView)findViewById(R.id.imageView2), "");
        imageViews.put((ImageView)findViewById(R.id.imageView3), "");
        imageViews.put((ImageView)findViewById(R.id.imageView4), "");

        if(checkPlayServices())
        {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(context);

            if (regid.isEmpty())
            {
                registerInBackground();
            }
        }
        else
        {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }
    }

    BroadcastReceiver receiver;

    @Override
    protected void onResume()
    {
        super.onResume();
        checkPlayServices();
        receiver = new UpdateImage();
        this.registerReceiver(receiver, new IntentFilter("UpdateImage"));
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        this.unregisterReceiver(receiver);
    }

    private final class UpdateImage extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (intent.getAction().equals("UpdateImage"))
            {
                String url = intent.getStringExtra("url");
                String imageID = intent.getStringExtra("imageID");
                ImageView imageView = (ImageView) imageViews.keySet().toArray()[alreadyLoadedImageCount];
                imageViews.put(imageView, imageID);
                alreadyLoadedImageCount++;
                new DownloadImageTask(imageView).execute(url);
                Log.e(TAG, url);
            }
        }
    }

    private final class VoteReceived extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (intent.getAction().equals("VoteReceived"))
            {
                Log.e(TAG, intent.getStringExtra("imageID"));

            }
        }
    }

    public void onTakePictureButtonPressed(View view)
    {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        imageFile = new File(Environment.getExternalStorageDirectory().getPath(),"temp85736583.jpg");
        try {
            imageFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Uri outputFileUri = Uri.fromFile(imageFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null)
        {
            startActivityForResult(takePictureIntent, 1);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == 1 && resultCode == RESULT_OK)
        {
            fullsizeImage = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            fullsizeImage = Bitmap.createScaledBitmap(fullsizeImage, fullsizeImage.getWidth()/4, fullsizeImage.getHeight()/4, true);
            Bitmap thumbImage = ThumbnailUtils.extractThumbnail(fullsizeImage, 100, 100);

            thumbnailView.setImageBitmap(thumbImage);

            ExifInterface exif = null;
            try {
                exif = new ExifInterface(imageFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);

            Matrix matrix = new Matrix();
            thumbnailView.setScaleType(ImageView.ScaleType.MATRIX);   //required
            Rect imageBounds = thumbnailView.getDrawable().getBounds();

            switch(orientation)
            {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate((float) 90, imageBounds.width()/2, imageBounds.height()/2);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate((float) 180, imageBounds.width()/2, imageBounds.height()/2);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate((float) 270, imageBounds.width()/2, imageBounds.height()/2);
                    break;
            }

            thumbnailView.setImageMatrix(matrix);

            sendPhoto();
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices()
    {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS)
        {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode))
            {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            }
            else
            {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Gets the current registration ID for application on GCM service.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    private String getRegistrationId(Context context)
    {
        final SharedPreferences prefs = getGCMPreferences(context);

        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty())
        {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion)
        {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context)
    {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context)
    {
        try
        {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        }
        catch (PackageManager.NameNotFoundException e)
        {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(SENDER_ID);
                    msg = "Device registered, registration ID=" + regid;

                    sendRegistrationIdToBackend();

                    // Persist the regID - no need to register again.
                    storeRegistrationId(context, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    mDisplay.append(msg + "\n");
                }
                return msg;
            }

            @Override
            protected void onPostExecute(Object msg) {
                mDisplay.append(msg + "\n");
            }
        }.execute(null, null, null);
    }

    /**
     * Sends the photo to a php script on our server that then sends it out to other players
     */
    private void sendPhoto()
    {
        new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                HttpURLConnection connection;
                OutputStreamWriter request = null;

                AccountManager manager = (AccountManager) getSystemService(ACCOUNT_SERVICE);
                Account[] list = manager.getAccounts();

                URL url = null;
                String parameters = "name="+list[0].name;

                System.setProperty("http.keepAlive", "false");
                HttpClient httpclient = new DefaultHttpClient();
                HttpPost httppost = new HttpPost("http://noblewhale.com/picit/consumePhoto.php");

                // Encode image as string to send to server
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                fullsizeImage.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] b = baos.toByteArray();

                String encodedImage = Base64.encodeToString(b, Base64.DEFAULT);

                try {

                    // Add your data
                    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
                    nameValuePairs.add(new BasicNameValuePair("name", list[0].name));
                    nameValuePairs.add(new BasicNameValuePair("image", encodedImage));

                    httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                    // Execute HTTP Post Request
                    HttpResponse response = httpclient.execute(httppost);

                    Log.e(TAG, response.toString());

                } catch (ClientProtocolException e) {
                    // TODO Auto-generated catch block
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                }

                return "";
            }
        }.execute(null, null, null);
    }

    /**
     * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP
     * or CCS to send messages to your app. Not needed for this demo since the
     * device sends upstream messages to a server that echoes back the message
     * using the 'from' address in the message.
     */
    private void sendRegistrationIdToBackend()
    {
        HttpURLConnection connection;
        OutputStreamWriter request = null;

        AccountManager manager = (AccountManager) getSystemService(ACCOUNT_SERVICE);
        Account[] list = manager.getAccounts();

        URL url = null;
        String parameters = "name="+list[0].name+"&regid="+regid;

        System.setProperty("http.keepAlive", "false");
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost("http://noblewhale.com/picit/registerDevice.php");

        try {
            // Add your data
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
            nameValuePairs.add(new BasicNameValuePair("name", list[0].name));
            nameValuePairs.add(new BasicNameValuePair("regid", regid));
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            // Execute HTTP Post Request
            HttpResponse response = httpclient.execute(httppost);

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
        } catch (IOException e) {
            // TODO Auto-generated catch block
        }

        Log.e(TAG, parameters);
    }

    /**
     * Stores the registration ID and app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration ID
     */
    private void storeRegistrationId(Context context, String regId)
    {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId())
        {
            case R.id.action_settings:
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void clickToVote(final View view)
    {

        new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                HttpURLConnection connection;
                OutputStreamWriter request = null;

                AccountManager manager = (AccountManager) getSystemService(ACCOUNT_SERVICE);
                Account[] list = manager.getAccounts();


                System.setProperty("http.keepAlive", "false");
                HttpClient httpclient = new DefaultHttpClient();
                HttpPost httppost = new HttpPost("http://noblewhale.com/picit/saveVote.php");



                try {

                    // Add your data
                    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
                    nameValuePairs.add(new BasicNameValuePair("name", list[0].name));
                    nameValuePairs.add(new BasicNameValuePair("imageID", imageViews.get((ImageView)view)));
                    Log.e(TAG, imageViews.get((ImageView)view));
                    httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                    // Execute HTTP Post Request
                    HttpResponse response = httpclient.execute(httppost);

                    Log.e(TAG, response.toString());

                } catch (ClientProtocolException e) {
                    // TODO Auto-generated catch block
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                }

                return "";
            }
        }.execute(null, null, null);
    }

}
