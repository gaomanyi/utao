package tv.utao.x5.service;

import android.content.Context;
import android.util.Log;

import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import tv.utao.x5.api.ConfigApi;
import tv.utao.x5.call.DownloadCallback;
import tv.utao.x5.domain.ConfigDTO;
import tv.utao.x5.domain.Res;
import tv.utao.x5.domain.live.DataWrapper;
import tv.utao.x5.domain.live.Live;
import tv.utao.x5.domain.live.Vod;
import tv.utao.x5.util.FileUtil;
import tv.utao.x5.util.HttpUtil;
import tv.utao.x5.util.JsonUtil;
import tv.utao.x5.util.Util;

public class UpdateService {

    private static final  String TAG="UpdateService";
    public static String baseFolder;
    public static   void updateRes(Context context)  {
        try {
            updateResWithError(context);
        }catch (Exception e){
            //e.printStackTrace();
            //Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }
    public static   void updateResWithError(Context context) throws IOException {
        //copyAssets 到应用目录
        String tvWebZip="tv-web";
        boolean isDev = Util.isDev();
        Log.i(TAG,"isDev "+isDev);
        baseFolder= context.getFilesDir().getPath();
        String toZipFilePath=baseFolder+"/"+tvWebZip+".zip";
        File toZipFile =new File(toZipFilePath);
        if(!toZipFile.exists()){
            //不存在 copy assert 下文件
            FileUtil.copyFileFromAssert(context, tvWebZip+".zip",toZipFilePath);
            FileUtil.unzipFile(toZipFilePath,baseFolder+"/tv-web",true);
        }else{
            //校验线上版本 不一致 更新
            new Thread(()->{ checkOnlineVersion(toZipFilePath);}).start();
        }
        if(isDev){
            //开发 每次都copy
            Log.i(TAG,"isDev");
            FileUtil.copyFileFromAssert(context,
                    tvWebZip+".zip",toZipFilePath);
            FileUtil.unzipFile(toZipFilePath,baseFolder+"/tv-web",true);
        }
    }
    protected static Map<String,Vod> indexVodMap = new HashMap<>();
    protected static Map<Integer,Integer> tagMaxMap = new HashMap<>();
    protected static Map<String,String> urlKeyMap= new HashMap<>();
    public static  void initTvData(){
        String json= FileUtil.readExt("tv-web/js/cctv/tv.json");
        if(json.trim().isEmpty()){
            return;
        }
        DataWrapper<List<Live>> data = JsonUtil.fromJson(json,new TypeToken<DataWrapper<List<Live>>>(){}.getType());
        List<Live> lives = data.getData();
        indexVodMap = new HashMap<>();
        tagMaxMap=new HashMap<>();
        urlKeyMap=new HashMap<>();
        int i=0,j;
        for (Live life : lives) {
            j=0;
            for (Vod vod : life.getVods()) {
                vod.setTagIndex(i);
                vod.setDetailIndex(j);
                String key= i+"_"+j;
                vod.setKey(key);
                indexVodMap.put(key,vod);
                urlKeyMap.put(vod.getUrl(),key);
                j++;
            }
            tagMaxMap.put(i,j-1);
            i++;
        }
    }
    public static Vod getByKey(String key){
        return indexVodMap.get(key);
    }
    public static Vod getByUrl(String url){
        String key= urlKeyMap.get(url);
        if(null==key){
            return getByKey("0_0");
        }
        return indexVodMap.get(key);
    }


    public static  String liveNext(Integer tagIndexNow,Integer detailIndexNow,String nextType){
        if(nextType.equals("up")){
            if(detailIndexNow==0){
                return tagIndexNow+"_"+tagMaxMap.get(tagIndexNow);
            }
            return tagIndexNow+"_"+(detailIndexNow-1);
        }
        if(nextType.equals("down")){
            if(Objects.equals(detailIndexNow, tagMaxMap.get(tagIndexNow))){
                return tagIndexNow+"_0";
            }
            return tagIndexNow+"_"+(detailIndexNow+1);
        }
        if(nextType.equals("left")){
            if(tagIndexNow==0){
                return (tagMaxMap.size()-1)+"_0";
            }
            return (tagIndexNow-1)+"_0";
        }
        if(nextType.equals("right")){
            if(tagIndexNow.equals(tagMaxMap.size()-1)){
                return "0_0";
            }
            return (tagIndexNow+1)+"_0";
        }
        return "0_0";
    }
    private static void checkOnlineVersion(String toZipFilePath){
         ConfigDTO newConfig = ConfigApi.getConfig();
         if(null==newConfig){
             return;
         }
         if(null==newConfig.getRes()||!newConfig.getRes().getUpdate()){
             Log.i(TAG,"checkOnlineVersion updateRes false");
             return;
         }
        Res resNew = newConfig.getRes();
         String oldJson= FileUtil.readExt("tv-web/update.json");
        Log.i(TAG,"checkOnlineVersion old "+oldJson);
        if(oldJson.trim().isEmpty()){
            return;
        }
         ConfigDTO oldConfig = JsonUtil.fromJson(oldJson,ConfigDTO.class);
         Res resOld = oldConfig.getRes();
         if(null!=resOld&&resNew.getVersion()>resOld.getVersion()){//res版本更新
             Log.i(TAG,"版本更新到 "+resNew.getVersion()+" toZipFilePath"+toZipFilePath);
            // FileUtil.del(toZipFilePath);
             //下载zip 到
             HttpUtil.download(resNew.getUrl(), baseFolder, "tv-web.zip", new DownloadCallback() {
                 @Override
                 public void downloaded() {
                     try {
                         Log.i(TAG,"downloaded");
                         FileUtil.unzipFile(toZipFilePath,baseFolder+"/tv-web",resNew.getSkipFirst());
                     } catch (IOException e) {
                         Log.e(TAG, "downloaded: "+e.getMessage());
                     }
                 }
             });
         }

    }

}
