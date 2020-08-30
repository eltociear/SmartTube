package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.mvp.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Header;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.MainView;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;

public class MainPresenter implements VideoItemPresenter<MainView> {
    private static final String TAG = MainPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static MainPresenter sInstance;
    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final ArrayList<MediaGroup> mMediaGroups;
    private final PlaybackPresenter mPlaybackPresenter;
    private final DetailsPresenter mDetailsPresenter;
    private final MediaService mMediaService;
    private final ViewManager mViewManager;
    private final OnboardingPresenter mOnboardingPresenter;
    private MainView mView;
    private Header mHomeHeader;
    private Header mSearchHeader;
    private Header mSubscriptionsHeader;
    private Header mHistoryHeader;

    private MainPresenter(Context context) {
        GlobalPreferences.instance(context); // auth token storage
        mMediaGroups = new ArrayList<>();
        mContext = context;
        mPlaybackPresenter = PlaybackPresenter.instance(context);
        mDetailsPresenter = DetailsPresenter.instance(context);
        mOnboardingPresenter = OnboardingPresenter.instance(context);
        mMediaService = YouTubeMediaService.instance();
        mViewManager = ViewManager.instance(context);
        initHeaders();
    }

    public static MainPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new MainPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        if (mView == null) {
            return;
        }

        mOnboardingPresenter.showOnboarding();

        checkUserIsSigned();
        loadHome();
        loadSubscriptions();
        loadHistory();
    }

    @Override
    public void register(MainView view) {
        mView = view;
    }

    @Override
    public void unregister(MainView view) {
        mView = null;
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (mView == null) {
            return;
        }

        mPlaybackPresenter.openVideo(item);
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (mView == null) {
            return;
        }

        mDetailsPresenter.openVideo(item);
    }

    private void initHeaders() {
        mHomeHeader = new Header(MediaGroup.TYPE_HOME, mContext.getString(R.string.header_home), Header.TYPE_ROW);
        mSearchHeader = new Header(MediaGroup.TYPE_SEARCH, mContext.getString(R.string.header_search));
        mSubscriptionsHeader = new Header(MediaGroup.TYPE_SUBSCRIPTIONS, mContext.getString(R.string.header_subscriptions));
        mHistoryHeader = new Header(MediaGroup.TYPE_HISTORY, mContext.getString(R.string.header_history));
    }

    // TODO: implement Android TV channels
    //private void updateRecommendations() {
    //    Intent recommendationIntent = new Intent(mContext, UpdateRecommendationsService.class);
    //    mContext.startService(recommendationIntent);
    //}

    @SuppressLint("CheckResult")
    private void checkUserIsSigned() {
        SignInManager signInManager = mMediaService.getSignInManager();

        signInManager.isSignedObserve()
                .subscribeOn(Schedulers.newThread())
                .subscribe(isSigned -> {
                    if (!isSigned) {
                        signInManager.signInObserve()
                                .subscribeOn(Schedulers.newThread())
                                .subscribe(userCode -> Log.d(TAG, "User code is: " + userCode),
                                        error -> Log.e(TAG, error));
                    } else {
                        Log.d(TAG, "User already signed");
                    }
                }, error -> Log.e(TAG, "checkUserIsSigned: " + error));
    }

    @SuppressLint("CheckResult")
    private void loadHome() {
        mView.updateHeader(VideoGroup.from(mHomeHeader));

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        Log.d(TAG, "Start loading home...");

        mediaGroupManager.getHomeObserve()
                .subscribeOn(Schedulers.newThread())
                .subscribe(mediaGroups -> {

            if (mediaGroups == null) {
                Log.e(TAG, "Home groups not found");
                return;
            }

            Log.d(TAG, "Loading home groups...");

            for (MediaGroup mediaGroup : mediaGroups) {
                if (mediaGroup.getMediaItems() == null) {
                    Log.e(TAG, "MediaGroup is empty. Name: " + mediaGroup.getTitle());
                    continue;
                }

                mView.updateHeader(VideoGroup.from(mediaGroup, mHomeHeader));

                mMediaGroups.add(mediaGroup);
            }
        }, error -> Log.e(TAG, "loadHomeData: " + error));
    }

    @SuppressLint("CheckResult")
    private void loadSubscriptions() {
        mView.updateHeader(VideoGroup.from(mSubscriptionsHeader));

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mediaGroupManager.getSubscriptionsObserve()
                .subscribeOn(Schedulers.newThread())
                .subscribe(mediaGroup -> {
                    if (mediaGroup == null) {
                        Log.e(TAG, "Can't obtain subscriptions. User probably not logged in");
                        return;
                    }

                    mView.updateHeader(VideoGroup.from(mediaGroup, mSubscriptionsHeader));
                }, error -> Log.e(TAG, "loadSubscriptions: " + error));
    }

    @SuppressLint("CheckResult")
    private void loadHistory() {
        mView.updateHeader(VideoGroup.from(mHistoryHeader));

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mediaGroupManager.getHistoryObserve()
                .subscribeOn(Schedulers.newThread())
                .subscribe(mediaGroup -> {
                    if (mediaGroup == null) {
                        Log.e(TAG, "Can't obtain history. User probably not logged in");
                        return;
                    }

                    mView.updateHeader(VideoGroup.from(mediaGroup, mHistoryHeader));
                }, error -> Log.e(TAG, "loadHistory: " + error));
    }

    @SuppressLint("CheckResult")
    private void continueGroup(VideoGroup group) {
        // avoid to continue multiple times
        if (group.isContinued()) {
            return;
        } else {
            group.setContinued(true);
        }

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mediaGroupManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .subscribe(continueMediaGroup -> {
                    if (continueMediaGroup == null) {
                        Log.e(TAG, "Can't continue group with name " + mediaGroup.getTitle());
                        return;
                    }

                    mView.updateHeader(VideoGroup.from(continueMediaGroup, group.getHeader()));
        }, error -> Log.e(TAG, "continueGroup: " + error));
    }

    public void onScrollEnd(VideoGroup group) {
        continueGroup(group);
    }
}
