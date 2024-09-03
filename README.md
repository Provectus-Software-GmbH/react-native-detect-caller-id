<p align="center">
  <img src="https://yesskyscrapers.github.io/app4t2site/reactnativedetectcalleridImage.png" alt="Cover" title="React Native Detect CallerID" width="800">
</p>

React Native Detect CallerID implements [Broadcast Receiver](https://developer.android.com/guide/components/broadcasts) (Android) and [CallKit: Call Directory Extension](https://developer.apple.com/documentation/callkit/cxcalldirectoryextensioncontext) (iOS).

With this library you can simple add CallerID for your React-Native Apps. For iOS library provides additional information on calling screen. For android library render additional layout with your information about incoming phone number.

<hr/>

<br/>
<p align="center">
    <img src="https://yesskyscrapers.github.io/app4t2site/reactnativedetectcalleridiOSImage.PNG" alt="React Native Detect CallerID iOS" title="React Native Detect CallerID" height="600" >
    <img src="https://yesskyscrapers.github.io/app4t2site/reactnativedetectcalleridAndroidClosedImage.jpg" alt="React Native Detect CallerID Android 1" title="React Native Detect CallerID" height="600">
    <img src="https://yesskyscrapers.github.io/app4t2site/reactnativedetectcalleridAndroidOpenedImage.jpg" alt="React Native Detect CallerID Android 2" title="React Native Detect CallerID" height="600">
</p>
<br/>

## Table of Contents

- [Installation](#installation)
- [Basic Usage](#basic-usage)
- [API](#api)

## Installation

Using Yarn

```sh
yarn add react-native-detect-caller-id
```

Using npm

```sh
npm install react-native-detect-caller-id --save
```

### iOS
Firsty, you should add `Call Directory Extension`.

<p align="center">
  <img src="https://yesskyscrapers.github.io/app4t2site/reactnativedetectcalleridImageTutorial1.png" alt="Cover" title="React Native Detect CallerID" width="200">
  <img src="https://yesskyscrapers.github.io/app4t2site/reactnativedetectcalleridImageTutorial2.png" alt="Cover" title="React Native Detect CallerID" width="600">
  <img src="https://yesskyscrapers.github.io/app4t2site/reactnativedetectcalleridImageTutorial3.png" alt="Cover" title="React Native Detect CallerID" width="800">
</p>

It creates new folder in your main app. Open `CallDirectoryHandler.swift` and delete all content. Then add content from `node_modules/react-native-detect-caller-id/ios/CallDirectoryExtension/CallDirectoryHandler.swift`. Its replace default handler implementation to library implementation.

<p align="center">
  <img src="https://yesskyscrapers.github.io/app4t2site/reactnativedetectcalleridImageTutorial4.png" alt="Cover" title="React Native Detect CallerID" height="300">
</p>

Secondly, you should use provisioning profile for your app with enabled AppGroup Capability, so add this Capability.

<p align="center">
  <img src="https://yesskyscrapers.github.io/app4t2site/reactnativedetectcalleridImageTutorial5.png" alt="Cover" title="React Native Detect CallerID" width="800">
</p>

Thirdly, select your CallDirectoryExtension target and set provisioning profile for extension and add similar AppGroup (see previous point).

<p align="center">
  <img src="https://yesskyscrapers.github.io/app4t2site/reactnativedetectcalleridImageTutorial6.png" alt="Cover" title="React Native Detect CallerID" width="800">
</p>

Lastly, IMPORTANT! check your `CallDirectoryHandler.swift`. It should define similar DATA_GROUP constant with your AppGroup.

### Android

Any actions not required.

For customization caller information layout you can add `caller_info_dialog.xml` inside `YOUR_APP/android/app/src/main/res/layout/` folder. For example, you can copy layout implementation from `node_modules/react-native-detect-caller-id/android/src/main/res/layout/caller_info_dialog.xml`.

Button with id `close_btn` will get click action to closing layout.
LinearLayout with id `callerLabel` will get click action to closing layout.
TextView with id `appName` will get text with your AppName.
TextView with id `callerName` will get text with CallerName provided with library for incoming number.
ImageView with id `appIcon` will get drawable with your app default icon.


## Basic Usage

```javascript
import CallerDetector from 'react-native-detect-caller-id';

// Check required permissions for your platform
checkPermissions = async () => {
    if (Platform.OS == 'android') {
        const checkInfo = await CallerDetector.checkPermissions()
        console.log('checkPermissions', checkInfo)
    } else {
        const checkInfo = await CallerDetector.getExtensionEnabledStatus()
        console.log('getExtensionEnabledStatus', checkInfo)
    }
}

// Open android overlay settings screen
onAskOverlayPermission = async () => {
    const permission = await CallerDetector.requestOverlayPermission()
    console.log('onAskOverlayPermission', permission)
}

// Ask required permissions for android 
onAskPermissions = async () => {
    const permission = await CallerDetector.requestPhonePermission()
    console.log('onAskPermissions', permission)
}

// Settings default detecting app
onAskServicePermissions = async () => {
    const permission = await CallerDetector.requestServicePermission()
    console.log('onAskServicePermissions', permission)
}

// Set params for your ios Extension, AppGroup and android encryption
onSetParams = async () => {
    CallerDetector.setParams({
        ios: {
            EXTENSION_ID: 'packagename.CallDirectoryExtension',
            DATA_GROUP: 'group.packagename',
            DATA_KEY: 'callerListKey'
        },
        android: {
            dbPassword: "dbpassword",
            fieldsPassword: "16-length-pass!!" // using 128bit mask for encryption so u need to use 16-length password
        }
    })
}

// Migrate unencryption db from prev version to new android encryption DB
onMigrateData = async () => {
    CallerDetector.migrateOldDataBaseToEncrypted()
}


// Open iOS settings for settings Detect Caller App
onOpenSettings = async () => {
    const permission = await CallerDetector.openExtensionSettings()
    console.log('onOpenSettings', permission)
}

// Setting Callers for detecting
setCallerList = async () => {
    let callers = []
    callers.push({
        name: `Pavel Nikolaev`,
        appointment: `Developer`,
        city: `Riga`,
        iosRow: `Pavel Nikolaev, Developer, Riga`,
        number: 79771678179,
        isDeleted: false
    })

    callers.push({
        name: `Pavel Nikolaev`,
        appointment: `Developer`,
        city: `Riga`,
        iosRow: `Pavel Nikolaev, Developer, Riga`,
        number: 79013308179,
        isDeleted: true
    })

    let result = await CallerDetector.setCallerList(callers)
    console.log(result);
}

```

## API
* `checkPermissions`: Promise<any> - (*ONLY Android*) returns all permissions for android
* `requestOverlayPermission`: Promise<string> - (*ONLY Android*) returns result of overlay request
* `requestPhonePermission`: Promise<string> - (*ONLY Android*) returns result of permissions request
* `requestServicePermission`: Promise<string> - (*ONLY Android*) returns result of service request
* `getExtensionEnabledStatus`: Promise<string> - (*ONLY iOS*) returns extension status
* `setParams`: Void - return nothing. Just set params for your ios extension, appgroup and android encryption
* `migrateOldDataBaseToEncrypted`: Promive<Void> - return nothing. Just migrate old unencryption db from prev version to new android encryption DB
* `openExtensionSettings`: Promise<any> - (*ONLY iOS*) return nothing. Just opening extension settings page (iOS 13.4+)
* `setCallerList`: Promise<any> - return nothing. Waiting end of process.


## Check Permissions (Android)
Checking all android permissions for correctly working. Service permissions required with Android 10. For lower version its return always true
```js
const checkInfo = await CallerDetector.checkPermissions()
console.log('checkPermissions', checkInfo)
//checkPermissions {"overlayPermissionGranted": true, "phonePermissionGranted": true, "servicePermissionGranted": true}
```

## Request Overlay Permission (Android)
Asking user to allow overlay permission. You should ask this, if checkPermissions method returned "overlayPermissionGranted": false
```js
const permission = await CallerDetector.requestOverlayPermission()
console.log('requestOverlayPermission', permission)
//requestOverlayPermission granted/denied
```

## Request Phone Permissions (Android)
Asking user to allow phone permissions. You should ask this, if checkPermissions method returned "phonePermissionGranted": false.
Library using READ_PHONE_STATE and READ_CALL_LOG permissions.
```js
const permission = await CallerDetector.requestPhonePermission()
console.log('requestPhonePermission', permission)
//requestPhonePermission ["granted/denied", "granted/denied"]
```

## Request Service Permission (Android)
Asking user to set default call detect app. You should ask this, if checkPermissions method returned "servicePermissionGranted": false.
Default call detect app starts with Android 10, so this method will always return "granted" for lower OS versions.
```js
const permission = await CallerDetector.requestServicePermission()
console.log('requestServicePermission', permission)
//requestServicePermission granted/denied
```

## Set Params
Before use methods you should to set your ios extension, appgroup and android encryption params by this method. IOS params should be similar with CallDirectoryHandler.swift.
```js
CallerDetector.setParams({
    
     ios: {
        EXTENSION_ID: 'packagename.CallDirectoryExtension', // Similar with your Extension name
        DATA_GROUP: 'group.packagename', // Similar with your AppGroup
        DATA_KEY: 'callerListKey' // Similar with DATA_KEY from your CallDirectoryHandler.swift file
    },
    android: {
        dbPassword: "password", // any length password for all db encryption
        fieldsPassword: "16-length-pass!!" // 16-length password for encryption fields in db. Using 128 bit mask, so we nned to use 16 length password
    }
})
```

## Migrate Old DB to New Encrypted version
If u used previous version of module, u have unsafe db on android. So use this method for save data between DBs.
```js
CallerDetector.migrateOldDataBaseToEncrypted()
```

## Check Extension Status (iOS)
Checking one required permission for working library on iOS. User required to set active for call detect extension. Method returns its status.
```js
const status = await CallerDetector.getExtensionEnabledStatus()
console.log('getExtensionEnabledStatus', status)
//getExtensionEnabledStatus granted/denied
```

## Open Extension Settings (iOS)
Library provides opening settings method to set active call detect extension. Available for iOS 13.4+
```js
CallerDetector.openExtensionSettings()
```

## Set Caller List
Setting callerlist for detecting. For 10k nubmers its takes average 1 min for ios.
```js
    let callers = []
    callers.push({
        name: `Pavel Nikolaev`,
        appointment: `Developer`,
        city: `Riga`,
        iosRow: `Pavel Nikolaev, Developer, Riga`,
        number: 79771678179,
        isDeleted: false
    })

    callers.push({
        name: `Pavel Nikolaev`,
        appointment: `Developer`,
        city: `Riga`,
        iosRow: `Pavel Nikolaev, Developer, Riga`,
        number: 79013308179,
        isDeleted: true
    })

    let result = await CallerDetector.setCallerList(callers)
    console.log(result);
```
