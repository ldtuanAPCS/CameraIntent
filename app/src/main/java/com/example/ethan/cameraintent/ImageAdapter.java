package com.example.ethan.cameraintent;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder>{
    private File imagesFile;
    private Bitmap placeHolderBitmap;

    public static class AsyncDrawable extends BitmapDrawable{
        final WeakReference<BitmapWorkerTask> taskReference;

        public AsyncDrawable(Resources resources,
                             Bitmap bitmap,
                             BitmapWorkerTask bitmapWorkerTask){
            super(resources, bitmap);
            taskReference = new WeakReference(bitmapWorkerTask);

        }

        public BitmapWorkerTask getBitmapWorkerTask(){
            return taskReference.get();
        }
    }

    public ImageAdapter(File folderFile){
        imagesFile = folderFile;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.gallery_images_relative_layout, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        File imageFile = imagesFile.listFiles()[position];
        //BitmapWorkerTask workerTask = new BitmapWorkerTask(viewHolder.getImageView());
        //workerTask.execute(imageFile);
        if (checkBitmapWorkerTask(imagesFile, viewHolder.getImageView())){
            BitmapWorkerTask bitmapWorkerTask = new BitmapWorkerTask(viewHolder.getImageView());
            AsyncDrawable asyncDrawable = new AsyncDrawable(viewHolder.getImageView().getResources(),
                    placeHolderBitmap,
                    bitmapWorkerTask);
            viewHolder.getImageView().setImageDrawable(asyncDrawable);
            bitmapWorkerTask.execute(imageFile);
        }
    }

    @Override
    public int getItemCount() {
        return imagesFile.listFiles().length;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{
        private ImageView imageView;

        public ViewHolder(View view){
            super(view);
            imageView = (ImageView) view.findViewById(R.id.imageGalleryView);
        }

        public ImageView getImageView(){
            return imageView;
        }
    }

    public static boolean checkBitmapWorkerTask(File imagesFile, ImageView imageView){
        BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null){
            final File workerFile = bitmapWorkerTask.getImageFile();
            if (workerFile != null){
                if (workerFile != imagesFile){
                    bitmapWorkerTask.cancel(true);
                } else {
                    //bitmap worker task is the same as the imageview is expecting
                    return false;
                }
            }
        }
        return true;
    }

    public static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView){
        Drawable drawable = imageView.getDrawable();
        if (drawable instanceof AsyncDrawable){
            AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
            return asyncDrawable.getBitmapWorkerTask();
        }
        return null;
    }

}
