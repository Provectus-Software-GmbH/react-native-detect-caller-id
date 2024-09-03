package ru.nasvyazi.callerid.permissions;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static android.content.Context.ROLE_SERVICE;
import static ru.nasvyazi.callerid.ReactBridgeTools.convertJsonToMap;

public class PermissionsHelper implements PermissionListener, ActivityEventListener {

  private final ReactApplicationContext reactContext;

  private final SparseArray<Request> mRequests;
  private final SparseArray<Request> oRequests;
  private int mRequestCode = 1;
  private int oRequestCode = 10000;
  
  private String[] CHECKING_PERMISSIONS = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG};

  public PermissionsHelper(ReactApplicationContext reactContext) {
    this.reactContext = reactContext;

    reactContext.addActivityEventListener(this);
    mRequests = new SparseArray<Request>();
    oRequests = new SparseArray<Request>();
  }

  public boolean isSystemAlertPermissionGranted() {
    return Settings.canDrawOverlays(this.reactContext);
  }

  public boolean isPhonePermissionGranted() {
    boolean result = true;
    
    for(int i = 0; i< CHECKING_PERMISSIONS.length; i++){
      result = result && ActivityCompat.checkSelfPermission(this.reactContext, CHECKING_PERMISSIONS[i]) == PackageManager.PERMISSION_GRANTED;
    }
    
    return result;
  }

  public boolean isServicePermissionGranted() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      RoleManager roleManager = (RoleManager) this.reactContext.getSystemService(ROLE_SERVICE);
      return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    } else {
      return true;
    }
  }

  public boolean isRequiredMiuiActions() {

   return true;
  }

  public void requestMiuiActions(String a, final Promise promise){
    if (this.isRequiredMiuiActions()) {
      if (a == "1"){;
        this.reactContext.startActivity(XiaomiUtils.createMiUi567OtherSettingsIntent(this.reactContext));
      } else if (a=="2"){
        this.reactContext.startActivity(XiaomiUtils.createMiUi8OtherSettingsIntent(this.reactContext));
      } else {
        XCheckPermission.applyMiuiPermission(this.reactContext);
      }
      promise.resolve("granted");
    }

  }



  public void checkPermissions(Promise promise) {
    final boolean overlayPermissionGranted = this.isSystemAlertPermissionGranted();
    final boolean phonePermissionGranted = this.isPhonePermissionGranted();
    final boolean servicePermissionGranted = this.isServicePermissionGranted();

    PermissionsInfo permissionsInfo = new PermissionsInfo();
    permissionsInfo.overlayPermissionGranted = overlayPermissionGranted;
    permissionsInfo.phonePermissionGranted = phonePermissionGranted;
    permissionsInfo.servicePermissionGranted = servicePermissionGranted;

    try {
      GsonBuilder builder = new GsonBuilder();
      Gson gson = builder.create();
      promise.resolve(convertJsonToMap(new JSONObject(gson.toJson(permissionsInfo))));
    } catch (JSONException e) {
      promise.reject("ERROR", "JSON PARSE ERROR");
    }
  }
  
  public void requestPhonePermission(final Promise promise) {
    if (!this.isPhonePermissionGranted()){

      try {
        PermissionAwareActivity activity = getPermissionAwareActivity();

        final List<String> askingPermissions = new ArrayList<String>();
        for (int i = 0; i<CHECKING_PERMISSIONS.length; i++){
          if (ActivityCompat.checkSelfPermission(this.reactContext, CHECKING_PERMISSIONS[i]) != PackageManager.PERMISSION_GRANTED){
            askingPermissions.add(CHECKING_PERMISSIONS[i]);
          }
        }
        
        mRequests.put(mRequestCode, new Request(
          null,
          new Callback() {
            @SuppressLint("ApplySharedPref")
            @Override
            public void invoke(Object... args) {
              int[] results = (int[]) args[0];
              String[] resultsForReturn = new String[askingPermissions.size()];

              if (results.length > 0) {
                for(int i=0; i<askingPermissions.size(); i++){
                  resultsForReturn[i] = results[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied";
                }
                promise.resolve(Arguments.makeNativeArray(resultsForReturn));
              } else {
                promise.resolve("error");
              }
            }
          }));
        
        
        activity.requestPermissions(askingPermissions.toArray(new String[askingPermissions.size()]), mRequestCode, this);
        mRequestCode++;
      } catch (IllegalStateException e) {
        promise.reject("ERROR", "INVALID ACTIVITY ERROR");
      }
    } else {
      promise.resolve("granted");
    }
  }
  
  public void requestOverlayPermission(final Promise promise){
    if (!isSystemAlertPermissionGranted()) {
      try {
        oRequests.put(oRequestCode, new Request(
          null,
          new Callback() {
            @Override
            public void invoke(Object... args) {
              if (isSystemAlertPermissionGranted()){
                promise.resolve("granted");
              } else {
                promise.resolve("denied");
              }
            }
          }));

        final String packageName =  this.reactContext.getPackageName();
        final Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName));
        this.reactContext.startActivityForResult(intent, oRequestCode, null);
        oRequestCode++;
      } catch (IllegalStateException e) {
        promise.reject("ERROR", "INVALID ACTIVITY ERROR");
      }

    } else {
      promise.resolve("granted");
    }
  }
  
  @RequiresApi(api = Build.VERSION_CODES.Q)
  public void requestServicePermission(final Promise promise){
    if (!this.isServicePermissionGranted()){
      try {
        oRequests.put(oRequestCode, new Request(
          null,
          new Callback() {
            @Override
            public void invoke(Object... args) {
              if (isServicePermissionGranted()){
                promise.resolve("granted");
              } else {
                promise.resolve("denied");
              }
            }
          }));

        RoleManager roleManager = (RoleManager) this.reactContext.getSystemService(ROLE_SERVICE);
        Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
        this.reactContext.startActivityForResult(intent, oRequestCode, null);

        oRequestCode++;
      } catch (IllegalStateException e) {
        promise.reject("ERROR", "INVALID ACTIVITY ERROR");
      }
    } else {
      promise.resolve("granted");
    }
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    Request request = oRequests.get(requestCode);
    if (request != null){
      request.callback.invoke();
      oRequests.remove(requestCode);
    }
  }

  @Override
  public void onNewIntent(Intent intent) {

  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    Request request = mRequests.get(requestCode);
    if (request != null){
      request.callback.invoke(grantResults, getPermissionAwareActivity(), request.rationaleStatuses);
      mRequests.remove(requestCode);
      return mRequests.size() == 0;
    } else {
      return false;
    }
  }

  private PermissionAwareActivity getPermissionAwareActivity() {
    Activity activity = this.reactContext.getCurrentActivity();
    if (activity == null) {
      throw new IllegalStateException(
        "Tried to use permissions API while not attached to an " + "Activity.");
    } else if (!(activity instanceof PermissionAwareActivity)) {
      throw new IllegalStateException(
        "Tried to use permissions API but the host Activity doesn't"
          + " implement PermissionAwareActivity.");
    }
    return (PermissionAwareActivity) activity;
  }
}
