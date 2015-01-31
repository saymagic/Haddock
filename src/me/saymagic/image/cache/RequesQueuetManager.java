package me.saymagic.image.cache;

import me.saymagic.imagecachedemo.DemoApplication;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;


/**
 * Created by saymagic on 15/1/27.
 */
public class RequesQueuetManager {
	public static RequestQueue mRequestQueue = Volley.newRequestQueue(DemoApplication.getInstance());

	public static void addRequest(Request<?> request, Object object){
		if (object != null){
			request.setTag(object);
		}
		mRequestQueue.add(request);
	}

	public static void cancelAll(Object tag) {
		mRequestQueue.cancelAll(tag);
	}
}
