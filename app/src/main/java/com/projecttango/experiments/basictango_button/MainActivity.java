/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.experiments.basictango_button;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.basictango_button.R;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Main Activity for the Tango Java Quickstart. Demonstrates establishing a
 * connection to the {@link Tango} service and printing the {@link TangoPose}
 * data to the LogCat. Also demonstrates Tango lifecycle management through
 * {@link TangoConfig}.
 */
public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String sTranslationFormat = " %f, %f, %f";
    private static final String sRotationFormat = " %f, %f, %f, %f";

    private TextView mTranslationTextView;
    private TextView mRotationTextView;
    private TextView mUuid;
    private TextView mtimestamp;

    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsTangoServiceConnected;
    private boolean mIsProcessing = false;
    private boolean isOn = false;
    private UUID uuid;
    private double timestamp;





    public void togglestate(View view){

    isOn = ((ToggleButton) view).isChecked();

        // Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        //Intent for Stop button on Notification
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("isOn","false");
        PendingIntent aIntent = PendingIntent.getActivity(this, 0, intent, 0);

        //Intent for Restart button on Notification
        Intent intent2 = new Intent(this, MainActivity.class);
        intent.putExtra("isOn","true");
        PendingIntent bIntent = PendingIntent.getActivity(this, 0, intent2, 0);

        //NOTIFICATION SETUP
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.appicon)
                        .setContentTitle("UTC Tango")
                        .setContentText("Collecting Data!")
                        .setOngoing(true)
                        .setPriority(2)
                        .setAutoCancel(true)
                        .setDeleteIntent(aIntent)
                        .addAction(R.drawable.none, "Stop Session", aIntent)
                        .addAction(R.drawable.none, "Restart", bIntent);
        int mNotificationId = 8001; //Notification ID


        if(isOn) {

            //create UUID for session;
            uuid = UUID.randomUUID();

            //Starting Toast
            Toast.makeText(getApplicationContext(),
                    "Collecting Data...", Toast.LENGTH_SHORT)
                    .show();

            // Builds the notification and issues it.
            mNotifyMgr.notify(mNotificationId, mBuilder.build());
        }
        else{ //action if button is false
            isOn = false;
            mNotifyMgr.cancelAll(); //kill notification

            //Stopping Toast
            Toast.makeText(getApplicationContext(),
                    "Stopped collecting data", Toast.LENGTH_SHORT)
                    .show();
        }
    }





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mTranslationTextView = (TextView) findViewById(R.id.translation_text_view);
        mRotationTextView = (TextView) findViewById(R.id.rotation_text_view);
        mUuid = (TextView) findViewById(R.id.uuid_text_view);
        mtimestamp = (TextView) findViewById(R.id.timestamp_text_view);

        // Instantiate Tango client
        mTango = new Tango(this);

        // Set up Tango configuration for motion tracking
        // If you want to use other APIs, add more appropriate to the config
        // like: mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true)
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
    }






    @Override
    protected void onResume() {
        super.onResume();
        // Lock the Tango configuration and reconnect to the service each time
        // the app
        // is brought to the foreground.
        super.onResume();
        if (!mIsTangoServiceConnected) {
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_MOTION_TRACKING),
                    Tango.TANGO_INTENT_ACTIVITYCODE);
        }

        // Clear all notification
        NotificationManager mNotifyMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyMgr.cancelAll();
    }





    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to

        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this,
                        "This app requires Motion Tracking permission!",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            try {
                setTangoListeners();
            } catch (TangoErrorException e) {
                Toast.makeText(this, "Tango Error! Restart the app!",
                        Toast.LENGTH_SHORT).show();
            }
            try {
                mTango.connect(mConfig);
                mIsTangoServiceConnected = true;
            } catch (TangoOutOfDateException e) {
                Toast.makeText(getApplicationContext(),
                        "Tango Service out of date!", Toast.LENGTH_SHORT)
                        .show();
            } catch (TangoErrorException e) {
                Toast.makeText(getApplicationContext(),
                        "Tango Error! Restart the app!", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }





    @Override
    protected void onPause() {
        super.onPause();
        // When the app is pushed to the background, unlock the Tango
        // configuration and disconnect
        // from the service so that other apps will behave properly.
        try {
            if(isOn) {

                mIsTangoServiceConnected = true;

            }
            else{
                mTango.disconnect();
                mIsTangoServiceConnected = false;
            }
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), "Tango Error!",
                    Toast.LENGTH_SHORT).show();
        }
    }





    @Override
    protected void onDestroy() {
        super.onDestroy();
    }






    private void setTangoListeners() {
        // Select coordinate frame pairs
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        // Add a listener for Tango pose data
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {

            @SuppressLint("DefaultLocale")
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                if (mIsProcessing) {
                    Log.i(TAG, "Processing UI");
                    return;
                }
                mIsProcessing = true;
                
                // Format Translation and Rotation data
                final String translationMsg = String.format(sTranslationFormat,
                        pose.translation[0], pose.translation[1],
                        pose.translation[2]);
                final String rotationMsg = String.format(sRotationFormat,
                        pose.rotation[0], pose.rotation[1], pose.rotation[2],
                        pose.rotation[3]);

                // Output to LogCat for testing purposes
               // String logMsg = translationMsg + " | " + rotationMsg + " | " + pose.timestamp;
               // Log.i(TAG, logMsg);

                //If button returns true
                if(isOn) {
                    try {
                        timestamp = pose.timestamp; //get timestamp data

                        //Initializes HTTP POST request
                        HttpClient httpclient = new DefaultHttpClient();
                        HttpPost httpPost = new HttpPost("http://10.101.102.123/datapoint");
                        List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(2);
                        nameValuePair.add(new BasicNameValuePair("timestamp", timestamp + ""));
                        nameValuePair.add(new BasicNameValuePair("translation", translationMsg));
                        nameValuePair.add(new BasicNameValuePair("rotation", rotationMsg));


                        httpPost.setEntity(new UrlEncodedFormEntity(nameValuePair));

                        try {
                            //Send Request
                            HttpResponse response = httpclient.execute(httpPost);
                            // write response to log
                            Log.d("Http Post Response:", response.toString());
                        } catch (ClientProtocolException e) { // Catch a few different Exceptions
                            // Log exception
                            e.printStackTrace();
                        } catch (IOException e) {
                            // Log exception
                            e.printStackTrace();
                            Log.i(TAG, e.getMessage());
                        }

                        // HttpResponse response = httpclient.execute(new HttpGet("http://localhost:1234/send-data"));
                    } catch (Exception exception) {
                        Log.i(TAG, exception.getMessage());
                    }
                }



                // Display data in TextViews. This must be done inside a
                // runOnUiThread call because
                // it affects the UI, which will cause an error if performed
                // from the Tango
                // service thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "ISON = " + isOn);

                            //If Button returns True
                            if(isOn) {
                                mTranslationTextView.setText("Translation: " + translationMsg);
                                mRotationTextView.setText("Rotation: " + rotationMsg);
                                mtimestamp.setText("Timestamp: " + timestamp);
                                mUuid.setText("UUID: " + uuid);
                            }
                            mIsProcessing = false;

                    }
                });
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData arg0) {
                // Ignoring XyzIj data
            }

            @Override
            public void onTangoEvent(TangoEvent arg0) {
                // Ignoring TangoEvents
            }

			@Override
			public void onFrameAvailable(int arg0) {
				// Ignoring onFrameAvailable Events
				
			}

        });
    }

}
