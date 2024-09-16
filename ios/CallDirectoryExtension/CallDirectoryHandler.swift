
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

    if let userDefaults = UserDefaults(suiteName: groupKey) {
      if let jsonString = userDefaults.string(forKey: dataKey) {
        let callerList = parseCallerList(from: jsonString)

        if context.isIncremental {
          // on logout
          if (callerListType == "clearAll") {
            NSLog("CallDirectoryHandler: remove all blocking and identification entries")
            context.removeAllIdentificationEntries()
            context.removeAllBlockingEntries()
          } else if (callerListType != "default") {
            addAllBlockedOrIdentificationPhoneNumbers(callerList, to: context)
          } else {
            addOrRemoveIncrementalPhoneNumbers(callerList, to: context)
          }
        } else {
          addAllPhoneNumbers(callerList, to: context)
        }
      } else {
        NSLog("CallDirectoryHandler: no items")
      }
    } else {
      NSLog("CallDirectoryHandler: UserDefaults group empty or not found")
    }

    context.completeRequest()
  }

  // vacation mode toggle -> remove all blocked/allowed and add all allowed/blocked afterwards
  private func addAllBlockedOrIdentificationPhoneNumbers(_ list: [CallerItem], to context: CXCallDirectoryExtensionContext) {
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
    NSLog("Adding \(list.count) phone numbers")

    for item in list {
      if (item.isRemoved) {
        continue;
      }

      if (item.isBlocked) {
        NSLog("add blocking entry: \(item.label) \(item.phoneNumber)")
        context.addBlockingEntry(withNextSequentialPhoneNumber: item.phoneNumber)
      } else {
        NSLog("add identification entry: \(item.label) \(item.phoneNumber)")
        context.addIdentificationEntry(withNextSequentialPhoneNumber: item.phoneNumber, label: item.label)
      }
    }
  }

  private func parseCallerList(from jsonString: String) -> [CallerItem] {
    guard let jsonData = jsonString.data(using: .utf8) else {
      NSLog("Failed to convert JSON string")

      return []
    }

    do {
      if let jsonObject = try JSONSerialization.jsonObject(with: jsonData, options: []) as? [String: Any] {

        if let type = jsonObject["type"] as? String {
          callerListType = type;
        }

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
  }
}
