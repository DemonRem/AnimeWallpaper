package com.github.miao1007.animewallpaper.ui.activity;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.github.miao1007.animewallpaper.R;
import com.github.miao1007.animewallpaper.support.api.ImageVO;
import com.github.miao1007.animewallpaper.ui.widget.ActionSheet;
import com.github.miao1007.animewallpaper.ui.widget.NavigationBar;
import com.github.miao1007.animewallpaper.ui.widget.PieImageView;
import com.github.miao1007.animewallpaper.ui.widget.Position;
import com.github.miao1007.animewallpaper.ui.widget.TagsActionSheet;
import com.github.miao1007.animewallpaper.ui.widget.blur.BlurDrawable;
import com.github.miao1007.animewallpaper.utils.FileUtils;
import com.github.miao1007.animewallpaper.utils.LogUtils;
import com.github.miao1007.animewallpaper.utils.SquareUtils;
import com.github.miao1007.animewallpaper.utils.StatusBarUtils;
import com.github.miao1007.animewallpaper.utils.WallpaperUtils;
import com.github.miao1007.animewallpaper.utils.picasso.Blur;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import java.io.File;
import java.io.IOException;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 用户默认的交互区主要在左下角
 */
public class DetailedActivity extends AppCompatActivity {

  public static final String TAG = LogUtils.makeLogTag(DetailedActivity.class);
  private static final String EXTRA_IMAGE = "URL";
  private static final String EXTRA_POSITION = "EXTRA_POSITION";

  @BindView(R.id.iv_detailed_card) PieImageView ivDetailedCard;
  @BindView(R.id.blur_bg) ImageView ivDetailedCardBlur;
  @BindView(R.id.navigation_bar) NavigationBar mNavigationBar;
  @BindView(R.id.ll_detailed_downloads) LinearLayout mLlDetailedDownloads;
  @BindView(R.id.image_share) ImageView mImageShare;

  BlurDrawable drawable;

  private ImageVO imageResult;

  private boolean isPlaying = false;
  private SquareUtils.ProgressListener listener = new SquareUtils.ProgressListener() {
    @Override public void update(@IntRange(from = 0, to = 100) final int percent) {
      runOnUiThread(new Runnable() {
        @Override public void run() {
          ivDetailedCard.setProgress(percent);
        }
      });
    }
  };
  private Picasso largeImagepicasso;

  private static Position getPosition(Intent intent) {
    return intent.getParcelableExtra(EXTRA_POSITION);
  }

  public static void startActivity(Context context, Position position, ImageVO parcelable) {
    Intent intent = new Intent(context, DetailedActivity.class);
    intent.putExtra(EXTRA_IMAGE, parcelable);
    intent.putExtra(EXTRA_POSITION, position);
    context.startActivity(intent);
  }

  @OnClick(R.id.detailed_back) void back() {
    onBackPressed();
  }

