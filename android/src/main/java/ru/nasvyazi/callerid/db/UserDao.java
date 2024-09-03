package ru.nasvyazi.callerid.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface UserDao {

  @Insert
  Long insertUser(User user);


  @Query("SELECT * FROM User")
  LiveData<List<User>> fetchAllUsers();

  @Query("DELETE FROM User")
  void clearDB();


  @Query("SELECT * FROM User WHERE id =:userId")
  LiveData<User> getUser(int userId);


  @Query("SELECT * FROM User WHERE number =:number")
  LiveData<User> getUser(String number);


  @Update
  void updateUser(User user);


  @Delete
  void deleteUser(User user);
}