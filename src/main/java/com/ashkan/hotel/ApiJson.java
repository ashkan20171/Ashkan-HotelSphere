package com.ashkan.hotel;

import java.math.BigDecimal;
import java.util.*;

/** Dependency-free JSON reader/writer for the small REST payloads used by the services. */
final class ApiJson {
    private ApiJson() {}
    static Map<String,Object> object(String json) {
        Object value=new Parser(json).parse();
        if(!(value instanceof Map<?,?> map)) throw new IllegalArgumentException("A JSON object is required");
        Map<String,Object> out=new LinkedHashMap<>(); map.forEach((k,v)->out.put(String.valueOf(k),v)); return out;
    }
    static String write(Object value) { StringBuilder b=new StringBuilder(); append(b,value); return b.toString(); }
    private static void append(StringBuilder b,Object v) {
        if(v==null){b.append("null");return;} if(v instanceof String s){b.append('"').append(s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")).append('"');return;}
        if(v instanceof Number || v instanceof Boolean){b.append(v);return;} if(v instanceof Map<?,?> m){b.append('{');boolean first=true;for(var e:m.entrySet()){if(!first)b.append(',');first=false;append(b,String.valueOf(e.getKey()));b.append(':');append(b,e.getValue());}b.append('}');return;}
        if(v instanceof Iterable<?> a){b.append('[');boolean first=true;for(Object x:a){if(!first)b.append(',');first=false;append(b,x);}b.append(']');return;} append(b,String.valueOf(v));
    }
    static String str(Map<String,Object> m,String k,String fallback){Object v=m.get(k);return v==null?fallback:String.valueOf(v);}
    static BigDecimal decimal(Map<String,Object> m,String k,String fallback){Object v=m.get(k);return v==null?new BigDecimal(fallback):new BigDecimal(String.valueOf(v));}
    static int integer(String s,int fallback){try{return Integer.parseInt(s);}catch(Exception e){return fallback;}}
    private static final class Parser {
        final String s; int i; Parser(String s){this.s=Objects.requireNonNull(s);}
        Object parse(){Object v=value();ws();if(i!=s.length())throw err();return v;}
        Object value(){ws();if(i>=s.length())throw err();char c=s.charAt(i);if(c=='{')return obj();if(c=='[')return arr();if(c=='"')return string();if(s.startsWith("true",i)){i+=4;return true;}if(s.startsWith("false",i)){i+=5;return false;}if(s.startsWith("null",i)){i+=4;return null;}int st=i;if(c=='-')i++;while(i<s.length()&&(Character.isDigit(s.charAt(i))||s.charAt(i)=='.'||s.charAt(i)=='e'||s.charAt(i)=='E'||s.charAt(i)=='+'||s.charAt(i)=='-'))i++;if(st==i)throw err();return new BigDecimal(s.substring(st,i));}
        Map<String,Object> obj(){i++;Map<String,Object> m=new LinkedHashMap<>();ws();if(take('}'))return m;do{ws();if(i>=s.length()||s.charAt(i)!='"')throw err();String k=string();ws();need(':');m.put(k,value());ws();if(take('}'))return m;need(',');}while(true);}
        List<Object> arr(){i++;List<Object>a=new ArrayList<>();ws();if(take(']'))return a;do{a.add(value());ws();if(take(']'))return a;need(',');}while(true);}
        String string(){need('"');StringBuilder b=new StringBuilder();while(i<s.length()){char c=s.charAt(i++);if(c=='"')return b.toString();if(c=='\\'){if(i>=s.length())throw err();char e=s.charAt(i++);switch(e){case '"','\\','/'->b.append(e);case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'b'->b.append('\b');case 'f'->b.append('\f');case 'u'->{if(i+4>s.length())throw err();b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;}default->throw err();}}else b.append(c);}throw err();}
        void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}boolean take(char c){if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}void need(char c){if(!take(c))throw err();}IllegalArgumentException err(){return new IllegalArgumentException("Malformed JSON at position "+i);}
    }
}
