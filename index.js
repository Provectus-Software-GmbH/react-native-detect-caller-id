'use strict';

import { NativeModules, Platform } from 'react-native';

const { DetectCallerId: CallerId } = NativeModules;

let CallerDetector = {
    checkPermissions: async () => { },
    requestIosPermissions: async () => { },
    requestAndroidOverlayPermission: async () => { },
    requestAndroidPhonePermission: async () => { },
    requestAndroidServicePermission: async () => { },
    setCallerList: async (options: {
        type: 'default' | 'allAllowed' | 'allBlocked',
        items: { label: string; phonenumber: number, isRemoved: boolean, isBlocked: boolean }[],
    }) => { },
    clearCallerList: async () => { },
    getCallerIdMode: async () => { },
    reloadExtension: async () => { },
    simulateIncomingCall: async (phonenumber: number) => { },
}

// ios:  returns "denied" or "granted"
// android returns { }
CallerDetector.checkPermissions = async () => {
    try {
        return CallerId.checkPermissions();
    } catch (error) {
        throw error;
    }
};


// ios only (no native permission alert, will just open related settings on ios >= 13.4)
CallerDetector.requestIosPermissions = async () => {
    try {
        return CallerId.requestPermissions();
    } catch (error) {
        throw error;
    }
};


//Android only
CallerDetector.requestOverlayPermission = async () => {
    try {
        if (Platform.OS === 'android') {
            return await CallerId.requestOverlayPermission();
        } else {
            return;
        }
    } catch (error) {
        throw error;
    }
};

//Android only
CallerDetector.requestPhonePermission = async () => {
    try {
        if (Platform.OS === 'android') {
            return await CallerId.requestPhonePermission();
        } else {
            return;
        }
    } catch (error) {
        throw error;
    }
};

//Android only
CallerDetector.requestServicePermission = async () => {
    try {
        if (Platform.OS === 'android') {
            return CallerId.requestServicePermission();
        } else {
            return;
        }
    } catch (error) {
        throw error;
    }
};

CallerDetector.setCallerList = async (options) => {
    return CallerId.setCallerList(JSON.stringify(options));
};

CallerDetector.clearCallerList = async () => {
  return CallerId.clearCallerList();
};

// android only! returns workProfileMode, defaultMode or compatibilityMode
CallerDetector.getCallerIdMode = async () => {
  try {
    return CallerId.getCallerIdMode();
  } catch (error) {
    throw error;
  }
};


// ios only! for debugging
CallerDetector.reloadExtension = async () => {
    try {
        return CallerId.reloadExtension();
    } catch (error) {
        throw error;
    }
};

CallerDetector.simulateIncomingCall = async (phonenumber) => {
    try {
      return CallerId.simulateIncomingCall(Platform.OS === 'android' ?
        phonenumber.toString():
        phonenumber);
    } catch (error) {
        throw error;
    }
};

export default CallerDetector;
