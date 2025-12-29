package com.defold.firebase;

import androidx.annotation.NonNull;
import android.app.Activity;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseApp;

import org.json.JSONObject;
import org.json.JSONException;


public class FirebaseJNI {
    private static final String TAG = "FirebaseJNI";

    public static native void firebaseAddToQueue(int msg, String json);

    // duplicate of enums from firebase_callback.h:
    // CONSTANTS:
    private static final int MSG_ERROR =                   0;
    private static final int MSG_INITIALIZED =             1;
    private static final int MSG_INSTALLATION_AUTH_TOKEN = 2;
    private static final int MSG_INSTALLATION_ID =         3;

    private Activity activity;
    
    private FirebaseOptions.Builder optionsBuilder;

    public FirebaseJNI(Activity activity) {
        this.activity = activity;
    }

    public void initialize() {
        try {
            if (optionsBuilder != null) {
                Log.d(TAG, "Initializing Firebase with custom options");
                FirebaseApp.initializeApp(activity.getApplicationContext(), optionsBuilder.build());
                optionsBuilder = null;
            }
            else if (FirebaseApp.getApps(activity.getApplicationContext()).size() == 0) {
                // Try to load options from resources (google-services.xml)
                Log.d(TAG, "Trying to load Firebase options from resources...");
                FirebaseOptions options = FirebaseOptions.fromResource(activity.getApplicationContext());
                if (options != null) {
                    Log.d(TAG, "Firebase options loaded from resources: appId=" + options.getApplicationId() + ", projectId=" + options.getProjectId());
                    FirebaseApp.initializeApp(activity.getApplicationContext(), options);
                } else {
                    Log.e(TAG, "FirebaseOptions.fromResource() returned null");
                    sendErrorMessage("Firebase initialization failed: no options found in resources. Make sure google-services.xml is in bundle/android/res/values/ or use firebase.set_option() before initialize()");
                    return;
                }
            } else {
                Log.d(TAG, "Firebase already initialized");
            }
            
            // Verify Firebase was actually initialized
            if (FirebaseApp.getApps(activity.getApplicationContext()).size() == 0) {
                sendErrorMessage("Firebase initialization failed: FirebaseApp was not created");
                return;
            }
            
            Log.d(TAG, "Firebase initialized successfully");
            sendSimpleMessage(MSG_INITIALIZED);
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization exception: " + e.getMessage(), e);
            sendErrorMessage("Firebase initialization failed: " + e.getMessage());
        }
    }

    public boolean setOption(String key, String value) {
        if (optionsBuilder == null) {
            FirebaseOptions defaultOption = FirebaseOptions.fromResource(activity.getApplicationContext());
            if (defaultOption != null) {
                optionsBuilder = new FirebaseOptions.Builder(defaultOption);
            }
            else {
                optionsBuilder = new FirebaseOptions.Builder();
            }
        }
        switch (key) {
            case "api_key":
                optionsBuilder.setApiKey(value);
                break;
            case "app_id":
                optionsBuilder.setApplicationId(value);
                break;
            case "database_url":
                optionsBuilder.setDatabaseUrl(value);
                break;
            case "messaging_sender_id":
                optionsBuilder.setGcmSenderId(value);
                break;
            case "project_id":
                optionsBuilder.setProjectId(value);
                break;
            case "storage_bucket":
                optionsBuilder.setStorageBucket(value);
                break;
            default:
                return false;
        }
        return true;
    }

    public void getInstallationAuthToken() {
        FirebaseInstallations.getInstance().getToken(false).addOnCompleteListener(new OnCompleteListener<InstallationTokenResult>() {
            @Override
            public void onComplete(@NonNull Task<InstallationTokenResult> task) {
                if (task.isSuccessful() && task.getResult() != null) {
                    sendSimpleMessage(MSG_INSTALLATION_AUTH_TOKEN, "token", task.getResult().getToken());
                } else {
                    sendErrorMessage("Unable to get Installation auth token");
                }
            }
        });
    }

    public void getInstallationId() {
        FirebaseInstallations.getInstance().getId().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (task.isSuccessful()) {
                    sendSimpleMessage(MSG_INSTALLATION_ID, "id", task.getResult());
                } else {
                    sendErrorMessage("Unable to get Installation ID");
                }
            }
        });
    }

    // https://www.baeldung.com/java-json-escaping
    private String getJsonConversionErrorMessage(String errorText) {
        String message = null;
        
        try {
            JSONObject obj = new JSONObject();
            obj.put("error", errorText);
            message = obj.toString();
        } catch (JSONException e) {
            message = "{ \"error\": \"Error while converting simple message to JSON.\"}";
        }

        return message;
    }

    private void sendErrorMessage(String errorText) {
        String message = getJsonConversionErrorMessage(errorText);
        Log.d(TAG, "FIR Error");
        Log.d(TAG, message);
        firebaseAddToQueue(MSG_ERROR, message);
    }

    private void sendSimpleMessage(int msg) {
        firebaseAddToQueue(msg, "{}");
    }

    private void sendSimpleMessage(int msg, String key, String value) {
        String message = null;

        try {
            JSONObject obj = new JSONObject();
            obj.put(key, value);
            message = obj.toString();
            firebaseAddToQueue(msg, message);
        } catch (JSONException e) {
            message = getJsonConversionErrorMessage(e.getLocalizedMessage());
            firebaseAddToQueue(MSG_ERROR, message);
        }
    }    
}