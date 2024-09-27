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

Lastly, IMPORTANT! check your `CallDirectoryHandler.swift`. It should define similar groupKey constant with your AppGroup.

### Android
customization caller information layout: `caller_info_dialog.xml`

## Basic Usage
implemented in your app `caller-id.service.ts` and `caller-id-local.service.ts` for android work profile mode

## API
* `checkPermissions`: Promise<any> - ios: returns "denied" or "granted", android: returns all required permissions based on caller id mode
* `requestIosPermissions`: Promise<any> - ios only (no native permission alert, will just open related settings on ios >= 13.4)
* `requestOverlayPermission`: Promise<string> - (*ONLY Android*) returns result of overlay request
* `requestPhonePermission`: Promise<string> - (*ONLY Android*) returns result of permissions request
* `requestServicePermission`: Promise<string> - (*ONLY Android*) returns result of service request
* `getCallerIdMode`: Promise<string> - (*ONLY Android*) returns 'defaultMode' | 'compatibilityMode' | 'workProfileMode'
* `setCallerList`: Promise<options: {
  type: 'default' | 'allAllowed' | 'allBlocked',
  items: { label: string; phonenumber: number, isRemoved: boolean, isBlocked: boolean }[],
  }>
