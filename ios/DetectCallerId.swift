import CallKit


@objc(DetectCallerId)
class DetectCallerId: NSObject {

    func saveData(transferData: CallerTransferData){
        do {
            let sharedDefaults = UserDefaults(suiteName: DATA_GROUP);

            let encoded = try JSONEncoder().encode(transferData);
            let jsonString = String(data: encoded,
                                    encoding: .utf8)

            sharedDefaults?.set(jsonString, forKey: DATA_KEY);
        }catch {
            print(error.localizedDescription)
        }
    }

    func loadData() -> CallerTransferData {
        let transferData = (UserDefaults(suiteName: DATA_GROUP)?.string(forKey: DATA_KEY) ?? nil)

        if (transferData != nil ){
            let jsonData = transferData!.data(using: .utf8)!
            let data: CallerTransferData = try! JSONDecoder().decode(CallerTransferData.self, from: jsonData)

            return data
        } else {
            let data = CallerTransferData(data: [], action: ActionType.parseCallers.rawValue, wasDbDropped: false)

            return data;
        }
    }

    @objc
    func setCallerList(
        _ callerList: Array<Any>,
        withExtensionId EXTENSION_ID: String,
        withDataGroup DATA_GROUP: String,
        withDataKey DATA_KEY: String,
        withResolver resolve: @escaping RCTPromiseResolveBlock,
        withRejecter reject:  @escaping RCTPromiseRejectBlock
    ) -> Void {

        do {

            let transferData = CallerTransferData(data: callerList as! [String], action: ActionType.parseCallers.rawValue, wasDbDropped: false)

            saveData(transferData: transferData)

            CXCallDirectoryManager.sharedInstance.reloadExtension(withIdentifier: EXTENSION_ID, completionHandler: {error -> Void in


                if ((error) != nil){
                    reject("CALLER_ID", error!.localizedDescription, error);
                } else {
                    resolve("true");
                }

            })
        } catch {
            reject("CALLER_ID", error.localizedDescription, error);
        }
    }


    @objc
    func getExtensionEnabledStatus(
        _ EXTENSION_ID: String,
        withResolver resolve: @escaping RCTPromiseResolveBlock,
        withRejecter reject: @escaping RCTPromiseRejectBlock
    ) -> Void {

        print("[native] getExtensionEnabledStatus");
        CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(withIdentifier: EXTENSION_ID, completionHandler: { enabledStatus, error -> Void in
            if (enabledStatus.rawValue == 0){
                reject("CALLER_ID", "getExtensionEnabledStatus error - Failed to get extension status: " + (error?.localizedDescription ?? ""), error);
            } else if (enabledStatus.rawValue == 1){
                resolve("denied");
            }else if (enabledStatus.rawValue == 2){
                resolve("granted");
            }
        })
    }

    @objc
    func openExtensionSettings(_ resolve: @escaping RCTPromiseResolveBlock, withRejecter reject: @escaping RCTPromiseRejectBlock) -> Void {

        if #available(iOS 13.4, *) {
            CXCallDirectoryManager.sharedInstance.openSettings(completionHandler: { error -> Void in

                if ((error) != nil){
                    reject("CALLER_ID", "openExtensionSettings error - failed to open settings" + error!.localizedDescription, error);
                } else {
                    resolve("");
                }
            })
        } else {
            reject("CALLER_ID", "openExtensionSettings error - openExtensionSettings allowed since iOS 13.4", "openExtensionSettings error - openExtensionSettings allowed since iOS 13.4" as! Error);
        }
    }

    @objc
    func getWasDbDrobbed(_ resolve: @escaping RCTPromiseResolveBlock, withRejecter reject: @escaping RCTPromiseRejectBlock) -> Void {

        let transferDataPrev = loadData()

        let transferDataNew = CallerTransferData(data: transferDataPrev.data, action: transferDataPrev.action, wasDbDropped: false)

        saveData(transferData: transferDataNew)

        resolve(transferDataPrev.wasDbDropped)
    }

    @objc
    func clearCallerList(_ EXTENSION_ID: String,
          withResolver resolve: @escaping RCTPromiseResolveBlock,
          withRejecter reject:  @escaping RCTPromiseRejectBlock) -> Void {

        do {

            let transferData = CallerTransferData(data: [], action: ActionType.dropAllDb.rawValue, wasDbDropped: false)

            saveData(transferData: transferData)


            CXCallDirectoryManager.sharedInstance.reloadExtension(withIdentifier: EXTENSION_ID, completionHandler: {error -> Void in


                if ((error) != nil){
                    reject("CALLER_ID", error!.localizedDescription, error);
                } else {
                    resolve("true");
                }

            })

        } catch {
            reject("CALLER_ID", error.localizedDescription, error);
        }
    }
}
