package ru.nasvyazi.callerid.db;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SupportFactory;

import java.util.List;

public class UserEncryptedRepository {

    private String DB_NAME = "db_users_encrypted";

    private UserDatabase userDatabase;

    public UserEncryptedRepository(Context context, String password) {
        final byte[] passphrase = SQLiteDatabase.getBytes(password.toCharArray());
        final SupportFactory factory = new SupportFactory(passphrase);
        userDatabase = Room.databaseBuilder(context, UserDatabase.class, DB_NAME)
                .openHelperFactory(factory)
                .build();
    }

    public Boolean checkDatabaseAccess(){
        try{
            SupportSQLiteDatabase db = userDatabase.getOpenHelper().getReadableDatabase();
            return true;
        }catch (Exception error){
            userDatabase.close();
            Log.i("CALLER_ID", error.getLocalizedMessage());
            return false;
        }
    }

    public void deleteAllTables(){
        userDatabase.clearAllTables();
    }

    public void insertUser(String number,
                           String fullName,
                           String appointment,
                           String city) {

        User user = new User();
        user.setNumber(number);
        user.setFullName(fullName);
        user.setAppointment(appointment);
        user.setCity(city);


        insertUser(user);
    }

    public void insertUser(final User user) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                userDatabase.userDao().insertUser(user);
                return null;
            }
        }.execute();
    }

    public void updateUser(final User user) {

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                userDatabase.userDao().updateUser(user);
                return null;
            }
        }.execute();
    }

    public void deleteUser(final int id) {
        final LiveData<User> user = getUser(id);
        if(user != null) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    userDatabase.userDao().deleteUser(user.getValue());
                    return null;
                }
            }.execute();
        }
    }

    public void deleteUser(final User user) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                userDatabase.userDao().deleteUser(user);
                return null;
            }
        }.execute();
    }


    public void clearDB() {
        userDatabase.userDao().clearDB();
    }

    public LiveData<User> getUser(int id) {
        return userDatabase.userDao().getUser(id);
    }

    public LiveData<User> getUser(String number) {
        return userDatabase.userDao().getUser(number);
    }

    public LiveData<List<User>> getUsers() {
        return userDatabase.userDao().fetchAllUsers();
    }
}