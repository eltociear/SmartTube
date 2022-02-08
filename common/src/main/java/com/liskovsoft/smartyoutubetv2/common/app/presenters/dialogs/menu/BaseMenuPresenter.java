package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;

public abstract class BaseMenuPresenter extends BasePresenter<Void> {
    private final MediaServiceManager mServiceManager;

    protected BaseMenuPresenter(Context context) {
        super(context);
        mServiceManager = MediaServiceManager.instance();
    }

    protected abstract Video getVideo();
    protected abstract AppDialogPresenter getDialogPresenter();
    protected abstract boolean isPinToSidebarEnabled();
    protected abstract boolean isAccountSelectionEnabled();

    public void closeDialog() {
        if (getDialogPresenter() != null) {
            getDialogPresenter().closeDialog();
        }
        MessageHelpers.cancelToasts();
    }

    protected void appendTogglePinToSidebarButton() {
        appendTogglePinToSidebarButton(getContext().getString(R.string.pin_unpin_from_sidebar), false);
    }

    protected void appendTogglePinToSidebarButton(String buttonTitle, boolean autoCloseDialog) {
        if (!isPinToSidebarEnabled()) {
            return;
        }

        Video original = getVideo();

        if (original == null || (!original.hasPlaylist() && !original.isPlaylist() && !original.hasReloadPageKey() && !original.isChannel())) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(buttonTitle,
                        optionItem -> {
                            if (original.hasPlaylist() || original.isPlaylist() || original.hasReloadPageKey() || original.isChannel()) {
                                togglePinToSidebar(createPinnedSection(original));
                                if (autoCloseDialog) {
                                    getDialogPresenter().closeDialog();
                                }
                            }
                        }));
    }
    
    private void togglePinToSidebar(Video section) {
        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        // Toggle between pin/unpin while dialog is opened
        boolean isItemPinned = presenter.isItemPinned(section);

        if (isItemPinned) {
            presenter.unpinItem(section);
        } else {
            presenter.pinItem(section);
        }
        MessageHelpers.showMessage(getContext(), isItemPinned ? R.string.unpinned_from_sidebar : R.string.pinned_to_sidebar);
    }

    private Video createPinnedSection(Video video) {
        if (video == null || (!video.hasPlaylist() && !video.isPlaylist() && !video.hasReloadPageKey() && !video.isChannel())) {
            return null;
        }

        Video section = new Video();
        section.playlistId = video.playlistId;
        section.playlistParams = video.playlistParams;
        section.channelId = video.channelId;
        section.reloadPageKey = video.getReloadPageKey();
        section.itemType = video.itemType;
        // Trying to properly format channel playlists, mixes etc
        boolean isChannelItem = (video.hasChannel() && !video.hasPlaylist() && !video.isChannel()) || (video.getGroupTitle() != null && video.belongsToSameAuthorGroup() && video.belongsToSamePlaylistGroup());
        boolean isUserPlaylistItem = video.getGroupTitle() != null && video.belongsToSamePlaylistGroup();
        String title = isChannelItem ? video.extractAuthor() : isUserPlaylistItem ? null : video.title;
        String subtitle = isChannelItem || isUserPlaylistItem ? video.getGroupTitle() : video.description;
        section.title = title != null && subtitle != null ? String.format("%s - %s", title, subtitle) : String.format("%s", title != null ? title : subtitle);
        section.cardImageUrl = video.cardImageUrl;

        return section;
    }

    protected void appendAccountSelectionButton() {
        if (!isAccountSelectionEnabled()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.dialog_account_list), optionItem -> {
                    AccountSelectionPresenter.instance(getContext()).show(true);
                }));
    }
}
