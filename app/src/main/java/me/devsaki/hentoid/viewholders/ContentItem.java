package me.devsaki.hentoid.viewholders;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ContentHelper;

import static androidx.core.view.ViewCompat.requireViewById;

public class ContentItem extends AbstractItem<ContentItem.ContentViewHolder> {

    private final static RequestOptions glideRequestOptions = new RequestOptions()
            .centerInside()
            .error(R.drawable.ic_placeholder);

    // Corresponding content
    // NB : When using PagedListAdapter, this does _not_ always match the freshest instance
    // of the object stored in the adapter since it is constantly updated as data evolves,
    // and does not trigger binding for each of these updates
    // (binding only happens when the content is different / see DiffUtil)
    private Content content;

    public ContentItem() {
        content = null;
    }

    public ContentItem(@NonNull Content content) {
        this.content = content;
        setIdentifier(content.getId());
        setSelectable(true);
    }

    public Content getContent() {
        return content;
    }

    @NotNull
    @Override
    public ContentItem.ContentViewHolder getViewHolder(@NotNull View view) {
        return new ContentViewHolder(view);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_download;
    }

    @Override
    public int getType() {
        return R.id.content;
    }


    public static class ContentViewHolder extends FastAdapter.ViewHolder<ContentItem> {

        private final View baseLayout;
        private final TextView tvTitle;
        private final View ivNew;
        private final ImageView ivCover;
        private final TextView tvSeries;
        private final TextView tvArtist;
        private final TextView tvPages;
        private final TextView tvTags;
        private final ImageView ivSite;
        private final ImageView ivError;
        private final ImageView ivFavourite;

        ContentViewHolder(View view) {
            super(view);
            baseLayout = requireViewById(itemView, R.id.item);
            tvTitle = requireViewById(itemView, R.id.tvTitle);
            ivNew = requireViewById(itemView, R.id.lineNew);
            ivCover = requireViewById(itemView, R.id.ivCover);
            tvSeries = requireViewById(itemView, R.id.tvSeries);
            tvArtist = requireViewById(itemView, R.id.tvArtist);
            tvPages = requireViewById(itemView, R.id.tvPages);
            tvTags = requireViewById(itemView, R.id.tvTags);
            ivSite = requireViewById(itemView, R.id.ivSite);
            ivError = requireViewById(itemView, R.id.ivError);
            ivFavourite = requireViewById(itemView, R.id.ivFavourite);
        }


        @Override
        public void bindView(@NotNull ContentItem item, @NotNull List<Object> list) {
            if (null == item.content) return; // Placeholders in paging mode

            updateLayoutVisibility(item.content);
            attachCover(item.content);
            attachTitle(item.content);
            attachSeries(item.content);
            attachArtist(item.content);
            attachPages(item.content);
            attachTags(item.content);
            attachButtons(item.content);
        }

        private void updateLayoutVisibility(Content content) {
            baseLayout.setVisibility(View.VISIBLE);
            ivNew.setVisibility((0 == content.getReads()) ? View.VISIBLE : View.GONE);

//            itemView.setSelected(content.isSelected());

//            ivError.setEnabled(!content.isSelected());
//            ivFavourite.setEnabled(!content.isSelected());
//            ivSite.setEnabled(!content.isSelected());

            if (content.isBeingDeleted())
                baseLayout.startAnimation(new BlinkAnimation(500, 250));
            else
                baseLayout.clearAnimation();
        }

        private void attachCover(Content content) {
            Context context = ivCover.getContext();
            Glide.with(context.getApplicationContext())
                    .load(ContentHelper.getThumb(content))
                    .apply(glideRequestOptions)
                    .into(ivCover);
        }

        private void attachTitle(Content content) {
            CharSequence title;
            Context context = tvTitle.getContext();
            if (content.getTitle() == null) {
                title = context.getText(R.string.work_untitled);
            } else {
                title = content.getTitle();
            }
            tvTitle.setText(title);
        }

        private void attachSeries(Content content) {
            Context context = tvSeries.getContext();
            String templateSeries = context.getResources().getString(R.string.work_series);
            List<Attribute> seriesAttributes = content.getAttributeMap().get(AttributeType.SERIE);
            if (seriesAttributes == null) {
                tvSeries.setText(templateSeries.replace("@series@", context.getResources().getString(R.string.work_untitled)));
            } else {
                StringBuilder seriesBuilder = new StringBuilder();
                for (int i = 0; i < seriesAttributes.size(); i++) {
                    Attribute attribute = seriesAttributes.get(i);
                    seriesBuilder.append(attribute.getName());
                    if (i != seriesAttributes.size() - 1) {
                        seriesBuilder.append(", ");
                    }
                }
                tvSeries.setText(templateSeries.replace("@series@", seriesBuilder));
            }
        }

