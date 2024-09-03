package ru.nasvyazi.callerid.db;

import android.content.Context;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;
import androidx.room.Room;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SupportFactory;

import java.util.List;

public class UserRepository {

  private String DB_NAME = "db_users_new";

  private UserDatabase userDatabase;
  public UserRepository(Context context) {
    userDatabase = Room.databaseBuilder(context, UserDatabase.class, DB_NAME).build();

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