package org.jellyfin.androidtv.ui.playback.overlay;

import static java.lang.Math.round;

import android.content.Context;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.PlaybackTransportRowPresenter;
import androidx.leanback.widget.PlaybackTransportRowView;
import androidx.leanback.widget.RowPresenter;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.constant.ClockBehavior;
import org.jellyfin.androidtv.ui.playback.PlaybackController;
import org.jellyfin.androidtv.ui.playback.overlay.action.AndroidAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.ChannelBarChannelAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.ChapterAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.ClosedCaptionsAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.CustomAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.FastForwardAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.GuideAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.PlayPauseAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.PlaybackSpeedAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.PreviousLiveTvChannelAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.RecordAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.RewindAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.SelectAudioAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.SelectQualityAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.SkipNextAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.SkipPreviousAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.ZoomAction;
import org.jellyfin.androidtv.util.DateTimeExtensionsKt;
import org.jellyfin.androidtv.util.TimeUtils;
import org.koin.java.KoinJavaComponent;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class CustomPlaybackTransportControlGlue extends PlaybackTransportControlGlue<VideoPlayerAdapter> {

    // Normal playback actions
    private PlayPauseAction playPauseAction;
    private RewindAction rewindAction;
    private FastForwardAction fastForwardAction;
    private SkipPreviousAction skipPreviousAction;
    private SkipNextAction skipNextAction;
    private SelectAudioAction selectAudioAction;
    private ClosedCaptionsAction closedCaptionsAction;
    private SelectQualityAction selectQualityAction;
    private PlaybackSpeedAction playbackSpeedAction;
    private ZoomAction zoomAction;
    private ChapterAction chapterAction;

    // TV actions
    private PreviousLiveTvChannelAction previousLiveTvChannelAction;
    private ChannelBarChannelAction channelBarChannelAction;
    private GuideAction guideAction;
    private RecordAction recordAction;

    private final PlaybackController playbackController;
    private ArrayObjectAdapter primaryActionsAdapter;
    private ArrayObjectAdapter secondaryActionsAdapter;

    // Injected views
    private TextView mEndsText = null;
    private TextView mRemainingText = null;
    private org.jellyfin.androidtv.ui.playback.overlay.action.FavoriteAction favoriteAction;

    private final Handler mHandler = new Handler();
    private Runnable mRefreshEndTime;
    private Runnable mRefreshViewVisibility;

    private LinearLayout mButtonRef;

    CustomPlaybackTransportControlGlue(Context context, VideoPlayerAdapter playerAdapter, PlaybackController playbackController) {
        super(context, playerAdapter);
        this.playbackController = playbackController;

        mRefreshEndTime = () -> {
            setEndTime();
            if (!isPlaying()) {
                mHandler.postDelayed(mRefreshEndTime, 30000);
            }
        };

        mRefreshViewVisibility = () -> {
            if (mButtonRef == null || mEndsText == null) return;
            if (mButtonRef.getVisibility() != mEndsText.getVisibility())
                mEndsText.setVisibility(mButtonRef.getVisibility());
            else
                mHandler.postDelayed(mRefreshViewVisibility, 100);
        };

        initActions(context);
    }

    @Override
    protected void onDetachedFromHost() {
        mHandler.removeCallbacks(mRefreshEndTime);
        mHandler.removeCallbacks(mRefreshViewVisibility);

        closedCaptionsAction.removePopup();
        playbackSpeedAction.dismissPopup();
        selectAudioAction.dismissPopup();
        selectQualityAction.dismissPopup();
        zoomAction.dismissPopup();

        super.onDetachedFromHost();
    }

    @Override
    protected PlaybackRowPresenter onCreateRowPresenter() {
        final AbstractDetailsDescriptionPresenter detailsPresenter = new AbstractDetailsDescriptionPresenter() {
            @Override
            protected void onBindDescription(ViewHolder vh, Object item) {

            }
        };
        PlaybackTransportRowPresenter rowPresenter = new PlaybackTransportRowPresenter() {
            @Override
            protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
                RowPresenter.ViewHolder vh = super.createRowViewHolder(parent);

                ClockBehavior showClock = KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getClockBehavior());
                View root = vh.view;

                // JellyChan: "ends at" sits next to the primary controls (see lb_playback_transport_controls_row)
                mEndsText = root.findViewById(R.id.jc_ends_text);
                if (mEndsText != null) {
                    if (showClock == ClockBehavior.ALWAYS || showClock == ClockBehavior.IN_VIDEO) setEndTime();
                    else mEndsText.setVisibility(View.GONE);
                }

                // JellyChan: remaining time on the right of the seek bar, follows Leanback's current time
                TextView currentTime = root.findViewById(androidx.leanback.R.id.current_time);
                mRemainingText = root.findViewById(R.id.jc_remaining_time);
                if (currentTime != null && mRemainingText != null) {
                    currentTime.addTextChangedListener(new android.text.TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                        @Override public void afterTextChanged(android.text.Editable s) { updateRemainingTime(); }
                    });
                }

                // JellyChan: hide "ends at" together with the controls while seeking
                ViewGroup controlsDock = root.findViewById(androidx.leanback.R.id.controls_dock);
                if (controlsDock != null && mEndsText != null && mEndsText.getVisibility() != View.GONE) {
                    final TextView endsText = mEndsText;
                    final View thumbsRow = root.findViewById(androidx.leanback.R.id.thumbs_row);
                    root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                        boolean seeking = thumbsRow != null && thumbsRow.getVisibility() == View.VISIBLE;
                        int target = seeking ? View.INVISIBLE : View.VISIBLE;
                        if (endsText.getVisibility() != target) endsText.setVisibility(target);
                    });
                }

                // JellyChan: icon sizes in web player proportions, play/pause bigger than the rest
                final ViewGroup secondaryDock = root.findViewById(androidx.leanback.R.id.secondary_controls_dock);
                if (controlsDock != null) {
                    final float d = root.getResources().getDisplayMetrics().density;
                    root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                        if (primaryActionsAdapter == null) return;
                        int index = primaryActionsAdapter.indexOf(playPauseAction);
                        resizeIcons(findControlBar(controlsDock), index, Math.round(17 * d), Math.round(30 * d));
                        if (secondaryDock != null) resizeIcons(findControlBar(secondaryDock), -1, Math.round(18 * d), 0);
                    });
                }

                // JellyChan: single trickplay preview that follows the seek position
                setupSingleThumb(root);
                return vh;
            }

            @Override
            protected void onProgressBarClicked(PlaybackTransportRowPresenter.ViewHolder vh) {
                CustomPlaybackTransportControlGlue controlglue = CustomPlaybackTransportControlGlue.this;
                controlglue.onActionClicked(controlglue.playPauseAction);
            }

            @Override
            protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
                super.onBindRowViewHolder(vh, item);
                vh.setOnKeyListener(CustomPlaybackTransportControlGlue.this);
            }

            @Override
            protected void onUnbindRowViewHolder(RowPresenter.ViewHolder vh) {
                super.onUnbindRowViewHolder(vh);
                vh.setOnKeyListener(null);
            }
        };
        rowPresenter.setDescriptionPresenter(detailsPresenter);
        // JellyChan: light seek bar on a translucent grey track
        rowPresenter.setProgressColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.jc_text));
        rowPresenter.setSecondaryProgressColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.jc_track));
        return rowPresenter;
    }

    private void initActions(Context context) {
        playPauseAction = new PlayPauseAction(context);
        rewindAction = new RewindAction(context);
        fastForwardAction = new FastForwardAction(context);
        skipPreviousAction = new SkipPreviousAction(context);
        skipNextAction = new SkipNextAction(context);
        selectAudioAction = new SelectAudioAction(context, this);
        selectAudioAction.setLabels(new String[]{context.getString(R.string.lbl_audio_track)});
        closedCaptionsAction = new ClosedCaptionsAction(context, this);
        closedCaptionsAction.setLabels(new String[]{context.getString(R.string.lbl_subtitle_track)});
        selectQualityAction = new SelectQualityAction(context, this, KoinJavaComponent.get(UserPreferences.class));
        selectQualityAction.setLabels(new String[]{context.getString(R.string.lbl_quality_profile)});
        playbackSpeedAction = new PlaybackSpeedAction(context, this, playbackController);
        playbackSpeedAction.setLabels(new String[]{context.getString(R.string.lbl_playback_speed)});
        zoomAction = new ZoomAction(context, this);
        zoomAction.setLabels(new String[]{context.getString(R.string.lbl_zoom)});
        chapterAction = new ChapterAction(context, this);
        favoriteAction = new org.jellyfin.androidtv.ui.playback.overlay.action.FavoriteAction(context, this);
        chapterAction.setLabels(new String[]{context.getString(R.string.lbl_chapters)});

        previousLiveTvChannelAction = new PreviousLiveTvChannelAction(context, this);
        previousLiveTvChannelAction.setLabels(new String[]{context.getString(R.string.lbl_prev_item)});
        channelBarChannelAction = new ChannelBarChannelAction(context, this);
        channelBarChannelAction.setLabels(new String[]{context.getString(R.string.lbl_other_channels)});
        guideAction = new GuideAction(context, this);
        guideAction.setLabels(new String[]{context.getString(R.string.lbl_live_tv_guide)});
        recordAction = new RecordAction(context, this);
        recordAction.setLabels(new String[]{
                context.getString(R.string.lbl_record),
                context.getString(R.string.lbl_cancel_recording)
        });
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter primaryActionsAdapter) {
        this.primaryActionsAdapter = primaryActionsAdapter;
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter secondaryActionsAdapter) {
        this.secondaryActionsAdapter = secondaryActionsAdapter;
    }

    void addMediaActions() {
        if (primaryActionsAdapter.size() > 0)
            primaryActionsAdapter.clear();
        if (secondaryActionsAdapter.size() > 0)
            secondaryActionsAdapter.clear();

        // JellyChan: transport controls on the left (like the web player), tools on the right
        VideoPlayerAdapter playerAdapter = getPlayerAdapter();

        // Primary Items (episodes always get previous/next: missing ones are fetched from the series)
        boolean isEpisode = playbackController.getCurrentlyPlayingItem() != null
                && playbackController.getCurrentlyPlayingItem().getType() == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE;
        if (playerAdapter.hasPreviousItem() || isEpisode) {
            primaryActionsAdapter.add(skipPreviousAction);
        }

        if (playerAdapter.canSeek()) {
            primaryActionsAdapter.add(rewindAction);
        }

        primaryActionsAdapter.add(playPauseAction);

        if (playerAdapter.canSeek()) {
            primaryActionsAdapter.add(fastForwardAction);
        }

        if (playerAdapter.hasNextItem() || isEpisode) {
            primaryActionsAdapter.add(skipNextAction);
        }

        // Secondary Items
        if (playerAdapter.isLiveTv()) {
            secondaryActionsAdapter.add(channelBarChannelAction);
            secondaryActionsAdapter.add(guideAction);
            secondaryActionsAdapter.add(previousLiveTvChannelAction);
            if (playerAdapter.canRecordLiveTv()) {
                secondaryActionsAdapter.add(recordAction);
                recordingStateChanged();
            }
        }

        // JellyChan: favorite, subtitles and audio are always available for videos (like the web player)
        if (!playerAdapter.isLiveTv()) {
            favoriteAction.setFavorite(favoriteAction.isFavorite(playbackController.getCurrentlyPlayingItem()));
            secondaryActionsAdapter.add(favoriteAction);
        }

        if (playerAdapter.hasSubs() || !playerAdapter.isLiveTv()) {
            secondaryActionsAdapter.add(closedCaptionsAction);
        }

        if (playerAdapter.hasMultiAudio() || !playerAdapter.isLiveTv()) {
            secondaryActionsAdapter.add(selectAudioAction);
        }

        if (playerAdapter.hasChapters()) {
            secondaryActionsAdapter.add(chapterAction);
        }

        if (!playerAdapter.isLiveTv()) {
            secondaryActionsAdapter.add(playbackSpeedAction);
            secondaryActionsAdapter.add(selectQualityAction);
        }

        secondaryActionsAdapter.add(zoomAction);
    }

    @Override
    public void onActionClicked(Action action) {
        if (action instanceof AndroidAction) {
            ((AndroidAction) action).onActionClicked(getPlayerAdapter());
        }
        notifyActionChanged(action);
    }

    public void onCustomActionClicked(Action action, View view) {
        // Handle custom action clicks which require a popup menu
        if (action instanceof CustomAction) {
            ((CustomAction) action).handleClickAction(playbackController, getPlayerAdapter(), getContext(), view);
        }

        if (action == playbackSpeedAction) {
            // Post a callback to calculate the new time, since Exoplayer updates this in an async fashion.
            // This is a hack, we should instead have onPlaybackParametersChanged call out to this
            // class to notify rather than poll. But communication is unidirectional at the moment:
            mHandler.postDelayed(mRefreshEndTime, 5000);  // 5 seconds
        }
    }

    private static void resizeIcons(ViewGroup bar, int bigIndex, int size, int bigSize) {
        if (bar == null) return;
        for (int i = 0; i < bar.getChildCount(); i++) {
            View icon = bar.getChildAt(i).findViewById(androidx.leanback.R.id.icon);
            if (icon == null) continue;
            int target = i == bigIndex ? bigSize : size;
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            if (lp.width != target) {
                lp.width = target;
                lp.height = target;
                icon.setLayoutParams(lp);
            }
        }
    }

    private static ViewGroup findControlBar(View v) {
        if (v instanceof ViewGroup && v.getClass().getSimpleName().equals("ControlBar")) return (ViewGroup) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                ViewGroup r = findControlBar(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private void updateRemainingTime() {
        if (mRemainingText == null) return;
        long duration = getPlayerAdapter().getDuration();
        if (duration < 1) {
            mRemainingText.setText("");
            return;
        }
        long left = Math.max(duration - getPlayerAdapter().getCurrentPosition(), 0);
        mRemainingText.setText("-" + TimeUtils.formatMillis(left));
    }

    private void setupSingleThumb(View root) {
        final androidx.leanback.widget.ThumbsBar thumbs = root.findViewById(androidx.leanback.R.id.thumbs_row);
        final androidx.leanback.widget.SeekBar seekBar = root.findViewById(androidx.leanback.R.id.playback_progress);
        if (thumbs == null || seekBar == null) return;
        final float density = root.getResources().getDisplayMetrics().density;
        final int w = Math.round(240 * density);
        final int h = Math.round(135 * density);
        thumbs.setNumberOfThumbs(1);
        thumbs.setHeroThumbSize(w, h);
        thumbs.setThumbSize(w, h);
        thumbs.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (thumbs.getVisibility() != View.VISIBLE || thumbs.getChildCount() == 0) return true;
            // Rounded corners on the preview
            for (int i = 0; i < thumbs.getChildCount(); i++) {
                View child = thumbs.getChildAt(i);
                if (child instanceof android.widget.ImageView) ((android.widget.ImageView) child).setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                if (!child.getClipToOutline()) {
                    child.setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 12 * density);
                        }
                    });
                    child.setClipToOutline(true);
                }
            }
            // Follow the seek position, clamped to the screen
            int[] seekLoc = new int[2];
            int[] barLoc = new int[2];
            seekBar.getLocationInWindow(seekLoc);
            thumbs.getLocationInWindow(barLoc);
            int max = Math.max(seekBar.getMax(), 1);
            float x = seekLoc[0] + seekBar.getWidth() * (seekBar.getProgress() / (float) max);
            float center = barLoc[0] - thumbs.getTranslationX() + thumbs.getWidth() / 2f;
            float half = w / 2f + 40 * density;
            float minX = barLoc[0] - thumbs.getTranslationX() + half;
            float maxX = barLoc[0] - thumbs.getTranslationX() + thumbs.getWidth() - half;
            float target = Math.max(minX, Math.min(maxX, x));
            float tx = target - center;
            if (Math.abs(thumbs.getTranslationX() - tx) > 0.5f) thumbs.setTranslationX(tx);
            return true;
        });
    }

    private void setEndTime() {
        if (mEndsText == null || getPlayerAdapter().getDuration() < 1)
            return;
        long msLeft = getPlayerAdapter().getDuration() - getPlayerAdapter().getCurrentPosition();
        long realTimeLeft = round(msLeft / playbackController.getPlaybackSpeed());

        LocalDateTime endTime = LocalDateTime.now().plus(realTimeLeft, ChronoUnit.MILLIS);
        mEndsText.setText(getContext().getString(R.string.lbl_playback_control_ends, DateTimeExtensionsKt.getTimeFormatter(getContext()).format(endTime)));
    }

    public void notifyActionChangedPublic(Action action) {
        if (primaryActionsAdapter == null || secondaryActionsAdapter == null) return;
        notifyActionChanged(action);
    }

    private void notifyActionChanged(Action action) {
        ArrayObjectAdapter adapter = primaryActionsAdapter;
        if (adapter.indexOf(action) >= 0) {
            adapter.notifyArrayItemRangeChanged(adapter.indexOf(action), 1);
            return;
        }
        adapter = secondaryActionsAdapter;
        if (adapter.indexOf(action) >= 0) {
            adapter.notifyArrayItemRangeChanged(adapter.indexOf(action), 1);
        }
    }

    void setInitialPlaybackDrawable() {
        playPauseAction.setIndex(PlaybackControlsRow.PlayPauseAction.INDEX_PAUSE);
        notifyActionChanged(playPauseAction);
    }

    void invalidatePlaybackControls() {
        if (primaryActionsAdapter.size() > 0)
            primaryActionsAdapter.clear();
        if (secondaryActionsAdapter.size() > 0)
            secondaryActionsAdapter.clear();
        addMediaActions();
    }

    void recordingStateChanged() {
        if (getPlayerAdapter().isRecording()) {
            recordAction.setIndex(RecordAction.INDEX_RECORDING);
        } else {
            recordAction.setIndex(RecordAction.INDEX_INACTIVE);
        }
        notifyActionChanged(recordAction);
    }

    void updatePlayState() {
        playPauseAction.setIndex(isPlaying() ? PlaybackControlsRow.PlayPauseAction.INDEX_PAUSE : PlaybackControlsRow.PlayPauseAction.INDEX_PLAY);
        notifyActionChanged(playPauseAction);
        setEndTime();
        if (!isPlaying()) {
            mHandler.removeCallbacks(mRefreshEndTime);
            mHandler.postDelayed(mRefreshEndTime, 30000);
        } else {
            mHandler.removeCallbacks(mRefreshEndTime);
        }

    }

    public void setInjectedViewsVisibility() {
        if (mButtonRef != null && mButtonRef.getVisibility() != mEndsText.getVisibility())
            mEndsText.setVisibility(mButtonRef.getVisibility());
        mHandler.removeCallbacks(mRefreshViewVisibility);
        mHandler.postDelayed(mRefreshViewVisibility, 100);
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            // Let the playback glue handle the key before applying the focus fallback below.
            if (super.onKey(v, keyCode, event)) return true;

            // Leanback UI doesn't work within compose. We need to implement a custom focus handler
            // to work around various leanback issues on older Android platforms
            if (event.getAction() == KeyEvent.ACTION_DOWN) return moveFocus(v, keyCode);
            return false;
        }

        VideoPlayerAdapter playerAdapter = getPlayerAdapter();

        if (playerAdapter.hasSubs() && keyCode == KeyEvent.KEYCODE_CAPTIONS) {
            closedCaptionsAction.handleClickAction(playbackController, getPlayerAdapter(), getContext(), v);
        }
        if (playerAdapter.hasMultiAudio() && keyCode == KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK) {
            selectAudioAction.handleClickAction(playbackController, getPlayerAdapter(), getContext(), v);
        }
        return super.onKey(v, keyCode, event);
    }

    private boolean moveFocus(View root, int keyCode) {
        final int direction;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                direction = View.FOCUS_UP;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                direction = View.FOCUS_DOWN;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                direction = View.FOCUS_LEFT;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                direction = View.FOCUS_RIGHT;
                break;
            default:
                return false;
        }

        View focused = root.findFocus();
        if (focused == null) return false;

        View next = focused.focusSearch(direction);
        if (next != null && next != focused) next.requestFocus(direction);

        return true;
    }
}
