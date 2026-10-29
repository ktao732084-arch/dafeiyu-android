package com.xiaoxiao.pet;
import java.util.regex.Pattern;

/** Conservative, local affordance detection. A mention or score report is not an invitation. */
final class GomokuInvite {
    static final Pattern NEGATIVE=Pattern.compile("不(?:想|要|用|能|会|打算)?(?:再|继续|一起|陪你|陪我)?(?:玩|下|开)|别(?:再)?(?:玩|下|开)|算了|改天|以后|下次|明天|等.*(?:忙完|有空)|暂时|稍后|晚点|拒绝");
    static final Pattern HISTORY=Pattern.compile("刚才|上次|昨天|之前|曾经|那时候|说过|提到|举例|例如|比如|假如|如果|为什么|怎么(?:玩|下)|规则|教程|战绩|谁赢|几局|水平|喜欢|爱好|擅长");
    static final Pattern PLAY=Pattern.compile("(?:想|要|来|陪我|陪你|一起|我们|咱们|不如|不然|愿意|能不能|要不要|可以|继续|开始|打开).{0,8}(?:下|玩|开).{0,5}(?:五子棋|一[盘局]|[盘局]棋)|(?:下|玩)(?:一?[盘局])?五子棋.{0,5}(?:吧|吗|么|怎么样|如何|好不好)|(?:开|来|再来)(?:一[盘局])五子棋");
    static String plain(String text){return text.replaceAll("“[^”]*”|「[^」]*」|『[^』]*』|\"[^\"]*\"|`[^`]*`","");}
    static boolean declined(String text){return NEGATIVE.matcher(plain(text).replace("要不要","要").replace("想不想","想").replace("能不能","能")).find();}
    static boolean wants(String text,boolean chessContext){
        String clean=plain(text);
        if(declined(clean))return false;
        for(String sentence:clean.split("[。！？!?；;\\n]")){
            if((sentence.contains("五子棋")||chessContext)&&!HISTORY.matcher(sentence).find()&&PLAY.matcher(sentence).find())return true;
        }
        return false;
    }
    static boolean offered(String user,String shown){
        if(shown.trim().isEmpty()||declined(shown)||declined(user))return false;
        return wants(shown,user.contains("五子棋"))||wants(user,false);
    }
}
