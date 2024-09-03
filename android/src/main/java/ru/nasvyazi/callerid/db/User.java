package ru.nasvyazi.callerid.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;

@Entity
public class User implements Serializable {

  @PrimaryKey(autoGenerate = true)
  private int id;

  private String number;
  private String fullName;
  private String appointment;
  private String city;

  

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getNumber() {
    return number;
  }

  public void setNumber(String number) {
    this.number = number;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getAppointment() {
    return appointment;
  }

  public void setAppointment(String appointment) {
    this.appointment = appointment;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }
}