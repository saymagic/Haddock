package me.saymagic.image.cache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import libcore.io.DiskLruCache;
import me.saymagic.imagecachedemo.DemoApplication;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.util.LruCache;

import com.android.volley.toolbox.ImageLoader;

/**
 * Created by saymagic on 15/1/27.
 */
class ImageLreCache extends LruCache<String, Bitmap> implements ImageLoader.ImageCache {
	
	private static String CACHE_FOLDER_NAME;
	private static int DISK_CACHE_SIZE;
	private static DiskLruCache mDiskLruCache ; 
    public ImageLreCache(int maxSize,String diskCacheFodler,int diskCacheSize) {
        super(maxSize);
        CACHE_FOLDER_NAME = diskCacheFodler;
        DISK_CACHE_SIZE = diskCacheSize;
        try {
        	mDiskLruCache = DiskLruCache.open(getDiskCacheDir(DemoApplication.getInstance(),CACHE_FOLDER_NAME),
					getAppVersion(DemoApplication.getInstance()) , 1, DISK_CACHE_SIZE);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    @Override
    protected int sizeOf(String key, Bitmap bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            return bitmap.getByteCount();
        }
        // Pre HC-MR1
        return bitmap.getRowBytes() * bitmap.getHeight();
    }

    @Override
    public Bitmap getBitmap(String s) {
        String key = hashKeyForDisk(s);  
        try {
			if(mDiskLruCache.get(key)==null){
			    return get(s);
			}else{
			    DiskLruCache.Snapshot snapShot = mDiskLruCache.get(key);  
			    Bitmap bitmap = null;
			    if (snapShot != null) {  
			        InputStream is = snapShot.getInputStream(0);  
			        bitmap = BitmapFactory.decodeStream(is);  
			    }
				return bitmap;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
        return null;
    }

    @Override
    public void putBitmap(String s, Bitmap bitmap) {
        put(s,bitmap);
        String key = hashKeyForDisk(s);  
        try {
			if(null == mDiskLruCache.get(key)){
			    DiskLruCache.Editor editor = mDiskLruCache.edit(key);  
			    if (editor != null) {  
			        OutputStream outputStream = editor.newOutputStream(0);  
			        if (bitmap.compress(CompressFormat.JPEG, 100, outputStream)) {  
			            editor.commit();  
			        } else {  
			            editor.abort();  
			        }  
			    }  
			    mDiskLruCache.flush(); 
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
 
    }
    
    //该方法会判断当前sd卡是否存在，然后选择缓存地址
    public static File getDiskCacheDir(Context context, String uniqueName) {  
        String cachePath;  
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())  
                || !Environment.isExternalStorageRemovable()) {  
            cachePath = context.getExternalCacheDir().getPath();  
        } else {  
            cachePath = context.getCacheDir().getPath();  
        }  
        return new File(cachePath + File.separator + uniqueName);  
    }  
    
    //获得应用version号码
    public int getAppVersion(Context context) {  
        try {  
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);  
            return info.versionCode;  
        } catch (NameNotFoundException e) {  
            e.printStackTrace();  
        }  
        return 1;  
    }  
    
    //根据key生成md5值，保证缓存文件名称的合法化
    public String hashKeyForDisk(String key) {  
        String cacheKey;  
        try {  
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");  
            mDigest.update(key.getBytes());  
            cacheKey = bytesToHexString(mDigest.digest());  
        } catch (NoSuchAlgorithmException e) {  
            cacheKey = String.valueOf(key.hashCode());  
        }  
        return cacheKey;  
    }  
      
    private String bytesToHexString(byte[] bytes) {  
        StringBuilder sb = new StringBuilder();  
        for (int i = 0; i < bytes.length; i++) {  
            String hex = Integer.toHexString(0xFF & bytes[i]);  
            if (hex.length() == 1) {  
                sb.append('0');  
            }  
            sb.append(hex);  
        }  
        return sb.toString();  
    }  
}
