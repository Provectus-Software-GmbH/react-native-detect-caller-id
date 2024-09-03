package ru.nasvyazi.callerid;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Base64;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.security.Key;
import java.util.ArrayList;
import java.util.List;

import ru.nasvyazi.callerid.db.User;
import ru.nasvyazi.callerid.db.UserEncryptedRepository;
import ru.nasvyazi.callerid.db.UserRepository;
import ru.nasvyazi.callerid.permissions.PermissionsHelper;

import static android.content.Context.MODE_PRIVATE;
import static android.os.Looper.getMainLooper;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;


interface CallerIdCallback{
  void onReturnUsers(List<User> users);
  void onError(String s);
}

interface DrobDbCallback{
  void onDrobEnd();
  void onError(String s);
}

public class DetectCallerIdModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;
  private final PermissionsHelper permissionsHelper;
  private final String sharedPreferencesName = "SP_storage";
  private final String sharedPreferencesDbPassword = "SP_DB_P";
  private final String sharedPreferencesFieldsPassword = "SP_F_P";
  private final String sharedPreferencesWasDbDrobbed = "SP_W_D_D";


  @SuppressLint("RestrictedApi")
  public DetectCallerIdModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.permissionsHelper = new PermissionsHelper(reactContext);
  }

  public void markDbDrobbed() {
      SharedPreferences sharedPreferences =  getReactApplicationContext().getSharedPreferences(sharedPreferencesName,MODE_PRIVATE);

      SharedPreferences.Editor editor = sharedPreferences.edit();
      editor.putBoolean(sharedPreferencesWasDbDrobbed, true);
      editor.commit();
  }

  @ReactMethod
  public void getWasDbDrobbed(Promise promise) {
      SharedPreferences sharedPreferences =  getReactApplicationContext().getSharedPreferences(sharedPreferencesName,MODE_PRIVATE);

      final Boolean wasDbDrobbed = sharedPreferences.getBoolean(sharedPreferencesWasDbDrobbed, false);

      SharedPreferences.Editor editor = sharedPreferences.edit();
      editor.putBoolean(sharedPreferencesWasDbDrobbed, false);
      editor.commit();

      promise.resolve(wasDbDrobbed);
  }

  @Override
  public String getName() {
    return "DetectCallerId";
  }



  @ReactMethod
  public void checkPermissions(Promise promise) {
    this.permissionsHelper.checkPermissions(promise);
  }

  @ReactMethod
  public void requestPhonePermission(Promise promise) {
    this.permissionsHelper.requestPhonePermission(promise);
  }

  @ReactMethod
  public void requestOverlayPermission(Promise promise) {
    this.permissionsHelper.requestOverlayPermission(promise);
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  @ReactMethod
  public void requestServicePermission(Promise promise) {
    this.permissionsHelper.requestServicePermission(promise);
  }




  @ReactMethod
  public void setCallerList(final ReadableArray callerList, final Promise promise) {

    SharedPreferences sharedPreferences =  getReactApplicationContext().getSharedPreferences(sharedPreferencesName,MODE_PRIVATE);

    final String dbPassword = EncodingModule.DecodeDbPassword(sharedPreferences.getString(sharedPreferencesDbPassword, null));
    final String fieldsPassword = EncodingModule.DecodeFieldsPassword(sharedPreferences.getString(sharedPreferencesFieldsPassword, null));

    if (dbPassword == null || fieldsPassword == null){
      promise.reject("CALLER_ID", "Params not set");
      return;
    }

    final List<Caller> callers = new ArrayList<Caller>();
    for (int i = 0; i < callerList.size(); i++) {
      ReadableMap caller = callerList.getMap(i);
      if (caller.hasKey("texts") && caller.hasKey("number")) {
        final ReadableMap texts = caller.getMap("texts");
        final String phoneNumber = caller.getString("number");
        final Boolean isDeleted = caller.getBoolean("isDeleted");

        if (texts.hasKey("name")){
          final String name = texts.getString("name");

          if (texts.hasKey("appointment") && texts.hasKey("city")){
            final String appointment = texts.getString("appointment");
            final String city = texts.getString("city");
            callers.add(new Caller(phoneNumber, name, appointment, city, isDeleted));
          } else {
            callers.add(new Caller(phoneNumber, name, isDeleted));
          }
        }
      }
    }

    final UserEncryptedRepository userRepository = new UserEncryptedRepository(getReactApplicationContext(), dbPassword);

    if (!userRepository.checkDatabaseAccess()){
      promise.reject("CALLER_ID", "Wrong DB password");
      return;
    }

    final LiveData<List<User>> usersData = userRepository.getUsers();

    GsonBuilder builder = new GsonBuilder();
    final Gson gson = builder.create();

    getAllUsers(new CallerIdCallback() {
      @Override
      public void onError(String s){
        promise.reject("CALLER_ID",s);
      }


      @Override
      public void onReturnUsers(List<User> users) {
        try{
          for(int i = 0; i<callers.size(); i++){
            Caller caller = callers.get(i);
            User user = null;

            for(int ii = 0; ii<users.size(); ii++){
              if (users.get(ii).getNumber().equals(caller.number)){
                user = users.get(ii);
              }
            }

            if (caller.isDeleted){
              if (user != null){
                userRepository.deleteUser(user);
              }
            } else {

              Key aesKey = new SecretKeySpec(fieldsPassword.getBytes(), "AES");
              Cipher cipher = Cipher.getInstance("AES");
              cipher.init(Cipher.ENCRYPT_MODE, aesKey);

              byte[] encryptedFullName = cipher.doFinal(caller.name.getBytes("UTF-8"));
              byte[] encryptedAppointment = cipher.doFinal(caller.appointment.getBytes("UTF-8"));
              byte[] encryptedCity = cipher.doFinal(caller.city.getBytes("UTF-8"));

              if (user != null){

                user.setFullName(new String(Base64.encode(encryptedFullName, Base64.DEFAULT)));
                user.setAppointment(new String(Base64.encode(encryptedAppointment, Base64.DEFAULT)));
                user.setCity(new String(Base64.encode(encryptedCity, Base64.DEFAULT)));

                userRepository.updateUser(user);
              } else {

                userRepository.insertUser(caller.number, new String(Base64.encode(encryptedFullName, Base64.DEFAULT)), new String(Base64.encode(encryptedAppointment, Base64.DEFAULT)), new String(Base64.encode(encryptedCity, Base64.DEFAULT)));
              }
            }
          }

          promise.resolve(true);


        }catch(Exception error){
          Log.e("CALLER_ID", error.getLocalizedMessage());
          promise.reject("CALLER_ID", error.getLocalizedMessage());
        }
      }
    });
  }

  @ReactMethod
  public void clearCallerList(final Promise promise) {

    try{
      final UserRepository oldUserRepository = new UserRepository(getReactApplicationContext());
      oldUserRepository.clearDB();
    }catch (Exception error){

    }

    try{

      ClearDb(new DrobDbCallback() {
        @Override
        public void onError(String s){
          promise.reject("CALLER_ID",s);
        }


        @Override
        public void onDrobEnd() {
          promise.resolve(true);
        }
      });
    }catch (Exception error){
      promise.reject("CALLER_ID", error.getLocalizedMessage());
    }
  }

  @ReactMethod
  public void setParams(final String dbPassword, final String fieldsPassword, final Promise promise) {
    SharedPreferences sharedPreferences =  getReactApplicationContext().getSharedPreferences(sharedPreferencesName,MODE_PRIVATE);

    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putString(sharedPreferencesDbPassword, EncodingModule.EncodeDbPassword(dbPassword));
    editor.putString(sharedPreferencesFieldsPassword, EncodingModule.EncodeFieldsPassword(fieldsPassword));
    editor.commit();

    promise.resolve(true);
  }

  public void ClearOldDb(final DrobDbCallback callback){
    HandlerThread mHandlerThread = new HandlerThread("clearDBHandler");
    mHandlerThread.start();
    final UserRepository oldRepository = new UserRepository(getReactApplicationContext());

    new Handler(mHandlerThread.getLooper()).postDelayed(new Runnable() {
      @Override
      public void run() {
        try {
          oldRepository.clearDB();
        }catch (Exception error){}

        try {
          oldRepository.deleteAllTables();
        }catch (Exception error){}

        try {
          getReactApplicationContext().deleteDatabase("db_users_new");
        }catch (Exception error){}

        callback.onDrobEnd();
      }
    }, 100);
  }

  public void ClearDb(final DrobDbCallback callback){
    SharedPreferences sharedPreferences =  getReactApplicationContext().getSharedPreferences(sharedPreferencesName,MODE_PRIVATE);
    final String dbPassword = EncodingModule.DecodeDbPassword(sharedPreferences.getString(sharedPreferencesDbPassword, null));


    HandlerThread mHandlerThread = new HandlerThread("clearDBHandler");
    mHandlerThread.start();
    final UserEncryptedRepository repository = new UserEncryptedRepository(getReactApplicationContext(), dbPassword);
    if (!repository.checkDatabaseAccess()){
      callback.onError("Wrong DB password");
      return;
    }

    new Handler(mHandlerThread.getLooper()).postDelayed(new Runnable() {
      @Override
      public void run() {
        try {
          repository.clearDB();
        }catch (Exception error){}

        try {
          repository.deleteAllTables();
        }catch (Exception error){}

        try {
          getReactApplicationContext().deleteDatabase("db_users_encrypted");
        }catch (Exception error){}

        callback.onDrobEnd();
      }
    }, 100);
  }

  @ReactMethod
  public void migrateOldDataBaseToEncrypted(final Promise promise) {
    SharedPreferences sharedPreferences =  getReactApplicationContext().getSharedPreferences(sharedPreferencesName,MODE_PRIVATE);

    try {
      final String dbPassword = EncodingModule.DecodeDbPassword(sharedPreferences.getString(sharedPreferencesDbPassword, null));
      final String fieldsPassword = EncodingModule.DecodeFieldsPassword(sharedPreferences.getString(sharedPreferencesFieldsPassword, null));

      if (dbPassword == null || fieldsPassword == null){
        promise.reject("CALLER_ID", "Params not set");
        return;
      }

      final UserEncryptedRepository newRepository = new UserEncryptedRepository(getReactApplicationContext(), dbPassword);
      if (!newRepository.checkDatabaseAccess()){
        promise.reject("CALLER_ID","Wrong DB password");
        return;
      }


      final UserRepository oldRepository = new UserRepository(getReactApplicationContext());

      getAllOldUsers(new CallerIdCallback() {
        @Override
        public void onError(String s){
          promise.reject("CALLER_ID",s);
        }


        @Override
            public void onReturnUsers(final List<User> oldUsers) {

              if (oldUsers.size() == 0){

                ClearOldDb(new DrobDbCallback() {

                  @Override
                  public void onError(String s){
                    promise.reject("CALLER_ID",s);
                  }


                  @Override
                  public void onDrobEnd() {
                    promise.resolve(true);
                  }
                });

              } else {


                getAllUsers(new CallerIdCallback() {

                  @Override
                  public void onError(String s){
                    promise.reject("CALLER_ID",s);
                  }

                  @Override
                  public void onReturnUsers(List<User> newUsers) {


                    try{
                      Key aesKey = new SecretKeySpec(fieldsPassword.getBytes(), "AES");
                      Cipher cipher = Cipher.getInstance("AES");
                      cipher.init(Cipher.ENCRYPT_MODE, aesKey);

                      for(int i = 0; i<oldUsers.size(); i++){

                        User user = oldUsers.get(i);
                        User newUser = new User();
                        newUser.setNumber(user.getNumber());


                        byte[] encryptedFullName = cipher.doFinal(user.getFullName().getBytes("UTF-8"));

                        newUser.setFullName(new String(Base64.encode(encryptedFullName, Base64.DEFAULT)));

                        byte[] encryptedAppointment = cipher.doFinal(user.getAppointment().getBytes("UTF-8"));

                        newUser.setAppointment(new String(Base64.encode(encryptedAppointment, Base64.DEFAULT)));

                        byte[] encryptedCity = cipher.doFinal(user.getCity().getBytes("UTF-8"));

                        newUser.setCity(new String(Base64.encode(encryptedCity, Base64.DEFAULT)));


                        User existUser = null;

                        for(int ii = 0; ii<newUsers.size(); ii++){
                          if (newUsers.get(ii).getNumber().equals(user.getNumber())){
                            existUser = newUsers.get(ii);
                          }
                        }


                        if (existUser == null){
                          newRepository.insertUser(newUser);
                        } else {
                          newRepository.updateUser(newUser);
                        }
                      }



                      ClearOldDb(new DrobDbCallback(){

                        @Override
                        public void onError(String s){
                          promise.reject("CALLER_ID",s);
                        }


                        @Override
                        public void onDrobEnd() {

                          promise.resolve(true);
                        }
                      });

                    }catch(Exception error){
                      promise.reject("CALLER_ID", error.getLocalizedMessage());
                    }
                  }
                });
              }
            }
          });

    } catch (Exception e) {
      promise.reject("CALLER_ID", e.getLocalizedMessage());
    }
  }

  public void getAllOldUsers(final CallerIdCallback callback) {
    new Handler(getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        final UserRepository repository = new UserRepository(getReactApplicationContext());
        final LiveData<List<User>> usersData = repository.getUsers();
        Observer<List<User>> observer = new Observer<List<User>>() {
          @Override
          public void onChanged(@Nullable final List<User> users) {
            callback.onReturnUsers(users);
          }
        };
        usersData.observeForever(observer);
      }
    });
  }


  public void getAllUsers(final CallerIdCallback callback) {
    SharedPreferences sharedPreferences =  getReactApplicationContext().getSharedPreferences(sharedPreferencesName,MODE_PRIVATE);

    final String dbPassword = EncodingModule.DecodeDbPassword(sharedPreferences.getString(sharedPreferencesDbPassword, null));

    new Handler(getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        final UserEncryptedRepository repository = new UserEncryptedRepository(getReactApplicationContext(), dbPassword);
        if (!repository.checkDatabaseAccess()){
          callback.onError("Wrong DB password");
          return;
        }
        final LiveData<List<User>> usersData = repository.getUsers();
        Observer<List<User>> observer = new Observer<List<User>>() {
          @Override
          public void onChanged(@Nullable final List<User> users) {
            callback.onReturnUsers(users);
          }
        };
        usersData.observeForever(observer);
      }
    });
  }
}
