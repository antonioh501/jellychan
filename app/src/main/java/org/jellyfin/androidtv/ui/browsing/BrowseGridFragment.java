package org.jellyfin.androidtv.ui.browsing;

import static org.koin.java.KoinJavaComponent.inject;

import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import org.koin.java.KoinJavaComponent;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.lifecycle.Lifecycle;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.constant.ChangeTriggerType;
import org.jellyfin.androidtv.constant.CustomMessage;
import org.jellyfin.androidtv.constant.Extras;
import org.jellyfin.androidtv.constant.GridDirection;
import org.jellyfin.androidtv.constant.ImageType;
import org.jellyfin.androidtv.constant.PosterSize;
import org.jellyfin.androidtv.constant.QueryType;
import org.jellyfin.androidtv.data.model.FilterOptions;
import org.jellyfin.androidtv.data.querying.GetUserViewsRequest;
import org.jellyfin.androidtv.data.repository.CustomMessageRepository;
import org.jellyfin.androidtv.data.service.BackgroundService;
import org.jellyfin.androidtv.databinding.HorizontalGridBrowseBinding;
import org.jellyfin.androidtv.databinding.PopupEmptyBinding;
import org.jellyfin.androidtv.preference.LibraryPreferences;
import org.jellyfin.androidtv.preference.PreferencesRepository;
import org.jellyfin.androidtv.ui.AlphaPickerView;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapterHelperKt;
import org.jellyfin.androidtv.ui.navigation.Destinations;
import org.jellyfin.androidtv.ui.navigation.NavigationRepository;
import org.jellyfin.androidtv.ui.presentation.CardPresenter;
import org.jellyfin.androidtv.ui.presentation.HorizontalGridPresenter;
import org.jellyfin.androidtv.util.CoroutineUtils;
import org.jellyfin.androidtv.util.ImageHelper;
import org.jellyfin.androidtv.util.InfoLayoutHelper;
import org.jellyfin.androidtv.util.KeyProcessor;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.androidtv.util.apiclient.EmptyResponse;
import org.jellyfin.sdk.api.client.ApiClient;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.jellyfin.sdk.model.api.CollectionType;
import org.jellyfin.sdk.model.api.ItemSortBy;
import org.jellyfin.sdk.model.api.SortOrder;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import kotlin.Lazy;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.serialization.json.Json;
import timber.log.Timber;

public class BrowseGridFragment extends Fragment implements View.OnKeyListener {
    private final static int CHUNK_SIZE_MINIMUM = 25;

    private String mainTitle;
    private FragmentActivity mActivity;
    private BaseRowItem mCurrentItem;
    private CompositeClickedListener mClickedListener = new CompositeClickedListener();
    private CompositeSelectedListener mSelectedListener = new CompositeSelectedListener();
    private final Handler mHandler = new Handler();
    private int mCardHeight;
    private BrowseRowDef mRowDef;
    private CardPresenter mCardPresenter;

    private boolean justLoaded = true;
    private PosterSize mPosterSizeSetting = PosterSize.MED;
    private ImageType mImageType = ImageType.POSTER;
    private GridDirection mGridDirection = GridDirection.HORIZONTAL;
    private boolean determiningPosterSize = false;

    private UUID mParentId;
    private BaseItemDto mFolder;
    private LibraryPreferences libraryPreferences;

    private HorizontalGridBrowseBinding binding;
    private ItemRowAdapter mAdapter;
    private Presenter mGridPresenter;
    private Presenter.ViewHolder mGridViewHolder;
    private BaseGridView mGridView;
    private int mSelectedPosition = -1;
    private int mGridHeight = -1;
    private int mGridWidth = -1;
    private int mGridItemSpacingHorizontal = 0;
    private int mGridItemSpacingVertical = 0;
    private int mGridPaddingLeft = 0;
    private int mGridPaddingTop = 0;

    private final Lazy<BackgroundService> backgroundService = inject(BackgroundService.class);
    private final Lazy<PreferencesRepository> preferencesRepository = inject(PreferencesRepository.class);
    private final Lazy<CustomMessageRepository> customMessageRepository = inject(CustomMessageRepository.class);
    private final Lazy<ItemLauncher> itemLauncher = inject(ItemLauncher.class);
    private final Lazy<KeyProcessor> keyProcessor = inject(KeyProcessor.class);
    private final Lazy<ApiClient> api = inject(ApiClient.class);
    private final Lazy<NavigationRepository> navigationRepository = inject(NavigationRepository.class);

    private int mCardsScreenEst = 0;
    private int mCardsScreenStride = 0;
    private double mCardFocusScale = 1.15; // 115%, just a default we use the resource card_scale_focus otherwise
    private final int MIN_NUM_CARDS = 5; // minimum number of visible cards we allow, this results in more empty space
    private final double CARD_SPACING_PCT = 2.5; // JellyChan: was 1.0 // 100% expressed as relative to the padding_left/top, which depends on the mCardFocusScale and AspectRatio
    private final double CARD_SPACING_HORIZONTAL_BANNER_PCT = 0.5; // 50% allow horizontal card overlapping for banners, otherwise spacing is too large
    private final int VIEW_SELECT_UPDATE_DELAY = 250; // delay in ms until we update the top-row info for a selected item

    private boolean mDirty = true; // CardHeight, RowDef or GridSize changed

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // init with some working defaults
        DisplayMetrics display = requireContext().getResources().getDisplayMetrics();
        // top + bottom in dp, elements scale with density so adjust accordingly
        mGridHeight = Math.round(display.heightPixels / getResources().getDisplayMetrics().density - 175f); // JellyChan header
        mGridWidth = Math.round(display.widthPixels / getResources().getDisplayMetrics().density) - 90; // JellyChan: A-Z index + web-like side margins


        mActivity = getActivity();

