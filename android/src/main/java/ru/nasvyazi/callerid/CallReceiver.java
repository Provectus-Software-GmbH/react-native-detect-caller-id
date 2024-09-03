package ru.nasvyazi.callerid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import ru.nasvyazi.callerid.db.User;
import ru.nasvyazi.callerid.db.UserEncryptedRepository;
import ru.nasvyazi.callerid.db.UserRepository;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.WINDOW_SERVICE;
import static android.os.Looper.getMainLooper;

import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

interface GetCallerHandler {
  public void onGetCaller(User user);
}


public class CallReceiver extends BroadcastReceiver {
  private static boolean isShowingOverlay = false;
  private static LinearLayout overlay;
  public static String callServiceNumber = null;
  private final String sharedPreferencesName = "SP_storage";
  private final String sharedPreferencesDbPassword = "SP_DB_P";
  private final String sharedPreferencesFieldsPassword = "SP_F_P";


  @Override
  public void onReceive(final Context context, final Intent intent) {
    if (!Settings.canDrawOverlays(context)){
      return;
    }
    String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
    if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
      if (!isShowingOverlay) {
        String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        if (phoneNumber == null){
          phoneNumber = callServiceNumber;
        }

        if (phoneNumber == null){
          return;
        }
        isShowingOverlay = true;

        getCallerName(context, phoneNumber, new GetCallerHandler() {
          @Override
          public void onGetCaller(User user) {


            if (user != null) {
              String callerName = user.getFullName();
              String callerAppointment = user.getAppointment();
              String callerCity = user.getCity();

              showCallerInfo(context, callerName, callerAppointment, callerCity);
            }
          }
        });

      }
    } else if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK) || state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
      if (isShowingOverlay) {
        isShowingOverlay = false;
        callServiceNumber = null;
        dismissCallerInfo(context);
      }
    }
  }

  private static String getApplicationName(Context context) {
    ApplicationInfo applicationInfo = context.getApplicationInfo();
    int stringId = applicationInfo.labelRes;
    return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
  }

  private int getLayoutTemplate(Context context) {
    PackageManager manager = context.getPackageManager();
    Resources resources = null;
    int layout;
    try {
      resources = manager.getResourcesForApplication(context.getPackageName());
      layout = resources.getIdentifier("caller_info_dialog", "layout", context.getPackageName());
    } catch (PackageManager.NameNotFoundException e) {
      layout = R.layout.caller_info_dialog;
    }

    return layout;
  }

  private void showCallerInfo(final Context context, final String callerName,final String callerAppointment,final String callerCity) {
    String appName = "";
    appName = this.getApplicationName(context);
    final String finalAppName = appName;
    final int layout = this.getLayoutTemplate(context);

    new Handler().postDelayed(new Runnable() {

      @Override
      public void run() {
        WindowManager windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        if (overlay == null) {
          LayoutInflater inflater = LayoutInflater.from(context);
          overlay = (LinearLayout) inflater.inflate(layout, null);



          fillLayout(context, finalAppName, callerName, callerAppointment, callerCity);
        }
        int typeParam = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
          WindowManager.LayoutParams.MATCH_PARENT,
          WindowManager.LayoutParams.WRAP_CONTENT,
          typeParam,
          WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
          PixelFormat.TRANSLUCENT);
        windowManager.addView(overlay, params);
      }
    }, 1000);
  }

  private void fillLayout(final Context context, String finalAppName, String callerName, String callerAppointment, String callerCity) {
    try {
      Button closeButton = overlay.findViewById(R.id.close_btn);
      closeButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          isShowingOverlay = false;
          dismissCallerInfo(context);
        }
      });
    }catch (Exception error){

    }

    try {
      LinearLayout CallerLabel = overlay.findViewById(R.id.callerLabel);
      CallerLabel.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          isShowingOverlay = false;
          dismissCallerInfo(context);
        }
      });
    }catch (Exception error){

    }

    try {
      // Set app name
      TextView textViewAppName = overlay.findViewById(R.id.appName);
      textViewAppName.setText(finalAppName);
    }catch (Exception error){

    }

    try {
      // Set caller name
      TextView textViewCallerName = overlay.findViewById(R.id.callerName);
      textViewCallerName.setText(callerName);
    }catch (Exception error){

    }

    try {
      // Set caller appointment
      TextView textViewCallerAppointment = overlay.findViewById(R.id.callerAppointment);
      if (callerAppointment != null && callerAppointment.length() > 0) {
        textViewCallerAppointment.setText(callerAppointment);
      }  else {
        textViewCallerAppointment.setVisibility(View.GONE);
      }
    }catch (Exception error){

    }

    try {
      // Set caller name
      TextView textViewCallerCity = overlay.findViewById(R.id.callerCity);
      if (callerCity != null && callerCity.length() > 0){
        textViewCallerCity.setText(callerCity);
      } else {
        textViewCallerCity.setVisibility(View.GONE);
      }

    }catch (Exception error){

    }

    try {
      // Set app icon
      ImageView appIconImage = overlay.findViewById(R.id.appIcon);
      Drawable icon = null;
      try {
        icon = context.getPackageManager().getApplicationIcon(context.getPackageName());
      } catch (PackageManager.NameNotFoundException e) {
        appIconImage.setVisibility(View.GONE);
      }
      appIconImage.setImageDrawable(icon);
    }catch (Exception error){

    }
  }

  private void dismissCallerInfo(final Context context) {
    if (overlay != null) {
      WindowManager windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
      if (windowManager != null) {
        windowManager.removeView(overlay);
        overlay = null;
      }
    }
  }

  private void getCallerName(final Context context, final String phoneNumberInString, final GetCallerHandler getCallerNameHandler) {
    try {
      String correctedPhoneNumber = phoneNumberInString;
      if (correctedPhoneNumber != null && correctedPhoneNumber.charAt(0) == '+'){
        correctedPhoneNumber = correctedPhoneNumber.substring(1);
      }

      final String phoneForSearch = correctedPhoneNumber;

      SharedPreferences sharedPreferences =  context.getSharedPreferences(sharedPreferencesName,MODE_PRIVATE);

      final String dbPassword = EncodingModule.DecodeDbPassword(sharedPreferences.getString(sharedPreferencesDbPassword, null));
      final String fieldsPassword = EncodingModule.DecodeFieldsPassword(sharedPreferences.getString(sharedPreferencesFieldsPassword, null));


      getOldUser(context, phoneForSearch, new GetCallerHandler() {
        @Override
        public void onGetCaller(User user) {
          if (user != null){
            getCallerNameHandler.onGetCaller(user);
          } else {
            if (dbPassword != null && fieldsPassword != null){
              getUser(context, phoneForSearch, new GetCallerHandler() {
                @Override
                public void onGetCaller(User user) {
                  if (user != null) {
                    getCallerNameHandler.onGetCaller(user);
                  } else {
                    getCallerNameHandler.onGetCaller(null);
                  }
                }
              });
            } else {
              getCallerNameHandler.onGetCaller(null);
            }
          }
        }
      });



    }catch (Exception err){
      Log.i("CALLER_ID", err.getLocalizedMessage());
      getCallerNameHandler.onGetCaller(null);
    }
  }

  public void getOldUser(final Context context, final String phone,  final GetCallerHandler callback) {
    new Handler(getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        final UserRepository repository = new UserRepository(context);
        final LiveData<User> userData = repository.getUser(phone);
        Observer<User> observer = new Observer<User>() {
          @Override
          public void onChanged(@Nullable final User user) {
            if (user == null){
              callback.onGetCaller(null);
            } else {
              callback.onGetCaller(user);
            }
          }
        };
        userData.observeForever(observer);
      }
    });
  }


  public void getUser(final Context context, final String phone,  final GetCallerHandler callback) {
    SharedPreferences sharedPreferences =  context.getSharedPreferences(sharedPreferencesName,MODE_PRIVATE);

    final String dbPassword = EncodingModule.DecodeDbPassword(sharedPreferences.getString(sharedPreferencesDbPassword, null));
    final String fieldsPassword = EncodingModule.DecodeFieldsPassword(sharedPreferences.getString(sharedPreferencesFieldsPassword, null));

    new Handler(getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        try {
        final UserEncryptedRepository repository = new UserEncryptedRepository(context, dbPassword);
        if (!repository.checkDatabaseAccess()){
          callback.onGetCaller(null);
          return;
        }
        final LiveData<User> userData = repository.getUser(phone);
        Observer<User> observer = new Observer<User>() {
          @Override
          public void onChanged(@Nullable final User user) {

            if (user == null){
              callback.onGetCaller(null);
            }

            try{

      
              Key aesKey = new SecretKeySpec(fieldsPassword.getBytes(), "AES");
              Cipher cipher = null;
              cipher = Cipher.getInstance("AES");
              cipher.init(Cipher.DECRYPT_MODE, aesKey);

              User userForReturn = new User();
              userForReturn.setId(user.getId());
              userForReturn.setNumber(user.getNumber());
           
              byte[] decryptedFullName = cipher.doFinal(Base64.decode(user.getFullName().getBytes(), Base64.DEFAULT));
            
              userForReturn.setFullName(new String(decryptedFullName));
              byte[] decryptedAppointment = cipher.doFinal(Base64.decode(user.getAppointment().getBytes(), Base64.DEFAULT));
              userForReturn.setAppointment(new String(decryptedAppointment));
              byte[] decryptedCity = cipher.doFinal(Base64.decode(user.getCity().getBytes(), Base64.DEFAULT));
              userForReturn.setCity(new String(decryptedCity));


              callback.onGetCaller(userForReturn);

            }catch (Exception error){
              callback.onGetCaller(null);
            }
          }
        };

          userData.observeForever(observer);

        }catch (Exception err){
          Log.i("CALLER_ID", err.getLocalizedMessage());
        }
      }
    });
  }
}
