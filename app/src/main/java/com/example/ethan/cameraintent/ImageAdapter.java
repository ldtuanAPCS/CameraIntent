package com.example.ethan.cameraintent;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;


import com.squareup.picasso.Picasso;

import java.io.File;
import java.lang.ref.WeakReference;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder>{
    private File[] mImageFiles;
    private static RecyclerViewClickPositionInterface mPositionInterface;

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

    public ImageAdapter(File[] folderFiles, RecyclerViewClickPositionInterface positionInterface){
        mPositionInterface = positionInterface;
        mImageFiles = folderFiles;
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
        File imageFile = mImageFiles[position];
        Picasso.get().load(imageFile).resize(200,200).into(viewHolder.getImageView());
    }

    @Override
    public int getItemCount() {
        if (mImageFiles != null) {
            return mImageFiles.length;
        } else return 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        private ImageView imageView;

        public ViewHolder(View view){
            super(view);
            view.setOnClickListener(this);
            imageView = (ImageView) view.findViewById(R.id.imageGalleryView);
        }

        public ImageView getImageView(){
            return imageView;
        }

        @Override
        public void onClick(View v) {
            mPositionInterface.getRecyclerViewAdapterPosition(this.getPosition());
        }
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
