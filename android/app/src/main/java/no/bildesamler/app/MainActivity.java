package no.bildesamler.app;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import com.google.android.gms.tasks.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  EditText query; Spinner mode; SeekBar threshold; TextView status; LinearLayout results;
  ExecutorService worker=Executors.newSingleThreadExecutor();
  volatile boolean cancelled=false;
  volatile boolean scanning=false;
  final com.google.mlkit.vision.text.TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  final com.google.mlkit.vision.label.ImageLabeler labeler=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
  final ArrayList<Uri> found=new ArrayList<>();
  final HashMap<String,Record> cache=new HashMap<>();
  android.content.SharedPreferences prefs;
  static class Record { String text="", labels=""; float score=0; }
  @Override public void onCreate(Bundle b){super.onCreate(b); prefs=getSharedPreferences("archive",MODE_PRIVATE); buildUI(); if(hasAccess()) startScan(); else requestAccess();}
  void buildUI(){
    ScrollView sc=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);sc.addView(root);
    TextView heading=new TextView(this);heading.setText("Bildesamler • automatisk bildesøk");heading.setTextSize(22);root.addView(heading);
    query=new EditText(this);query.setSingleLine(true);query.setText("Spansk språk");query.setHint("Søk etter emne");root.addView(query);
    mode=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Smart søk","Tekst","Motiv","Tekst + motiv"});mode.setAdapter(adapter);root.addView(mode);
    TextView label=new TextView(this);label.setText("Minste relevans (heuristisk skår)");root.addView(label);
    threshold=new SeekBar(this);threshold.setMax(100);threshold.setProgress(80);root.addView(threshold);
    Button scan=new Button(this);scan.setText("Søk automatisk i telefonens bilder");root.addView(scan);scan.setOnClickListener(v->startScan());
    Button stop=new Button(this);stop.setText("Stopp");root.addView(stop);stop.setOnClickListener(v->cancelled=true);
    status=new TextView(this);status.setPadding(0,12,0,12);root.addView(status);
    results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);root.addView(results);setContentView(sc);
  }
  boolean hasAccess(){return checkSelfPermission(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
  @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==1){if(hasAccess())startScan();else status.setText("Gi tilgang til alle bilder for automatisk analyse av hele biblioteket. Du kan endre dette i telefonens appinnstillinger.");}}
  @Override protected void onDestroy(){cancelled=true;worker.shutdown();new Thread(()->{try{worker.awaitTermination(60,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{recognizer.close();labeler.close();}}).start();super.onDestroy();}
  void requestAccess(){if(android.os.Build.VERSION.SDK_INT>=34){requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},1);}else if(android.os.Build.VERSION.SDK_INT>=33){requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},1);}else if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);}}
  void startScan(){
    if(!hasAccess()){status.setText("Gi tilgang til ALLE bilder i Android-dialogen. Begrenset tilgang gir bare et utvalg.");requestAccess();return;}
    if(scanning){status.setText("Analyse pågår. Trykk Stopp og vent før nytt søk.");return;}
    cancelled=false;results.removeAllViews();String term=query.getText().toString().trim();if(term.isEmpty()){status.setText("Skriv et søkeemne.");return;}
    int m=mode.getSelectedItemPosition(), min=threshold.getProgress();status.setText("Finner bilder …");
    scanning=true;worker.execute(()->{try{scanAll(term,m,min);}finally{scanning=false;}});
  }
  void scanAll(String term,int mode,int min){
    ArrayList<Uri> images=new ArrayList<>();
    HashMap<String,Long> modified=new HashMap<>();
    try(Cursor c=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media._ID,MediaStore.Images.Media.DATE_MODIFIED},null,null,MediaStore.Images.Media.DATE_ADDED+" DESC")){
      if(c!=null)while(c.moveToNext()){Uri uri=Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getString(0));images.add(uri);modified.put(uri.toString(),c.getLong(1));}
    }catch(Exception e){runOnUiThread(()->status.setText("Kan ikke lese bildebiblioteket: "+e.getMessage()));return;}
    runOnUiThread(()->status.setText("Analyserer "+images.size()+" bilder. Dette kan ta tid …"));
    int n=0, errors=0;for(Uri uri:images){if(cancelled)break;
      try{
        String key=uri.toString();
        Record r=new Record();
        if(prefs.contains(key+".text") && prefs.getLong(key+".modified",-1)==modified.get(key)){r.text=prefs.getString(key+".text","");r.labels=prefs.getString(key+".labels","");}
        else {
          Bitmap bitmap=getContentResolver().loadThumbnail(uri,new android.util.Size(1600,1600),null);
          try {
            InputImage image=InputImage.fromBitmap(bitmap,0);
            r.text=Tasks.await(recognizer.process(image)).getText();
            StringBuilder labels=new StringBuilder();
            for(com.google.mlkit.vision.label.ImageLabel l:Tasks.await(labeler.process(image))) labels.append(l.getText()).append(' ');
            r.labels=labels.toString();
            prefs.edit().putLong(key+".modified",modified.get(key)).putString(key+".text",r.text).putString(key+".labels",r.labels).apply();
          } finally {bitmap.recycle();}
        }
        int score=SearchMatcher.score(term,r.text,r.labels,mode);r.score=score;
        if(score>0 && score>=min){runOnUiThread(()->addResult(uri,r,score));}
      }catch(Exception failure){errors++;}
      n++;if(n%10==0){final int done=n;runOnUiThread(()->status.setText("Analysert "+done+" / "+images.size()+" bilder"));}
    }
    final int done=n, failed=errors;runOnUiThread(()->status.setText((cancelled?"Stoppet. ":"Ferdig. ")+"Analysert "+done+" bilder. Feil: "+failed));
  }
  void addResult(Uri uri,Record r,int score){
    if(isDestroyed()||isFinishing())return;
    LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(0,12,0,12);
    ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setMaxHeight(500);
    try{image.setImageBitmap(getContentResolver().loadThumbnail(uri,new android.util.Size(500,500),null));}catch(Exception ignored){}
    item.addView(image);TextView meta=new TextView(this);meta.setText("Skår: "+score+"/100 | Tekst: "+r.text.replace('\n',' ').substring(0,Math.min(90,r.text.length()))+"\nMotiv: "+r.labels);item.addView(meta);
    Button open=new Button(this);open.setText("Åpne bilde");open.setOnClickListener(v->{Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(uri,"image/*");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(intent);}catch(ActivityNotFoundException e){status.setText("Ingen app kan åpne bildet.");}});item.addView(open);
    results.addView(item);
  }
}