        mFolder = Json.Default.decodeFromString(BaseItemDto.Companion.serializer(), getArguments().getString(Extras.Folder));
        mParentId = mFolder.getId();
        mainTitle = mFolder.getName();
        libraryPreferences = preferencesRepository.getValue().getLibraryPreferences(Objects.requireNonNull(mFolder.getDisplayPreferencesId()));
        mPosterSizeSetting = libraryPreferences.get(LibraryPreferences.Companion.getPosterSize());
        mImageType = libraryPreferences.get(LibraryPreferences.Companion.getImageType());
        mGridDirection = libraryPreferences.get(LibraryPreferences.Companion.getGridDirection());
        // JellyChan: lay the grid out as if the focus zoom were 12% larger, so cards get
        // more breathing room and the zoomed first row is never clipped at the top
        mCardFocusScale = getResources().getFraction(R.fraction.card_scale_focus, 1, 1) + 0.12;

        if (mGridDirection.equals(GridDirection.VERTICAL))
        {
            // JellyChan: flat cards, no focus shadow box behind the title
            VerticalGridPresenter vgp = new VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_LARGE, false);
            vgp.setShadowEnabled(false);
            setGridPresenter(vgp);
        }
        else
            setGridPresenter(new HorizontalGridPresenter());

        sortOptions = new HashMap<>();
        {
            boolean isTvShows = mFolder.getCollectionType() == CollectionType.TVSHOWS;

            sortOptions.put(0, new SortOption(getString(R.string.lbl_name), ItemSortBy.SORT_NAME, SortOrder.ASCENDING));
            if (isTvShows) {
                sortOptions.put(1, new SortOption(getString(R.string.lbl_date_show_added), ItemSortBy.DATE_CREATED, SortOrder.DESCENDING));
            } else {
                sortOptions.put(1, new SortOption(getString(R.string.lbl_date_added), ItemSortBy.DATE_CREATED, SortOrder.DESCENDING));
            }
            sortOptions.put(2, new SortOption(getString(R.string.lbl_premier_date), ItemSortBy.PREMIERE_DATE, SortOrder.DESCENDING));
            sortOptions.put(3, new SortOption(getString(R.string.lbl_rating), ItemSortBy.OFFICIAL_RATING, SortOrder.ASCENDING));
            sortOptions.put(4, new SortOption(getString(R.string.lbl_community_rating), ItemSortBy.COMMUNITY_RATING, SortOrder.DESCENDING));
            sortOptions.put(5, new SortOption(getString(R.string.lbl_critic_rating), ItemSortBy.CRITIC_RATING, SortOrder.DESCENDING));

            if (isTvShows) {
                sortOptions.put(6, new SortOption(getString(R.string.lbl_last_played), ItemSortBy.SERIES_DATE_PLAYED, SortOrder.DESCENDING));
                sortOptions.put(7, new SortOption(getString(R.string.lbl_date_episode_added), ItemSortBy.DATE_LAST_CONTENT_ADDED, SortOrder.DESCENDING));
            } else {
                sortOptions.put(6, new SortOption(getString(R.string.lbl_last_played), ItemSortBy.DATE_PLAYED, SortOrder.DESCENDING));
                if (mFolder.getCollectionType() == CollectionType.MOVIES) {
                    sortOptions.put(7, new SortOption(getString(R.string.lbl_runtime), ItemSortBy.RUNTIME, SortOrder.ASCENDING));
                }
            }
        }

        setDefaultGridRowCols(mPosterSizeSetting, mImageType);
        setAutoCardGridValues();
        setupQueries();
        setupEventListeners();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = HorizontalGridBrowseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        createGrid();
        loadGrid();
        addTools();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;
        return keyProcessor.getValue().handleKey(keyCode, mCurrentItem, mActivity);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        binding = null;
        mGridView = null;
    }

    private void createGrid() {
        mGridViewHolder = mGridPresenter.onCreateViewHolder(binding.rowsFragment);
        if (mGridViewHolder instanceof HorizontalGridPresenter.ViewHolder) {
            mGridView = ((HorizontalGridPresenter.ViewHolder) mGridViewHolder).getGridView();
            mGridView.setGravity(Gravity.CENTER_VERTICAL);
            ViewGroup.MarginLayoutParams titleMargin = (ViewGroup.MarginLayoutParams) binding.title.getLayoutParams();
            ViewGroup.MarginLayoutParams clockMargin = (ViewGroup.MarginLayoutParams) binding.clock.getLayoutParams();
            mGridView.setPadding(titleMargin.getMarginStart(), mGridPaddingTop, clockMargin.getMarginEnd(), mGridPaddingTop); // prevent initial card cutoffs
        } else if (mGridViewHolder instanceof VerticalGridPresenter.ViewHolder) {
            mGridView = ((VerticalGridPresenter.ViewHolder) mGridViewHolder).getGridView();
            mGridView.setGravity(Gravity.CENTER_HORIZONTAL);
            mGridView.setPadding(mGridPaddingLeft, mGridPaddingTop, mGridPaddingLeft, mGridPaddingTop); // prevent initial card cutoffs
        }
        // JellyChan: spacing values are computed in dp, apply them in pixels
        mGridView.setHorizontalSpacing(Utils.convertDpToPixel(requireContext(), mGridItemSpacingHorizontal));
        mGridView.setVerticalSpacing(mGridItemSpacingVertical);
        mGridView.setFocusable(true);
        mGridView.setOnKeyInterceptListener(this::moveToShorterLastLine);
        binding.rowsFragment.removeAllViews();
        binding.rowsFragment.addView(mGridViewHolder.view);

        updateAdapter();
    }

    // JellyChan: the last line of a grid can be shorter, go to its last card instead of getting stuck
    private boolean moveToShorterLastLine(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || mGridView.getAdapter() == null) return false;

        boolean vertical = mGridPresenter instanceof VerticalGridPresenter;
        if (event.getKeyCode() != (vertical ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_RIGHT)) return false;

        int lineSize = vertical
                ? ((VerticalGridPresenter) mGridPresenter).getNumberOfColumns()
                : ((HorizontalGridPresenter) mGridPresenter).getNumberOfRows();
        int position = mGridView.getSelectedPosition();
        int lastPosition = mGridView.getAdapter().getItemCount() - 1;
        if (lineSize < 1 || position < 0 || position + lineSize <= lastPosition) return false;
        if (position / lineSize == lastPosition / lineSize) return false;

        mGridView.setSelectedPositionSmooth(lastPosition);
        return true;
    }

    private void updateAdapter() {
        if (mGridView != null) {
            mGridPresenter.onBindViewHolder(mGridViewHolder, mAdapter);
            if (mSelectedPosition != -1) {
                mGridView.setSelectedPosition(mSelectedPosition);
            }
        }
    }

    /**
     * Sets the grid presenter.
     */
    public void setGridPresenter(HorizontalGridPresenter gridPresenter) {
        if (gridPresenter == null) {
            throw new IllegalArgumentException("Grid presenter may not be null");
        }
        gridPresenter.setOnItemViewSelectedListener(mRowSelectedListener);
        gridPresenter.setOnItemViewClickedListener(mClickedListener);
        gridPresenter.setShadowEnabled(false);
        mGridPresenter = gridPresenter;
    }

    /**
     * Sets the grid presenter.
     */
    public void setGridPresenter(VerticalGridPresenter gridPresenter) {
        if (gridPresenter == null) {
            throw new IllegalArgumentException("Grid presenter may not be null");
        }
        gridPresenter.setOnItemViewSelectedListener(mRowSelectedListener);
        gridPresenter.setOnItemViewClickedListener(mClickedListener);
        gridPresenter.setShadowEnabled(false);
        mGridPresenter = gridPresenter;
    }


    public void setItem(BaseRowItem item) {
        if (item != null) {
            // JellyChan: the header keeps the library name
        } else {
            binding.title.setText("");
            binding.infoRow.removeAllViews();
        }
    }

    public class SortOption {
        public String name;
        public ItemSortBy value;
        public SortOrder order;

        public SortOption(String name, ItemSortBy value, SortOrder order) {
            this.name = name;
            this.value = value;
            this.order = order;
        }
    }

    private Map<Integer, SortOption> sortOptions;

    private SortOption getSortOption(ItemSortBy value) {
        for (SortOption sortOption : sortOptions.values()) {
            if (sortOption.value.equals(value)) return sortOption;
        }

        return new SortOption(getString(R.string.lbl_bracket_unknown), ItemSortBy.SORT_NAME, SortOrder.ASCENDING);
    }

    public void setStatusText(String folderName) {
        String text = getString(R.string.lbl_showing) + " ";
        FilterOptions filters = mAdapter.getFilters();
        if (filters == null || (!filters.isFavoriteOnly() && !filters.isUnwatchedOnly())) {
            text += getString(R.string.lbl_all_items);
        } else {
            text += (filters.isUnwatchedOnly() ? getString(R.string.lbl_unwatched) : "") + " " +
                    (filters.isFavoriteOnly() ? getString(R.string.lbl_favorites) : "");
        }

        if (mAdapter.getStartLetter() != null) {
            text += " " + getString(R.string.lbl_starting_with) + " " + mAdapter.getStartLetter();
        }

        text += " " + getString(R.string.lbl_from) + " '" + folderName + "' " + getString(R.string.lbl_sorted_by) + " " + getSortOption(mAdapter.getSortBy()).name;

        binding.statusText.setText(text);
    }

    final private OnItemViewSelectedListener mRowSelectedListener =
            new OnItemViewSelectedListener() {
                @Override
                public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                           RowPresenter.ViewHolder rowViewHolder, Row row) {
                    int position = mGridView.getSelectedPosition();
                    Timber.d("row selected position %s", position);
                    if (position != mSelectedPosition) {
                        mSelectedPosition = position;
                    }
                    // Update the counter
                    updateCounter(position + 1);
                    if (position >= 0) {
                        mSelectedListener.onItemSelected(itemViewHolder, item, rowViewHolder, row);
                    }
                }
            };

    public void updateCounter(int position) {
        if (mAdapter != null) {
            int total = mAdapter.getTotalItems();
            binding.counter.setText(total > 0 ? getString(R.string.jc_counter, total) : "");
        }
    }

    private void setRowDef(final BrowseRowDef rowDef) {
        if (mRowDef == null || mRowDef.hashCode() != rowDef.hashCode()) {
            mDirty = true;
        }
        mRowDef = rowDef;
    }

    private double getCardWidthBy(final double cardHeight, ImageType imageType, BaseItemDto folder) {
        switch (imageType) {
            case POSTER:
                // special handling for square posters
                BaseItemKind fType = folder.getType();
                if (fType == BaseItemKind.AUDIO || fType == BaseItemKind.GENRE || fType == BaseItemKind.MUSIC_ALBUM || fType == BaseItemKind.MUSIC_ARTIST || fType == BaseItemKind.MUSIC_GENRE) {
                    return cardHeight;
                } else if (fType == BaseItemKind.COLLECTION_FOLDER && CollectionType.MUSIC.equals(folder.getCollectionType())) {
                    return cardHeight;
                } else {
                    return cardHeight * ImageHelper.ASPECT_RATIO_2_3;
                }
            case THUMB:
                return cardHeight * ImageHelper.ASPECT_RATIO_16_9;
            case BANNER:
                return cardHeight * ImageHelper.ASPECT_RATIO_BANNER;
            default:
                throw new IllegalStateException("Unexpected value: " + imageType);
        }
    }

    private double getCardHeightBy(final double cardWidth, ImageType imageType, BaseItemDto folder) {
        switch (imageType) {
            case POSTER:
                // special handling for square posters
                BaseItemKind fType = folder.getType();
                if (fType == BaseItemKind.AUDIO || fType == BaseItemKind.GENRE || fType == BaseItemKind.MUSIC_ALBUM || fType == BaseItemKind.MUSIC_ARTIST || fType == BaseItemKind.MUSIC_GENRE) {
                    return cardWidth;
                } else if (fType == BaseItemKind.COLLECTION_FOLDER && CollectionType.MUSIC.equals(folder.getCollectionType())) {
                    return cardWidth;
                } else {
                    return cardWidth / ImageHelper.ASPECT_RATIO_2_3;
                }
            case THUMB:
                return cardWidth / ImageHelper.ASPECT_RATIO_16_9;
            case BANNER:
                return cardWidth / ImageHelper.ASPECT_RATIO_BANNER;
            default:
                throw new IllegalArgumentException("Unexpected value: " + imageType);
        }
    }

    private void setDefaultGridRowCols(PosterSize posterSize, ImageType imageType) {
        // HINT: use uneven Rows/Cols if possible, so selected middle lines up with TV middle!
        if (mGridPresenter instanceof VerticalGridPresenter) {
            int numCols;
            switch (posterSize) {
                // JellyChan: medium = 8 posters per row, like the web TV layout
                case SMALLEST:
                    numCols = imageType.equals(ImageType.BANNER) ? 5 : imageType.equals(ImageType.THUMB) ? 7 : 12;
                    break;
                case SMALL:
                    numCols = imageType.equals(ImageType.BANNER) ? 4 : imageType.equals(ImageType.THUMB) ? 6 : 10;
                    break;
                case MED:
                    numCols = imageType.equals(ImageType.BANNER) ? 3 : imageType.equals(ImageType.THUMB) ? 5 : 8;
                    break;
                case LARGE:
                    numCols = imageType.equals(ImageType.BANNER) ? 2 : imageType.equals(ImageType.THUMB) ? 4 : 6;
                    break;
                case X_LARGE:
                    numCols = imageType.equals(ImageType.BANNER) ? 2 : imageType.equals(ImageType.THUMB) ? 3 : 5;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + mPosterSizeSetting);
            }
            ((VerticalGridPresenter) mGridPresenter).setNumberOfColumns(numCols);
        } else if (mGridPresenter instanceof HorizontalGridPresenter) {
            int numRows;
            switch (posterSize) {
                case SMALLEST:
                    numRows = imageType.equals(ImageType.BANNER) ? 13 : imageType.equals(ImageType.THUMB) ? 7 : 5;
                    break;
                case SMALL:
                    numRows = imageType.equals(ImageType.BANNER) ? 11 : imageType.equals(ImageType.THUMB) ? 6 : 4;
                    break;
                case MED:
                    numRows = imageType.equals(ImageType.BANNER) ? 9 : imageType.equals(ImageType.THUMB) ? 5 : 3;
                    break;
                case LARGE:
                    numRows = imageType.equals(ImageType.BANNER) ? 7 : imageType.equals(ImageType.THUMB) ? 4 : 2;
                    break;
                case X_LARGE:
                    numRows = imageType.equals(ImageType.BANNER) ? 5 : imageType.equals(ImageType.THUMB) ? 2 : 1;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + mPosterSizeSetting);
            }
            ((HorizontalGridPresenter) mGridPresenter).setNumberOfRows(numRows);
        }
    }

    private void setAutoCardGridValues() {
        if (mGridPresenter == null) {
            Timber.e("Invalid presenter, cant calculate CardGridValues!");
            return;
        }
        double cardScaling = Math.max(mCardFocusScale - 1.0, 0.0);
        int cardHeightInt = 100;
        int spacingHorizontalInt = 0;
        int spacingVerticalInt = 0;
        int paddingLeftInt = 0;
        int paddingTopInt = 0;
        int numRows = 0;
        int numCols = 0;
        int numCardsScreen = 0; // number of cards visible, including cutoff's

        if (mGridPresenter instanceof HorizontalGridPresenter) {
            numRows = ((HorizontalGridPresenter) mGridPresenter).getNumberOfRows();
            if (numRows == 1) { // reduce size so minimal cards are shown
                numRows = 0;
                numCols = MIN_NUM_CARDS;
            }
        } else if (mGridPresenter instanceof VerticalGridPresenter) {
            numCols = ((VerticalGridPresenter) mGridPresenter).getNumberOfColumns();
        }

        if (numRows > 0) {
            double paddingPct = cardScaling / numRows;
            double spacingPct = ((paddingPct / 2.0) * CARD_SPACING_PCT) * (numRows - 1);

            double wastedSpacePct = paddingPct + spacingPct;
            double usableCardSpace = mGridHeight / (1.0 + wastedSpacePct); // decrease size
            double cardHeight = usableCardSpace / numRows;

            // fix any rounding errors and make pixel perfect
            cardHeightInt = (int) Math.round(cardHeight);
            double cardPaddingTopBottomAdj = cardHeightInt * cardScaling;
            spacingVerticalInt = Math.max((int) (Math.round((cardPaddingTopBottomAdj / 2.0) * CARD_SPACING_PCT)), 0); // round spacing
            int paddingTopBottomInt = mGridHeight - ((cardHeightInt * numRows) + (spacingVerticalInt * (numRows - 1)));
            paddingTopInt = Math.max(paddingTopBottomInt / 2, 0);

            int sumSize = (cardHeightInt * numRows) + (spacingVerticalInt * (numRows - 1)) + (paddingTopInt * 2);
            if (Math.abs(sumSize - mGridHeight) > 2) {
                Timber.w("setAutoCardGridValues calculation delta > 2, something is off GridHeight <%s> sumSize <%s>!", mGridHeight, sumSize);
            }
            int cardWidthInt = (int) getCardWidthBy(cardHeightInt, mImageType, mFolder);
            paddingLeftInt = (int) Math.round((cardWidthInt * cardScaling) / 2.0);
            spacingHorizontalInt = Math.max((int) (Math.round(paddingLeftInt * CARD_SPACING_PCT)), 0); // round spacing
            if (mImageType == ImageType.BANNER) {
                spacingHorizontalInt = Math.max((int) (Math.round(paddingLeftInt * CARD_SPACING_HORIZONTAL_BANNER_PCT)), 0); // round spacing
            }
            int cardsCol = (int) Math.round(((double) mGridWidth / (cardWidthInt + spacingHorizontalInt)) + 0.5);
            mCardsScreenEst = numRows * cardsCol;
            mCardsScreenStride = numRows;
        } else if (numCols > 0) {
            double paddingPct = cardScaling / numCols;
            double spacingPct = ((paddingPct / 2.0) * CARD_SPACING_PCT) * (numCols - 1);
            if (mImageType == ImageType.BANNER) {
                spacingPct = ((paddingPct / 2.0) * CARD_SPACING_HORIZONTAL_BANNER_PCT) * (numCols - 1);
            }

            double wastedSpacePct = paddingPct + spacingPct;
            double usableCardSpace = mGridWidth / (1.0 + wastedSpacePct); // decrease size
            double cardWidth = usableCardSpace / numCols;

            // fix any rounding errors and make pixel perfect
            cardHeightInt = (int) Math.round(getCardHeightBy(cardWidth, mImageType, mFolder));
            int cardWidthInt = (int) getCardWidthBy(cardHeightInt, mImageType, mFolder);
            double cardPaddingLeftRightAdj = cardWidthInt * cardScaling;
            spacingHorizontalInt = Math.max((int) (Math.round((cardPaddingLeftRightAdj / 2.0) * CARD_SPACING_PCT)), 0); // round spacing
            if (mImageType == ImageType.BANNER) {
                spacingHorizontalInt = Math.max((int) (Math.round((cardPaddingLeftRightAdj / 2.0) * CARD_SPACING_HORIZONTAL_BANNER_PCT)), 0); // round spacing
            }
            int paddingLeftRightInt = mGridWidth - ((cardWidthInt * numCols) + (spacingHorizontalInt * (numCols - 1)));
            paddingLeftInt = Math.max(paddingLeftRightInt / 2, 0);

            int sumSize = (cardWidthInt * numCols) + (spacingHorizontalInt * (numCols - 1)) + (paddingLeftInt * 2);
            if (Math.abs(sumSize - mGridWidth) > 2) {
                Timber.w("setAutoCardGridValues calculation delta > 2, something is off GridWidth <%s> sumSize <%s>!", mGridWidth, sumSize);
            }
            paddingTopInt = (int) Math.round((cardHeightInt * cardScaling) / 2.0);
            spacingVerticalInt = Math.max((int) (Math.round(paddingTopInt * CARD_SPACING_PCT)), 0); // round spacing
            int cardsRow = (int) Math.round(((double) mGridHeight / (cardHeightInt + spacingVerticalInt)) + 0.5);
            mCardsScreenEst = numCols * cardsRow;
            mCardsScreenStride = numCols;
        }

        Timber.d("numCardsScreen <%s>", numCardsScreen);

        if (mCardHeight != cardHeightInt) {
            mDirty = true;
        }
        mCardHeight = cardHeightInt;
        mGridItemSpacingHorizontal = spacingHorizontalInt;
        mGridItemSpacingVertical = spacingVerticalInt;
        mGridPaddingLeft = paddingLeftInt;
        mGridPaddingTop = paddingTopInt;
    }

    private void setupQueries() {
        if (mFolder.getType() == BaseItemKind.USER_VIEW || mFolder.getType() == BaseItemKind.COLLECTION_FOLDER) {
            CollectionType type = mFolder.getCollectionType() != null ? mFolder.getCollectionType() : CollectionType.UNKNOWN;
            if (type == CollectionType.MUSIC) {
                //Special queries needed for album artists
                String includeType = getArguments().getString(Extras.IncludeType, null);
                if ("AlbumArtist".equals(includeType)) {
                    setRowDef(new BrowseRowDef("", BrowsingUtils.createAlbumArtistsRequest(mParentId), CHUNK_SIZE_MINIMUM, new ChangeTriggerType[]{}));
                    return;
                } else if ("Artist".equals(includeType)) {
                    setRowDef(new BrowseRowDef("", BrowsingUtils.createArtistsRequest(mParentId), CHUNK_SIZE_MINIMUM, new ChangeTriggerType[]{}));
                    return;
                }
            }
        }

        setRowDef(new BrowseRowDef("", BrowsingUtils.createBrowseGridItemsRequest(mFolder), CHUNK_SIZE_MINIMUM, false, true));
    }

    @Override
    public void onResume() {
        super.onResume();

        PosterSize posterSizeSetting = libraryPreferences.get(LibraryPreferences.Companion.getPosterSize());
        ImageType imageType = libraryPreferences.get(LibraryPreferences.Companion.getImageType());
        GridDirection gridDirection = libraryPreferences.get(LibraryPreferences.Companion.getGridDirection());

        if (mImageType != imageType || mPosterSizeSetting != posterSizeSetting || mGridDirection != gridDirection || mDirty) {
            determiningPosterSize = true;

            mImageType = imageType;
            mPosterSizeSetting = posterSizeSetting;
            mGridDirection = gridDirection;

            if (mGridDirection.equals(GridDirection.VERTICAL) && (mGridPresenter == null || !(mGridPresenter instanceof VerticalGridPresenter))) {
                setGridPresenter(new VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_LARGE, false));
            } else if (mGridDirection.equals(GridDirection.HORIZONTAL) && (mGridPresenter == null || !(mGridPresenter instanceof HorizontalGridPresenter))) {
                setGridPresenter(new HorizontalGridPresenter());
            }
            setDefaultGridRowCols(mPosterSizeSetting, mImageType);
            setAutoCardGridValues();
            createGrid();
            loadGrid();
            determiningPosterSize = false;
        }

        if (!justLoaded) {
            //Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
            if (mAdapter != null) {
                mHandler.postDelayed(() -> {
                    if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                        return;

                    if (mAdapter != null && mAdapter.size() > 0) {
                        if (!mAdapter.ReRetrieveIfNeeded()) {
                            refreshCurrentItem();
                        }
                    }
                }, 500);
            }
        } else {
            justLoaded = false;
        }
    }

    private void buildAdapter() {
        mCardPresenter = new CardPresenter(mGridDirection.equals(GridDirection.VERTICAL), mImageType, mCardHeight, true); // JellyChan: title + year under posters

        Timber.d("buildAdapter cardHeight <%s> getCardWidthBy <%s> chunks <%s> type <%s>", mCardHeight, (int) getCardWidthBy(mCardHeight, mImageType, mFolder), mRowDef.getChunkSize(), mRowDef.getQueryType().toString());

        // adapt chunk size if needed
        int chunkSize = mRowDef.getChunkSize();
        if (mCardsScreenEst > 0 && mCardsScreenEst >= chunkSize) {
            chunkSize = Math.min(mCardsScreenEst + mCardsScreenStride, 150); // cap at 150
            Timber.d("buildAdapter adjusting chunkSize to <%s> screenEst <%s>", chunkSize, mCardsScreenEst);
        }
        chunkSize=100;

        switch (mRowDef.getQueryType()) {
            case NextUp:
                mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getNextUpQuery(), true, mCardPresenter, null);
                break;
            case Views:
                mAdapter = new ItemRowAdapter(requireContext(), GetUserViewsRequest.INSTANCE, mCardPresenter, null);
                break;
            case SimilarSeries:
                mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getSimilarQuery(), QueryType.SimilarSeries, mCardPresenter, null);
                break;
            case SimilarMovies:
                mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getSimilarQuery(), QueryType.SimilarMovies, mCardPresenter, null);
                break;
            case LiveTvChannel:
                mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getTvChannelQuery(), 40, mCardPresenter, null);
                break;
            case LiveTvProgram:
                mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getProgramQuery(), mCardPresenter, null);
                break;
            case LiveTvRecording:
                mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getRecordingQuery(), chunkSize, mCardPresenter, null);
                break;
            case Artists:
                mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getArtistsQuery(), chunkSize, mCardPresenter, null);
                break;
            case AlbumArtists:
                mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getAlbumArtistsQuery(), chunkSize, mCardPresenter, null);
                break;
            default:
                mAdapter = new ItemRowAdapter(requireContext(), mRowDef.getQuery(), chunkSize, mRowDef.getPreferParentThumb(), mRowDef.isStaticHeight(), mCardPresenter, null);
                break;
        }
        mDirty = false;

        FilterOptions filters = new FilterOptions();
        filters.setFavoriteOnly(libraryPreferences.get(LibraryPreferences.Companion.getFilterFavoritesOnly()));
        filters.setUnwatchedOnly(libraryPreferences.get(LibraryPreferences.Companion.getFilterUnwatchedOnly()));

        mAdapter.setRetrieveFinishedListener(new EmptyResponse(getLifecycle()) {
            @Override
            public void onResponse() {
                if (!isActive()) return;
                setStatusText(mFolder.getName());
                if (mCurrentItem == null) { // don't mess-up pos via loadMoreItemsIfNeeded
                    setItem(null);
                    updateCounter(mAdapter.getTotalItems() > 0 ? 1 : 0);
                }
                // JellyChan: the A-Z index only makes sense when sorted by name
                if (binding != null) binding.jcAlphaIndex.setVisibility(ItemSortBy.SORT_NAME.equals(mAdapter.getSortBy()) ? View.VISIBLE : View.INVISIBLE);
                if (mAdapter.getItemsLoaded() == 0) {
                    mGridView.setFocusable(false);
                    mHandler.postDelayed(() -> {
                        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                            return;

                        binding.title.setText(mFolder.getName());
                    }, 500);
                } else if (mGridView != null) {
                    mGridView.setFocusable(true);
                    mGridView.requestFocus();
                }
            }
        });
        mAdapter.setFilters(filters);

        updateAdapter();
    }

    public void loadGrid() {
        if (mCardPresenter == null || mAdapter == null || mDirty) {
            buildAdapter();
        }

        mAdapter.setSortBy(getSortOption(libraryPreferences.get(LibraryPreferences.Companion.getSortBy())));
        mAdapter.Retrieve();
    }

    private ImageButton mSortButton;
    private ImageButton mSettingsButton;
    private ImageButton mUnwatchedButton;
    private ImageButton mFavoriteButton;
    private ImageButton mLetterButton;

    private void updateDisplayPrefs() {
        CoroutineUtils.runOnLifecycle(getLifecycle(), (coroutineScope, continuation) -> {
            libraryPreferences.set(LibraryPreferences.Companion.getFilterFavoritesOnly(), mAdapter.getFilters().isFavoriteOnly());
            libraryPreferences.set(LibraryPreferences.Companion.getFilterUnwatchedOnly(), mAdapter.getFilters().isUnwatchedOnly());
            libraryPreferences.set(LibraryPreferences.Companion.getSortBy(), mAdapter.getSortBy());
            libraryPreferences.set(LibraryPreferences.Companion.getSortOrder(), getSortOption(mAdapter.getSortBy()).order);
            return libraryPreferences.commit(continuation);
        });
    }

    private void addTools() {
        // JellyChan: header title, tabs, tools row and A-Z index like the web TV layout
        binding.jcLibraryTitle.setText(mainTitle);
        binding.jcLibraryBack.setOnClickListener(v -> {
            if (navigationRepository.getValue().getCanGoBack()) navigationRepository.getValue().goBack();
            else navigationRepository.getValue().reset(Destinations.INSTANCE.getHome());
        });
        setupTabs();
        setupAlphaIndex();

        // Shuffle
        ImageButton shuffle = createToolButton(R.drawable.ic_shuffle, R.string.lbl_shuffle_all);
        shuffle.setOnClickListener(v -> KoinJavaComponent.<org.jellyfin.androidtv.util.PlaybackHelper>get(org.jellyfin.androidtv.util.PlaybackHelper.class)
                .retrieveAndPlay(mFolder.getId(), true, requireContext()));
        binding.toolBar.addView(shuffle);

        // View settings
        if (mFolder.getDisplayPreferencesId() != null) {
            MutableStateFlow<Boolean> settingsVisible = BrowseGridFragmentHelperKt.createSettingsVisibility(BrowseGridFragment.this);
            mSettingsButton = createToolButton(R.drawable.ic_grid, R.string.lbl_settings);
            mSettingsButton.setOnClickListener(v -> settingsVisible.setValue(true));
            binding.toolBar.addView(mSettingsButton);
            BrowseGridFragmentHelperKt.addSettings(BrowseGridFragment.this, binding.settings, mFolder.getId(), mFolder.getDisplayPreferencesId(), settingsVisible);
        }

        // Sort
        mSortButton = createToolButton(R.drawable.ic_abc, R.string.lbl_sort_by);
        mSortButton.setOnClickListener(v -> {
            PopupMenu sortMenu = new PopupMenu(getActivity(), mSortButton, Gravity.CENTER);
            for (Map.Entry<Integer, SortOption> entry : sortOptions.entrySet()) {
                MenuItem item = sortMenu.getMenu().add(0, entry.getKey(), entry.getKey(), entry.getValue().name);
                item.setChecked(entry.getValue().value.equals(libraryPreferences.get(LibraryPreferences.Companion.getSortBy())));
            }
            sortMenu.getMenu().setGroupCheckable(0, true, true);
            sortMenu.setOnMenuItemClickListener(item -> {
                mAdapter.setSortBy(Objects.requireNonNull(sortOptions.get(item.getItemId())));
                mAdapter.Retrieve();
                item.setChecked(true);
                updateDisplayPrefs();
                return true;
            });
            sortMenu.show();
        });
        binding.toolBar.addView(mSortButton);

        // Filter: unwatched only
        if (mRowDef.getQueryType() == QueryType.Items) {
            mUnwatchedButton = createToolButton(R.drawable.ic_filter, R.string.lbl_unwatched);
            mUnwatchedButton.setActivated(mAdapter.getFilters().isUnwatchedOnly());
            mUnwatchedButton.setOnClickListener(v -> {
                FilterOptions filters = mAdapter.getFilters();
                if (filters == null) filters = new FilterOptions();
                filters.setUnwatchedOnly(!filters.isUnwatchedOnly());
                mUnwatchedButton.setActivated(filters.isUnwatchedOnly());
                mAdapter.setFilters(filters);
                mAdapter.Retrieve();
                updateDisplayPrefs();
            });
            binding.toolBar.addView(mUnwatchedButton);
        }
    }

    private ImageButton createToolButton(int icon, int description) {
        int size = Utils.convertDpToPixel(requireContext(), 30);
        int pad = Utils.convertDpToPixel(requireContext(), 7);
        int margin = Utils.convertDpToPixel(requireContext(), 6);
        ImageButton button = new ImageButton(requireContext(), null, 0, R.style.Button_Icon);
        button.setImageResource(icon);
        button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        button.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginStart(margin);
        lp.setMarginEnd(margin);
        button.setLayoutParams(lp);
        button.setContentDescription(getString(description));
        return button;
    }

    private android.widget.Button mLibraryTab;
    private android.widget.Button mFavoritesTab;

    private android.widget.Button createTab(String text) {
        android.widget.Button tab = new android.widget.Button(requireContext(), null, 0);
        tab.setText(text);
        tab.setAllCaps(false);
        tab.setTextSize(12);
        tab.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.inter_medium));
        tab.setTextColor(androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.jc_tab_text));
        tab.setBackgroundResource(R.drawable.jc_tab_back);
        tab.setStateListAnimator(null);
        tab.setMinWidth(0);
        tab.setMinHeight(0);
        tab.setMinimumWidth(0);
        tab.setMinimumHeight(0);
        int ph = Utils.convertDpToPixel(requireContext(), 15);
        tab.setPadding(ph, 0, ph, 0);
        tab.setGravity(Gravity.CENTER);
        tab.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Utils.convertDpToPixel(requireContext(), 29));
        int m = Utils.convertDpToPixel(requireContext(), 6);
        lp.setMarginStart(m);
        lp.setMarginEnd(m);
        tab.setLayoutParams(lp);
        binding.jcTabs.addView(tab);
        return tab;
    }

    private void setupTabs() {
        boolean isMovies = mFolder.getCollectionType() == CollectionType.MOVIES;
        boolean isShows = mFolder.getCollectionType() == CollectionType.TVSHOWS;
        if (!isMovies && !isShows) {
            binding.jcTabs.setVisibility(View.GONE);
            return;
        }

        boolean favoritesOnly = mAdapter.getFilters() != null && mAdapter.getFilters().isFavoriteOnly();

        mLibraryTab = createTab(mainTitle);
        mLibraryTab.setActivated(!favoritesOnly);
        mLibraryTab.setOnClickListener(v -> setFavoritesOnly(false));
        mLibraryTab.setNextFocusLeftId(R.id.jc_library_back);
        mLibraryTab.setNextFocusUpId(R.id.jc_library_back);

        if (isMovies) {
            android.widget.Button suggestions = createTab(getString(R.string.lbl_suggested));
            suggestions.setOnClickListener(v -> navigationRepository.getValue().navigate(Destinations.INSTANCE.librarySuggestions(mFolder)));
        }

        mFavoritesTab = createTab(getString(R.string.lbl_favorites));
        mFavoritesTab.setActivated(favoritesOnly);
        mFavoritesTab.setOnClickListener(v -> setFavoritesOnly(true));

        android.widget.Button genres = createTab(getString(R.string.lbl_genres));
        genres.setOnClickListener(v -> navigationRepository.getValue().navigate(Destinations.INSTANCE.libraryByGenres(mFolder, isMovies ? "Movie" : "Series")));
    }

    private void setFavoritesOnly(boolean favoritesOnly) {
        FilterOptions filters = mAdapter.getFilters();
        if (filters == null) filters = new FilterOptions();
        if (filters.isFavoriteOnly() != favoritesOnly) {
            filters.setFavoriteOnly(favoritesOnly);
            mAdapter.setFilters(filters);
            mAdapter.Retrieve();
            updateDisplayPrefs();
        }
        if (mLibraryTab != null) mLibraryTab.setActivated(!favoritesOnly);
        if (mFavoritesTab != null) mFavoritesTab.setActivated(favoritesOnly);
    }

    private void setupAlphaIndex() {
        String letters = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int size = Utils.convertDpToPixel(requireContext(), 15);
        for (int i = 0; i < letters.length(); i++) {
            final String letter = String.valueOf(letters.charAt(i));
            TextView view = new TextView(requireContext());
            view.setText(letter);
            view.setTextSize(7.5f);
            view.setGravity(Gravity.CENTER);
            view.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.inter_medium));
            view.setTextColor(androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.jc_alpha_text));
            view.setBackgroundResource(R.drawable.jc_round_focus);
            view.setFocusable(true);
            view.setClickable(true);
            view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            view.setOnClickListener(v -> {
                mAdapter.setStartLetter("#".equals(letter) ? null : letter);
                loadGrid();
            });
            binding.jcAlphaIndex.addView(view);
        }
    }

    class JumplistPopup {
        private final int WIDTH = Utils.convertDpToPixel(requireContext(), 900);
        private final int HEIGHT = Utils.convertDpToPixel(requireContext(), 55);

        private final PopupWindow popupWindow;
        private final AlphaPickerView alphaPicker;

        JumplistPopup() {
            PopupEmptyBinding layout = PopupEmptyBinding.inflate(getLayoutInflater(), binding.rowsFragment, false);
            popupWindow = new PopupWindow(layout.emptyPopup, WIDTH, HEIGHT, true);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setAnimationStyle(R.style.WindowAnimation_SlideTop);

            alphaPicker = new AlphaPickerView(requireContext(), null);
            alphaPicker.setOnAlphaSelected(letter -> {
                mAdapter.setStartLetter(letter.toString());
                loadGrid();
                dismiss();
                return null;
            });

            layout.emptyPopup.addView(alphaPicker);
        }

        public void show() {
            popupWindow.showAtLocation(binding.rowsFragment, Gravity.TOP, binding.rowsFragment.getLeft(), binding.rowsFragment.getTop());
            if (mAdapter.getStartLetter() != null && !mAdapter.getStartLetter().isEmpty()) {
                alphaPicker.focus(mAdapter.getStartLetter().charAt(0));
            }
        }

        public void dismiss() {
            if (popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
        }
    }

    private void setupEventListeners() {
        if (mGridPresenter != null) {
            if (mGridPresenter instanceof HorizontalGridPresenter)
                ((HorizontalGridPresenter) mGridPresenter).setOnItemViewClickedListener(mClickedListener);
            else if (mGridPresenter instanceof VerticalGridPresenter)
                ((VerticalGridPresenter) mGridPresenter).setOnItemViewClickedListener(mClickedListener);
        }
        mClickedListener.registerListener(new ItemViewClickedListener());
        mSelectedListener.registerListener(new ItemViewSelectedListener());

        CoroutineUtils.readCustomMessagesOnLifecycle(getLifecycle(), customMessageRepository.getValue(), message -> {
            if (message.equals(CustomMessage.RefreshCurrentItem.INSTANCE)) refreshCurrentItem();
            return null;
        });
    }

    private void refreshCurrentItem() {
        if (mCurrentItem == null) return;
        Timber.i("Refresh item \"%s\"", mCurrentItem.getFullName(requireContext()));
        ItemRowAdapterHelperKt.refreshItem(mAdapter, api.getValue(), this, mCurrentItem, () -> {
            //Now - if filtered make sure we still pass
            if (mAdapter.getFilters() == null) return null;
            if ((mAdapter.getFilters().isFavoriteOnly() && !mCurrentItem.isFavorite()) || (mAdapter.getFilters().isUnwatchedOnly() && mCurrentItem.isPlayed())) {
                // if we are about to remove the current item, throw focus to toolbar so framework doesn't crash
                binding.toolBar.requestFocus();
                mAdapter.remove(mCurrentItem);
                mAdapter.setTotalItems(mAdapter.getTotalItems() - 1);
                updateCounter(mAdapter.indexOf(mCurrentItem));
            }
            return null;
        });
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(final Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (!(item instanceof BaseRowItem)) return;
            itemLauncher.getValue().launch((BaseRowItem) item, mAdapter, requireContext());
        }
    }

    private final Runnable mDelayedSetItem = new Runnable() {
        @Override
        public void run() {
            if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;

            // JellyChan: plain navy background on the library page, like the web
            if (!mGridDirection.equals(GridDirection.VERTICAL)) backgroundService.getValue().setBackground(mCurrentItem.getBaseItem());
            else backgroundService.getValue().clearBackgrounds();
            setItem(mCurrentItem);
        }
    };

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            mHandler.removeCallbacks(mDelayedSetItem);
            if (!(item instanceof BaseRowItem)) {
                mCurrentItem = null;
                binding.title.setText(mainTitle);
                //fill in default background
                backgroundService.getValue().clearBackgrounds();
            } else {
                mCurrentItem = (BaseRowItem) item;
                // JellyChan: the header keeps the library name
                mHandler.postDelayed(mDelayedSetItem, VIEW_SELECT_UPDATE_DELAY);

                if (!determiningPosterSize)
                    mAdapter.loadMoreItemsIfNeeded(mAdapter.indexOf(mCurrentItem));
            }
        }
    }
}
