import Foundation
import CallKit

struct CallerItem: CustomStringConvertible {
  let label: String
  let phoneNumber: Int64
  let isRemoved: Bool
  let isBlocked: Bool
  
  
  var description: String {
      return "Caller(label: \(label), phoneNumber: \(phoneNumber))"
  }
}

class CallDirectoryHandler: CXCallDirectoryProvider {
  // !! Make sure we have the same config as in DetetCallerId.swift !!
  let groupKey = "group.de.provectus.SecureContacts22";
  let dataKey = "callerId"; // this have to match with values in DetectCallerId.swift

  // default, allAllowed, allBlocked, clearAll
  // allAllowed or allBlocked is set when vacation mode has been toggled
  var callerListType: String = "default";
  
  // We need this to keep all items regardless of current request chunk size
  var blockItems: [CallerItem] = []
  var identifyItems: [CallerItem] = []

  override func beginRequest(with context: CXCallDirectoryExtensionContext) {
      context.delegate = self
      NSLog("CallDirectoryHandler: beginRequest")

      // Access the shared container using your group key.
      if let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupKey) {
          let fileURL = containerURL.appendingPathComponent("callerList.json")

          do {
            // Use the new parseCallerList function that loads data with memory mapping.
            let callerList = parseCallerList(fromFileURL: fileURL)

            // Log the total count of callerList entries.
            NSLog("CallDirectoryHandler: Total callerList count: \(callerList.count)")
            NSLog("CallDirectoryHandler: callerListType: \(callerListType)")
            NSLog("CallDirectoryHandler: isIncremental: \(context.isIncremental)")

            // type block, unblock, identify, default, clearAll

            switch callerListType {
              case "clearAll":
                  NSLog("CallDirectoryHandler: remove all blocking and identification entries")
                  blockItems = []
                  identifyItems = []
                  context.removeAllIdentificationEntries()
                  context.removeAllBlockingEntries()

              case "block":
                block(callerList, to: context)

              case "unblock":
                unblock(callerList, to: context)

              case "identify":
                identify(callerList, to: context)

              case "default":
                identify(callerList, to: context)

              default:
                  // Optionally handle any unexpected callerListType values
                  NSLog("CallDirectoryHandler: unrecognized callerListType \(callerListType)")
            }
          } catch {
              NSLog("CallDirectoryHandler: Error reading file - \(error)")
          }
      } else {
          NSLog("CallDirectoryHandler: Unable to access shared container with group \(groupKey)")
      }

      context.completeRequest()
  }

  private func block(_ list: [CallerItem], to context: CXCallDirectoryExtensionContext) {
    NSLog("[CallDirectoryHandler] block: isIncremental: \(context.isIncremental)")
    
    if (context.isIncremental) {
      var seen = Set(blockItems.map { $0.phoneNumber })

      for item in list {
          if !seen.contains(item.phoneNumber) {
            blockItems.append(item)
            seen.insert(item.phoneNumber)
          }
      }
    } else {
      blockItems = list
    }
    
    for item in blockItems {
        NSLog("add blocking entry: \(item.label) \(item.phoneNumber)")
        context.addBlockingEntry(withNextSequentialPhoneNumber: item.phoneNumber)
    }
  }
  
  private func unblock(_ list: [CallerItem], to context: CXCallDirectoryExtensionContext) {
    NSLog("[CallDirectoryHandler] unblock")
    for item in list {
      NSLog("remove blocking entry: \(item.label) \(item.phoneNumber)")
      context.removeBlockingEntry(withPhoneNumber: item.phoneNumber)
      blockItems.removeAll { blockedItem in
        return item.phoneNumber == blockedItem.phoneNumber
      }
    }
  }

  // called when context is not incremental yet / empty
  private func identify(_ list: [CallerItem], to context: CXCallDirectoryExtensionContext) {
    NSLog("[CallDirectoryHandler] identify: isIncremental: \(context.isIncremental)")
    
    if (context.isIncremental) {
      var seen = Set(identifyItems.map { $0.phoneNumber })

      for item in list {
          if !seen.contains(item.phoneNumber) {
              identifyItems.append(item)
              seen.insert(item.phoneNumber)
          }
      }
    } else {
      identifyItems = list
    }
    
    for item in identifyItems {
      if item.isRemoved { continue }
    
      NSLog("identify: \(item.label) \(item.phoneNumber)")
      context.addIdentificationEntry(withNextSequentialPhoneNumber: item.phoneNumber, label: item.label)
    }
  }

  private func parseCallerList(fromFileURL fileURL: URL) -> [CallerItem] {
      NSLog("parseCallerList")
      do {
          // Use memory mapping to load the file data without loading the entire file into memory at once.
          let jsonData = try Data(contentsOf: fileURL, options: .mappedIfSafe)

          // Deserialize the JSON data.
          if let jsonObject = try JSONSerialization.jsonObject(with: jsonData, options: []) as? [String: Any] {

              // Update the callerListType if available.
              if let type = jsonObject["type"] as? String {
                  callerListType = type
              }

              // Process the items array.
              if let items = jsonObject["items"] as? [[String: Any]] {
                  return items.compactMap { dict in
                      guard let label = dict["label"] as? String,
                            let phoneNumber = dict["phonenumber"] as? Int64,
                            let isRemoved = dict["isRemoved"] as? Bool,
                            let isBlocked = dict["isBlocked"] as? Bool else {
                          return nil
                      }
                      return CallerItem(label: label, phoneNumber: phoneNumber, isRemoved: isRemoved, isBlocked: isBlocked)
                  }
              } else {
                  NSLog("Failed to cast JSON data to array")
                  return []
              }
          } else {
              NSLog("Failed to cast JSON data to object")
              return []
          }
      } catch let error {
          NSLog("Error deserializing JSON: \(error.localizedDescription)")
          return []
      }
  }

}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {

  func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
    NSLog("requestFailed: "+error.localizedDescription)
    extensionContext.removeAllIdentificationEntries()
    extensionContext.removeAllBlockingEntries()
  }

  func requestDidComplete(for extensionContext: CXCallDirectoryExtensionContext) {
    NSLog("requestDidComplete")
  }
}
