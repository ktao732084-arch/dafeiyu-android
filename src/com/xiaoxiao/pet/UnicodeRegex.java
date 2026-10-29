package com.xiaoxiao.pet;

import java.util.regex.Pattern;

/** Android already uses Unicode classes but rejects the desktop JVM's explicit flag. */
final class UnicodeRegex {
    private static final int UNICODE_FLAG=unicodeFlag();
    private static int unicodeFlag(){
        try{Pattern.compile("",Pattern.UNICODE_CHARACTER_CLASS);return Pattern.UNICODE_CHARACTER_CLASS;}
        catch(IllegalArgumentException unsupported){return 0;}
    }
    static Pattern compile(String regex,int flags){
        return Pattern.compile(regex,(flags&~Pattern.UNICODE_CHARACTER_CLASS)|UNICODE_FLAG);
    }
}
