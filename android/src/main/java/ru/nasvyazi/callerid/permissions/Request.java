package ru.nasvyazi.callerid.permissions;

import com.facebook.react.bridge.Callback;

public class Request {

  public boolean[] rationaleStatuses;
  public Callback callback;

  public Request(boolean[] rationaleStatuses, Callback callback) {
    this.rationaleStatuses = rationaleStatuses;
    this.callback = callback;
  }
}
