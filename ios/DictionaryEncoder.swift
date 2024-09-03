struct DictionaryEncoder {
    static func encode<T>(_ value: T) throws -> [String: Any] where T: Encodable {
        let jsonData = try JSONEncoder().encode(value)
        return try JSONSerialization.jsonObject(with: jsonData) as? [String: Any] ?? [:]
    }
    
    static func convertSetCallerListResult(obj: SetCallerSequentiallyResult) -> [String:Any] {
        
        var map = [String:Any]()
        
        let result : String = obj.result;
        let errors : [String] = obj.errors;
        
        map.updateValue(result, forKey: "result");
        map.updateValue(errors, forKey: "errors");
        
        return map;
    }
}
