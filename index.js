'use strict';

import { NativeModules, Platform } from 'react-native';

const { DetectCallerId: CallerId } = NativeModules;

let CallerDetector = {
    checkPermissions: async () => { },
    ensureContactPermissions: async () => { },
    init: async (forceAndroidMode: 'defaultMode' | 'compatibilityMode' | 'workProfileMode') => {},
    requestIosPermissions: async () => { },
    requestAndroidOverlayPermission: async () => { },
    requestAndroidPhonePermission: async () => { },
    requestAndroidServicePermission: async () => { },
    setCallerList: async (options: {
        type: 'block' | 'unblock' | 'identify' | 'default' | 'clearAll',
        items: { label: string; phonenumber: number, isRemoved: boolean, isBlocked: boolean }[],
    }) => { },
    clearCallerList: async () => { },
    getCallerIdMode: async () => { },
    reloadExtension: async () => { },
    simulateIncomingCall: async (phonenumber: string) => { },
    syncContacts: async(items: any[], isVacationModeActive: boolean) => {},
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

// ios:  not implemented
// android returns 'granted' | 'denied'
CallerDetector.ensureContactPermissions = async () => {
    try {
        return CallerId.ensureContactPermissions();
    } catch (error) {
        throw error;
    }
};

// ios:  not implemented
// android returns 'granted' | 'denied'
CallerDetector.init = async (forceAndroidMode) => {
  console.log('CallerDetector init');
    try {
        return CallerId.init(forceAndroidMode);
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
CallerDetector.clearContacts = async () => {
    try {
        if (Platform.OS === 'android') {
            return await CallerId.clearContacts();
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

CallerDetector.syncContacts = async (items, isVacationModeActive) => {
    try {
        if (Platform.OS === 'android') {
            return CallerId.syncContacts(items, isVacationModeActive);
        } else {
            throw new Error('This method is Android only');
        }
    } catch (error) {
        throw error;
    }
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