  @OnClick(R.id.detailed_tags) void tags() {
    final ActionSheet a = new TagsActionSheet(getWindow(), new AdapterView.OnItemClickListener() {
      @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MainActivity.startRefreshActivity(DetailedActivity.this,
            imageResult.getTags().get(position));
      }
    }, imageResult.getTags());
    drawable = new BlurDrawable(getWindow());
    a.setDrawable(drawable);
    a.show();
  }

  @OnClick(R.id.image_download) void image_download() {
    downloadLargeImgViaPicasso(new okhttp3.Callback() {
      @Override public void onFailure(Call call, IOException e) {

      }

      @Override public void onResponse(Call call, Response response) throws IOException {
        final Intent shareIntent = new Intent(Intent.ACTION_VIEW);
        File file = FileUtils.saveBodytoExtStorage(response.body(),
            Uri.parse(call.request().url().toString()).getLastPathSegment());
        shareIntent.setDataAndType(Uri.fromFile(file), "image/*");
        startActivity(Intent.createChooser(shareIntent, getString(R.string.view_image_by)));
      }
    });
  }

  @OnClick(R.id.image_setwallpaper) void image_setWallpaper(ImageView v) {
    Toast.makeText(DetailedActivity.this, R.string.start_download_image, Toast.LENGTH_SHORT).show();
    downloadLargeImgViaPicasso(new okhttp3.Callback() {
      @Override public void onFailure(Call call, IOException e) {

      }

      @Override public void onResponse(Call call, Response response) throws IOException {
        File file = FileUtils.saveBodytoExtStorage(response.body(),
            Uri.parse(call.request().url().toString()).getLastPathSegment());
        WallpaperUtils.setWallpaper(DetailedActivity.this, file);
      }
    });
  }

  @OnClick(R.id.iv_detailed_card) void download(final ImageView v) {
    image_setWallpaper(v);
  }

  @OnClick(R.id.image_share) void image_share(ImageView v) {
    downloadLargeImgViaPicasso(new okhttp3.Callback() {
      @Override public void onFailure(Call call, IOException e) {

      }

      @Override public void onResponse(Call call, Response response) throws IOException {
        File file = FileUtils.saveBodytoExtStorage(response.body(),
            Uri.parse(call.request().url().toString()).getLastPathSegment());
        WallpaperUtils.refreshAlbum(DetailedActivity.this, file, file.getName());
        WallpaperUtils.previewImage(DetailedActivity.this, file);
      }
    });
  }

  private void downloadLargeImgViaPicasso(final okhttp3.Callback callback) {
    if (mNavigationBar.getProgress()) {
      //debounce
      return;
    }
    mNavigationBar.setProgressBar(true);
    final String largeImgUrl = imageResult.getDownload_url();
    largeImagepicasso.load(largeImgUrl).placeholder(ivDetailedCard.getDrawable())
        //fix oom
        .config(Bitmap.Config.ARGB_4444).into(ivDetailedCard, new Callback() {
      @Override public void onSuccess() {
        mNavigationBar.setProgressBar(false);
        final Request request = new Request.Builder().url(largeImgUrl)
            .cacheControl(CacheControl.FORCE_CACHE)
            .get()
            .build();
        SquareUtils.getClient().newCall(request).enqueue(callback);
      }

      @Override public void onError() {
        mNavigationBar.setProgressBar(false);
      }
    });
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    if (drawable != null) {
      drawable.onDestroy();
    }
    listener = null;
    largeImagepicasso.cancelRequest(ivDetailedCard);
  }

  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.fragment_image_detailed_card);
    ButterKnife.bind(this);
    StatusBarUtils.from(this)
        .setTransparentStatusbar(true)
        .setTransparentNavigationbar(true)
        .setActionbarView(mNavigationBar)
        .setLightStatusBar(false)
        .process();
    largeImagepicasso = SquareUtils.getPicasso(this, listener);
    mNavigationBar.setTextColor(Color.WHITE);
    imageResult = getIntent().getParcelableExtra(EXTRA_IMAGE);
    SquareUtils.getPicasso(this).load(imageResult.getPrevurl()) //shared disk cache
        .into(ivDetailedCard, new Callback.EmptyCallback() {
          @Override public void onSuccess() {
            Bitmap bitmap = ((BitmapDrawable) ivDetailedCard.getDrawable()).getBitmap();
            final Bitmap blur = Blur.apply(DetailedActivity.this, bitmap, 20);
            ivDetailedCard.post(new Runnable() {
              @Override public void run() {
                anim(getPosition(getIntent()), new BitmapDrawable(blur), true, new Runnable() {
                  @Override public void run() {

                  }
                }, ivDetailedCard, mLlDetailedDownloads, ivDetailedCardBlur);
              }
            });
          }
        });
  }

  /**
   * 动画封装，千万不要剁手改正负
   */
  private void anim(final Position position, @Nullable Drawable drawable, final boolean in,
      final Runnable runnable, @Size(value = 3) View... views) {
    if (isPlaying) {
      return;
    }
    View detailImg = views[0];
    //记住括号哦，我这里调试了一小时
    float delta = ((float) (position.width)) / ((float) (position.height));
    //243 - 168(navi) = 75 = status_bar
    float[] y_img = {
        position.top - (detailImg.getY() + (in ? (StatusBarUtils.getStatusBarOffsetPx(this)) : 0)),
        0
    };
    float[] s_img = { 1f, delta };

    float[] y_icn = { views[1].getHeight() * 4, 0 };

    detailImg.setPivotX(detailImg.getWidth() / 2);
    detailImg.setPivotY(0);
    views[1].setPivotX(views[1].getWidth() / 2);
    views[1].setPivotY(0);
    ImageView bg = ((ImageView) views[2]);
    if (drawable != null) {
      bg.setImageDrawable(drawable);
    }
    Animator trans_Y =
        ObjectAnimator.ofFloat(detailImg, View.TRANSLATION_Y, in ? y_img[0] : y_img[1],
            in ? y_img[1] : y_img[0]);
    Animator scale_X = ObjectAnimator.ofFloat(detailImg, View.SCALE_X, in ? s_img[0] : s_img[1],
        in ? s_img[1] : s_img[0]);
    Animator scale_Y = ObjectAnimator.ofFloat(detailImg, View.SCALE_Y, in ? s_img[0] : s_img[1],
        in ? s_img[1] : s_img[0]);

    Animator alpha_icn = ObjectAnimator.ofFloat(views[1], View.ALPHA, in ? 0f : 1f, in ? 1f : 0f);
    Animator alpha_bg = ObjectAnimator.ofFloat(views[2], View.ALPHA, in ? 0f : 1f, in ? 1f : 0f);

    Animator trans_icn_Y =
        ObjectAnimator.ofFloat(views[1], View.TRANSLATION_Y, in ? y_icn[0] : y_icn[1],
            in ? y_icn[1] : y_icn[0]);
    AnimatorSet set = new AnimatorSet();
    set.playTogether(trans_Y, scale_X, scale_Y);
    set.playTogether(trans_icn_Y, alpha_icn, alpha_bg);
    set.setDuration(300);
    set.addListener(new Animator.AnimatorListener() {
      @Override public void onAnimationStart(Animator animation) {
        isPlaying = true;
      }

      @Override public void onAnimationEnd(Animator animation) {
        isPlaying = false;
        runnable.run();
      }

      @Override public void onAnimationCancel(Animator animation) {
        isPlaying = false;
      }

      @Override public void onAnimationRepeat(Animator animation) {
      }
    });
    set.setInterpolator(new AccelerateInterpolator());
    set.start();
  }

  @Override public void onBackPressed() {

    anim(getPosition(getIntent()), null, false, new Runnable() {
      @Override public void run() {
        DetailedActivity.super.onBackPressed();
        overridePendingTransition(0, 0);
      }
    }, ivDetailedCard, mLlDetailedDownloads, ivDetailedCardBlur);
  }
}
