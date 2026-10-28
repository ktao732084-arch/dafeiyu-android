package com.xiaoxiao.pet;
public final class ApiEndpointTest {
 static int checks;
 static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
 static void reject(String input)throws Exception{boolean failed=false;try{new ApiEndpoint(input);}catch(java.io.IOException e){failed=true;}check(failed,"must reject "+input);}
 public static void main(String[] args)throws Exception{
  check("https://example.com/v1/chat/completions".equals(new ApiEndpoint("https://example.com/v1/").endpoint),"base path normalized");
  check("https://example.com/chat/completions".equals(new ApiEndpoint("https://example.com/chat/completions/").endpoint),"complete path not duplicated");
  check(new ApiEndpoint("https://EXAMPLE.com:443/v1").origin.equals(new ApiEndpoint("https://example.com").origin),"same origin normalized");
  check(!new ApiEndpoint("https://example.com:444").origin.equals(new ApiEndpoint("https://example.com").origin),"ports isolate credentials");
  reject("http://example.com/v1");reject("https://user:pass@example.com");reject("https://example.com/v1?key=abc");reject("https://example.com/#x");reject("file:///tmp/model");reject("https://example.com:99999");reject("https://bad host/v1");
  boolean badKey=false;try{ApiEndpoint.validateSecret("abc\nheader: yes");}catch(java.io.IOException e){badKey=true;}check(badKey,"control characters cannot become headers");
  boolean badModel=false;try{ApiEndpoint.validateModel("  ");}catch(java.io.IOException e){badModel=true;}check(badModel,"empty model rejected");
  System.out.println("PASS: "+checks+" API endpoint checks");
 }
}
