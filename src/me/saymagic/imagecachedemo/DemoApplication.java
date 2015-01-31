package me.saymagic.imagecachedemo;

import android.app.Application;

public class DemoApplication extends Application {
	 public static String TAG;
	    private static DemoApplication application;

	    public static DemoApplication getInstance() {
	        return application;
	    }

	    @Override
	    public void onCreate() {
	        super.onCreate();
	        TAG = this.getClass().getSimpleName();
	        application = this;
	    }
}
