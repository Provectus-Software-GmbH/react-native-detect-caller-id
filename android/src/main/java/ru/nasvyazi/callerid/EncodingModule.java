package ru.nasvyazi.callerid;

public class EncodingModule {
    public static String EncodeDbPassword(String password){

        if (password == null){
            return null;
        }

        String result = "";

        for(int i = 0; i<password.length(); i++){
            if (i%2 == 1){
                result = result + password.charAt(i) + password.charAt(i-1);
            }
        }

        if (password.length()%2 == 1){
            result = result + password.charAt(password.length()-1);
        }

        return result;
    }

    public static String DecodeDbPassword(String password){
        return EncodeDbPassword(password);
    }

    public static String EncodeFieldsPassword(String password){

        if (password == null){
            return null;
        }

        String result = "";

        for(int i = 0; i<password.length(); i++){
            if (i%4 == 3){
                result = result + password.charAt(i-1) + password.charAt(i) + password.charAt(i-3) + password.charAt(i-2);
            }
        }


        return result;
    }

    public static String DecodeFieldsPassword(String password){
        return EncodeFieldsPassword(password);
    }
}
