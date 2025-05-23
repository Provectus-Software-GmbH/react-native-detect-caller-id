import CallKit



@objc(DetectCallerId)
class DetectCallerId: NSObject {
    let groupKey = "group.de.provectus.SecureContacts22";
    let dataKey = "callerId";
    let identifier = "de.provectus.SecureContacts22.CallDirectoryExtension";

    @objc
    func setCallerList(
        _ options: String,
        withResolver resolve: @escaping RCTPromiseResolveBlock,
        withRejecter reject:  @escaping RCTPromiseRejectBlock
    ) -> Void {
        // Get the shared container URL using your group key.
        if let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupKey) {
            // Choose a file name for your caller list.
            let fileURL = containerURL.appendingPathComponent("callerList.json")
            
            do {
                // Write the JSON string to the file.
                try options.write(to: fileURL, atomically: true, encoding: .utf8)
                
                // Call your reload extension function after successfully writing the file.
                reloadExtension(resolve, withRejecter: reject)
            } catch {
                // Reject the promise if file writing fails.
                reject("FILE_WRITE_ERROR", "Unable to write caller list to file", error)
            }
        } else {
            reject("GROUP_ERROR", "Unable to access shared container for group \(groupKey)", nil)
        }
    }

    @objc
    func clearCallerList(
        _ resolve: @escaping RCTPromiseResolveBlock,
        withRejecter reject:  @escaping RCTPromiseRejectBlock) -> Void {
            let options = """
            {
                "type": "clearAll",
                "items": []
            }
            """

            NSLog("DetectCallerId: clearCallerList")

            if let userDefaults = UserDefaults(suiteName: groupKey) {
              userDefaults.set(options, forKey: dataKey)

              // our CallDirectoryHandler will parse json stringified callerList
              reloadExtension(resolve, withRejecter: reject);
            }
    }


    @objc
    func checkPermissions(
        _ resolve: @escaping RCTPromiseResolveBlock,
        withRejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
            NSLog("checkPermissions")
        CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(withIdentifier: identifier, completionHandler: { enabledStatus, error -> Void in
            if (enabledStatus.rawValue == 0){
                reject("DetectCallerId", "getExtensionEnabledStatus error - Failed to get extension status: " + (error?.localizedDescription ?? ""), error);
            } else if (enabledStatus.rawValue == 1){
                resolve("denied");
            }else if (enabledStatus.rawValue == 2){
                resolve("granted");
            }
        })
    }

    @objc
    func requestPermissions(
        _ resolve: @escaping RCTPromiseResolveBlock,
        withRejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
            NSLog("requestPermissions")

        if #available(iOS 13.4, *) {
            CXCallDirectoryManager.sharedInstance.openSettings(completionHandler: { error -> Void in

                if ((error) != nil){
                    reject("DetectCallerId", "openExtensionSettings error - failed to open settings" + error!.localizedDescription, error);
                } else {
                    resolve("");
                }
            })
        } else {
            reject("DetectCallerId", "openExtensionSettings error - openExtensionSettings allowed since iOS 13.4", "openExtensionSettings error - openExtensionSettings allowed since iOS 13.4" as! Error);
        }
    }

    @objc
    func reloadExtension(
        _ resolve: @escaping RCTPromiseResolveBlock,
        withRejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
            NSLog("reloadExtension")
        do {
            CXCallDirectoryManager.sharedInstance.reloadExtension(withIdentifier: identifier, completionHandler: {error -> Void in
                self.clearStore();

                if ((error) != nil){
                    self.reloadFailed(withError: error!, withRejecter: reject);
                } else {
                    resolve("CXCallDirectoryManager process finished");
                }

            })

        } catch {
            clearStore();

            reject("DetectCallerId", error.localizedDescription, error);
        }
    }

    @objc
    func simulateIncomingCall(
    _ phoneNumber: Int64,
    withResolver resolve: @escaping RCTPromiseResolveBlock,
    withRejecter reject:  @escaping RCTPromiseRejectBlock) -> Void {

        NSLog("Simulate incoming call: \(phoneNumber)")

        let uuid = UUID()
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .phoneNumber, value: "\(phoneNumber)")
        update.hasVideo = false
        let pconfig = CXProviderConfiguration(localizedName: "SecureContacts Call")
        let provider = CXProvider(configuration: pconfig)

        provider.reportNewIncomingCall(with: uuid, update: update) { error in
            if let error = error as NSError? {

                var errorMessage: String

                switch error.code {
                case CXErrorCodeIncomingCallError.callUUIDAlreadyExists.rawValue:
                    // Handle duplicate call ID
                    errorMessage = "Handle duplicate call ID"
                case CXErrorCodeIncomingCallError.filteredByBlockList.rawValue:
                    // Handle call from blocked user
                    errorMessage = "Handle call from blocked user"
                case CXErrorCodeIncomingCallError.filteredByDoNotDisturb.rawValue:
                    errorMessage = "Handle call while in do-not-disturb mode"
                default:
                    // Handle unknown error
                    errorMessage = "Handle unknown error"
                }

                reject("simulateIncomingCall", errorMessage, error);
            } else {
              resolve("simulateIncomingCall succeeded");
            }
        }
    }

    private func clearStore() -> Void {
        NSLog("clearStore")
      if let userDefaults = UserDefaults(suiteName: groupKey) {
          userDefaults.removeObject(forKey: dataKey)
          NSLog("clean up temp store")
      }
    }

    private func reloadFailed(
        withError error: Error,
        withRejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
        NSLog("CallDirectoryHandler Error: " + error.localizedDescription)

        let nsError = error as NSError
        var errorMessage: String

        switch nsError.code {
        case 0:
            errorMessage = "Extension could not be loaded for an unknown reason."
        case 1:
            errorMessage = "Could not load extension. Extension not found."
        case 2:
            errorMessage = "Could not load extension. Extension was interrupted while loading."
        case 3:
            errorMessage = "Could not load extension. Call entries are out of order."
        case 4:
            errorMessage = "Could not load extension. Duplicate entries."
        case 5:
            errorMessage = "Could not load extension. Maximum entries exceeded."
        case 6:
            errorMessage = "Extension not enabled in Settings."
        case 7:
            errorMessage = "Could not load extension. The extension is currently loading."
        case 8:
            errorMessage = "Unexpected incremental removal."
        default:
            errorMessage = error.localizedDescription
        }

        NSLog("Error: \(errorMessage) \(nsError)")

        reject("DetectCallerId", errorMessage, error);
    }
}
