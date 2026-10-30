package com.xiaoxiao.pet;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Older complete turns, independent of the bounded model working state. One atomic file per turn. */
final class ChatArchive {
    final File dir;
    // The folder is listed once; afterwards put/forget keep these in step, so paging and the deleted check do not
    // rescan thousands of files on the main thread. Keys are file names without ".json"; tombstones are id hashes.
    private TreeSet<String> keys;private Set<String> tombstones;
    ChatArchive(File parent)throws IOException{dir=new File(parent,"chat-history");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建聊天记录目录");}
    static String hash(String value)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    static String id(JSONObject turn)throws Exception{return turn.optString("id","").isEmpty()?"legacy-"+hash(turn.toString()):turn.getString("id");}
    static String key(JSONObject turn)throws Exception{return String.format(Locale.ROOT,"%019d",Math.max(0,turn.optLong("at")))+"-"+hash(id(turn));}
    private synchronized TreeSet<String> keys()throws IOException{if(keys==null){TreeSet<String> found=new TreeSet<>();for(File f:files())found.add(f.getName().substring(0,f.getName().length()-5));keys=found;}return keys;}
    private synchronized Set<String> tombstones()throws IOException{
        if(tombstones==null){String[] names=dir.list((d,n)->n.matches("deleted-[0-9a-f]{64}"));if(names==null)throw new IOException("无法读取聊天记录");Set<String> found=new HashSet<>();for(String n:names)found.add(n.substring(8));tombstones=found;}
        return tombstones;
    }
    File file(String key){return new File(dir,key+".json");}
    /** Archived turn files, oldest first, from the cached listing. */
    synchronized List<File> archived()throws IOException{List<File> out=new ArrayList<>();for(String k:keys())out.add(file(k));return out;}
    synchronized boolean deleted(String id)throws Exception{return tombstones().contains(hash(id));}
    synchronized void put(JSONObject turn)throws Exception{if(deleted(id(turn)))return;String k=key(turn);write(file(k),turn.toString().getBytes(StandardCharsets.UTF_8));keys().add(k);}
    static void write(File file,byte[] bytes)throws Exception{File tmp=new File(file+".tmp");try(FileOutputStream stream=new FileOutputStream(tmp)){stream.write(bytes);stream.getFD().sync();}try{Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}}
    JSONObject read(File file)throws Exception{if(file.length()>16*1024*1024)throw new IOException("聊天记录文件过大");return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}
    File[] files()throws IOException{File[] files=dir.listFiles((d,n)->n.matches("[0-9]{19}-[0-9a-f]{64}\\.json"));if(files==null)throw new IOException("无法读取聊天记录");return files;}
    void evicted(JSONArray previous,JSONArray next)throws Exception{
        Set<String> kept=new HashSet<>();for(int i=0;i<next.length();i++)kept.add(id(next.getJSONObject(i)));
        for(int i=0;i<previous.length();i++){JSONObject t=previous.getJSONObject(i);if(!kept.contains(id(t)))put(t);}
    }
    JSONArray page(JSONArray recent,String before,int limit)throws Exception{return page(recent,before,limit,Long.MIN_VALUE,Long.MAX_VALUE);}
    synchronized JSONArray page(JSONArray recent,String before,int limit,long fromInclusive,long toExclusive)throws Exception{
        return page(recent,before,limit,fromInclusive,toExclusive,false);
    }
    /** UI pages omit physical reactions before counting the page, without deleting their memory evidence. */
    synchronized JSONArray page(JSONArray recent,String before,int limit,long fromInclusive,long toExclusive,boolean conversationOnly)throws Exception{
        TreeMap<String,Object> index=new TreeMap<>();for(String k:keys())index.put(k,file(k));
        for(int i=0;i<recent.length();i++){JSONObject t=recent.getJSONObject(i);index.put(key(t),t);}
        List<JSONObject> selected=new ArrayList<>();
        for(Map.Entry<String,Object> entry:index.descendingMap().entrySet()){
            if(before!=null&&entry.getKey().compareTo(before)>=0)continue;
            long at=Long.parseLong(entry.getKey().substring(0,19));if(at<fromInclusive||at>=toExclusive)continue;
            // A file removed behind our back is dropped from the listing rather than failing the page.
            if(entry.getValue() instanceof File&&!((File)entry.getValue()).exists()){keys.remove(entry.getKey());continue;}
            JSONObject t=entry.getValue() instanceof File?read((File)entry.getValue()):new JSONObject(entry.getValue().toString());
            if(deleted(id(t))||conversationOnly&&t.has("interaction"))continue;t.put("id",id(t)).put("pageKey",entry.getKey());selected.add(t);if(selected.size()>=limit)break;
        }
        Collections.reverse(selected);return new JSONArray(selected);
    }
    synchronized HistoryRecall.Scan search(JSONArray recent,HistoryRecall.Plan plan,AgentTransport.Token token)throws Exception{
        token.check();TreeMap<String,Object> index=new TreeMap<>();for(String k:keys())index.put(k,file(k));
        for(int i=0;i<recent.length();i++){JSONObject t=recent.getJSONObject(i);index.put(key(t),t);}
        List<Map.Entry<String,Object>> matches=new ArrayList<>();
        for(Map.Entry<String,Object> entry:index.entrySet()){
            token.check();long at=Long.parseLong(entry.getKey().substring(0,19));
            if(at>=plan.from&&at<plan.to&&!tombstones().contains(entry.getKey().substring(20)))matches.add(entry);
        }
        HistoryRecall.Scan scan=new HistoryRecall.Scan();scan.total=matches.size();int count=Math.min(HistoryRecall.MAX_SCAN,matches.size());scan.limited=count<matches.size();long bytes=0;
        for(int i=0;i<count;i++){
            token.check();Object value=matches.get(count==1?0:(int)((long)i*(matches.size()-1)/(count-1))).getValue();scan.examined++;
            long size=value instanceof File?((File)value).length():value.toString().getBytes(StandardCharsets.UTF_8).length;
            if(size>HistoryRecall.MAX_SCAN_BYTES-bytes){scan.limited=true;continue;}bytes+=size;
            try{JSONObject t=value instanceof File?read((File)value):(JSONObject)value;if(!deleted(id(t)))scan.rows.add(t);}catch(IOException|JSONException invalid){scan.unreadable++;}
        }
        token.check();return scan;
    }
    synchronized void forget(Set<String> ids)throws Exception{
        Set<String> hashes=new HashSet<>();for(String id:ids){String h=hash(id);hashes.add(h);write(new File(dir,"deleted-"+h),new byte[0]);tombstones().add(h);}
        // Tombstones are written first: a crash or an old main-file backup cannot resurrect these turns.
        for(Iterator<String> it=keys().iterator();it.hasNext();){String k=it.next();if(hashes.contains(k.substring(20))){Files.deleteIfExists(file(k).toPath());it.remove();}}
    }
    synchronized void clear(JSONArray recent)throws Exception{
        Set<String> ids=new HashSet<>();for(int i=0;i<recent.length();i++)ids.add(id(recent.getJSONObject(i)));
        for(String k:new ArrayList<>(keys()))ids.add(id(read(file(k))));forget(ids);
    }
    static void markShown(JSONObject t,int count)throws Exception{
        int previous=t.optInt("displayed"),next=Math.min(count,ReplySegments.count(t));if(next<=previous)return;
        long now=System.currentTimeMillis();JSONArray times=t.optJSONArray("segmentDisplayedAt");if(times==null)times=new JSONArray();
        while(times.length()<next)times.put(now);t.put("segmentDisplayedAt",times).put("displayed",next).put("lastDisplayedAt",now).put("displayTimeZone",java.time.ZoneId.systemDefault().getId());
        if(!t.has("displayedAt"))t.put("displayedAt",now);
    }
    synchronized JSONObject shown(String id,int count)throws Exception{String suffix="-"+hash(id);for(String k:keys())if(k.endsWith(suffix)){JSONObject t=read(file(k));markShown(t,count);put(t);return t;}return null;}
}
