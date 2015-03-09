在移动应用中，我们一般将网络图片分为三个级别，第一级别是网络层，即根据图片的url地址可以找到服务器上相应图片，获取这一层的图片会消耗流量，所以我们希望可以获取后本地就永久使用，所以就会有接下来的缓存策略；第二层缓存是在手机内存层，是将第一层的图片下载到手机内存，这种缓存读取速度非常快，但当图片内存被回收时，图片自然就不会存在了，第三层则是在手机硬盘层，是会缓存到sd卡。但这一层相对于内存的读取速度会慢很多，所以，很好的协调这三层图片缓存就可以提升应用性能和用户体验。

秉着不重复造轮子原则，这里我采用Volley+LruCache+DiskLruCache三个谷歌官方认可的库来实现网络图片三级缓存。并且以“one line”风格来实现将网络图片显示在ImageView上，而无需关心任何缓存细节。

## 类库下载
 
 1. Volley是Goole在2013年Google I/O大会上推出了一个新的网络通信框架，它是开源的，你可以通过git来clone源码并倒入项目：
 
 	git clone https://android.googlesource.com/platform/frameworks/volley
 	
 这个地址可能会被和谐，所以你可以在这里下载完整的jar包：
 [http://cdn.saymagic.cn/150131132336.jar](http://cdn.saymagic.cn/150131132336.jar)
 
 2.LruCache这个类是Android3.1版本中提供的，如果你是在更早的Android版本中开发，则需要导入android-support-v4的jar包。
 
 3.DiskLruCache是非Google官方编写，但获得官方认证的硬盘缓存类，该类没有限定在Android内，所以理论上java应用也可以使用DiskLreCache来缓存。该类你可以在这个下载：[http://cdn.saymagic.cn/150131132428.java](http://cdn.saymagic.cn/150131132428.java)
 
 
 
## 方法与流程
 
1.要想实现图片三级缓存，就需要将图片下载到本地，我们所有网络图片请求都是通过Volley来统一管理的，Volley需要我们声明一个RequesQueuetManager来维持请求队列，因此，我们首先定义`RequesQueuetManager`类来管理RequesQueuetManager,代码如下：

	public class RequesQueuetManager {
	public static RequestQueue mRequestQueue = 	Volley.newRequestQueue(DemoApplication.getInstance());

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

因为RequestQueue需要一个Context类型参数，所以我们在`Volley.newRequestQueue(DemoApplication.getInstance())`这句里传入了`DemoApplication.getInstance()`,这个静态方法是什么呢？就是应用的application实例，我们自定义一个application，然后将这个application传入给RequestQueue，我们自定义的application如下：

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
	
要记得将自定义的application添加到AndroidManifest.xml文件的application标签的name属性里：

![http://cdn.saymagic.cn/150131111011.09.02.png](http://cdn.saymagic.cn/150131111011.09.02.png)

2.RequestQueue是负责图片请求顺序的，具体的图片请求工作由Volley里的ImageLoader类来完成，new它的时候它会接收两个参数，一个是我们刚刚声明的RequestQueue请求队列，另外一个是`ImageLoader.ImageCache`接口，看名字就知道，这个接口是方便我们写缓存用的，也就是说，我们接下来的二级缓存与三级缓存就是在实现此基础上进行的:

首先我们新建`ImageLreCache`类来让它继承LreCache并实现ImageLoader.ImageCache接口，秉着父债子偿的原理，父类没有实现的接口子类就需要来实现，所以我们需要重写LreCache类的`sizeOf`方法，须要重写ImageLoader.ImageCache的`getBitmap`与`putBitmap`方法。


 于是该类下会有如下三个函数：

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
		return get(s);
	}
	
	@Override
    public void putBitmap(String s, Bitmap bitmap) {
    	put(s,bitmap)
    }
    
sizeOf方法是LruCache 留给子类重写来衡量Bitmap大小的函数，因为LruCache里面会对size大小是否小于0进行判断，size小与0的Bitmap会抛出`IllegalStateException`异常。

getBitmap与putBitmap方法是ImageLoader.ImageCache留给子类实现的接口，Volley在请求网络数据时会先回调getBitmap方法来查看缓存中是否有所需图片，有的话会直接使用缓存的图片，没有再去请求，同理，当Volley下载完图片后会来回调putBitmap方法来将图片进行缓存。所以，我们实现这两个方法，然后在方法里直接使用LreCache的get与set方法即可。

综上，我们就将二级缓存完成，接下来一鼓作气，在此基础上完成硬盘级的第三级缓存，DiskLruCache的使用方法会稍微有些复杂，首先，我们需要DiskLruCache实例，它的构造方法是私有的，所以我们需要通过它提供的open方法来生成。open原型如下：

	 /**
     * Opens the cache in {@code directory}, creating a cache if none exists
     * there.
     *
     * @param directory a writable directory
     * @param appVersion
     * @param valueCount the number of values per cache entry. Must be positive.
     * @param maxSize the maximum number of bytes this cache should use to store
     * @throws java.io.IOException if reading or writing the cache directory fails
     */
    public static DiskLruCache open(File directory, int appVersion, int valueCount, long maxSize)
            throws IOException {}
            
            
  
它会接收四个参数，directory是缓存路径对应的File类，appVersion代表缓存版本，valueCount代表每个key对应的缓存个数，一般为1，maxSize代表缓存文件最大size。所以，在Android里，directory与appVersion可以从系统中获得，因此我们会这样写：

	private static DiskLruCache mDiskLruCache = DiskLruCache.open(getDiskCacheDir(DemoApplication.getInstance(),CACHE_FOLDER_NAME),
					getAppVersion(DemoApplication.getInstance()) , 1, 10*1024*1024);
					
					
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
    
  
    
在获得DiskLruCache实例后，我们就可以来完善 ImageLoader.ImageCache接口下的getBitmap与putBitmap方法。主要思想是在方法里多一层逻辑判断，当图片不在LruCache时，再次查询DiskLruCache中是否存在，存在的话去取出然后转换成Bitmap并返回。需要注意的是DiskLruCache关于缓存内容的读取与写入是通过其内部封装的Editor与Snapshot两给类来实现，所以代码会稍有些复杂，但是很好理解。完善后的代码如下：

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
    
    

这样，缓存类以完结，然我们回到这一步的最开始，有了这个实现ImageLoader.ImageCache接口的类就可以用来生成ImageLoader实例了：


	public static ImageLoader  mImageLoder = new 	ImageLoader(RequesQueuetManager.mRequestQueue,new ImageLreCache());
		

3.拿到mImageLoder之后我们就可以请求网络上的图片了，请求的函数为get，接收的参数为图片远程地址url，回调接口listener，图片需要的宽度maxWidth与高度maxHeight，这里比较难以理解的是listener参数，它是ImageLoader的内部接口ImageListener，主要是图片请求成功或者失败的两个回调方法，这里我们写一个统一生成isterner的函数：

	public static ImageLoader.ImageListener getImageLinseter(final ImageView view,
			final Bitmap defaultImageBitmap, final Bitmap errorImageBitmap){

		return new ImageLoader.ImageListener() {
			@Override
			public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
				if(imageContainer.getBitmap() != null ){
					view.setImageBitmap(imageContainer.getBitmap());
				}else if(defaultImageBitmap != null ){
					view.setImageBitmap(defaultImageBitmap);
				}
			}

			@Override
			public void onErrorResponse(VolleyError volleyError) {
				if(errorImageBitmap != null){
					view.setImageBitmap(errorImageBitmap);
				}
			}
		};
	}
	
这个函数接收view,defaultImageBitmap,errorImageBitmap三个参数，当图片请求成功后将下载后的bitmap显示到view上，失败则显示errorImageBitmap。

综上，我们可以封装一个函数来提供给外部，接收6个参数，实现"one line"式编程，让网络图片请求变的更容易。代码如下：

	/**
	 * 外部调用次方法即可完成将url处图片现在view上，并自动实现内存和硬盘双缓存。
	 * @param url 远程url地址
	 * @param view 待现实图片的view
	 * @param defaultImageBitmap 默认显示的图片
	 * @param errorImageBitmap 网络出错时显示的图片
	 * @param maxWidtn
	 * @param maxHeight 
	 */
	public static ImageLoader.ImageContainer loadImage(final String url, final ImageView view,
			final Bitmap defaultImageBitmap, final Bitmap errorImageBitmap, int maxWidtn, int maxHeight){
			return mImageLoder.get(url, getImageLinseter(view,defaultImageBitmap, 
				errorImageBitmap),maxWidtn,maxHeight);
	}
	

## 效果
	
我们先看一下它的使用方法，首先拿到控件：

        ImageView iv = (ImageView) findViewById(R.id.iv_hello);

接着调用如下方法即可将图片`http://ww2.sinaimg.cn/large/7cc829d3gw1eahy2zrjlxj20kd0a10t9.jpg`显示在上面的控件上：

        ImageCacheManger.loadImage("http://ww2.sinaimg.cn/large/7cc829d3gw1eahy2zrjlxj20kd0a10t9.jpg", iv, 
        		getBitmapFromResources(this, R.drawable.ic_launcher), getBitmapFromResources(this, R.drawable.error));
        		

所谓无图无真相，这是请求成功的效果(在请求成功后，断网后第二次打开依然可以显示，因为缓存在本地硬盘里)：

![http://cdn.saymagic.cn/150131132130.png](http://cdn.saymagic.cn/150131132130.png)

DiskLruCache缓存在硬盘中的文件：

![http://cdn.saymagic.cn/150131132219.png](http://cdn.saymagic.cn/150131132219.png)

原文链接：[http://blog.saymagic.cn/2015/01/30/android-pic-three-cache.html](http://blog.saymagic.cn/2015/01/30/android-pic-three-cache.html)
 
 
 
