//
//  CallDirectoryHandler.swift
//  CallDirectoryExtension
//
//  use this file to replace default CallDirectoryHandler in ios project
//  + update DATA_KEY and DATA_GROUP

import Foundation
import CallKit

let DATA_KEY = "callerListKey2";
let DATA_GROUP = "group.ru.nasvyazi";

struct CallerInfo : Decodable, Encodable {
    let name: String
    let number: Int
    let isDeleted: Bool
}

struct CallerTransferData: Decodable, Encodable {
    let data: [String]
    let action: Int
    let wasDbDropped: Bool
}

enum ActionType : Int {
  case parseCallers = 1
  case dropAllDb = 2
}


class CallDirectoryHandler: CXCallDirectoryProvider {

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self

        if context.isIncremental {
            incrementalProcessing(to: context)
        }

        context.completeRequest()
    }


  private func getTransferData() -> CallerTransferData {
    let transferData = (UserDefaults(suiteName: DATA_GROUP)?.string(forKey: DATA_KEY) ?? nil)
//
//      (UserDefaults(suiteName: DATA_GROUP))?.set("", forKey: DATA_KEY);

      let jsonData = transferData!.data(using: .utf8)!
      let data: CallerTransferData = try! JSONDecoder().decode(CallerTransferData.self, from: jsonData)

      return data
  }


  private func getTransferAction(transferData: CallerTransferData) -> Int {
      return transferData.action
  }


  private func getCallerList(transferData: CallerTransferData) -> [CallerInfo]{
      var callerList: [CallerInfo] = [CallerInfo]();

      let receivedList = transferData.data;

      for receivedItem in receivedList {
          let jsonData = receivedItem.data(using: .utf8)!
          let callerItem: CallerInfo = try! JSONDecoder().decode(CallerInfo.self, from: jsonData)
          callerList.append(callerItem);
      }
      return callerList.sorted(by: { $0.number < $1.number });
  }

    private func incrementalProcessing(to context: CXCallDirectoryExtensionContext) {
        let data = self.getTransferData();
        let action = self.getTransferAction(transferData: data)

        if (action == ActionType.parseCallers.rawValue){
            let callerList = self.getCallerList(transferData: data);
            for caller in callerList {
                context.removeIdentificationEntry(withPhoneNumber: CXCallDirectoryPhoneNumber(caller.number));
                if (!caller.isDeleted){
                  context.addIdentificationEntry(withNextSequentialPhoneNumber: CXCallDirectoryPhoneNumber(caller.number), label: caller.name);
                }
            }
            print("add new to DB called")
        } else if (action == ActionType.dropAllDb.rawValue){
            context.removeAllIdentificationEntries()
            print("dropDB called")
        }
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {

    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        print("requestFailed: "+error.localizedDescription)

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
        case 7
            errorMessage = "Could not load extension. The extension is currently loading."
        case 8:
            errorMessage = "Unexpected incremental removal."
        default:
            errorMessage = "Extension could not be loaded for an unknown reason."
        }

        print("requestFailed: \(errorMessage)")
    }
}