        private void attachArtist(Content content) {
            Context context = tvArtist.getContext();
            String templateArtist = context.getResources().getString(R.string.work_artist);
            List<Attribute> attributes = new ArrayList<>();

            List<Attribute> artistAttributes = content.getAttributeMap().get(AttributeType.ARTIST);
            if (artistAttributes != null)
                attributes.addAll(artistAttributes);
            List<Attribute> circleAttributes = content.getAttributeMap().get(AttributeType.CIRCLE);
            if (circleAttributes != null)
                attributes.addAll(circleAttributes);

            if (attributes.isEmpty()) {
                tvArtist.setText(templateArtist.replace("@artist@", context.getResources().getString(R.string.work_untitled)));
            } else {
                List<String> allArtists = new ArrayList<>();
                for (Attribute attribute : attributes) {
                    allArtists.add(attribute.getName());
                }
                String artists = android.text.TextUtils.join(", ", allArtists);
                tvArtist.setText(templateArtist.replace("@artist@", artists));
            }
        }

        private void attachPages(Content content) {
            Context context = tvPages.getContext();
            String template = context.getResources().getString(R.string.work_pages);
            tvPages.setText(template.replace("@pages@", content.getQtyPages() + ""));
        }

        private void attachTags(Content content) {
            Context context = tvTags.getContext();
            List<Attribute> tagsAttributes = content.getAttributeMap().get(AttributeType.TAG);
            if (tagsAttributes == null) {
                tvTags.setText(context.getResources().getString(R.string.work_untitled));
            } else {
                List<String> allTags = new ArrayList<>();
                for (Attribute attribute : tagsAttributes) {
                    allTags.add(attribute.getName());
                }
                if (Build.VERSION.SDK_INT >= 24) {
                    allTags.sort(null);
                }
                String tags = android.text.TextUtils.join(", ", allTags);
                tvTags.setText(tags);
            }
        }

        private void attachButtons(final Content content) {
            // Source icon
            if (content.getSite() != null) {
                int img = content.getSite().getIco();
                ivSite.setImageResource(img);
            } else {
                ivSite.setImageResource(R.drawable.ic_stat_hentoid);
            }

            // When transitioning to the other state, button blinks with its target state
            if (content.isBeingFavourited()) {
                ivFavourite.startAnimation(new BlinkAnimation(500, 250));
                if (content.isFavourite()) {
                    ivFavourite.setImageResource(R.drawable.ic_fav_empty);
                } else {
                    ivFavourite.setImageResource(R.drawable.ic_fav_full);
                }
            } else {
                ivFavourite.clearAnimation();
                if (content.isFavourite()) {
                    ivFavourite.setImageResource(R.drawable.ic_fav_full);
                } else {
                    ivFavourite.setImageResource(R.drawable.ic_fav_empty);
                }
            }

            // Error icon
            if (content.getStatus() != null) {
                StatusContent status = content.getStatus();
                if (status == StatusContent.ERROR) {
                    ivError.setVisibility(View.VISIBLE);
                } else {
                    ivError.setVisibility(View.GONE);
                }
            }
        }

/*

        private void onFavouriteClicked() {
            adapter.getFavClickListener().accept(content);
            // Hack to make fav icon blink with PagedListAdapter by force-rebinding the holder
            // Call is delayed to give time for the adapter list to be updated by LiveData
            new Handler().postDelayed(() -> adapter.notifyItemChanged(getLayoutPosition()), 100);
        }

        private void attachOnClickListeners() {

            // Simple click
            itemView.setOnClickListener(v -> {
                if (adapter.getSelectedItemsCount() > 0) { // Selection mode is on -> select more
                    int itemPos = getLayoutPosition();
                    if (itemPos > -1) {
                        Content c = adapter.getItemAtPosition(itemPos); // Get the freshest content from the adapter
                        if (c != null) {
                            c.setSelected(!c.isSelected());
                            adapter.getSelectionChangedListener().accept(adapter.getSelectedItemsCount());
                            adapter.notifyItemChanged(itemPos);
                        }
                    }
                } else adapter.getOpenBookListener().accept(content); // Open book
            });

            // Long click = select item
            itemView.setOnLongClickListener(v -> {

                int itemPos = getLayoutPosition();
                Content c = adapter.getItemAtPosition(itemPos); // Get the freshest content from the adapter

                if (c != null && itemPos > -1 && !c.isBeingDeleted()) {
                    c.setSelected(!c.isSelected());
                    adapter.getSelectionChangedListener().accept(adapter.getSelectedItemsCount());
                    adapter.notifyItemChanged(itemPos);
                }

                return true;
            });
        }
 */

        public View getFavouriteButton() {
            return ivFavourite;
        }

        public View getSiteButton() {
            return ivSite;
        }

        public View getErrorButton() {
            return ivError;
        }

        @Override
        public void unbindView(@NotNull ContentItem item) {
            // Nothing to do here
        }
    }
}