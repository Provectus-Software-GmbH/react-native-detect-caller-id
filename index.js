'use strict';

import { NativeModules, Platform } from 'react-native';

const { DetectCallerId: CallerId } = NativeModules;

let params = null;

let CallerDetector = {
    setCallerList: async (callerList) => { },
    clearCallerList: async () => { },
    requestOverlayPermission: async () => { },
    requestPhonePermission: async () => { },
    requestServicePermission: async () => { },
    requestMiuiActions: async () => { },
    checkPermissions: async () => { },
    getExtensionEnabledStatus: async () => { },
    openExtensionSettings: async () => { },
    setParams: (params) => { },
    migrateOldDataBaseToEncrypted: () => { },
    getWasDbDrobbed: async()=>{}
}



const formatData = (callerList) => {
    try {
        let uniqueFilter = []
        let _callerList = callerList.filter(caller => {

            if (!uniqueFilter.includes(caller.number)) {
                uniqueFilter.push(caller.number)
                return true;
            } else {
                return false;
            }
        })

        if (Platform.OS == 'android') {

            _callerList = _callerList.map(item => {
                return ({
                    ...item,
                    texts: {
                        name: item.name,
                        appointment: item.appointment,
                        city: item.city
                    },
                    number: `${item.number}`,
                    isDeleted: item.isDeleted
                })
            })

        } else {
            _callerList = _callerList.map(item => {
                let _item = JSON.stringify({
                    number: item.number,
                    name: item.iosRow ? item.iosRow : `${item.name}${item.appointment ? ` ${item.appointment}` : ""}${item.city ? ` ${item.city}` : item.city}`,
                    isDeleted: item.isDeleted
                })
                return _item
            })
        }
        return _callerList
    } catch (err) { console.log(err) }
}

let isActive = false
let storage = []

CallerDetector.setCallerList = async (callerList) => {
    if (params == null) {
        throw 'You should set params before use it via setParams(params)'
    }
    try {
        if (isActive) {
            return await new Promise(resolve => {
                storage.push({
                    resolve,
                    list: callerList
                })
            })
        }


        isActive = true

        let _callerList = formatData(callerList)
        let result = null


        if (Platform.OS == 'android') {
            result = await CallerId.setCallerList(_callerList);
        } else {
            result = await CallerId.setCallerList(_callerList, params.ios.EXTENSION_ID, params.ios.DATA_GROUP, params.ios.DATA_KEY);
        }

        return result
    } catch (error) {

        console.log(error)
        throw error;
    } finally {
        isActive = false;
        if (storage.length > 0) {
            let newList = []
            let callbacks = []
            storage.forEach(item => {
                newList = newList.concat(item.list)
                callbacks.push(item.resolve)
            })
            storage = []

            let result = await CallerDetector.setCallerList(newList)
            callbacks.forEach((resolve) => {
                resolve(result)
            })
        }
    }
};


//Android only
CallerDetector.requestOverlayPermission = async () => {
    if (params == null) {
        throw 'You should set params before use it via setParams(params)'
    }
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
    if (params == null) {
        throw 'You should set params before use it via setParams(params)'
    }
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
CallerDetector.clearCallerList = async () => {
    if (params == null) {
        throw 'You should set params before use it via setParams(params)'
    }
    try {
        if (Platform.OS == 'android') {
            return await CallerId.clearCallerList();
        } else {
            return await CallerId.clearCallerList(params.ios.EXTENSION_ID);
        }
    } catch (error) {
        throw error;
    }
};
//Android only
CallerDetector.requestServicePermission = async () => {
    if (params == null) {
        throw 'You should set params before use it via setParams(params)'
    }
    try {
        if (Platform.OS === 'android') {
            return await CallerId.requestServicePermission();
        } else {
            return;
        }
    } catch (error) {
        throw error;
    }
};

//Android only
CallerDetector.checkPermissions = async () => {
    if (params == null) {
        throw 'You should set params before use it via setParams(params)'
    }
    try {
        if (Platform.OS === 'android') {
            return await CallerId.checkPermissions();
        } else {
            return;
        }
    } catch (error) {
        throw error;
    }
};

//Android only
CallerDetector.migrateOldDataBaseToEncrypted = async () => {
    if (params == null) {
        throw 'You should set params before use it via setParams(params)'
    }
    try {
        if (Platform.OS === 'android') {
            return await CallerId.migrateOldDataBaseToEncrypted();
        } else {
            return;
        }
    } catch (error) {
        throw error;
    }
}

//iOS only 
CallerDetector.getExtensionEnabledStatus = async () => {
    if (params == null) {
        throw 'You should set params before use it via setParams(params)'
    }
    try {
        if (Platform.OS === 'ios') {
            return await CallerId.getExtensionEnabledStatus(params.ios.EXTENSION_ID);
        } else {
            return;
        }
    } catch (error) {
        throw error;
    }
};

//iOS only
CallerDetector.openExtensionSettings = async () => {
    try {
        if (Platform.OS === 'ios') {
            return await CallerId.openExtensionSettings();
        } else {
            return;
        }
    } catch (error) {
        throw error;
    }
};

//iOS only
CallerDetector.setParams = async (_params) => {
    params = _params;
    if (Platform.OS == "android") {
        await CallerId.setParams(params.android.dbPassword, params.android.fieldsPassword);
    }
};

CallerDetector.getWasDbDrobbed = async () => {
    try {
        return await CallerId.getWasDbDrobbed();
    } catch (error) {
        throw error;
    }
};

export default CallerDetector;