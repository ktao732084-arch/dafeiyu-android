package com.xiaoxiao.pet;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.InputStream;

/** A separately drawn rear-waist pose, suspended at its apron knot rather than rotating the idle clip. */
final class WaistLiftView extends View {
    static final String ASSET="main/waist-lift-v1.png";
    // Source image is 1448x1086. Keep the supplied alpha; this rect selects the sprite's useful area.
    static final float PIN_U=(900f-356)/774, PIN_V=(430f-218)/684;
    static final float FOOT_U=(933f-356)/774, FOOT_V=(874f-218)/684;
    static final float ASPECT=684f/774;
    static Bitmap shared;
    final Bitmap sprite;
    final LiftInteraction lift;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    final float[] vertices=new float[(12+1)*(16+1)*2];
    static synchronized Bitmap load(Context c)throws java.io.IOException {
        if(shared!=null&&!shared.isRecycled())return shared;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;options.inScaled=false;
        Bitmap source;try(InputStream stream=c.getAssets().open(ASSET)){source=BitmapFactory.decodeStream(stream,null,options);}
        if(source==null)throw new java.io.IOException("Missing rear-waist pose");
        int left=Math.round(source.getWidth()*356f/1448),top=Math.round(source.getHeight()*218f/1086);
        int right=Math.round(source.getWidth()*1130f/1448),bottom=Math.round(source.getHeight()*902f/1086);
        shared=Bitmap.createBitmap(source,left,top,right-left,bottom-top);
        if(shared!=source)source.recycle();return shared;
    }
    WaistLiftView(Context c,LiftInteraction lift)throws java.io.IOException {
        super(c);this.lift=lift;sprite=load(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas){
        if(!lift.renderingWaist())return;
        lift.visualFrame();
        PetService.Pet pet=lift.pet;
        float width=lift.poseWidth(),height=width*ASPECT;
        boolean animated=UiMotion.enabled();float t=lift.duration()/1000f;
        float angle=animated?lift.motion.angle:0;
        float entry=lift.active?lift.entry():1;
        canvas.save();canvas.translate(lift.pinX()-pet.cropL*pet.width,lift.pinY()-pet.cropT*pet.width*.75f);
        canvas.rotate(angle);canvas.scale(.94f+.06f*entry,.94f+.06f*entry);
        int k=0;
        for(int row=0;row<=16;row++)for(int col=0;col<=12;col++){
            float u=col/12f,v=row/16f,dx=u-PIN_U,dy=v-PIN_V;
            float weight=Math.min(1,(dx*dx+dy*dy)*2.5f);
            float sway=animated?(float)Math.sin(t*3.8f+v*3)*Apple.dp(getContext(),1)*weight:0;
            float breath=animated?(float)Math.sin(t*2.4f)*Apple.dp(getContext(),1)*weight:0;
            vertices[k++]=dx*width+sway;vertices[k++]=dy*height+breath;
        }
        canvas.drawBitmapMesh(sprite,12,16,vertices,0,null,0,paint);canvas.restore();
    }
    PointF footWorld(){
        PetService.Pet pet=lift.pet;
        float scale=.94f+.06f*(lift.active?lift.entry():1),w=lift.poseWidth(),h=w*ASPECT;
        float dx=FOOT_U-PIN_U,dy=FOOT_V-PIN_V,weight=Math.min(1,(dx*dx+dy*dy)*2.5f),t=lift.duration()/1000f;
        float x=dx*w,y=dy*h,angle=0;
        if(UiMotion.enabled()){x+=(float)Math.sin(t*3.8f+FOOT_V*3)*Apple.dp(getContext(),1)*weight;y+=(float)Math.sin(t*2.4f)*Apple.dp(getContext(),1)*weight;angle=(float)Math.toRadians(lift.motion.angle);}
        return new PointF(pet.x+lift.pinX()+scale*(x*(float)Math.cos(angle)-y*(float)Math.sin(angle)),pet.y+lift.pinY()+scale*(x*(float)Math.sin(angle)+y*(float)Math.cos(angle)));
    }

}
