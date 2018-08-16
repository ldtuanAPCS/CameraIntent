package com.example.ethan.cameraintent;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

import java.io.File;
import java.lang.ref.WeakReference;

public class BitmapWorkerTask extends AsyncTask<File, Void, Bitmap>{

    WeakReference<ImageView> imageViewReferences;

    public BitmapWorkerTask(ImageView imageView){
        imageViewReferences = new WeakReference<>(imageView);
    }

    @Override
    protected Bitmap doInBackground(File... params) {
        return BitmapFactory.decodeFile(params[0].getAbsolutePath());
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (bitmap != null && imageViewReferences != null){
            ImageView viewImage = imageViewReferences.get();
            if (viewImage != null){
                viewImage.setImageBitmap(bitmap);
            }
        }
        super.onPostExecute(bitmap);
    }
}
