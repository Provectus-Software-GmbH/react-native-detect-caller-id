import Foundation
import CallKit

struct CallerItem {
  let label: String
  let phoneNumber: Int64
  let isRemoved: Bool
  let isBlocked: Bool
}

class CallDirectoryHandler: CXCallDirectoryProvider {
  // !! Make sure we have the same config as in DetetCallerId.swift !!
  let groupKey = "group.de.provectus.SecureContacts22";
  let dataKey = "callerId"; // this have to match with values in DetectCallerId.swift

  // default, allAllowed, allBlocked, clearAll
  // allAllowed or allBlocked is set when vacation mode has been toggled
  var callerListType: String = "default";

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

              // Continue with your logic as before.
              if context.isIncremental {
                switch callerListType {
                  case "clearAll":
                      NSLog("CallDirectoryHandler: remove all blocking and identification entries")
                      context.removeAllIdentificationEntries()
                      context.removeAllBlockingEntries()

                  case "allBlocked":
                      addAllPhoneNumbers(callerList, to: context)

                  case "allAllowed":
                      addAllBlockedOrIdentificationPhoneNumbers(callerList, to: context)

                  case "default":
                      addOrRemoveIncrementalPhoneNumbers(callerList, to: context)

                  default:
                      // Optionally handle any unexpected callerListType values
                      NSLog("CallDirectoryHandler: unrecognized callerListType \(callerListType)")
                }
              } else {
                  addAllPhoneNumbers(callerList, to: context)
              }
          } catch {
              NSLog("CallDirectoryHandler: Error reading file - \(error)")
          }
      } else {
          NSLog("CallDirectoryHandler: Unable to access shared container with group \(groupKey)")
      }

      context.completeRequest()
  }

  // vacation mode toggle -> remove all blocked/allowed and add all allowed/blocked afterwards
  private func addAllBlockedOrIdentificationPhoneNumbers(_ list: [CallerItem], to context: CXCallDirectoryExtensionContext) {
      NSLog("addAllBlockedOrIdentificationPhoneNumbers")
    if (callerListType == "allAllowed") {
      NSLog("CallDirectoryHandler: remove all blocking entries")
      context.removeAllBlockingEntries()
    } else {
      NSLog("CallDirectoryHandler: remove all identification entries")
      context.removeAllIdentificationEntries()
    }

    addAllPhoneNumbers(list, to: context)
  }

  private func addOrRemoveIncrementalPhoneNumbers(_ list: [CallerItem], to context: CXCallDirectoryExtensionContext) {
    NSLog("addOrRemoveIncrementalPhoneNumbers")
    for item in list {
      if item.isBlocked {
        if item.isRemoved {
          NSLog("inc remove blocking entry: \(item.label) \(item.phoneNumber)")
          context.removeBlockingEntry(withPhoneNumber: item.phoneNumber)
        } else {
          NSLog("inc add blocking entry: \(item.label) \(item.phoneNumber)")
          context.addBlockingEntry(withNextSequentialPhoneNumber: item.phoneNumber)
        }
      } else {
        if item.isRemoved {
          NSLog("inc remove identification entry: \(item.label) \(item.phoneNumber)")
          context.removeIdentificationEntry(withPhoneNumber: item.phoneNumber)
        } else {
          NSLog("inc add identification entry: \(item.label) \(item.phoneNumber)")
          context.addIdentificationEntry(withNextSequentialPhoneNumber: item.phoneNumber, label: item.label)
        }
      }
    }
  }

  // called when context is not incremental yet / empty
    private func addAllPhoneNumbers(_ list: [CallerItem], to context: CXCallDirectoryExtensionContext) {
        NSLog("addAllPhoneNumbers")
        let batchSize = 5000 // Set a reasonable batch size
        for batchStart in stride(from: 0, to: list.count, by: batchSize) {
            let batch = list[batchStart..<min(batchStart + batchSize, list.count)]

            for item in batch {
                if item.isRemoved { continue }
                if item.isBlocked {
                    context.addBlockingEntry(withNextSequentialPhoneNumber: item.phoneNumber)
                } else {
                    context.addIdentificationEntry(withNextSequentialPhoneNumber: item.phoneNumber, label: item.label)
                }
            }

            NSLog("Processed batch \(batchStart / batchSize + 1) of \(list.count / batchSize + 1)")
            // Check if time or memory limits are close and stop if necessary
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
