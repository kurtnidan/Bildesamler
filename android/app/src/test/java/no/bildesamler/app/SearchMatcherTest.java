package no.bildesamler.app;
public class SearchMatcherTest {
 @org.junit.Test public void searchModes(){
  check(SearchMatcher.score("hund","","Dog Pet",2)==100,"norsk motiv");
  check(SearchMatcher.score("kvittering","Kvittering fra butikk","",1)==100,"OCR");
  check(SearchMatcher.score("spansk språk","hola gracias comer hablar queso pollo","",0)==100,"spansk");
  check(SearchMatcher.score("hund","","Cat",0)==0,"utelukk treff");
  check(SearchMatcher.score("hund","hund","Dog",3)==100,"tekst og motiv");
  System.out.println("5 søketester bestått");
 }
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
