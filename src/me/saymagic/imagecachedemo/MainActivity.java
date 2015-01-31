package me.saymagic.imagecachedemo;

import me.saymagic.image.cache.ImageCacheManger;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;


public class MainActivity extends Activity {

	ImageView iv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iv = (ImageView) findViewById(R.id.iv_hello);
        ImageCacheManger.loadImage("http://ww2.sinaimg.cn/large/7cc829d3gw1eahy2zrjlxj20kd0a10t9.jpg", iv, 
        		getBitmapFromResources(this, R.drawable.ic_launcher), getBitmapFromResources(this, R.drawable.error));
    }
    
    /**
     * 从资源中取出Bitmap
     * @param act
     * @param resId
     * @return
     */
    public static Bitmap getBitmapFromResources(Activity act, int resId) {
        Resources res = act.getResources();
        return BitmapFactory.decodeResource(res, resId);
    }


}
