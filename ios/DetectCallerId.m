#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(DetectCallerId, NSObject)

RCT_EXTERN_METHOD(setCallerList: (NSArray*) callerList
                  withExtensionId: (NSString *)EXTENSION_ID
                  withDataGroup: (NSString *)DATA_GROUP
                  withDataKey: (NSString *)DATA_KEY
                  withResolver:(RCTPromiseResolveBlock)resolve
                  withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getExtensionEnabledStatus:
                  (NSString *)EXTENSION_ID
                  withResolver: (RCTPromiseResolveBlock)resolve
                  withRejecter: (RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(getWasDbDrobbed:
                  (RCTPromiseResolveBlock)resolve
                  withRejecter:(RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(openExtensionSettings: (RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)


RCT_EXTERN_METHOD(clearCallerList: (NSString *) EXTENSION_ID
                  withResolver:(RCTPromiseResolveBlock)resolve
                  withRejecter:(RCTPromiseRejectBlock)reject)

@end
