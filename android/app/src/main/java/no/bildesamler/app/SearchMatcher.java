package no.bildesamler.app;
import java.util.Locale;
final class SearchMatcher {
  static int score(String term,String text,String labels,int mode){
    String q=term.toLowerCase(Locale.ROOT), t=text.toLowerCase(Locale.ROOT), l=labels.toLowerCase(Locale.ROOT);
    String[][] translations={{"hund","dog"},{"katt","cat"},{"bil","car"},{"motorsykkel","motorcycle"},{"strand","beach"},{"fjell","mountain"},{"mat","food"},{"blomst","flower"},{"fugl","bird"},{"båt","boat"},{"sykkel","bicycle"},{"hest","horse"},{"tre","tree"},{"sjø","sea"},{"snø","snow"},{"solnedgang","sunset"},{"barn","child"}};
    for(String[] pair:translations)if(java.util.Arrays.asList(q.split("\\s+")).contains(pair[0]) && l.contains(pair[1]))l+=" "+pair[0];
    int ts=t.contains(q)?100:0, ms=l.contains(q)?100:0;
    if(q.contains("spansk")||q.contains("spanish")){
      // Spanish vocabulary posters commonly contain multiple Spanish terms.
      String[] words={"el ","la ","los ","las ","una ","un ","huevo","hola","gracias","comer","hablar","español","espanol","tortilla","queso","pollo","cómo","dónde","para ","con ","sin "};
      int count=0;for(String w:words)if(t.contains(w))count++;
      ts=Math.max(ts,Math.min(100,count*18));
    }
    for(String word:q.split("\\s+")){if(word.length()>3){if(t.contains(word))ts=Math.max(ts,65);if(l.contains(word))ms=Math.max(ms,65);}}
    if(mode==1)return ts;if(mode==2)return ms;if(mode==3)return Math.min(ts,ms);return Math.max(ts,ms);
  }
}
