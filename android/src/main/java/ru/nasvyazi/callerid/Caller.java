package ru.nasvyazi.callerid;

import com.facebook.react.bridge.Callback;


public class Caller {

    public String number;
    public String name;
    public String appointment;
    public String city;
    public Boolean isDeleted = false;

    public Caller(String number, String name, String appointment, String city, Boolean isDeleted) {
        this.number = number;
        this.name = name;
        this.appointment = appointment;
        this.city = city;
        this.isDeleted = isDeleted;
    }

    public Caller(String number, String name, Boolean isDeleted) {
        this.number = number;
        this.name = name;
        this.appointment = "";
        this.city = "";
        this.isDeleted = isDeleted;
    }
}
